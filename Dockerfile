# syntax=docker/dockerfile:1

# ---- Build stage -----------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Optional: build for a given release version (the release workflow passes the tag, e.g. 0.2.0).
ARG VERSION=
COPY . .
# The BuildKit cache mount keeps the local Maven repository between builds.
RUN --mount=type=cache,target=/root/.m2 \
    if [ -n "$VERSION" ]; then ./mvnw -B -ntp versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false; fi \
    && ./mvnw -B -ntp clean package -DskipTests

# ---- Runtime stage ---------------------------------------------------------
FROM eclipse-temurin:25-jre

LABEL org.opencontainers.image.title="Blue Ring Octopus CLI" \
      org.opencontainers.image.description="Local-first AI code analysis and generation in your terminal (Ollama + LangChain4j)." \
      org.opencontainers.image.url="https://github.com/burak-can-onarim/blue-ring-octopus-cli" \
      org.opencontainers.image.source="https://github.com/burak-can-onarim/blue-ring-octopus-cli" \
      org.opencontainers.image.documentation="https://github.com/burak-can-onarim/blue-ring-octopus-cli#readme" \
      org.opencontainers.image.licenses="MIT" \
      org.opencontainers.image.authors="Burak Can Onarım"

# Run as an unprivileged user. Mount the project you want to analyse at /workspace.
RUN useradd --system --create-home --uid 10001 octopus
WORKDIR /workspace
COPY --from=build /app/target/blue-ring-octopus-cli.jar /opt/octopus/app.jar

# Ollama runs on the Docker host, not inside this container. On Linux, add
# --add-host=host.docker.internal:host-gateway to `docker run`. Override as needed.
ENV LANGCHAIN4J_OLLAMA_CHAT_MODEL_BASE_URL=http://host.docker.internal:11434 \
    LOGGING_FILE_NAME=/tmp/octopus.log \
    SPRING_SHELL_HISTORY_ENABLED=false

USER octopus

# No arguments starts the full-screen TUI (needs `docker run -it`).
# Arguments run a one-shot command, e.g. `analyze /workspace`.
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED", "-jar", "/opt/octopus/app.jar"]
