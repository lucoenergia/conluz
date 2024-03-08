# This base image contains OpenJDK 17 on Alpine Linux which is a lightweight Linux distribution.
FROM openjdk:17-jdk-alpine

# Tries to find a .jar file from within the build/libs directory from the current path.
ARG JAR_FILE=../build/libs/*.jar
# Copy the jar file to the container image.
COPY ${JAR_FILE} conluz.jar

# Run the application using the java -jar command.
# This sets conluz as default application, which Docker will execute when a container is launched from the image.
ENTRYPOINT ["java", "-jar", "/conluz.jar"]
