# Multi-stage Dockerfile for Spring Boot 3.2.5 (Java 17)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and source code
COPY pom.xml .
COPY src ./src

# Build production JAR (skipping unit tests for fast build)
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy executable jar from build stage
COPY --from=build /app/target/evacsense-backend-1.0.0.jar app.jar

# Expose port
EXPOSE 5000

# Run Spring Boot application
ENTRYPOINT ["java", "-Dserver.port=5000", "-jar", "app.jar"]
