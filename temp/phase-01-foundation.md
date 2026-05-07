# Phase 01 Foundation 验收记录

## 阶段目标

完成 Spring Boot 多模块基础工程搭建，接入 MySQL、Redis、Spring Security、WebSocket、Spring AI 版本管理、腾讯云 COS SDK、测试框架、统一异常、日志和配置分层，并验证项目可编译、应用上下文可启动、test profile 可加载、基础健康检查可访问。

## 修改文件

- `pom.xml`
  - 创建 Maven 父工程 `ppt-agent-platform`，声明 7 个子模块。
  - 使用 Spring Boot `3.5.6`，Java `17`。
  - 接入 Spring AI BOM `1.1.4`、腾讯云 COS SDK `5.6.269`、JJWT 版本管理。
  - 配置 Spring Boot Maven Plugin 管理。

- `.gitignore`
  - 忽略 `target/`、IDE 文件、日志文件和项目内 Maven 本地仓库 `.m2/repository/`。

- `.mvn/settings.xml`
  - 将 Maven 本地仓库指向 `G:\coding\PPT_agent\.m2\repository`，避免写入全局仓库时受沙箱权限限制。

- `.mvn/maven.config`
  - 默认使用项目内 `.mvn/settings.xml`。

- `common/pom.xml`
  - 创建公共模块，接入 Bean Validation。

- `common/src/main/java/com/pptagent/common/api/ApiResponse.java`
  - 定义统一 API 响应结构。

- `common/src/main/java/com/pptagent/common/error/ErrorCode.java`
  - 定义基础错误码。

- `common/src/main/java/com/pptagent/common/error/PlatformException.java`
  - 定义平台统一业务异常。

- `common/src/main/java/com/pptagent/common/module/ModuleNames.java`
  - 定义当前基础阶段启用的模块清单，用于健康检查。

- `domain/pom.xml`
  - 创建领域模块，依赖 `common` 并接入 Spring Data JPA。

- `domain/src/main/java/com/pptagent/domain/DomainModule.java`
  - 添加领域模块标记类。

- `infrastructure/pom.xml`
  - 创建基础设施模块，依赖 `domain`，接入 Redis 和腾讯云 COS SDK。

- `infrastructure/src/main/java/com/pptagent/infrastructure/InfrastructureModule.java`
  - 添加基础设施模块标记类。

- `infrastructure/src/main/java/com/pptagent/infrastructure/config/CosStorageProperties.java`
  - 定义 COS 配置属性，支持后续真实 COS Client 和 Mock Client 切换。

- `infrastructure/src/main/java/com/pptagent/infrastructure/config/RedisPlatformProperties.java`
  - 定义 Redis 命名空间、协同广播 Topic、RAG 索引名配置。

- `agent-core/pom.xml`
  - 创建 Agent 核心模块，依赖 `infrastructure`。

- `agent-core/src/main/java/com/pptagent/agent/AgentCoreModule.java`
  - 添加 Agent 模块标记类。

- `agent-core/src/main/java/com/pptagent/agent/config/AgentProperties.java`
  - 定义 Agent 模型、最大页数、Mock LLM、MCP 工具开关配置。

- `ppt-renderer/pom.xml`
  - 创建 PPT 渲染模块，依赖 `domain`。

- `ppt-renderer/src/main/java/com/pptagent/renderer/PptRendererModule.java`
  - 添加 PPT 渲染模块标记类。

- `collab-server/pom.xml`
  - 创建协同服务模块，依赖 `infrastructure` 并接入 WebSocket。

- `collab-server/src/main/java/com/pptagent/collab/CollabServerModule.java`
  - 添加协同模块标记类。

- `collab-server/src/main/java/com/pptagent/collab/config/CollaborationProperties.java`
  - 定义 WebSocket 入口、快照间隔、Yjs 持久化开关配置。

- `api-server/pom.xml`
  - 创建启动模块，聚合 Agent、渲染、协同模块。
  - 接入 Web、Actuator、Security、MySQL Driver、H2 Test、Spring Boot Test、Spring Security Test。

- `api-server/src/main/java/com/pptagent/api/ApiServerApplication.java`
  - 创建 Spring Boot 启动类。
  - 开启跨模块包扫描和配置属性扫描。

- `api-server/src/main/java/com/pptagent/api/config/FoundationSecurityConfig.java`
  - 建立第一阶段安全默认策略。
  - 允许 `GET /api/health` 和 `GET /actuator/health` 公开访问，其余接口默认需要认证，后续 Phase 02 接入 JWT。

- `api-server/src/main/java/com/pptagent/api/config/GlobalExceptionHandler.java`
  - 增加全局异常处理。
  - 将业务异常和参数校验异常转为统一 `ApiResponse`。

- `api-server/src/main/java/com/pptagent/api/health/FoundationHealthController.java`
  - 增加 `GET /api/health` 基础健康检查。
  - 返回状态、active profiles 和模块清单。

- `api-server/src/main/resources/application.yml`
  - 增加默认运行配置。
  - 配置 MySQL、Redis、Actuator、COS、协同、Agent 配置入口。
  - 外部密钥全部通过环境变量注入，默认不要求真实 API Key。

- `api-server/src/test/resources/application-test.yml`
  - 增加 test profile。
  - 使用 H2 内存数据库代替 MySQL。
  - COS 和 LLM 默认启用 Mock 配置。

- `api-server/src/test/java/com/pptagent/api/ApiServerApplicationTests.java`
  - 验证 Spring 上下文可启动。
  - 验证 COS、Agent、Collaboration 配置可绑定。
  - 验证 `/api/health` 可公开访问并返回基础模块清单。

## 核心逻辑

- 工程采用 Maven 多模块结构，`api-server` 是唯一启动模块，其余模块按职责拆分为公共能力、领域、基础设施、Agent、PPT 渲染和协同服务。
- 第一阶段只完成可扩展地基，不实现具体用户、文件、PPT、协同、Agent 业务逻辑，避免提前跨阶段开发。
- Spring AI 在本阶段通过 BOM 锁定依赖版本和 Agent 配置入口接入，不启用真实 LLM 自动配置，避免缺少 API Key 时影响测试。
- 腾讯云 COS SDK 已进入 `infrastructure` 模块，COS 密钥、Bucket、Region 均通过配置属性管理；真实 Client 和 Mock Client 留到 Phase 03 实现。
- 安全策略先提供最小默认值：健康检查公开，其他接口保护；JWT 认证链路留到 Phase 02。

## 测试命令

```powershell
mvn test
```

## 测试结果

- 结果：通过。
- Reactor Summary：
  - `ppt-agent-platform` SUCCESS
  - `common` SUCCESS
  - `domain` SUCCESS
  - `infrastructure` SUCCESS
  - `agent-core` SUCCESS
  - `ppt-renderer` SUCCESS
  - `collab-server` SUCCESS
  - `api-server` SUCCESS
- 测试数量：`Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`
- 验证内容：
  - 多模块编译成功。
  - Spring Boot 测试上下文启动成功。
  - `test` profile 加载成功。
  - COS、Agent、Collaboration 配置绑定成功。
  - `GET /api/health` 无需认证即可访问，并返回 `UP` 和 7 个基础模块。

## 外部依赖 Mock 情况

- LLM：本阶段未调用真实 LLM，仅配置 `ppt.agent.mock-llm-enabled=true`。
- COS：本阶段未调用真实 COS，仅配置 `ppt.cos.mock-enabled=true` 并验证配置绑定。
- MySQL：测试环境使用 H2 内存数据库，不依赖真实 MySQL。
- Redis：本阶段未执行 Redis 读写，仅接入依赖和配置入口。
- MCP/Skill：本阶段仅保留 `ppt.agent.mcp-tools-enabled` 配置入口，未调用外部工具。

## 未完成事项

- Phase 02 实现用户、等级、权限与 JWT。
- Phase 03 实现 COS 文件上传、元数据、Mock COS Client 和失败处理。
- Phase 04 实现 PPT Slide JSON 模型和基础编辑接口。
- Phase 05 实现 WebSocket 协同协议和 CRDT/Yjs 持久化。
- Phase 06 以后实现 Agent Plan-Execution、图片 RAG、PPTX 导出和企业级补强。

## 暂停说明

按照计划规则，Phase 01 已完成测试与验收文档创建。此处暂停，不继续执行 Phase 02，等待人工确认。
