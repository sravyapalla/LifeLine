FROM maven:3.9.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace
COPY . .
RUN mvn -pl ${SERVICE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG SERVICE
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/${SERVICE}/target/*.jar app.jar
EXPOSE 8088 8091 8092 8093 8094 8095 8096
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
