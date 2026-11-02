# 基础运行时镜像：基于轻量、经过安全加固的 Eclipse Temurin JRE 17 Alpine 镜像
FROM eclipse-temurin:17-jre-alpine

# 维护者信息与元数据
LABEL maintainer="mcp-manager" \
      description="MCP Governance Gateway - 独立响应式控制面治理网关"

# 安全强化：创建非 root 运行专属系统组与用户 (UID/GID 10001:10001)
RUN addgroup -g 10001 -S mcp && \
    adduser -u 10001 -S mcp -G mcp

# 创建应用目录及外部配置挂载目录，并赋予 mcp 用户所属权限
WORKDIR /app
RUN mkdir -p /app/config && \
    chown -R mcp:mcp /app

# 拷贝网关可执行 jar 包
ARG JAR_FILE=target/mcp-governance-gateway-*.jar
COPY ${JAR_FILE} /app/gateway.jar
RUN chown mcp:mcp /app/gateway.jar

# 切换为非 root 用户运行
USER mcp:mcp

# 暴露网关默认端口 18080
EXPOSE 18080

# 容器健康检查：探测 Spring Boot Actuator 端点 /actuator/health
HEALTHCHECK --interval=15s --timeout=3s --retries=3 CMD wget -qO- http://localhost:18080/actuator/health || exit 1

# 默认环境变量配置
ENV JAVA_OPTS="-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

# 启动命令
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/gateway.jar"]
