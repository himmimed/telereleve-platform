# syntax=docker/dockerfile:1

# --- Build stage -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q dependency:go-offline
COPY src/ src/
# Tests run in CI / `mvnw verify`; the image only packages the shaded jar.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -B -q package -DskipTests \
    && cp target/telereleve-simulator-*.jar simulator.jar

# --- Runtime stage ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/simulator.jar simulator.jar
USER app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/simulator.jar"]
