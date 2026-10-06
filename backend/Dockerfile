# syntax=docker/dockerfile:1

# ---- Stage 1: build ---------------------------------------------------
# Il progetto richiede Java 25 (vedi toolchain in build.gradle.kts): l'immagine
# eclipse-temurin:25-jdk-alpine esiste ed è quella usata qui.
FROM eclipse-temurin:25-jdk-alpine AS build
WORKDIR /workspace

# Copia prima i file di configurazione Gradle per sfruttare la cache dei layer
# Docker quando cambia solo il codice sorgente.
COPY gradlew ./
COPY gradle ./gradle
COPY settings.gradle.kts build.gradle.kts ./
RUN chmod +x gradlew

# Scarica il wrapper Gradle e (best effort) le dipendenze, cache-friendly.
RUN ./gradlew --no-daemon help

COPY src ./src

RUN ./gradlew --no-daemon bootJar -x test

# ---- Stage 2: runtime ---------------------------------------------------
# Solo un JRE 25: nessun tool di build nell'immagine finale.
FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S walletinsight && adduser -S walletinsight -G walletinsight
USER walletinsight

COPY --from=build /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
