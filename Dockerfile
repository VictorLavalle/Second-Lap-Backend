# Build stage: compile and package with the Maven wrapper on JDK 25.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src/ src/
RUN ./mvnw -q -B -DskipTests package

# Runtime stage: slim JRE image running the fat jar.
FROM eclipse-temurin:25-jre AS runtime
WORKDIR /app
COPY --from=build /app/target/second-lap-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
