# syntax=docker/dockerfile:1

# --- Build stage -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Dependencies first: this layer is cached as long as pom.xml does not change.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline

COPY src/ src/
# Tests are not run here: they need Docker (Testcontainers) and run in CI / `mvnw verify`.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests \
    && cp target/telereleve-*.jar app.jar

# --- Runtime stage ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
