# syntax=docker/dockerfile:1.7

# ---- Build stage: compile and package with the Maven wrapper (JDK 21) ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so this layer is cached until pom.xml changes
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B dependency:go-offline

COPY src/ src/
# Tests need MySQL and the live SOAP API; they run in CI / locally (./mvnw test), not in the image build
RUN --mount=type=cache,target=/root/.m2 ./mvnw -q -B -DskipTests package \
 && java -Djarmode=tools -jar target/country-info-service-*.jar extract --layers --launcher --destination extracted

# ---- Runtime stage: JRE only, non-root, layered for small incremental pulls ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system --gid 1001 app && useradd --system --uid 1001 --gid app --no-create-home app

# Least frequently changing layers first: dependencies rarely change, application code changes often
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER 1001:1001
EXPOSE 8080

# Size the heap from the container memory limit; exit (and let Kubernetes restart the pod) on OOM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
