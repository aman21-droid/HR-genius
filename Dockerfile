# Single-service image for hosted demos (Render): the Angular app is bundled into the Spring Boot
# jar and served from the same origin as the API. docker-compose.yml keeps the separate
# backend/frontend/Oracle setup for full local runs.

# ---- Frontend build ----
FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Backend build (with the SPA as static resources) ----
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=web /web/dist/hrgenius/browser ./src/main/resources/static
RUN mvn -q clean package -DskipTests

# ---- Runtime ----
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=api /app/target/hrgenius-backend.jar app.jar
ENV SPRING_PROFILES_ACTIVE=demo \
    JAVA_TOOL_OPTIONS="-Xmx300m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
