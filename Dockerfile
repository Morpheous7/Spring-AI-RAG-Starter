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
# Reranker: cross-encoder/ms-marco-MiniLM-L6-v2 (Apache-2.0), baked in so nothing downloads at start-up
ADD --chmod=644 https://huggingface.co/cross-encoder/ms-marco-MiniLM-L6-v2/resolve/main/onnx/model.onnx /app/models/reranker/model.onnx
ADD --chmod=644 https://huggingface.co/cross-encoder/ms-marco-MiniLM-L6-v2/resolve/main/tokenizer.json /app/models/reranker/tokenizer.json
# Remote ADD can leave root-only permissions; make the model readable by the app user and fail the build if it's missing
RUN chmod -R a+rX /app/models && ls -l /app/models/reranker && test -s /app/models/reranker/model.onnx
COPY --from=build /src/target/app.jar /app/app.jar
USER app
ENV MODEL_CACHE_DIR=/app/model-cache \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
