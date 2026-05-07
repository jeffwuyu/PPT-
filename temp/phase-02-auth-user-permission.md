# Phase 02 Auth User Permission 验收记录

## 阶段目标

完成用户、用户等级、团队、项目成员、角色权限、刷新令牌、额度消耗、审计日志表设计；实现注册、登录、刷新 Token、退出登录、用户等级校验、项目权限判断和 JWT 接口鉴权。

## 修改文件

- `pom.xml`
  - 增加 Maven Compiler Plugin 配置，并在 `.mvn/maven.config` 中关闭增量编译，规避 Windows/Javac 偶发 `无法关闭编译器资源` 问题。

- `.mvn/maven.config`
  - 增加 `-Dmaven.compiler.useIncrementalCompilation=false`，保证后续直接执行 `mvn test` 稳定。

- `api-server/pom.xml`
  - 增加 Flyway、Flyway MySQL、JJWT API/Impl/Jackson 依赖。

- `api-server/src/main/resources/application.yml`
  - 增加 Flyway 配置。
  - 增加 `ppt.jwt` 配置，包括 issuer、secret、access token TTL、refresh token TTL。

- `api-server/src/test/resources/application-test.yml`
  - 测试环境启用 Flyway 与 JPA validate。
  - 配置测试 JWT secret，access token TTL 设置为 `1s` 以验证过期拒绝。

- `api-server/src/main/resources/db/migration/V1__auth_user_permission.sql`
  - 创建 `user_levels`、`users`、`roles`、`permissions`、`role_permissions`、`user_roles`、`teams`、`project_members`、`refresh_tokens`、`quota_consumptions`、`audit_logs`。
  - 初始化 `FREE`、`PRO`、`ENTERPRISE` 用户等级。
  - 初始化 `ROLE_USER`、`ROLE_EDITOR`、`ROLE_ADMIN` 角色和 `PROJECT_VIEW`、`PROJECT_EDIT`、`PROJECT_ADMIN`、`AGENT_RUN` 权限。

- `domain/src/main/java/com/pptagent/domain/auth/*`
  - 增加用户、等级、角色、权限、团队、项目成员、刷新令牌、额度消耗、审计日志实体。
  - 增加 `UserStatus`、`TokenStatus`、`ProjectRole` 枚举。

- `domain/src/main/java/com/pptagent/domain/auth/repository/*`
  - 增加用户、等级、角色、项目成员、刷新令牌、额度消耗、审计日志 Repository。
  - 用户和角色查询使用 `EntityGraph` 加载等级、角色和权限，避免权限判断时懒加载丢上下文。

- `api-server/src/main/java/com/pptagent/api/ApiServerApplication.java`
  - 显式增加 `@EntityScan` 和 `@EnableJpaRepositories`，支持多模块 JPA 实体与 Repository 扫描。

- `api-server/src/main/java/com/pptagent/api/config/FoundationSecurityConfig.java`
  - 接入 JWT Filter。
  - 放行健康检查、注册、登录、刷新接口。
  - 其他接口默认需要 Bearer Token。
  - 提供 BCrypt `PasswordEncoder`。

- `api-server/src/main/java/com/pptagent/api/config/GlobalExceptionHandler.java`
  - 根据 `ErrorCode` 映射 HTTP 状态码，例如 `UNAUTHORIZED -> 401`、`CONFLICT -> 409`。

- `api-server/src/main/java/com/pptagent/api/security/*`
  - 增加 `JwtAuthenticationFilter`，从 `Authorization: Bearer` 解析 JWT 并设置 Spring Security 上下文。
  - 增加 `PlatformUserDetailsService`，从 MySQL 用户表加载用户、角色和权限。
  - 增加 `AuthenticatedUser` 作为 Security Principal。

- `api-server/src/main/java/com/pptagent/api/auth/*`
  - 增加 `JwtProperties`、`JwtService`、`AuthService`、`PermissionPolicyService`、`AuthController`。
  - 注册时默认分配 `FREE` 等级和 `ROLE_USER`。
  - 登录时校验 BCrypt 密码。
  - Refresh Token 使用随机明文返回给客户端，服务端仅保存 SHA-256 Hash。
  - 刷新 Token 时执行 rotation，旧 refresh token 立即失效。
  - 登出时可撤销传入的 refresh token 并记录审计日志。
  - 权限策略支持等级比较、全局权限判断、项目编辑权限判断。

- `api-server/src/main/java/com/pptagent/api/auth/dto/*`
  - 增加注册、登录、刷新、退出、认证响应、用户信息响应 DTO。

- `api-server/src/test/java/com/pptagent/api/auth/AuthIntegrationTests.java`
  - 增加注册登录、错误密码拒绝、refresh token rotation、JWT 过期拒绝、等级/角色/项目权限策略测试。

## 核心逻辑

- 用户信息和等级存储在 MySQL 表结构中，测试环境通过 H2 MySQL mode 验证同一套 Flyway 迁移。
- JWT access token 只用于无状态接口鉴权，包含用户 ID、等级和权限声明；服务端仍以数据库为最终权限来源。
- Refresh token 不以明文落库，只保存 SHA-256 Hash；刷新成功后旧 token 被 revoke，降低泄漏后的复用风险。
- 用户权限由两层组成：全局角色权限和项目成员权限。`ROLE_ADMIN` 可通过 `PROJECT_ADMIN` 管理项目，普通用户需要项目成员记录才能编辑具体项目。
- 用户等级通过 `rank_order` 比较，支持后续会员功能、额度和 Agent 能力限制。
- 审计日志记录注册、登录、刷新、退出动作，后续 Phase 10 可扩展为完整操作审计。

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
- 测试数量：`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`
- 覆盖场景：
  - Spring Boot 上下文启动成功。
  - Flyway 建表并通过 JPA schema validate。
  - 注册成功并返回 access token 和 refresh token。
  - 登录成功。
  - 错误密码返回 401。
  - JWT 过期后访问受保护接口返回 401。
  - Refresh token 刷新成功并执行 rotation，旧 token 再次使用返回 401。
  - 默认用户等级为 `FREE`，升级到 `PRO` 后等级判断生效。
  - 默认 `ROLE_USER` 无 `PROJECT_EDIT`，添加 `ROLE_EDITOR` 后权限判断生效。
  - 项目成员编辑权限判断生效。

## 外部依赖 Mock 情况

- LLM：本阶段未调用。
- COS：本阶段未调用。
- MySQL：生产配置指向 MySQL；测试使用 H2 内存数据库和 Flyway 验证表结构及 JPA 映射。
- Redis：本阶段未执行 Redis 读写。
- JWT：使用本地 JJWT 签发和校验，不依赖外部服务。

## 未完成事项

- Phase 03 实现文件与腾讯云 COS 模块，包括上传签名、服务端上传、文件元数据、类型校验、大小限制和 Mock COS Client。
- 当前用户注册未加入邮箱验证码、找回密码、MFA、账号锁定策略；这些更适合 Phase 10 企业级补强。
- 当前权限模型已支持项目编辑判断，但真实 PPT 项目表将在 Phase 04 引入后再与项目资源绑定。

## 暂停说明

按照计划规则，Phase 02 已完成测试与验收文档创建。此处暂停，不继续执行 Phase 03，等待人工确认。
