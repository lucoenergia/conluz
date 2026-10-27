# Eclipse Temurin 17 JRE on Alpine Linux, pinned to a specific release so builds are reproducible
# and Dependabot proposes base-image bumps.
# - JRE only: the JAR is built by Gradle in CI (build-and-push-image.yml), outside the image, and
#   nothing at runtime needs JDK tooling. Without jcmd, take a thread dump with
#   `docker exec <container> kill -3 1` (written to the container log) and record JFR with
#   -XX:StartFlightRecording at startup.
# - Alpine: a supported OS that still receives security updates, with a smaller package surface.
# - Runs as the non-root user and group conluz, UID 10001 and GID 10001, set numerically in USER
#   so runtimes can verify non-root without resolving names. The JAR stays owned by root, so the
#   process cannot overwrite its own binary. The only path written at runtime is /tmp (mode 1777),
#   for Tomcat's base and multipart temp directories.
# - TZ=UTC keeps the JVM default zone at UTC, as the previous base image's Etc/UTC did. @Scheduled
#   crons declared without a zone depend on it; without TZ an Alpine image defaults to GMT.
FROM eclipse-temurin:17.0.20.1_1-jre-alpine

ENV TZ=UTC

RUN addgroup -S -g 10001 conluz \
    && adduser -S -D -H -u 10001 -G conluz conluz

# Tries to find a .jar file from within the build/libs directory from the current path.
ARG JAR_FILE=build/libs/conluz-*.jar
# Copy the jar file to the container image, owned by root and world-readable.
COPY --chmod=0444 ${JAR_FILE} /conluz.jar

USER 10001:10001

# Run the application using the java -jar command.
ENTRYPOINT ["java", "-jar", "/conluz.jar"]
