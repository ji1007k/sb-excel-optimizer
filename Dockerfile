FROM openjdk:17-jdk-slim

WORKDIR /app

COPY build/libs/*.jar app.jar

#CMD ls -a

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]