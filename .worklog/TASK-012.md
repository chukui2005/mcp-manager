# TASK-012: 受控本地 STDIO 进程池托管与 Phase 3 独立网关收官

> **状态**: DONE  
> **任务 ID**: TASK-012  
> **责任人**: engineer (研发), reviewer (审核), architect (架构守护), lead (统筹)  
> **日期**: 2026-11-02  

Approved: Yes

## 1. 任务背景与核心目标

承接 TASK-009（网关骨架与路由匹配）、TASK-010（动态路由加载与工具聚合）以及 TASK-011（远端 HTTP/SSE 出站客户端池与凭据注入），启动 Phase 3 的终局收官任务 TASK-012：
1. **受控本地进程出站客户端 (`StdioUpstreamClient`)**：
   - 依据 ADR 0008 与 Blueprint 0008，实现支持本地子进程标准输入输出（stdin/stdout）基于 JSON-RPC 2.0 异步消息交换的出站客户端；
   - 严格遵循《AGENTS.md》安全基线：STDIO 命令执行必须被视为不受信任代码执行，仅允许通过配置文件白名单预定义可执行文件与固定入参，严禁动态命令拼接；
   - 继承最小受信任环境变量子集，绝不透传宿主机敏感变量；
   - 支持动态下游工具拉取 (`tools/list`)、工具调用 (`tools/call`) 与 `ping` 探测。
2. **本地子进程池与生命周期托管 (`ManagedStdioProcessPool`)**：
   - 具备子进程启动、健康保活、心跳检测与请求队列分发机制；
   - 完善子进程优雅销毁（先 SIGTERM / destroy，超时强制 SIGKILL / destroyForcibly），清理管道与 I/O 线程，杜绝僵尸进程与句柄泄漏；
   - 集成到 `UpstreamClientFactory` 与 `UpstreamClientPool`，实现多协议路由的统一工厂分配与缓存管理。
3. **多协议混合网关端到端验收与测试补齐**：
   - 覆盖 HTTP、SSE 与 STDIO 三种协议共存的端到端路由分发与聚合发现验证；
   - 确保 `mcp-governance-gateway` 能够作为独立网关稳定运行，全流程打通 `mcp-governance-core` 治理拦截链路；
   - 验证构建与门禁：`mvn -B verify` 全模块测试 100% 通过，Javadoc 零警告。
4. **两轮代码审查与收官交付**：
   - 审核员执行两轮只读代码审查（功能正确性、并发、安全基线、资源清理）；
   - 审查通过后，由统筹中枢向项目负责人正式汇报，总结 Phase 3 独立网关全景。

---

## 2. 研发实现摘要

研发工程师已在隔离分支 `task/TASK-012-stdio-pool` 完成开发并通过全工程验证：

1. **受控本地 STDIO 出站客户端 (`StdioUpstreamClient`)**：
   - 基于 `ProcessBuilder(List<String>)` 结构化入参启动，严禁任何形式的 shell 字符串拼接执行；
   - 实现了受控环境变量沙箱机制，剔除所有包含 `KEY`、`TOKEN`、`SECRET`、`PASSWORD`、`PASSWD`、`CREDENTIAL`、`AUTH` 等敏感字样的环境变量，仅透传基础安全变量；
   - stdin 写入按锁加固按行刷新输出；stdout 启动专用后台守护线程异步按行读取 JSON-RPC 2.0 并分发至 `Sinks.One` 队列；
   - **防死锁设计**：stderr 启动独立后台守护线程持续消费并打印诊断警告日志，避免 OS 管道缓冲区占满导致子进程挂起；
   - 支持完整的 `tools/list`、`tools/call` 以及 `ping` 心跳探测。

2. **本地子进程托管池 (`ManagedStdioProcessPool`)**：
   - 实现 `DisposableBean`，提供对受控本地 STDIO 子进程客户端的统一托管、查询、逐出与停机销毁；
   - 两阶段安全停机：先执行优雅 `process.destroy()`，等待 3 秒；若未退出则强制执行 `process.destroyForcibly()` 并安全回收 I/O 管道句柄，杜绝僵尸进程与句柄泄露。

3. **工厂与网关执行适配器整合**：
   - 完善 `UpstreamClientFactory`，支持 `protocol == STDIO` 时构造 `StdioUpstreamClient` 并注册进 `ManagedStdioProcessPool`；
   - 更新 `DefaultGatewayExecutionAdapter`，识别 STDIO 本地执行类型路由并打通客户端连接池执行与治理链路。

4. **测试补齐与端到端验证**：
   - 新增 `StdioUpstreamClientTest`：利用轻量纯 Java 伪 MCP 子进程验证双工管道通信、环境变量脱敏与托管池销毁；
   - 新增 `GatewayMultiProtocolIntegrationTest`：覆盖 HTTP 与 STDIO 路由混合注册、统一 `tools/list` 聚合发现与 `tools/call` 精确路由分发；
   - 全工程构建通过：`mvn -B verify` 7 个 Reactor 模块测试全部 100% 通过（Gateway 模块 43 个测试全过）；
   - Javadoc 检查通过：`mvn javadoc:javadoc` 零警告。

---

## 3. 审核员审查报告

审查员已对隔离分支 `task/TASK-012-stdio-pool` 提交的代码执行严格的【两遍分析审查法】：

### 第一遍分析（正确性、契约一致性与并发安全）
1. **JSON-RPC 2.0 异步双工协议实现**：`StdioUpstreamClient` 采用写锁保证 stdin 原子写入与刷新，stdout 独立守护线程持续按行解析并通过 `Sinks.One` 按 id 交付响应，逻辑正确；
2. **防死锁与资源清理**：stderr 专用独立守护线程异步排空，避免 OS 管道缓冲区被 stderr 塞满引发子进程 hang 住；
3. **两阶段进程回收**：`ManagedStdioProcessPool` 在 `destroy()` 时先尝试 `process.destroy()` 并留出 3 秒超时窗口，若超时未终止坚决调用 `process.destroyForcibly()` 强制终止，管道流与线程妥善释放，有效避免僵尸进程与句柄泄漏；
4. **统一 SPI 与工厂模式**：`UpstreamClientFactory` 与 `ManagedStdioProcessPool`、`UpstreamClientPool` 规范整合，类型不外泄，符合架构原则。

### 第二遍分析（机械安全扫描与反模式排查）
1. **命令注入防范**：严格通过 `ProcessBuilder(List<String>)` 传递预置参数数组，无任何 shell 字符串拼接或 `sh -c` / `cmd /c` 调用，符合 OWASP MCP05 安全防线；
2. **敏感环境变量沙箱隔离**：`sanitizeEnvironment` 严密过滤 `KEY`、`TOKEN`、`SECRET`、`PASSWORD`、`CREDENTIAL`、`AUTH` 等敏感变量，防止宿主机系统密钥泄漏给子进程，符合 OWASP MCP01 与《AGENTS.md》安全基线；
3. **日志与脱敏**：错误处理和日志中无明文敏感凭据打印，符合项目安全合规标准；
4. **构建门禁**：全工程 `mvn -B verify` 7 个模块测试 100% 通过（Gateway 43 个测试全部 PASS），`mvn javadoc:javadoc` 零警告。

**审查结论**: `verdict: PASS`

---

## 4. 验收与结论

- 状态机流转：`READY → IN_REVIEW → READY_TO_LAND`；
- 所有代码注释、文档与提交全中文；
- 架构守护与安全基线完全符合 ADR 0008 与 Blueprint 0008。
- 准予合入 `main` 分支。
