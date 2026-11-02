# 基础运行时镜像：基于轻量、经过安全加固的 Eclipse Temurin JRE 17 Alpine 镜像
FROM eclipse-temurin:17-jre-alpine

# 维护者信息与元数据
LABEL maintainer="mcp-manager" \
      description="MCP Governance Scanner CLI - 命令行扫描器"

# 安全强化：创建非 root 运行专属系统组与用户 (UID/GID 10001:10001)
RUN addgroup -g 10001 -S mcp && \
    adduser -u 10001 -S mcp -G mcp

# 创建应用程序目录与宿主机扫描挂载工作区
WORKDIR /workspace
RUN mkdir -p /app && \
    chown -R mcp:mcp /app /workspace

# 拷贝 CLI 可执行 jar 包
ARG JAR_FILE=target/mcp-governance-cli-*.jar
COPY ${JAR_FILE} /app/cli.jar
RUN chown mcp:mcp /app/cli.jar

# 切换为非 root 用户运行
USER mcp:mcp

# 容器工作目录默认设为 /workspace，便于宿主机一键挂载并扫描本地配置文件
WORKDIR /workspace

# 默认入口点与帮助命令
ENTRYPOINT ["java", "-jar", "/app/cli.jar"]
CMD ["--help"]
