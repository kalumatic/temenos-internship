FROM gradle:9.3.0-jdk17 AS builder

WORKDIR /app

COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle gradle
COPY openapi-templates openapi-templates
COPY src src

RUN chmod +x gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=builder /app/build/libs/*.jar app.jar

EXPOSE 8080
EXPOSE 5005


ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "/app/app.jar"]
