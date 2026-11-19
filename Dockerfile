# Runtime image for the Quarkus application (HOS-1, SEC-9).
# Build the application first with ./mvnw package; the pipeline does this before building the image.
# The base image is pinned by digest, and Dependabot updates it (SEC-3, SEC-4).
FROM eclipse-temurin:21-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474

# Set by the pipeline to the short commit ID and the build time (HOS-35)
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION}

# A small, fast-starting JVM for scale to zero (HOS-2): one garbage collector thread, memory
# limited to the container, no restart loops after an out-of-memory error.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=75 -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

# The application does not run as root (SEC-9)
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app -h /app app
WORKDIR /app
COPY --chown=app:app target/quarkus-app/lib/ lib/
COPY --chown=app:app target/quarkus-app/app/ app/
COPY --chown=app:app target/quarkus-app/quarkus/ quarkus/
COPY --chown=app:app target/quarkus-app/quarkus-run.jar quarkus-run.jar
USER 10001:10001

# Cloud Run sends traffic to the port in $PORT (default 8080)
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/quarkus-run.jar"]
