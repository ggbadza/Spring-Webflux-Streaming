# 첫 번째 스테이지: 빌드 스테이지
FROM gradle:jdk21 AS builder
WORKDIR /app
COPY . .
# 두 개의 JAR 파일을 모두 빌드하도록 태스크를 나열합니다.
RUN gradle webfluxJar batchJar

# 두 번째 스테이지: 실행 스테이지
FROM eclipse-temurin:21-alpine
WORKDIR /app

# FFmpeg 설치
RUN apk add --no-cache ffmpeg

COPY --from=builder /app/build/libs/*-webflux.jar webflux-app.jar
COPY --from=builder /app/build/libs/*-batch.jar batch-app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","webflux-app.jar"]