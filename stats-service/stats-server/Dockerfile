FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/*.jar stats-server.jar
ENTRYPOINT ["java", "-jar", "stats-server.jar"]