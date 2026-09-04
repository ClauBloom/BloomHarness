# BloomHarness 贡献指南 (CONTRIBUTING.md)

感谢你对 **BloomHarness** 项目的关注与贡献！我们非常欢迎来自社区的 Issue 报告、新特性建议、架构讨论以及代码 Pull Request (PR)。

为了让每一位参与者都能高效协作，并保持代码库的高水准工程质量，请在提交贡献前仔细阅读本指南。

---

## 目录

- [行为准则](#行为准则)
- [如何参与贡献](#如何参与贡献)
  - [1. 提交 Issue](#1-提交-issue)
  - [2. 提出 Feature Request](#2-提出-feature-request)
  - [3. 提交代码 (Pull Request)](#3-提交代码-pull-request)
- [Git 分支管理与 Commit 规范](#git-分支管理与-commit-规范)
- [代码编写风格与设计哲学](#代码编写风格与设计哲学)
  - [Java 后端规范](#java-后端规范)
  - [Vue 前端规范](#vue-前端规范)
  - [注释与文档语言](#注释与文档语言)
- [测试与质量检查](#测试与质量检查)

---

## 行为准则

作为 BloomHarness 开源社区的一员，我们倡导开放、包容、互相尊重与技术为先的交流氛围。请在所有沟通中保持客观友好，聚焦技术方案本身。

---

## 如何参与贡献

### 1. 提交 Issue
如果你在使用过程中遇到了 Bug、异常报错或性能卡顿：
1. 请先在 GitHub Issues 中搜索，确认该问题是否已被提出；
2. 若未被提出，请使用清晰的标题创建 Issue；
3. **提供重现步骤**：包含操作系统版本、JDK / Node 版本、相关后端的 `WARN` / `ERROR` 完整堆栈日志以及网络请求/响应体（请注意脱敏敏感的 API Key）；
4. 如果是前端界面异常，建议附带 DevTools 控制台报错信息与截图。

### 2. 提出 Feature Request
我们欢迎围绕智能体架构（ReAct 循环、工具链生态、MCP 协议、多模型路由调度）的新特性建议：
1. 阐明该功能的实际业务背景或痛点；
2. 描述期望达成的最终交互效果与技术设计思路。

### 3. 提交代码 (Pull Request)
1. **Fork 本仓库** 到你的个人 GitHub 账号；
2. 从 `main` 分支拉出特性分支：`git checkout -b feature/your-feature-name` 或修复分支 `fix/issue-description`；
3. 编写代码并遵循工程规范；
4. 运行全量单元测试与前端打包检查，确保所有测试均为绿灯：
   ```bash
   mvn test
   cd bloom-harness-web && pnpm build
   ```
5. 提交你的修改并推送到远程仓库；
6. 创建 Pull Request，详细描述本次改动的动机、改动点以及自测验证结果。

---

## Git 分支管理与 Commit 规范

为了保持版本日志的清晰可读，本项目采用 **[Conventional Commits](https://www.conventionalcommits.org/)** 语义化提交规范：

```text
<type>(<scope>): <subject>

<body>
```

### 常用 Type 标识
- `feat`: 新增功能（Feature）
- `fix`: 修复 Bug 或异常
- `docs`: 文档增补与修改（如 README、DEVELOP、Javadocs 等）
- `style`: 代码格式变动（空格、格式化、缺少分号等，不影响代码逻辑）
- `refactor`: 代码重构（既非新增功能也非修复 Bug）
- `perf`: 性能优化（如减少回流、节流、提升执行效率）
- `test`: 增加或修改测试用例
- `chore`: 构建过程、依赖项更新或辅助工具变动

**示例**：
```bash
git commit -m "feat(ai-adapter): 增加标准 OpenAI tools 结构注入与双向模型校验"
git commit -m "fix(sse): 修复多连接与刷新时的 unicast 订阅冲突"
```

---

## 代码编写风格与设计哲学

### Java 后端规范
1. **统一 JDK 21 现代特性**：推崇使用虚拟线程（Virtual Threads）、Record 不可变数据载体、Pattern Matching 模式匹配、Switch 表达式；
2. **防御性编程与明确报错**：捕获特定异常并映射为明确的领域异常（如 `BloomAiException` 体系），携带排查指引（Suggestion），禁止泛化忽略异常；
3. **代码整洁**：遵循 Lombok 与 Spring Boot 官方最佳实践。

### Vue 前端规范
1. **Vue 3 Composition API**：使用 `<script setup lang="ts">` 语法糖，杜绝 Options API；
2. **强类型定义**：严禁随意使用 `any`，所有 API 契约必须在 `types/` 目录下拥有对应的 TypeScript Interface；
3. **渲染性能保护**：在处理长文本或流式推文字段时，必须考虑大文本截断、折叠与帧调度（如 `requestAnimationFrame`），避免造成界面假死。

### 注释与文档语言
- **优先使用中文注释**：本项目主要文档与关键逻辑代码注释采用规范、通俗、准确的中文，让开发者能快速掌握业务意图。

---

## 测试与质量检查

在提交 PR 之前，请务必在本地通过以下三项质量关卡：

1. **多模块后端编译与测试**：
   ```bash
   mvn clean test
   ```
2. **端到端 ReAct 冒烟验证**：
   ```bash
   mvn test -Dtest=ReActSmokeTest -pl bloom-harness-app -am
   ```
3. **前端 TypeScript 类型检查与 Production 打包**：
   ```bash
   cd bloom-harness-web
   pnpm run build
   ```

再次感谢你为 BloomHarness 建设更强大、更优雅的开源智能体底座贡献力量！🚀
