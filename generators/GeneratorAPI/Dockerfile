FROM openjdk:8-jre-alpine

ENV APPLICATION_USER ktor
RUN adduser -D -g '' $APPLICATION_USER

RUN mkdir /app
RUN chown -R $APPLICATION_USER /app

USER $APPLICATION_USER

COPY ./build/libs/generators*.jar /app/generators.jar
WORKDIR /app

ENTRYPOINT ["java", "-server", "-XX:MaxRAMPercentage=75.00", "-jar", "generators.jar"]