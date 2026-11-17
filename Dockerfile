# Build stage: Maven + JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package

# Run stage: JRE 21 (glibc-based, which the ONNX runtime needs)
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home app && mkdir -p /app/model-cache && chown -R app /app
COPY --from=build /src/target/app.jar /app/app.jar
USER app
ENV MODEL_CACHE_DIR=/app/model-cache \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
