# syntax=docker/dockerfile:1.6
# =============================================================================
# 微迎新 CampusArrive — 通用 Java 服务 Dockerfile
# 多阶段构建：Maven 编译 -> JRE 运行
# 通过构建参数 SERVICE_NAME 指定要打包的微服务模块名
#   例: docker build --build-arg SERVICE_NAME=gateway -t campusarrive/gateway .
# =============================================================================
ARG SERVICE_NAME

# ----------------------------------------------------------------------------
# 阶段 1：构建阶段 (Maven + JDK 17)
# ----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder
ARG SERVICE_NAME
ENV SERVICE_NAME=${SERVICE_NAME}

WORKDIR /build

# 先复制 Maven 工程描述文件以利用层缓存（仅在 pom 变化时才重新拉取依赖）
COPY pom.xml ./
COPY backend/pom.xml backend/pom.xml
COPY backend/ backend/

# 使用 BuildKit 缓存挂载加速依赖下载；-pl 指定模块，-am 同时构建其依赖模块
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl "backend/${SERVICE_NAME}" -am clean package -DskipTests

# 从 target 目录中挑出 Spring Boot 可执行 JAR（排除 *.original / *-plain.jar）
RUN JAR_FILE="$(find "backend/${SERVICE_NAME}/target" -maxdepth 1 \
      -type f -name '*.jar' ! -name '*.original' ! -name '*-plain.jar' | head -n 1)" \
    && test -n "${JAR_FILE}" \
    && cp "${JAR_FILE}" /build/app.jar

# ----------------------------------------------------------------------------
# 阶段 2：运行阶段 (JRE 17 Alpine，体积小、启动快)
# ----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-alpine
ARG SERVICE_NAME
ENV SERVICE_NAME=${SERVICE_NAME}
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 \
               -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/app/heapdump \
               -Djava.security.egd=file:/dev/./urandom"

# 安装 curl 供容器内 healthcheck 使用；设置时区
RUN apk add --no-cache curl tzdata \
    && cp /usr/share/zoneinfo/${TZ} /etc/localtime \
    && echo "${TZ}" > /etc/timezone

WORKDIR /app
COPY --from=builder /build/app.jar app.jar

# 健康检查：由 docker-compose 覆盖具体端口，这里给一个默认探测
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -fsS "http://localhost:${SERVER_PORT:-8080}/actuator/health" || exit 1

EXPOSE ${SERVER_PORT:-8080}
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
