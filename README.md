# BloomHarness

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange.svg" alt="Java 21" />
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.5-brightgreen.svg" alt="Spring Boot 3.3.5" />
  <img src="https://img.shields.io/badge/Vue-3.5-blue.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Vite-5.4-purple.svg" alt="Vite 5" />
  <img src="https://img.shields.io/badge/Database-SQLite%203-lightgrey.svg" alt="SQLite 3" />
  <img src="https://img.shields.io/badge/License-Apache%202.0-blue.svg" alt="License" />
</p>

<p align="center">
  <strong>基于 Java 21 虚拟线程与响应式事件流构建的新一代自主 AI 软件工程智能体（Agent）底座</strong>
</p>

---

## 快速导航

- 📖 **[开发指南 (DEVELOP.md)](DEVELOP.md)**：包含详细的架构拓扑、环境配置、运行机制与测试指引。
- 🤝 **[贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md)**：代码规范、Conventional Commits 提交标准与 PR 审核流程。
- 🛠️ **[技术选型全景 (BLOOM-HARNESS-TECH-STACK.md)](BLOOM-HARNESS-TECH-STACK.md)**：各层技术栈选型依据与工程指标。
- 📐 **[核心架构设计 (DESIGN.md)](DESIGN.md)**：系统时序、接口规范与事件流转机制。

---

## 项目简介

**BloomHarness** 是一个由 Java 21、Spring Boot 3 与 Vue 3 全栈驱动的高性能、可扩展的自主 AI 编码智能体运行环境。项目采用了Pi-Agent的基本架构，使用Java实现了完整的 **ReAct自主闭环**。

智能体能够在本地安全沙箱环境中自主调用工具进行文件读取、代码分析、批量编辑、项目构建及命令执行，并通过高响应性的 SSE 流式通道为开发者带来沉浸式的结对编程交互体验。

---

## 核心特性

- 🧠 **真正的 ReAct 自主智能体循环 (Autonomous Loop)**
  - 严格遵循 OpenAI Function Calling 协议标准，实现模型思考（Thinking）、工具调用（Action）与结果回灌（Observation）的全自主多轮推理循环。
- 🚀 **Java 21 虚拟线程原生并发 (Virtual Threads)**
  - 每一个 Agent 任务会话运行在轻量级虚拟线程上，大模型网络 I/O 与耗时命令执行自动让出载体线程，具备极高的单机并发承载力。
- 🛡️ **安全隔离与路径沙箱 (PathSandbox)**
  - 内置工作区边界约束，严防路径穿越（`../`）；文件修改采用操作系统级“临时文件落盘 + 原子重命名 (Atomic Move)”机制，避免写坏用户工程代码。
- 🌐 **多上游服务商接入与动态持久化 (AI Router)**
  - 零内置硬编码供应商，界面支持自由配置 DeepSeek、OpenAI、Anthropic、本地 Ollama 或自建 vLLM 端点；
  - **一键拉取模型列表**：自动探测并拉取服务商所有可用模型标签，支持密钥单向掩码与磁盘原子持久化（`bloom-providers.json`）。
- ⚡ **多级超时与细粒度报错回显**
  - 分级控制连接超时（8s）、首包等待超时（25s）与单轮交互超时；
  - 精准映射 HTTP 400（参数异常）、401/403（鉴权失败）、402（余额不足）、429（限流）及 5xx 服务端故障，前端专属诊断卡片直观呈现排查建议。
- 🎨 **极致流畅的现代化前端交互**
  - Vue 3 + TailwindCSS 暗黑科技风格，集成立时供应商/模型切换、工作区动态切换、流式思考折叠、工具执行结果超长智能截断与 `requestAnimationFrame` 防抖节流。

---

## 模块架构一览

```
BloomHarness
├── bloom-harness-protocol      # 通信协议契约与数据模型 (Messages, Snapshots, Content)
├── bloom-harness-core          # 核心调度与状态机引擎 (AgentLoop, ToolRegistry, Context)
├── bloom-harness-ai-adapter    # AI 模型适配器与路由桥接 (AiModelAdapter, StreamAdapter, ProviderRegistry)
├── bloom-harness-tools         # 内置工具集 (bash, read, write, edit, glob, grep 及沙箱)
├── bloom-harness-skills        # 可动态装载的 Agent Skill 技能体系
├── bloom-harness-extension     # 扩展与插件接入层
├── bloom-harness-mcp           # Model Context Protocol (MCP) 客户端集成
├── bloom-harness-storage       # SQLite 持久化服务 (MyBatis, 会话流水与统计)
├── bloom-harness-server        # HTTP REST API、SSE 响应式推送端点
├── bloom-harness-app           # Spring Boot 启动入口与集成装配
└── bloom-harness-web           # Vue 3 + Vite + TailwindCSS 现代化前端操作面板
```

---

## 快速上手 (Quick Start)

### 1. 环境准备
- **Java**: JDK 21 或更高版本
- **Maven**: 3.9+
- **Node.js**: >= 20.x
- **pnpm**: >= 9.x

### 2. 编译并启动后端服务
```bash
# 1. 编译全部后端模块
mvn clean compile -DskipTests

# 2. 启动 Spring Boot 后端
mvn spring-boot:run -pl bloom-harness-app
```
后端服务将启动并监听在：`http://localhost:8787`

### 3. 启动前端操作面板
```bash
cd bloom-harness-web

# 1. 安装前端依赖
pnpm install

# 2. 启动 Vite 开发服务器
pnpm run dev
```
在浏览器中打开：`http://localhost:5173`

### 4. 首次使用配置
1. 点击左侧工具栏的 **⚙️ 设置** 图标；
2. 点击 **“添加自定义服务商”**，填写您的 Base URL（如 `https://api.deepseek.com`）与 API Key；
3. 点击 **“一键获取模型列表”**，自动探测并同步支持的模型；
4. 点击 **“保存并生效”**，在发送栏左侧选择模型，即可向 Agent 发送编程指令！

---

## 测试验证

BloomHarness 具备完善的多层测试套件，可端到端验证 ReAct 循环与文件写入：

```bash
# 运行全模块测试
mvn test

# 运行端到端 ReAct 自主循环冒烟测试 (自动验证模型调用工具并在真实目录创建文件)
mvn test -Dtest=ReActSmokeTest -pl bloom-harness-app -am
```

---

## 更多参考与指引

- 查看架构实现细节与接口规范，请参阅 **[开发指南 (DEVELOP.md)](DEVELOP.md)**。
- 参与代码贡献、提交 Issue 或发起 PR，请阅读 **[贡献指南 (CONTRIBUTING.md)](CONTRIBUTING.md)**。

---

## 开源协议

本项目基于 [Apache License 2.0](LICENSE) 协议开源。
