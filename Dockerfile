FROM eclipse-temurin:17.0.6_10-jre-alpine AS unpacker
WORKDIR /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM zenika/alpine-chrome:112-with-node
ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:17.0.6_10-jre-alpine $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV CHROME_VERSION="112"
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1
ENV PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH=/usr/bin/chromium-browser
ENV PLAYWRIGHT_NODEJS_PATH=/usr/bin/node
USER root

WORKDIR /opt/jds
COPY --from=unpacker /tmp/dependencies/ ./
COPY --from=unpacker /tmp/spring-boot-loader/ ./
COPY --from=unpacker /tmp/snapshot-dependencies/ ./
COPY --from=unpacker /tmp/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]

LABEL org.opencontainers.image.source=https://github.com/aaejo/profile-finder
