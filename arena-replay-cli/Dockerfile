# syntax=docker/dockerfile:1

FROM alpine:3.22 AS artifact
ARG JAR_FILE=arena-replay-cli/build/libs/arena-replay-cli-0.1.0-SNAPSHOT.jar
COPY ${JAR_FILE} /opt/app/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /opt/app
COPY --from=artifact /opt/app/app.jar /opt/app/app.jar
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]
