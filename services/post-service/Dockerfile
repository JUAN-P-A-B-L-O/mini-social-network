FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradle/ gradle/
COPY gradlew settings.gradle build.gradle ./
RUN chmod +x gradlew
COPY src/ src/
RUN ./gradlew --no-daemon installDist

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/build/install/post-service/ ./
USER app
EXPOSE 8081
ENTRYPOINT ["/app/bin/post-service"]
