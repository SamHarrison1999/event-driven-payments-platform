FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./

RUN chmod +x gradlew
RUN ./gradlew --no-daemon --version

COPY src src

RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:25-jre

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 payments \
    && useradd --system --uid 10001 --gid 10001 --home-dir /app --no-create-home payments

WORKDIR /app

COPY --from=build /workspace/build/libs/payments-platform-backend.jar app.jar

RUN chown payments:payments /app/app.jar

USER payments:payments

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=40s --retries=6 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/readiness

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
