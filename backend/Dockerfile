# --- Build stage -------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Warm the dependency cache: copy the wrapper + pom first so `mvn` only
# re-downloads when dependencies actually change, not on every source edit.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Railway injects $PORT; application.yml binds server.port to it.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
