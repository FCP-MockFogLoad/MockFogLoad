FROM openjdk:8-jre-alpine

RUN adduser -D -g '' app && mkdir /app && chown -R app /app
USER app
COPY --chown=app config.yaml /app/config.yaml
COPY --chown=app ./target/exampleapp*.jar /app/exampleapp.jar
WORKDIR /app

CMD ["java", "-server", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:InitialRAMFraction=2", "-XX:MinRAMFraction=2", "-XX:MaxRAMFraction=2", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "exampleapp.jar"]
