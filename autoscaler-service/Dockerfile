FROM eclipse-temurin:17-jdk-alpine

RUN apk add --no-cache docker-cli

WORKDIR /app
COPY target/autoscaler-service-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java","-jar","app.jar"]