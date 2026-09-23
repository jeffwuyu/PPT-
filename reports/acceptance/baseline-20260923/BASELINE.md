# PPT Agent 基线记录

- GitHub 基准提交：`93577d20af800cef5e2d335c6445f2a8feb186ea`
- 当前实现提交：`01899dd`（P01 解析任务、Markdown/PDF/DOCX/TXT block 分类与结构化定位、P04 逐页异步执行/租约/控制排空、P05 游标过期信号与数据库轮询补发；基线提交仍为 `93577d20af800cef5e2d335c6445f2a8feb186ea`）
- 当前工作区改动数：9（主要为文档、评测入口和本次实现）
- Java：21
- Spring AI：1.1.4
- 数据库：MySQL 生产配置，H2 测试配置
- provider：测试默认使用 mock LLM、mock 图片和 mock COS

已执行验证：

- `mvn -q -DskipTests compile`：通过
- `mvn -pl api-server -am "-Dtest=AgentIntegrationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`：17/17 通过
- `mvn -q -DskipTests package`：通过

本记录只证明构建和确定性测试结果，不证明 P01–P06 或简历指标已经整体验收。P04 的单个异步用例证据见 `reports/acceptance/run-20260923-p04/P04/P04-ASYNC-01/`。

P01 的格式覆盖证据见 `reports/acceptance/run-20260923-p01/P01/P01-PARSE-01/`（Markdown）和
`reports/acceptance/run-20260923-p01/P01/P01-PARSE-02/`（PDF、DOCX、TXT，以及上一轮集成测试的原始日志）。
