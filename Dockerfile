FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src src
RUN mvn -B -q clean package -Dmaven.test.skip=true

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
RUN mkdir -p /app/storage && chown -R app:app /app/storage
COPY --from=build /build/target/inmobiliaria-api-*.jar app.jar
USER app
EXPOSE 8080
# La memoria de Java se ajusta con JAVA_TOOL_OPTIONS (compose.prod.yaml la toma de API_JAVA_OPTS).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
