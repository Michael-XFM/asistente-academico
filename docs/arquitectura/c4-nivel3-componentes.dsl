workspace "Asistente Virtual Académico" "Diagrama C4 Nivel 3 - Componentes del Backend" {

    model {
        estudiante = person "Estudiante"
        frontend = softwareSystem "Frontend Web" "Páginas HTML/JS estáticas"

        sistema = softwareSystem "Asistente Virtual Académico" {

            backend = container "Backend API" "Spring Boot 3.5 / Java 21" {

                authController = component "AuthController" "Expone /api/auth/login, /api/auth/registro" "Spring MVC REST Controller"
                dashboardController = component "DashboardController" "Expone /api/dashboard y /api/dashboard/avisos/marcar-leidos" "Spring MVC REST Controller"
                tareaController = component "TareaController" "Expone el CRUD paginado de /api/tareas" "Spring MVC REST Controller"

                jwtAuthFilter = component "JwtAuthFilter" "Valida el JWT en cada petición protegida y puebla el SecurityContext" "OncePerRequestFilter"
                authService = component "AuthService" "Genera y valida JWT, verifica credenciales con BCrypt" "Spring Service"
                redisService = component "RedisService" "Gestiona la blacklist de tokens revocados (fail-open ante caída de Redis)" "Spring Service"
                usuarioService = component "UsuarioService" "Resuelve el usuario autenticado a partir del email del JWT" "Spring Service"

                dashboardRepository = component "DashboardRepository" "Invoca las funciones SQL de agregación vía @Query nativo" "Spring Data JPA Repository"
                tareaRepository = component "TareaRepository" "CRUD elemental paginado sobre la entidad Tarea" "Spring Data JPA Repository"
                usuarioRepository = component "UsuarioRepository" "CRUD elemental sobre la entidad Usuario" "Spring Data JPA Repository"
            }

            baseDatos = container "Base de datos" "PostgreSQL 16"
            cache = container "Cache / Blacklist" "Redis 7"
        }

        estudiante -> frontend "Usa"
        frontend -> authController "POST /api/auth/login" "JSON/HTTPS"
        frontend -> dashboardController "GET /api/dashboard" "JSON/HTTPS + JWT"
        frontend -> tareaController "CRUD /api/tareas" "JSON/HTTPS + JWT"

        dashboardController -> jwtAuthFilter "Pasa primero por" ""
        tareaController -> jwtAuthFilter "Pasa primero por" ""
        jwtAuthFilter -> authService "Valida token con"
        jwtAuthFilter -> redisService "Verifica blacklist con"

        authController -> authService "Usa"
        dashboardController -> usuarioService "Resuelve usuario autenticado con"
        dashboardController -> dashboardRepository "Usa"
        tareaController -> tareaRepository "Usa"
        usuarioService -> usuarioRepository "Usa"

        dashboardRepository -> baseDatos "Invoca fn_obtener_resumen_academico, fn_listar_tareas_pendientes, fn_marcar_avisos_leidos" "JDBC"
        tareaRepository -> baseDatos "CRUD directo sobre tareas" "JDBC"
        usuarioRepository -> baseDatos "CRUD directo sobre usuarios" "JDBC"
        redisService -> cache "hasKey / set blacklist:&lt;token&gt;" "Redis Protocol"
    }

    views {
        component backend "C4_Nivel3_Componentes" {
            include *
            autoLayout
            title "C4 Nivel 3 — Componentes del Backend"
            description "Detalle interno del contenedor Backend API: controladores, filtro JWT, servicios y repositorios."
        }

        styles {
            element "Person" {
                shape person
                background #08427b
                color #ffffff
            }
            element "Spring MVC REST Controller" {
                background #85bbf0
                color #000000
            }
            element "Spring Service" {
                background #1168bd
                color #ffffff
            }
            element "Spring Data JPA Repository" {
                background #2e7d46
                color #ffffff
            }
            element "OncePerRequestFilter" {
                background #b23a2e
                color #ffffff
            }
        }
    }
}
