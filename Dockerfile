# JitterTravel — production image. Targets Java 26 explicitly so the build does not depend on
# a platform builder (e.g. Railway Nixpacks) keeping pace with new JDK releases.

# ---- build stage ----
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

# Cache dependencies first (only re-runs when pom/wrapper change).
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp -DskipTests dependency:go-offline

# Build the executable jar.
COPY src ./src
RUN ./mvnw -B -ntp -DskipTests package

# ---- runtime stage ----
FROM eclipse-temurin:26-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Production is the DEFAULT (secured) profile — do NOT set SPRING_PROFILES_ACTIVE=local here.
# Railway injects PORT; the app binds to it via server.port=${PORT:8080}.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
