# Imagenes base fijadas por digest sha256 (Bloque B.1 - Reproducibilidad).
# Digests verificados en Docker Hub el 20/07/2026. Si se actualiza la
# version de Java, verificar el nuevo digest antes de reemplazar.
FROM eclipse-temurin@sha256:1ff763083f2993d57d0bf374ab10bb3e2cb873af6c13a04458ebbd3e0337dc76 AS build
WORKDIR /app
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
COPY src src
RUN chmod +x ./mvnw
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app
# pg_dump para el respaldo administrativo (BackupService): corre DENTRO
# de este contenedor, conectandose por red a "postgres" -- misma ruta de
# red que ya usa JDBC, sin darle al backend acceso al socket de Docker
# (que le permitiria controlar otros contenedores) para llegar al
# pg_dump que ya existe en la imagen de postgres. Version 16 para que
# coincida con el servidor (16.14).
RUN apk add --no-cache postgresql16-client
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]