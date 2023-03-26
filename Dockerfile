FROM eclipse-temurin:17.0.6_10-jre-alpine AS unpacker
WORKDIR /tmp
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM zenika/alpine-chrome:108-with-chromedriver
ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:17.0.6_10-jre-alpine $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"
ENV CHROME_VERSION="108"
USER root

WORKDIR /opt/jds
COPY --from=unpacker /tmp/dependencies/ ./
COPY --from=unpacker /tmp/spring-boot-loader/ ./
COPY --from=unpacker /tmp/snapshot-dependencies/ ./
COPY --from=unpacker /tmp/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.JarLauncher"]

LABEL org.opencontainers.image.source=https://github.com/aaejo/profile-finder
