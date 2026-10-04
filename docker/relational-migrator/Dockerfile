FROM maven:3.9-eclipse-temurin-21-alpine AS mysql-driver

ARG MYSQL_CONNECTOR_VERSION=8.3.0

RUN mvn -B -q org.apache.maven.plugins:maven-dependency-plugin:3.7.1:copy \
    -Dartifact=com.mysql:mysql-connector-j:${MYSQL_CONNECTOR_VERSION} \
    -DoutputDirectory=/driver \
    -Dmdep.stripVersion=true

FROM public.ecr.aws/v4d7k6c9/relational-migrator:latest

COPY --from=mysql-driver \
    /driver/mysql-connector-j.jar \
    /opt/mongodb-relational-migrator/lib/app/lib/driver.jar
