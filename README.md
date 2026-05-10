# PPT Agent Platform

PPT Agent Platform 是一个面向智能协作演示文稿创作的全栈项目。项目包含 Spring Boot 多模块后端、React/Vite 前端、WebSocket 协作能力、AI 生成与执行流程、文件上传、图片工具、PPT 导出以及基础权限认证能力。

## 功能概览

- 用户注册、登录、刷新令牌与权限校验
- 演示文稿、幻灯片、元素、版本和成员管理
- 在线编辑器与实时协作 WebSocket 通道
- Agent 任务创建、计划生成、计划审批、执行、暂停、恢复和发布
- Agent 进度 SSE 推送与任务记忆管理
- 文件上传、预签名上传、对象存储适配
- 图片候选搜索、图片评估与图片资产管理
- PPT 渲染与导出
- Flyway 数据库迁移和集成测试

## 技术栈

### 后端

- Java 17
- Spring Boot 3.5.6
- Spring Security
- Spring Web / WebSocket / Actuator
- Spring AI 1.1.4
- MyBatis-Plus 3.5.16
- Flyway
- MySQL
- Redis
- Apache POI
- Apache PDFBox
- Tencent COS SDK
- JJWT

### 前端

- React 18
- TypeScript
- Vite 6
- React Router
- Yjs
- fetch-event-source
- lucide-react

## 项目结构

```text
PPT_agent/
├── agent-core/                 # Agent 核心能力：AI 客户端、计划解析、任务执行、工具调用、记忆与图片生成
│   └── src/main/java/com/pptagent/agent/
├── api-server/                 # Spring Boot 启动模块：REST API、安全认证、SSE、业务编排、Flyway 迁移
│   ├── src/main/java/com/pptagent/api/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── db/migration/
│   └── src/test/
├── collab-server/              # 实时协作模块：WebSocket、会话注册、广播事件、软锁和快照持久化
│   └── src/main/java/com/pptagent/collab/
├── common/                     # 通用 API 响应、错误码、异常和模块常量
│   └── src/main/java/com/pptagent/common/
├── domain/                     # 领域模型与仓储接口：认证、文件、演示文稿、协作、Agent、图片
│   └── src/main/java/com/pptagent/domain/
├── infrastructure/             # 基础设施适配：MyBatis-Plus、Redis、对象存储、Tencent COS / Mock COS
│   └── src/main/java/com/pptagent/infrastructure/
├── ppt-renderer/               # PPT 渲染与导出模块
│   └── src/main/java/com/pptagent/renderer/
├── frontend/                   # React + Vite 前端应用
│   ├── src/
│   │   ├── components/
│   │   ├── lib/
│   │   ├── pages/
│   │   ├── App.tsx
│   │   └── main.tsx
│   ├── scripts/
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   └── vite.config.ts
├── SQL/                        # 数据库初始化脚本
│   └── init_ppt_agent_database.sql
├── .mvn/                       # Maven 本地配置
├── pom.xml                     # Maven 父工程与模块聚合配置
└── README.md
```

## 模块说明

| 模块 | 说明 |
| --- | --- |
| `api-server` | 应用入口，聚合各业务模块，提供认证、演示文稿、文件、图片、Agent、健康检查等 REST API。 |
| `agent-core` | 负责 Agent 对话、计划解析、任务执行、工具 Schema 校验、MCP 工具注册、记忆压缩和图片生成调用。 |
| `collab-server` | 提供实时协作 WebSocket、用户会话、协作事件广播、快照持久化和软锁服务。 |
| `ppt-renderer` | 基于领域对象生成 PPT 渲染结果，负责图片解析和 PPT 文件导出。 |
| `domain` | 定义领域实体、枚举与 Repository 接口，包括用户、权限、项目、文件、图片、演示文稿和 Agent 任务。 |
| `infrastructure` | 提供数据库、Redis、对象存储等基础设施配置与实现。 |
| `common` | 放置跨模块共享的 API 响应结构、错误码、异常和模块名称。 |
| `frontend` | Web 客户端，包含登录注册、仪表盘、编辑器和 Agent 页面。 |

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 18+
- npm 9+
- MySQL 8+
- Redis 6+

> 项目默认开启 Mock LLM 与 Mock 对象存储，便于本地开发。接入真实模型和 Tencent COS 时需要配置对应环境变量。

## 数据库初始化

先创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS ppt_agent
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

也可以直接执行：

```bash
mysql -u root -p < SQL/init_ppt_agent_database.sql
```

业务表结构由 `api-server/src/main/resources/db/migration/` 下的 Flyway 脚本自动迁移。

## 后端启动

在项目根目录执行：

```bash
mvn clean install
mvn -pl api-server -am spring-boot:run
```

默认服务地址：

- API: `http://localhost:8080`
- 健康检查: `http://localhost:8080/api/health`
- WebSocket: `ws://localhost:8080/ws/presentations/{presentationId}`
- Agent SSE: `GET /api/agent/jobs/{id}/events`

## 前端启动

进入前端目录：

```bash
cd frontend
npm install
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

Vite 开发服务器会将：

- `/api` 代理到 `http://localhost:8080`
- `/ws` 代理到 `ws://localhost:8080`

## 常用配置

后端配置位于 `api-server/src/main/resources/application.yml`，可通过环境变量覆盖。

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `PPT_MYSQL_URL` | `jdbc:mysql://localhost:3306/ppt_agent?...` | MySQL 连接地址 |
| `PPT_MYSQL_USERNAME` | `root` | MySQL 用户名 |
| `PPT_MYSQL_PASSWORD` | 空 | MySQL 密码 |
| `PPT_REDIS_HOST` | `localhost` | Redis 主机 |
| `PPT_REDIS_PORT` | `6379` | Redis 端口 |
| `PPT_JWT_SECRET` | `change-me-change-me-change-me-change-me` | JWT 签名密钥，生产环境必须修改 |
| `PPT_AGENT_MOCK_LLM_ENABLED` | `true` | 是否启用 Mock LLM |
| `PPT_AGENT_MOCK_BAILIAN_ENABLED` | `true` | 是否启用 Mock 图片生成 |
| `PPT_AGENT_BAILIAN_API_KEY` / `DASHSCOPE_API_KEY` | 空 | 阿里云 DashScope / 百炼 API Key |
| `PPT_COS_MOCK_ENABLED` | `true` | 是否启用 Mock 对象存储 |
| `TENCENT_COS_REGION` | 空 | Tencent COS 地域 |
| `TENCENT_COS_BUCKET` | 空 | Tencent COS 存储桶 |
| `TENCENT_COS_SECRET_ID` | 空 | Tencent COS SecretId |
| `TENCENT_COS_SECRET_KEY` | 空 | Tencent COS SecretKey |
| `TENCENT_COS_PUBLIC_BASE_URL` | 空 | COS 公网访问基础地址 |

本地私有配置可以写入：

```text
api-server/src/main/resources/application-local.yml
```

该文件已在 `.gitignore` 中忽略，适合存放本地密码和密钥。

## 前端环境变量

`frontend/.env.example`：

```env
VITE_API_BASE=/api
VITE_WS_BASE=/ws
```

本地如需覆盖，可创建 `frontend/.env.local`。

## 构建与测试

后端：

```bash
mvn test
mvn clean package
```

前端：

```bash
cd frontend
npm run build
npm run preview
```

生产部署前端时，可使用 `frontend/Dockerfile` 构建静态资源镜像，`frontend/nginx.conf` 已配置 SPA fallback、API 代理和 WebSocket 代理。

## API 分组

| 路径 | 说明 |
| --- | --- |
| `/api/auth` | 注册、登录、刷新令牌、退出登录、当前用户信息 |
| `/api/presentations` | 演示文稿创建、查询、删除、预览、成员、版本、导出 |
| `/api/slides` / `/api/elements` | 幻灯片和元素管理 |
| `/api/files` | 文件上传令牌、上传完成、直接上传、文件查询 |
| `/api/images` | 图片候选、评估和资产管理 |
| `/api/agent` | Agent 任务、计划、执行、进度、记忆和工具调用 |
| `/api/health` | 基础健康检查 |
| `/ws/presentations/{id}` | 演示文稿实时协作通道 |

## 开发建议

- 后端新增表结构时，优先在 `api-server/src/main/resources/db/migration/` 添加 Flyway 迁移脚本。
- 领域实体、枚举和 Repository 放在 `domain`，基础设施实现放在 `infrastructure`。
- API 层只做接口、安全校验和业务编排，复杂 Agent 逻辑放在 `agent-core`。
- 前端 API 请求封装位于 `frontend/src/lib/api.ts`，协作逻辑位于 `frontend/src/lib/collaboration.ts`。
- 本地开发默认可使用 Mock LLM、Mock 图片生成和 Mock 对象存储，避免必须依赖外部云服务。

## Git 提交前检查

建议提交前执行：

```bash
mvn test
cd frontend
npm run build
```

同时确认不要提交以下本地文件：

- `api-server/src/main/resources/application-local.yml`
- `.m2/repository/`
- `frontend/node_modules/`
- `frontend/dist/`
- `temp/`
- `*.log`

