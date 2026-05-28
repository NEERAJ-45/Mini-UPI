# ════════════════════════════════════════════════════════════════════
#  user-service — Multi-Stage, Security-Hardened Dockerfile
#  Stage 1 (builder): Compiles the fat JAR using the parent POM.
#  Stage 2 (runtime): Minimal JRE image with a non-root appuser.
# ════════════════════════════════════════════════════════════════════

# ── Stage 1: Build ───────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace

# ── Layer-cache optimisation ─────────────────────────────────────────
# Copy POMs and common source FIRST — they change less often than
# service source, so Docker reuses cached layers on incremental builds.
COPY pom.xml .
COPY common/pom.xml common/pom.xml
COPY common/src     common/src

COPY user-service/pom.xml user-service/pom.xml
COPY user-service/src     user-service/src

# Install common into the local Maven cache, then package the service.
RUN mvn -B install -pl common -am -DskipTests
RUN mvn -B package -pl user-service -DskipTests

# ── Stage 2: Runtime ─────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Security: run as a dedicated non-root user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the fat JAR — nothing else from the build stage
COPY --from=builder /workspace/user-service/target/*.jar app.jar

# Container-aware JVM flags:
#   -XX:MaxRAMPercentage=75.0  → use up to 75 % of cgroup memory limit
#   -XX:+UseContainerSupport   → respect Docker CPU/memory limits
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport"

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
