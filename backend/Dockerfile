FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

ENV SERVER_PORT=8080 \
    JAVA_OPTS="" \
    KNOWLEDGE_UPLOAD_DIR=/app/uploads/kb \
    TTS_OUTPUT_DIR=/app/uploads/audio

RUN mkdir -p /app/uploads/kb /app/uploads/audio /app/config

COPY --from=build /workspace/target/qasystem-0.0.1-SNAPSHOT.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
