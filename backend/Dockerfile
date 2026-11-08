
# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew bootJar --no-daemon

# Select the executable Spring Boot JAR
RUN find build/libs -maxdepth 1 -name "*.jar" \
    ! -name "*-plain.jar" \
    -exec cp {} /app/app.jar \;

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "app.jar"]
