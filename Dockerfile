ARG BASE_IMAGE=registry.access.redhat.com/ubi9/openjdk-21-runtime:latest

FROM ${BASE_IMAGE} AS layers
USER 0
WORKDIR /layers
COPY build/libs/beadle-protech-*.jar application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

FROM ${BASE_IMAGE}
LABEL org.opencontainers.image.title="BBH Beadle-ProTech" \
      org.opencontainers.image.source="https://github.com/mateuszmatan/Beadle-ProTech" \
      io.k8s.display-name="BBH Beadle-ProTech" \
      io.k8s.description="Raises, reads and updates the production changes of Beadle in ProTech, BBH's ServiceNow, and looks up its people, groups, releases and records" \
      io.openshift.expose-services="8083:http"
USER 0
WORKDIR /application
RUN mkdir -p /application/data /application/logs && chown 185:0 /application/data /application/logs && chmod 0775 /application/data /application/logs
COPY --from=layers --chown=185:0 /layers/extracted/dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/spring-boot-loader/ ./
COPY --from=layers --chown=185:0 /layers/extracted/snapshot-dependencies/ ./
COPY --from=layers --chown=185:0 /layers/extracted/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError" \
    BEADLE_PROTECH_DATA_DIR=/application/data \
    LOG_PATH=/application/logs \
    PORT=8083
VOLUME /application/data
USER 185
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "application.jar"]
