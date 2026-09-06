package com.uteq.asistente_academico.service;

import com.uteq.asistente_academico.dto.ChatMensaje;
import com.uteq.asistente_academico.dto.TareaPendienteProjection;
import com.uteq.asistente_academico.entity.Horario;
import com.uteq.asistente_academico.entity.Usuario;
import com.uteq.asistente_academico.exception.ApiExternaException;
import com.uteq.asistente_academico.repository.DashboardRepository;
import com.uteq.asistente_academico.repository.HorarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chatbot academico: arma un system prompt fijo (reglas de alcance y
 * tono) + un bloque de contexto del estudiante autenticado (tareas
 * pendientes, horario de hoy, fecha/hora actual), y llama a la API de
 * Gemini pasando ese system prompt + el historial de la conversacion +
 * el mensaje nuevo. El contexto se reconstruye en cada llamada con datos
 * en vivo -- no se cachea ni se persiste nada del lado del servidor.
 */
@Service
public class ChatbotService {

    private static final String MODELO = "gemini-2.5-flash";
    // La clave va en el header x-goog-api-key (recomendado por Google),
    // no como query param -- evita que quede expuesta en logs de
    // request/exception (URLs completas suelen loguearse tal cual,
    // headers no tanto, y ademas es la forma que Google documenta).
    private static final String URL_GEMINI =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODELO + ":generateContent";

    // Cuantas tareas pendientes como maximo entran en el contexto: cuida
    // la cuota gratuita de Gemini y evita que el prompt crezca sin limite
    // si el estudiante acumula muchas tareas. fn_listar_tareas_pendientes
    // ya devuelve solo pendientes (no vencidas) ordenadas por
    // fecha_entrega ASC, asi que limitar a las primeras N ya da "las mas
    // urgentes primero" sin necesidad de reordenar aca.
    private static final int MAX_TAREAS_EN_CONTEXTO = 15;

    private static final String[] DIAS_SEMANA = {
            "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo"
    };
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm");

    // Texto confirmado con el usuario antes de implementar -- no
    // modificar sin volver a revisarlo con él. El bloque de contexto del
    // estudiante se concatena despues de esto en cada llamada (ver
    // construirContexto), nunca se cachea entre requests.
    private static final String SYSTEM_PROMPT_BASE = """
            Sos el Asistente Virtual Académico de la Universidad Técnica Estatal de
            Quevedo (UTEQ). Ayudás a estudiantes con consultas académicas: materias,
            conceptos de estudio, y preguntas sobre sus propias tareas, horario y
            materias (los datos reales del estudiante están en la sección "Contexto
            del estudiante" más abajo, actualizados al momento de esta consulta).

            Reglas que debés seguir siempre, sin excepción:

            1. ALCANCE TEMÁTICO: respondé únicamente consultas académicas — materias,
               conceptos de estudio, dudas sobre las tareas/horario/materias reales
               del estudiante. Si la pregunta es sobre deportes, entretenimiento,
               noticias, cultura general o cualquier otro tema no académico,
               rechazala cortésmente en una o dos oraciones e invitá al estudiante a
               volver a una consulta académica. No profundices en el tema no
               académico antes de rechazarlo.

            2. NO RESUELVAS TAREAS NI EJERCICIOS PUNTUALES: si el estudiante te pide
               la respuesta final a un ejercicio, problema o tarea concreta, no se la
               des. En su lugar: explicá el concepto involucrado, sugerí un enfoque o
               los pasos para llegar a la solución, guialo con preguntas que lo hagan
               pensar, y si ayuda, dale un ejemplo genérico similar (con otros
               números o otro enunciado) resuelto paso a paso — pero nunca resuelvas
               el ejercicio exacto que te trajo con sus datos reales.

            3. FUERA DE TU COMPETENCIA: no des consejos médicos, legales, financieros
               ni psicológicos, aunque te los pidan disfrazados de duda académica. Si
               surge algo así, decilo con claridad y sugerí que consulte a un
               profesional o a la instancia correspondiente de la universidad.

            4. TONO: formal pero cercano — como un profesor auxiliar que quiere
               ayudar de verdad, nunca condescendiente, nunca cortante. Respuestas
               claras y no más largas de lo necesario.

            5. DATOS DEL ESTUDIANTE: usá el contexto de abajo para responder
               preguntas puntuales sobre SU situación real (ej. "¿qué tareas tengo
               esta semana?", "¿a qué hora es mi próxima clase?"). Si te preguntan
               algo sobre sus datos que no está en ese contexto, decí que no tenés
               esa información en vez de inventarla.""";

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private HorarioRepository horarioRepository;

    @Autowired
    @Qualifier("geminiRestTemplate")
    private RestTemplate restTemplate;

    @Value("${gemini.api.key}")
    private String apiKey;

    public String responder(Usuario estudiante, String mensajeNuevo, List<ChatMensaje> historial) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ApiExternaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "El asistente virtual no está disponible en este momento (falta configuración).", null);
        }

        String systemInstruction = SYSTEM_PROMPT_BASE + "\n\n" + construirContexto(estudiante);
        return llamarGemini(systemInstruction, historial, mensajeNuevo);
    }

    private String construirContexto(Usuario estudiante) {
        LocalDateTime ahora = LocalDateTime.now();

        List<TareaPendienteProjection> pendientes = dashboardRepository.listarTareasPendientes(estudiante.getIdUsuario());
        String listaTareas = pendientes.isEmpty()
                ? "Sin tareas pendientes."
                : pendientes.stream()
                        .limit(MAX_TAREAS_EN_CONTEXTO)
                        .map(t -> "- " + t.getTitulo() + " (" + t.getMateria() + ") — vence "
                                + t.getFechaEntrega().format(FORMATO_FECHA) + " "
                                + (t.getHoraLimite() != null ? t.getHoraLimite().format(FORMATO_HORA) : "23:59")
                                + ", " + formatearCuentaRegresiva(t.getSegundosRestantes()))
                        .collect(Collectors.joining("\n"));

        String diaHoy = DIAS_SEMANA[ahora.getDayOfWeek().getValue() - 1];
        List<Horario> horarioHoy = horarioRepository.findMisHorarios(estudiante.getIdUsuario()).stream()
                .filter(h -> diaHoy.equalsIgnoreCase(h.getDiaSemana()))
                .collect(Collectors.toList());
        String listaHorario = horarioHoy.isEmpty()
                ? "Sin clases registradas para hoy."
                : horarioHoy.stream()
                        .map(h -> "- " + h.getHoraInicio().format(FORMATO_HORA) + "–" + h.getHoraFin().format(FORMATO_HORA)
                                + " " + h.getMateria().getNombre()
                                + (h.getAula() != null ? " (aula " + h.getAula() + ")" : ""))
                        .collect(Collectors.joining("\n"));

        return """
                --- Contexto del estudiante (actualizado al momento de esta consulta) ---
                Fecha y hora actual: %s %s

                Tareas pendientes:
                %s

                Horario de hoy (%s):
                %s
                --- Fin del contexto ---""".formatted(
                ahora.format(FORMATO_FECHA), ahora.format(FORMATO_HORA),
                listaTareas, diaHoy, listaHorario);
    }

    // Misma logica que formatearCuentaRegresiva() en tareas.html/dashboard.html
    // (badge Vigente/Vencida): dias+horas si hay dias, si no horas+minutos.
    private String formatearCuentaRegresiva(Integer segundos) {
        if (segundos == null) {
            return "";
        }
        boolean vencida = segundos < 0;
        long abs = Math.abs(segundos.longValue());
        long dias = abs / 86400;
        long horas = (abs % 86400) / 3600;
        long minutos = (abs % 3600) / 60;

        List<String> partes = new ArrayList<>();
        if (dias > 0) {
            partes.add(dias + "d");
        }
        if (dias > 0 || horas > 0) {
            partes.add(horas + "h");
        }
        partes.add(minutos + "m");
        String texto = String.join(" ", partes.subList(0, Math.min(2, partes.size())));
        return vencida ? "cerró hace " + texto : "quedan " + texto;
    }

    private String mapRolAGemini(String rolInterno) {
        return "asistente".equals(rolInterno) ? "model" : "user";
    }

    private String llamarGemini(String systemInstruction, List<ChatMensaje> historial, String mensajeNuevo) {
        List<GeminiContent> contents = new ArrayList<>();
        if (historial != null) {
            for (ChatMensaje m : historial) {
                contents.add(new GeminiContent(mapRolAGemini(m.rol()), List.of(new GeminiPart(m.contenido()))));
            }
        }
        contents.add(new GeminiContent("user", List.of(new GeminiPart(mensajeNuevo))));

        GeminiRequest cuerpo = new GeminiRequest(
                new GeminiSystemInstruction(List.of(new GeminiPart(systemInstruction))),
                contents
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        HttpEntity<GeminiRequest> entidad = new HttpEntity<>(cuerpo, headers);

        try {
            ResponseEntity<GeminiResponse> respuestaHttp = restTemplate.exchange(
                    URL_GEMINI, HttpMethod.POST, entidad, GeminiResponse.class);
            GeminiResponse respuesta = respuestaHttp.getBody();

            if (respuesta == null || respuesta.candidates() == null || respuesta.candidates().isEmpty()) {
                throw new ApiExternaException(HttpStatus.BAD_GATEWAY,
                        "El asistente no generó una respuesta. Probá de nuevo.", null);
            }
            GeminiContent contenido = respuesta.candidates().get(0).content();
            if (contenido == null || contenido.parts() == null || contenido.parts().isEmpty()) {
                throw new ApiExternaException(HttpStatus.BAD_GATEWAY,
                        "El asistente no generó una respuesta. Probá de nuevo.", null);
            }
            return contenido.parts().get(0).text();
        } catch (HttpClientErrorException e) {
            // 429: cuota agotada. Otro 4xx (401/403 clave invalida, 400
            // request mal formado): responsabilidad nuestra, no de quien
            // pregunto, pero igual no debe verse como un 500 generico.
            if (e.getStatusCode().value() == 429) {
                throw new ApiExternaException(HttpStatus.TOO_MANY_REQUESTS,
                        "El asistente alcanzó su límite de uso por ahora. Probá de nuevo en unos minutos.", e);
            }
            throw new ApiExternaException(HttpStatus.BAD_GATEWAY,
                    "El asistente no pudo procesar la consulta en este momento.", e);
        } catch (HttpServerErrorException e) {
            throw new ApiExternaException(HttpStatus.BAD_GATEWAY,
                    "El servicio del asistente no está disponible en este momento.", e);
        } catch (ResourceAccessException e) {
            throw new ApiExternaException(HttpStatus.GATEWAY_TIMEOUT,
                    "Se agotó el tiempo de espera al consultar el asistente.", e);
        }
    }

    // ---- Shapes minimos del request/response de la API de Gemini
    // (generateContent) -- Jackson serializa/deserializa records usando
    // el nombre de cada componente como propiedad JSON, que coincide
    // exactamente con lo que espera/devuelve la API. ----

    private record GeminiPart(String text) {
    }

    private record GeminiContent(String role, List<GeminiPart> parts) {
    }

    private record GeminiSystemInstruction(List<GeminiPart> parts) {
    }

    private record GeminiRequest(GeminiSystemInstruction systemInstruction, List<GeminiContent> contents) {
    }

    private record GeminiCandidate(GeminiContent content) {
    }

    private record GeminiResponse(List<GeminiCandidate> candidates) {
    }
}
