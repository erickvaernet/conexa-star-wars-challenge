# syntax=docker/dockerfile:1

# Stage 1: build the application with JDK 21.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY src src
# Tests are skipped here: the acceptance tests start containers and run outside the image build.
# The cache mount keeps downloaded dependencies between builds.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -ntp -DskipTests package

# Stage 2: run it on a small JRE 21 image, as a user without privileges.
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=build /workspace/target/starwars-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
