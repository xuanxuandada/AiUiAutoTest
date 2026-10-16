# AI 测试平台 · yx-test-AiTest

> 用自然语言写用例，让 AI 替你点页面。
>
> 从「AI 生成用例 → AI 步骤转换 → 浏览器自动化执行 → 报告回传 → 失败 AI 诊断」的 UI 自动化测试全链路平台。



<video src="F:\WXWork\1688856340868196\Cache\Video\2026-08\新建智能报价.mp4"></video>

视频演示

## 项目简介

yx-test-AiTest 是一个 AI 驱动的 UI 自动化测试平台后端项目。使用者只需用自然语言描述测试场景，平台即可：

1. 调用大模型**生成结构化测试用例**；
2. 将自然语言步骤**自动转换为可执行的浏览器操作脚本**；
3. 由 Midscene.js + Playwright **驱动真实浏览器逐步执行**，并自动完成 AI 断言；
4. 执行结束后**回传报告与截图**，失败时支持**一键 AI 诊断**，直接给出报错含义、可能原因与解决建议。

整个项目由「Java 后端服务」与「Node.js 浏览器执行引擎（midscene-runner）」双进程协作组成，后端负责业务、数据与调度，Runner 负责真正的浏览器操作。

## 功能特性

### AI 用例生成

- 自然语言生成用例：输入一句话（如「测试用户登录功能」）+ 目标 URL，AI 直接产出多条结构化用例；
- 场景批量生成：按「页面功能描述 + 期望数量」批量生成整套测试场景；
- AI 生成记录留档（prompt、响应、生成用例 ID、使用模型），过程可追溯；
- 生成结果自动解析为用例实体（名称、步骤、预期结果、优先级、标签），兼容 AI 返回的代码块包裹 JSON。

### AI 步骤转换

- 自然语言步骤 → Midscene.js 可执行格式（`action` + `prompt`），支持一个自然语言步骤拆分为多个执行步骤；
- 预期结果自动落到「能验证它的那一步」上，形成步骤级自动断言；
- 已是 Midscene 格式的步骤直接落库，不浪费 AI 调用；
- 转换过程异步执行，不阻塞接口响应，转换状态（PENDING / CONVERTED / FAILED）全程可见；
- 脚本编辑前自动备份到历史表，误改可追溯。

### 浏览器自动化执行

- 后端通过 HTTP 将任务下发至 Runner（默认端口 3001），Runner 用 Midscene.js + Playwright 驱动 Chromium 执行；
- 支持步骤类型：AI 操作、AI 输入、AI 断言、条件等待、周期刷新等待、点击、滚动、页面跳转、浏览器后退、文件上传、固定等待；
- **智能文件上传**：AI 自动定位上传按钮 → 捕获文件选择框 → 注入本地文件，无需手写选择器；
- **周期刷新等待（aiWaitRefresh）**：适合「提交后状态异步变化」的流程，自动轮询刷新直到条件满足；
- 任务队列 + 并发闸门：所有任务先入队再开跑，并发数可配（默认 1 串行），避免瞬间拉起大量浏览器打满内存与 AI 配额；
- 容错导航：统一 `domcontentloaded` + SPA 兜底，兼容跳转中断、SPA 渲染延迟等常见问题。

### 回归测试套件

- 套件与用例多对多关联，支持自定义执行顺序；
- **套件内副本机制**：在套件内编辑用例步骤只影响本套件，不污染父用例；副本脚本独立存储，执行时按「套件副本 → 父用例脚本 → 父用例原始步骤」优先级回退；
- **套件级登录态注入**：支持粘贴 F12 控制台导出的 localStorage JSON，执行时自动注入登录态并刷新，免去每条用例重复登录；
- 套件预留 Cron 表达式字段（可对接调度器扩展），并记录上次执行时间与结果概览。

### 执行报告与 AI 诊断

- 每次执行落盘 Playwright HTML 报告 + 页面截图（成功/失败都生成），报告地址回传数据库，列表页点击即开；
- 执行结果包含步骤级详情：每步的操作、断言结果、失败原因；
- **失败一键 AI 诊断**：自动把报错原文 + 用例信息 + 步骤现场交给大模型，输出固定三段式结论（报错含义 / 可能原因 / 解决建议），结果缓存复用，不重复烧 token；
- 应用重启时自动清理残留的「执行中」记录并标记为异常，避免任务永远转圈。

### 多用户与数据隔离

- **每个用户使用自己的 AI API Key**：Key 存数据库、按登录用户隔离、互不可见；系统不提供全局兜底 Key，未配置则明确报错提示去配置，绝不静默串用他人 Key；
- Midscene 以 `isolatedMode` 运行，多用户并发执行也不会串 Key；
- 多租户隔离：业务数据按租户划分；
- 组织树数据可见范围：基于「直属上级」关系链，数据对「本人 + 全部下级」可见，他人不可见。

## 系统架构

```
┌──────────────────────┐   POST /execute（下发任务）   ┌────────────────────────┐
│   Spring Boot 后端    │ ───────────────────────────▶ │   midscene-runner      │
│      (Java 21)       │                              │  (Node.js / Express)   │
│        :8080         │ ◀─────────────────────────── │        :3001           │
└──────────────────────┘   POST /callback（回传结果）   └────────────────────────┘
          │                                                       │
          ▼                                                       ▼
   ┌─────────────┐                                     ┌───────────────────┐
   │    MySQL    │                                     │ Playwright 浏览器  │
   │  业务数据    │                                     │  Midscene.js Agent │
   └─────────────┘                                     └───────────────────┘
          │                                                       │
          ▼                                                       ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │   报告 / 截图共享目录（同机文件系统，默认 G:/aitest/...）              │
   │   reports/{executionId}.html      screenshots/{executionId}.png    │
   └────────────────────────────────────────────────────────────────────┘
```

设计要点：

- 报告与截图由 Runner 直接写入共享磁盘目录，回调只传 URL，避免几十 MB 的 base64 数据挤爆 HTTP 通道；
- 后端启动时自动拉起 Runner 子进程（`MidsceneRunnerStarter`），关闭时按进程树整体回收，无需手工维护两个终端；
- Runner 也可独立启动调试，不依赖后端。

## 技术栈

### 后端（Spring Boot）

| 技术 | 版本 | 说明 |
| --- | --- | --- |
| Java | 21 | 开发语言 |
| Spring Boot | 3.3.1 | Web 基础框架 |
| MyBatis-Plus | 3.5.7 | ORM 增强 |
| MySQL | 8.x | 业务数据库 |
| JJWT | 0.12.6 | JWT 令牌签发与校验 |
| spring-security-crypto | - | 仅用于 BCrypt 密码加密 |
| Aliyun OSS SDK | 3.18.1 | 文件存储（可选） |
| Lombok | 1.18.38 | 简化样板代码 |

### 执行引擎（midscene-runner）

| 技术 | 说明 |
| --- | --- |
| Node.js | 运行环境（建议 18+） |
| Express | 任务接收 HTTP 服务 |
| Midscene.js (`@midscene/web`) | AI 页面操作与断言 |
| Playwright | 浏览器驱动（Chromium） |
| dotenv | 环境变量配置 |

## 核心流程

```
自然语言描述 ──▶ [AI生成] ──▶ 结构化用例（存库）
                                   │
                                   ▼
                     [AI步骤转换] 自然语言 → Midscene 可执行步骤
                                   │
                                   ▼
             触发执行（单条/批量/套件）──▶ 执行记录 PENDING
                                   │
                                   ▼
        Runner 队列 ──▶ 启动 Chromium ──▶ 注入登录态（可选）──▶ 逐步执行 + AI断言
                                   │
                                   ▼
        报告/截图落盘 ──▶ 回调后端（状态、步骤详情、报告URL）──▶ PASSED / FAILED
                                   │
                                   ▼
                        失败时 ──▶ AI 诊断（含义/原因/建议）
```

## 快速开始

### 环境要求

- JDK 21+
- Maven 3.8+
- Node.js 18+（推荐 20+）
- MySQL 8.x

### 1. 初始化数据库

无需手工建库建表：连接串带 `createDatabaseIfNotExist=true`，应用启动时自动执行 `src/main/resources/sql/init.sql` 完成建表与字段升级。

只需保证 MySQL 已启动，并在 `src/main/resources/application.yml`（或环境变量）中配置好连接信息。

### 2. 启动后端

```bash
# 方式一：Maven 直接运行（注意：项目路径含中文时建议用方式二）
mvn spring-boot:run

# 方式二：打包后运行（推荐）
mvn clean package -DskipTests
java -jar target/yx-test-AiTest-0.0.1-SNAPSHOT.jar
```

> 提示：Windows 下项目路径包含中文字符时，`spring-boot:run` 可能因 JVM fork 失败而找不到主类，请改用方式二。

### 3. 启动 midscene-runner

后端启动时会自动拉起 Runner（子进程方式）。首次使用需要先安装依赖：

```bash
cd midscene-runner
npm install
cp .env.example .env      # Windows: copy .env.example .env
npx playwright install chromium
```

也可以独立启动 Runner 调试：

```bash
cd midscene-runner
npm start
```

启动成功标志：

```
[Runner] Midscene.js 测试执行器已启动，端口: 3001
[Runner] 等待后端发送测试任务...
```

### 4. 配置 AI API Key

平台不自带任何全局 Key。登录后前往「设置 → AI 模型配置」保存自己的 API Key（支持任意 OpenAI 兼容接口，默认模板使用阿里云百炼 `qwen-vl-max`）。

> 注意：浏览器自动化要求多模态模型族（如 `qwen2.5-vl`），纯文本模型无法用于页面操作。

## 环境变量配置

后端（`application.yml` 中均已提供本地默认值，生产环境必须覆盖）：

| 变量名 | 说明 | 默认 |
| --- | --- | --- |
| `DB_PASSWORD` | 数据库密码 | 见 application.yml（本地调试默认，生产必须覆盖） |
| `JWT_SECRET` | JWT 签名密钥（泄露可伪造任意用户令牌） | 本地占位符 |
| `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS 凭据（不配置则 OSS 接口不可用，其余功能不受影响） | 空 |
| `APP_REPORT_DIR` | 报告存储目录 | `G:/aitest/reports` |
| `APP_SCREENSHOT_DIR` | 截图存储目录 | `G:/aitest/screenshots` |

Runner（`midscene-runner/.env`）：

| 变量名 | 说明 | 默认 |
| --- | --- | --- |
| `PORT` | Runner 监听端口 | `3001` |
| `MAX_CONCURRENT_TASKS` | 并发执行任务上限（1 = 完全串行） | `1` |
| `BACKEND_REPORT_DIR` | 报告目录（必须与后端一致） | `G:/aitest/reports` |
| `BACKEND_SCREENSHOT_DIR` | 截图目录（必须与后端一致） | `G:/aitest/screenshots` |

> 报告/截图目录是「Runner 写入、后端读取」的共享文件系统，两端配置必须保持一致，否则前端打开报告会 404。

## 支持的测试步骤

| action | 说明 |
| --- | --- |
| `navigate` | 页面跳转（跨页面测试，自动补全 URL 协议） |
| `back` | 浏览器后退 |
| `aiAction` | AI 自然语言操作（描述含文件路径时自动转上传） |
| `aiTap` | AI 点击（描述含文件路径时自动转上传） |
| `aiInput` | AI 输入文本 |
| `aiAssert` | AI 断言（视觉理解页面内容） |
| `aiWaitFor` | AI 等待条件满足 |
| `aiWaitRefresh` | 周期刷新页面并等待条件（适合异步状态流转） |
| `upload` | 文件上传（AI 定位按钮 + 捕获文件框 + 注入本地文件） |
| `aiScroll` | 页面滚动 |
| `sleep` | 固定等待 |

步骤创建时可以只写自然语言，由 AI 自动转换；也可以直接写上述 Midscene 格式，跳过 AI 转换直接执行。

## 项目结构

```
yx-test-AiTest
├── src/main/java/com/yx/test
│   ├── common/            # 统一返回体、业务异常
│   ├── config/            # 鉴权拦截器、跨域、Jackson、Runner 生命周期等
│   ├── controller/        # 接口层（用例/套件/执行/设置）
│   ├── dto/               # 请求响应对象
│   ├── entity/            # 数据实体
│   ├── mapper/            # MyBatis-Plus Mapper
│   ├── service/           # 业务服务
│   └── util/              # JWT、AI 客户端、OSS 工具、租户上下文等
├── src/main/resources
│   ├── application.yml    # 全局配置
│   └── sql/init.sql       # 建表脚本（启动自动执行，含字段升级兼容）
├── midscene-runner        # Node.js 浏览器执行引擎
│   ├── index.js           # HTTP 服务入口（/execute、/status）
│   └── src/
│       ├── executor.js    # 任务执行编排主流程
│       ├── task-queue.js  # 任务队列与并发闸门
│       ├── steps/         # 步骤执行器（11 种 action）
│       ├── login-state.js # 登录态解析与注入
│       ├── artifacts.js   # 报告/截图落盘
│       └── callback.js    # 结果回调后端
└── pom.xml
```

## 安全设计

- **不引入 Spring Security Starter**：项目使用自定义 `AuthInterceptor` + `TenantContext` 鉴权体系，仅引入 `spring-security-crypto` 用于密码加密，避免安全过滤器链冲掉现有鉴权逻辑；
- **密钥零硬编码**：数据库口令、JWT 密钥、OSS 凭据全部通过环境变量注入，仓库内不含任何真实密钥；
- **AI Key 用户级隔离**：Key 存库按用户隔离，接口层与执行引擎双重校验，无 Key 直接拒绝执行，绝不回退全局 Key；
- **越权防护**：所有业务查询均经过数据归属校验，修改他人数据直接拒绝；
- **接口文档默认关闭**：Swagger/OpenAPI 默认关闭，避免接口清单暴露在公网。

## 接口概览

| 模块 | 前缀 | 说明 |
| --- | --- | --- |
| 认证 | `/api/auth`、`/api/register` | 登录、注册 |
| 测试用例 | `/api/cases`、`/api/case/categories` | 用例与分类管理 |
| AI 生成 | `/api/ai-generation` | 自然语言生成用例、场景批量生成 |
| 回归套件 | `/api/suites` | 套件管理、套件内用例编辑、套件执行 |
| 执行记录 | `/api/executions` | 单条/批量/套件执行、回调、报告、AI 诊断 |
| 报告产物 | `/api/reports`、`/api/screenshots` | 报告与截图访问 |
| 用户设置 | `/api/user/ai-config` | 用户自己的 AI 模型配置 |
| 系统管理 | `/api/users`、`/api/roles`、`/api/tenants` | 用户、角色、租户管理 |
| 文件存储 | `/api/oss` | OSS 上传/下载（可选） |

Runner 侧接口：`POST /execute`（接收任务）、`GET /status`（运行状态与队列观测）。

## 作者

**liangzixuan** —— 独立设计并实现（2026）
