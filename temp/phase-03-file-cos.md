# Phase 03 File COS 验收记录

## 阶段目标

完成文件与腾讯云 COS 模块，包括上传签名、服务端上传、文件元数据保存、文件类型校验、大小限制、COS Key 规范、上传失败重试和 Mock COS Client。缺少真实 COS API Key 时，通过 Mock COS 验证输入输出、Key 生成、元数据落库和异常处理。

## 修改文件

- `pom.xml`
  - Maven Compiler Plugin 增加 `fork=true` 和 `executable=javac`。
  - 目的：当前环境 Maven 运行在 Dragonwell 21，但 PATH 上 `javac` 为稳定的 Oracle 17；fork 外部 `javac` 后，普通 `mvn test` 可稳定执行。

- `api-server/src/main/resources/db/migration/V2__file_assets.sql`
  - 新增 `file_assets` 表。
  - 保存 owner、文件类型、原始文件名、content type、大小、COS object key、public URL、状态、上传模式、失败原因和时间戳。
  - 增加 owner、文件类型与状态索引。

- `domain/src/main/java/com/pptagent/domain/file/*`
  - 新增 `FileAssetEntity`、`FileType`、`FileAssetStatus`、`FileUploadMode`。
  - `FileType` 内置允许的 content type 和扩展名，用于上传前校验。

- `domain/src/main/java/com/pptagent/domain/file/repository/FileAssetRepository.java`
  - 新增文件元数据 MyBatis-Plus Mapper。
  - 文件归属通过 `owner_user_id` 标量字段判断，支持鉴权判断。

- `infrastructure/src/main/java/com/pptagent/infrastructure/config/CosStorageProperties.java`
  - 增加 `mockFailUpload` 配置，用于测试 COS 上传失败路径。

- `infrastructure/src/main/java/com/pptagent/infrastructure/config/ObjectStorageConfiguration.java`
  - 根据 `ppt.cos.mock-enabled` 创建 Mock COS Client 或真实 Tencent COS Client。
  - 默认启用 Mock，保证无 COS API Key 时测试可运行。

- `infrastructure/src/main/java/com/pptagent/infrastructure/storage/*`
  - 新增 `ObjectStorageClient` 抽象。
  - 新增 `MockObjectStorageClient`，支持生成 mock presigned URL、mock public URL、mock 上传失败。
  - 新增 `TencentCosObjectStorageClient`，封装腾讯云 COS SDK 的 presigned upload 和 putObject。
  - 新增 `PresignedUpload`、`StoredObject`、`ObjectStorageException`。

- `api-server/src/main/resources/application.yml`
  - 增加 `ppt.cos.mock-fail-upload`。
  - 增加 `ppt.files.max-upload-size` 和 `ppt.files.presigned-upload-ttl`。

- `api-server/src/test/resources/application-test.yml`
  - 测试环境使用 Mock COS。
  - 文件上传大小限制设为 `1MB`，方便验证超大文件拒绝。

- `api-server/src/main/java/com/pptagent/api/file/*`
  - 新增 `FileProperties`、`FileService`、`FileController`。
  - 实现 `POST /api/files/upload-token`、`POST /api/files/complete`、`POST /api/files/upload`、`GET /api/files/{id}`。
  - 服务端上传失败时执行 3 次重试，最终失败会把文件状态标记为 `FAILED`。

- `api-server/src/main/java/com/pptagent/api/file/dto/*`
  - 新增上传 token、完成上传、文件元数据响应 DTO。

- `api-server/src/test/java/com/pptagent/api/file/FileIntegrationTests.java`
  - 覆盖上传 token、完成上传、服务端上传、非法类型、超大文件、owner 鉴权、JWT 必需。

- `api-server/src/test/java/com/pptagent/api/file/FileUploadFailureIntegrationTests.java`
  - 使用 `ppt.cos.mock-fail-upload=true` 验证 Mock COS 上传失败时返回 `EXTERNAL_SERVICE_ERROR`，并将文件状态落为 `FAILED`。

## 核心逻辑

- 文件上传分两种模式：
  - `PRESIGNED`：后端先创建文件元数据和 COS object key，返回上传 URL，客户端上传后调用 complete 将文件标记为 `AVAILABLE`。
  - `SERVER`：后端接收 multipart 文件，校验后直接通过 `ObjectStorageClient` 上传到 COS 或 Mock COS。

- COS Key 规范：
  - `users/{userId}/{fileType}/{yyyy}/{MM}/{dd}/{uuid}-{safeFilename}`
  - 该结构便于按用户、类型和日期做生命周期管理与排查。

- 文件类型校验：
  - `SOURCE_TEXT`：`txt`、`md`、`pdf`
  - `USER_IMAGE`、`CRAWLED_IMAGE`、`COVER_IMAGE`、`SLIDE_THUMBNAIL`：`png`、`jpg`、`jpeg`、`webp`
  - `GENERATED_PPTX`：`pptx`
  - 校验同时检查 content type 和扩展名。

- 大小限制：
  - 通过 `ppt.files.max-upload-size` 配置。
  - 测试环境为 `1MB`，默认运行配置为 `20MB`。

- 权限逻辑：
  - 所有 `/api/files/**` 接口都需要 JWT。
  - 文件只能由 owner 查询或完成上传，其他用户访问返回 403。

- COS Mock 策略：
  - 默认 `ppt.cos.mock-enabled=true`，无需真实 COS Key。
  - Mock 上传成功会返回稳定格式的 public URL。
  - Mock 上传失败可通过 `ppt.cos.mock-fail-upload=true` 打开。

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
- 测试数量：`Tests run: 13, Failures: 0, Errors: 0, Skipped: 0`
- 覆盖场景：
  - Phase 01/02 原有上下文、认证、权限测试继续通过。
  - Flyway 成功应用 2 个 migration，MyBatis-Plus Mapper 读写通过。
  - 合法文件可创建 presigned upload token。
  - presigned 上传完成后状态变为 `AVAILABLE`。
  - 服务端上传可保存元数据并返回 Mock COS public URL。
  - 非法 content type/扩展名组合被拒绝。
  - 超过大小限制的文件被拒绝。
  - 未登录访问文件接口返回 401。
  - 非 owner 访问文件元数据返回 403。
  - Mock COS 上传失败返回 500，并将文件状态标记为 `FAILED`。

## 外部依赖 Mock 情况

- COS：默认使用 `MockObjectStorageClient`，未调用真实腾讯云 COS。
- COS API Key：本阶段不需要真实 `secretId`、`secretKey`、bucket 或 region。
- MySQL：生产配置指向 MySQL；测试使用 H2 MySQL mode 通过 Flyway 和 MyBatis-Plus 读写验证表结构。
- Redis：本阶段未执行 Redis 读写。
- LLM/MCP/Skill：本阶段未调用。

## 未完成事项

- Phase 04 将引入 PPT JSON 模型，并开始引用 `file_assets` 中的图片和生成文件元数据。
- 当前真实 COS Client 已具备基础 presigned URL 和 putObject 能力，但尚未做真实云端集成测试；需要真实 COS Key 后单独执行环境验收。
- 当前文件安全只做类型、扩展名和大小校验；病毒扫描、图片内容安全、水印/版权检测留到 Phase 10 或 Agent 图片阶段增强。
- 当前 presigned complete 不校验 COS 对象是否真实存在；真实 COS 环境下可在 Phase 03 后续增强为 headObject 校验。

## 暂停说明

按照计划规则，Phase 03 已完成测试与验收文档创建。此处暂停，不继续执行 Phase 04，等待人工确认。
