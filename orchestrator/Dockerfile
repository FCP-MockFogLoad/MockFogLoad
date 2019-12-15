FROM openjdk:8-jre-alpine

ENV APPLICATION_USER ktor

RUN adduser -D -g '' $APPLICATION_USER && mkdir /app && chown -R $APPLICATION_USER /app
USER $APPLICATION_USER

COPY --chown=$APPLICATION_USER ./build/libs/orchestrator*.jar /app/orchestrator.jar
WORKDIR /app

CMD ["java", "-server", "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XX:InitialRAMFraction=2", "-XX:MinRAMFraction=2", "-XX:MaxRAMFraction=2", "-XX:+UseG1GC", "-XX:MaxGCPauseMillis=100", "-XX:+UseStringDeduplication", "-jar", "orchestrator.jar"]
