FROM ubuntu:latest
LABEL authors="bco"

# 1. Aşama: Derleme (Build) Aşaması
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./mvnw clean package -DskipTests

# 2. Aşama: Çalıştırma (Runtime) Aşaması
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/code-analyzer.jar app.jar

# Etkileşimli terminal (Tty) ve konsol için giriş komutu
ENTRYPOINT ["java", "-jar", "app.jar"]