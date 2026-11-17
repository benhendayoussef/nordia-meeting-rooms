FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first, so this layer is reused until the build files change.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- -plain.jar)" app.jar

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && mkdir /app/data \
    && chown app:app /app/data

COPY --from=build /workspace/app.jar app.jar

ENV NORDIA_DB_PATH=/app/data/nordia.db
VOLUME /app/data
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
