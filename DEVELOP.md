# BloomHarness 开发者指南 (DEVELOP.md)

欢迎来到 **BloomHarness** 开发者文档！本文档旨在为开发人员提供清晰、全面的项目全貌介绍、环境配置指导、系统架构解析、代码编写规范以及调试测试流程。

---

## 目录

- [一、项目架构与模块拓扑](#一项目架构与模块拓扑)
- [二、技术栈与环境依赖](#二技术栈与环境依赖)
- [三、本地开发环境搭建](#三本地开发环境搭建)
- [四、核心业务与运行机制剖析](#四核心业务与运行机制剖析)
  - [1. ReAct 自主智能体循环 (Agent Loop)](#1-react-自主智能体循环-agent-loop)
  - [2. 上游大模型与 OpenAI Tools 协议适配](#2-上游大模型与-openai-tools-协议适配)
  - [3. 本地安全沙箱与文件操作 (PathSandbox)](#3-本地安全沙箱与文件操作-pathsandbox)
  - [4. 全双工/流式进度广播 (SSE Pipeline)](#4-全双工流式进度广播-sse-pipeline)
  - [5. 服务商配置与动态持久化](#5-服务商配置与动态持久化)
- [五、构建与启动](#五构建与启动)
  - [1. 启动后端 Spring Boot 服务](#1-启动后端-spring-boot-服务)
  - [2. 启动前端 Vite 开发服务器](#2-启动前端-vite-开发服务器)
- [六、测试指南](#六测试指南)
- [七、代码规范与提交约定](#七代码规范与提交约定)

---

## 一、项目架构与模块拓扑

BloomHarness 采用模块化的 Maven 多工程架构，职责边界清晰：

```
BloomHarness (Root Parent)
├── bloom-harness-protocol      # 通信协议契约与数据模型 (Messages, Snapshots, Content)
├── bloom-harness-core          # 核心调度与状态机引擎 (AgentLoop, ToolRegistry, Context)
├── bloom-harness-ai-adapter    # AI 模型适配器与路由桥接 (AiModelAdapter, StreamAdapter, ProviderRegistry)
├── bloom-harness-tools         # 基础内置工具集 (bash, read, write, edit, glob, grep 及沙箱)
├── bloom-harness-skills        # 可动态装载的 Agent Skill 技能体系
├── bloom-harness-extension     # 扩展与插件接入层
├── bloom-harness-mcp           # Model Context Protocol (MCP) 客户端集成
├── bloom-harness-storage       # SQLite 数据库持久化服务 (MyBatis, 会话与消息流水)
├── bloom-harness-server        # HTTP REST API、SSE 响应式推送与 WebSocket 服务端点
├── bloom-harness-app           # Spring Boot 启动入口与运行时生命周期聚合
└── bloom-harness-web           # Vue 3 + Vite + TailwindCSS 现代化前端操作面板
```

---

## 二、技术栈与环境依赖

- **JDK**: Java 21（深度利用虚拟线程 Virtual Threads 并发能力）
- **Spring Boot**: 3.3.5
- **构建工具**: Apache Maven 3.9+
- **数据库**: SQLite 3 (通过 `sqlite-jdbc` 与 MyBatis 驱动，零额外数据库安装)
- **响应式编程**: Project Reactor (Flux, Sinks 多播流)
- **Node.js**: >= 20.x
- **前端工具链**: pnpm 9+, Vite 5, Vue 3, Pinia, TypeScript, TailwindCSS, Lucide Icons

---

## 三、本地开发环境搭建

### 1. 克隆代码库
```bash
git clone https://github.com/claubloom/BloomHarness.git
cd BloomHarness
```

### 2. 本地 Maven 依赖准备
项目耦合了 `com.miniapi:ai-router-core` 引擎，若本地尚未安装该包，可预先在其源码目录下安装至本地 Maven 仓库：
```bash
mvn clean install -DskipTests
```

### 3. 前端依赖安装
```bash
cd bloom-harness-web
pnpm install
cd ..
```

---

## 四、核心业务与运行机制剖析

### 1. ReAct 自主智能体循环 (Agent Loop)
位于 `bloom-harness-core` 的 `AgentLoop.java` 中。整个任务在 Java 21 **虚拟线程 (Virtual Thread)** 中调度运行：
1. **Think（推理）**：调用 `AiModelAdapter` 获取大模型的流式回复；若检测到思考标签，独立向前端推送 `thinking` 内容。
2. **Act（行动）**：若 Assistant 消息中包含 `tool_calls`（例如 `write`、`bash`），暂停模型输出，将调用集封装为 `ToolContext` 交由 `ToolExecutor` 批处理执行。
3. **Observe（观察）**：将每个工具执行产出的标准输出/错误包装为 `ToolResultMessage`，回灌至对话上下文。
4. **下一轮循环**：标记 `hasMoreToolCalls = true`，自动触发下一轮推理，直至大模型判定任务完成，优雅退出。

### 2. 上游大模型与 OpenAI Tools 协议适配
位于 `bloom-harness-ai-adapter`：
- **Schema 动态提取**：`AiModelAdapter` 动态遍历 `ToolRegistry` 中的工具，按标准 OpenAI Function Calling 规范生成 `tools` 字段；
- **自适应 URL 解析**：支持标准 OpenAI 格式，无论 BaseURL 是否附带 `/v1` 或 `/chat/completions`，自动规整，杜绝多斜杠或 404；
- **智能错误映射**：细粒度捕获网络异常（`NetworkException`）、连接/读取超时（`TimeoutException`）、鉴权失效（401）、账户欠费（402）、频控限流（429）与服务端故障（5xx），并将排查建议直观渲染至前端卡片。

### 3. 本地安全沙箱与文件操作 (PathSandbox)
位于 `bloom-harness-tools` 的 `PathSandbox.java`：
- 所有内置文件工具（`read`, `write`, `edit`, `glob`, `grep`）均需经由 `PathSandbox.resolve()` 校验；
- 严格限制文件修改只能发生在允许的工作区目录范围内，拦截路径遍历（`../`）攻击；
- `write` 工具写入时使用**原子临时文件落盘 + 操作系统级原子重命名 (Atomic Move)**，防止并发写坏文件。

### 4. 全双工/流式进度广播 (SSE Pipeline)
- 后端采用 `DefaultSessionEventBroadcaster`，通过 `Sinks.many().multicast().onBackpressureBuffer()` 广播打字机增量；
- 前端 `useSse.ts` 通过 `EventSource` 监听 `/api/stream/{sessionId}`，实时接收 `item_started`、`assistant_delta`、`item_finished` 与 `end` 事件；
- 滚动更新采用 `requestAnimationFrame` 防抖节流，避免高频 DOM 回流导致前端假死。

### 5. 服务商配置与动态持久化
- `ProviderRegistry.java` 实现开机自动读取 `bloom-providers.json`；
- 用户在前端修改、测试连通性、增删服务商或一键拉取可用模型（`/api/config/providers/fetch-models`）后，配置原子落盘，重启完全不丢失。

---

## 五、构建与启动

### 1. 启动后端 Spring Boot 服务
```bash
# 根目录下执行
mvn clean compile -DskipTests
mvn spring-boot:run -pl bloom-harness-app
```
后端默认运行在端口：`http://localhost:8787`

### 2. 启动前端 Vite 开发服务器
```bash
cd bloom-harness-web
pnpm run dev
```
前端默认运行在：`http://localhost:5173`，并通过 Vite Proxy 自动反向代理 `/api` 请求至 `8787` 端口。

---

## 六、测试指南

项目包含从单元测试、组件冒烟测试到全链路 ReAct 自主循环测试：

```bash
# 运行全模块单元测试
mvn test

# 专门执行 ReAct 自主循环冒烟测试 (端到端验证模型调用工具并创建文件)
mvn test -Dtest=ReActSmokeTest -pl bloom-harness-app -am
```