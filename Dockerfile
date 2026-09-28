# ========== 阶段一：编译打包 ==========
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build

# 先拷 pom.xml 单独拉依赖（利用 Docker 缓存，改代码不用重新下依赖）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 再拷源码打包
COPY src ./src
RUN mvn clean package -DskipTests -B

# ========== 阶段二：精简运行时 ==========
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# 只从阶段一拷贝最终的 jar
COPY --from=builder /build/target/*.jar app.jar

# 时区设置，避免日志时间错 8 小时
ENV TZ=Asia/Shanghai

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
