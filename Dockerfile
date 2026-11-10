# syntax=docker/dockerfile:1
# Server deployment image. Local development still runs the backend directly (see docker-compose.yml).

# ---------------------------------------------------------------------------------------------------
# Build. Dependencies are resolved before the sources are copied, so editing a source file does not
# invalidate the dependency layer. mvnw is run through sh because it is not executable in Git.
# ---------------------------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN sh mvnw -B -q dependency:go-offline

COPY src/ src/
RUN sh mvnw -B -q -DskipTests package

# ---------------------------------------------------------------------------------------------------
# Extract. The layered jar splits into dependencies, which rarely change, and application classes,
# which change on every commit.
# ---------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /extract
COPY --from=build /build/target/*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------------------------------------------------------------------------------------------------
# Runtime. Runs as a non-root user. No secret is baked into an image layer: every credential arrives
# as an environment variable at run time.
# ---------------------------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# The media library (MEDIA_UPLOAD_DIR, default "uploads") lives in /app/uploads. It is created here
# and owned by the app user, so a named volume mounted on it starts with the same owner.
RUN addgroup -S museotek && adduser -S museotek -G museotek \
    && mkdir /app/uploads && chown museotek:museotek /app/uploads
USER museotek:museotek

COPY --from=extract --chown=museotek:museotek /extract/extracted/dependencies/ ./
COPY --from=extract --chown=museotek:museotek /extract/extracted/spring-boot-loader/ ./
COPY --from=extract --chown=museotek:museotek /extract/extracted/snapshot-dependencies/ ./
COPY --from=extract --chown=museotek:museotek /extract/extracted/application/ ./

EXPOSE 8080

# /actuator/health is the one endpoint SecurityConfig permits without a token.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/museotekbox/actuator/health | grep -q '"status":"UP"'

# The extracted application layer is a thin jar whose manifest classpath points at the dependency
# layers copied alongside it, so it is launched directly.
ENTRYPOINT ["java", "-jar", "application.jar"]
