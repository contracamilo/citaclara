# Etapa 1: construcción con Maven Wrapper sobre JDK 25
FROM eclipse-temurin:27.0.1.1_4-jdk AS construccion
WORKDIR /fuente
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -q package -DskipTests

# Etapa 2: ejecución con JRE 25, usuario sin privilegios
FROM eclipse-temurin:27.0.1.1_4-jre
RUN groupadd --system citaclara && useradd --system --gid citaclara citaclara \
    && apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=construccion /fuente/target/citaclara.jar /app/citaclara.jar
# Carpeta del correo simulado (003-recordatorios-cita): escribible por el usuario sin privilegios.
RUN mkdir -p /app/datos/salida-correo && chown -R citaclara:citaclara /app/datos
USER citaclara
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/citaclara.jar"]
