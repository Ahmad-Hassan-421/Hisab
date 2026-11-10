FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /build
COPY gradlew build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src/main ./src/main
RUN sh ./gradlew --no-daemon installDist

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install --no-install-recommends -y util-linux \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 hisab \
    && useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin hisab
WORKDIR /opt/hisab
COPY --from=build /build/build/install/hisab-server ./
COPY docker-entrypoint.sh ./
ENV HOST=0.0.0.0 PORT=10000 HISAB_ENV=production \
    UPLOAD_DIR=/var/data/uploads JAVA_OPTS="-XX:MaxRAMPercentage=60"
EXPOSE 10000
ENTRYPOINT ["/bin/sh", "/opt/hisab/docker-entrypoint.sh"]
