# The sync server, as one container - see docs/decisions/0001-self-hosted-server.md.
#
# Packages a distribution Gradle has already built rather than building inside
# the image: configuring this repository's build needs the Android SDK, which
# has no business in a server image or its build. Build it first with
#
#   ./gradlew :server:installDist
#   docker build -t lukia-server server
FROM eclipse-temurin:21-jre-noble

RUN useradd --system --uid 10001 --home-dir /app lukia \
    && mkdir /data \
    && chown lukia /data

COPY build/install/server /app

USER lukia

# Headless because a PDF's cover is drawn with AWT, and the image has no display.
ENV LUKIA_DATA_DIR=/data \
    LUKIA_PORT=8080 \
    JAVA_OPTS=-Djava.awt.headless=true

# Everything the server writes. Back this up and you have backed up the server.
VOLUME /data
EXPOSE 8080

ENTRYPOINT ["/app/bin/server"]
