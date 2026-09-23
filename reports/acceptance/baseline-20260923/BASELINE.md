# PPT Agent 基线记录

- GitHub 基准提交：`93577d20af800cef5e2d335c6445f2a8feb186ea`
- 当前实现提交：`93577d20af800cef5e2d335c6445f2a8feb186ea`
- 当前工作区改动数：9（主要为文档、评测入口和本次实现）
- Java：21
- Spring AI：1.1.4
- 数据库：MySQL 生产配置，H2 测试配置
- provider：测试默认使用 mock LLM、mock 图片和 mock COS

已执行验证：

- `mvn -q -DskipTests compile`：通过
- `mvn -q -pl api-server -am -Dtest=AgentIntegrationTests test`：8/8 通过
- `mvn -q -DskipTests package`：通过

本记录只证明构建和既有测试结果，不证明 P01–P06 或简历指标已经验收。
