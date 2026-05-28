# ════════════════════════════════════════════════════════════════════
#  wallet-service — Multi-Stage, Security-Hardened Dockerfile
#  Stage 1 (builder): Compiles the fat JAR using the parent POM.
#  Stage 2 (runtime): Minimal JRE image with a non-root appuser.
# ════════════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# ── Layer-cache optimisation ─────────────────────────────────────────
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src     common/src

COPY wallet-service/pom.xml wallet-service/pom.xml
COPY wallet-service/src     wallet-service/src

RUN mvn -B install -pl common -am -DskipTests
RUN mvn -B package -pl wallet-service -DskipTests

# ── Stage 2: Runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=builder /workspace/wallet-service/target/*.jar app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

EXPOSE 8082

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
