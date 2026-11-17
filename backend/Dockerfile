# 后端构建与运行（多阶段）
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
# 使用阿里云 Maven 镜像加速依赖解析
COPY docker-settings.xml /root/.m2/settings.xml
COPY pom.xml .
RUN mvn -q -B dependency:go-offline || true
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
ENV SERVER_PORT=8080 \
    DB_HOST=db \
    DB_PORT=5432 \
    DB_NAME=ccdev \
    DB_USER=ccdev \
    DB_PASSWORD=ccdev
COPY --from=build /app/target/scf-metadata-service-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
