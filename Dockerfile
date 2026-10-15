# syntax=docker/dockerfile:1
#
# One image serving UI + API:   docker build -t logviewer .
#                               docker run -p 127.0.0.1:8080:8080 -v ./logs:/logs:ro logviewer

# --- 1. React UI ---------------------------------------------------------------------------------------------------
FROM node:24-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- 2. Spring Boot jar (the compiled UI is placed on the classpath as /static) --------------------------------------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /workspace
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
COPY backend/gradle gradle
# Resolve dependencies in their own layer so source edits do not re-download them.
RUN ./gradlew --no-daemon -PskipFrontend dependencies > /dev/null
COPY backend/src src
COPY --from=frontend /app/dist src/main/resources/static
RUN ./gradlew --no-daemon -PskipFrontend bootJar

# --- 3. Runtime ----------------------------------------------------------------------------------------------------
# A glibc-based image is required: DuckDB ships a native library that does not run on Alpine/musl.
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 --home-dir /app logviewer \
    && mkdir -p /app /data /logs \
    && chown logviewer /app /data
WORKDIR /app
COPY --from=backend --chown=logviewer /workspace/build/libs/logviewer.jar app.jar

ENV SERVER_ADDRESS=0.0.0.0 \
    LOGVIEWER_DATA_DIR=/data \
    LOGVIEWER_ALLOWED_DIRS=/logs \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70"

USER logviewer
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
    CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
