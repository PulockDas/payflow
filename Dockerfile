# ---------- Stage 1: build the jar (full JDK + Maven wrapper) ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Build files first: this layer (dependency download) is cached and only
# re-runs when pom.xml changes, not on every code change.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Now the source code.
COPY src src
# Tests are skipped here: they need Docker (Testcontainers), which isn't available
# inside a build. Run them before building: .\mvnw.cmd test
RUN ./mvnw -q -B package -DskipTests


# ---------- Stage 2: run it (small JRE-only image) ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as an unprivileged user, not root.
RUN groupadd --system payflow && useradd --system --gid payflow --no-create-home payflow

# Only the jar comes over from the build stage: no JDK, Maven or source code.
COPY --from=build /workspace/target/payflow-*.jar app.jar

USER payflow
EXPOSE 8080

# No secrets in the image: DB_*, STRIPE_*, ADMIN_API_TOKEN are passed as
# environment variables when the container starts (docker run -e / --env-file, or compose).
ENTRYPOINT ["java", "-jar", "app.jar"]
