# This base image contains OpenJDK 17 on Alpine Linux which is a lightweight Linux distribution.
# https://hub.docker.com/layers/library/eclipse-temurin/17-focal/images/sha256-35d99da9aed93017ce7345247674e4d995eef49d4b67763e5bface2dd0703c32?context=explore
FROM eclipse-temurin:17-focal

# Tries to find a .jar file from within the build/libs directory from the current path.
ARG JAR_FILE=build/libs/conluz-*.jar
# Copy the jar file to the container image.
COPY ${JAR_FILE} conluz.jar

# Command that will be executed when a container is started from this image.
# Run the application using the java -jar command.
# This sets conluz as default application, which Docker will execute when a container is launched from the image.
ENTRYPOINT ["java", "-jar", "/conluz.jar"]
