# 첫 번째 스테이지: 빌드 스테이지
FROM gradle:jdk21 AS builder
WORKDIR /app
COPY . .
RUN gradle build -x test

# 두 번째 스테이지: 실행 스테이지
FROM openjdk:21
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","app.jar"]