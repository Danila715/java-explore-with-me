FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY target/*.jar ewm-service.jar
ENTRYPOINT ["java", "-jar", "ewm-service.jar"]