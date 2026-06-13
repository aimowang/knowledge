# ===== Stage 1: Build =====
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# 先复制 pom.xml 和父 pom，利用 Docker 缓存层
COPY pom.xml ./
COPY api/pom.xml ./api/
COPY model/pom.xml ./model/
COPY core/pom.xml ./core/
COPY starter/pom.xml ./starter/

# 提前下载依赖（构建缓存优化）
RUN mvn dependency:go-offline -B -q || true

# 复制源码并打包
COPY api ./api
COPY model ./model
COPY core ./core
COPY starter ./starter

RUN mvn clean package -DskipTests -B

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre

WORKDIR /app

# 从 build 阶段复制构建产物
COPY --from=build /app/starter/target/starter-*.jar app.jar

# 创建日志目录
RUN mkdir -p /var/log/knowledge-rag

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD curl -s http://localhost:8080/actuator/health | grep UP || exit 1

# JVM 参数优化（可覆盖）
# 生产环境建议使用以下 JVM 参数
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS:--Xms2g -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200} \
  -jar app.jar \
  --spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"]
