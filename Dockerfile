# Build stage
FROM openjdk:21 AS build
WORKDIR /build
COPY . .
RUN ./gradlew clean build -x test


# Run stage
FROM openjdk:21
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]