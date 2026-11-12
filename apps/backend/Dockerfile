FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace/app

COPY pom.xml ./
COPY src ./src

RUN apk add --no-cache maven && \
    mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S reviewpilot && adduser -S reviewpilot -G reviewpilot
USER reviewpilot

COPY --from=build /workspace/app/target/*.jar /app/app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

