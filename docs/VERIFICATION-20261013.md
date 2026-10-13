# 展示整理验证记录 · 2026-10-13

## 版本与范围

- 基线提交：`2c8040356af1dd9d9b0677513870d452b66e960b`。
- 修改分支：`codex/showcase-cleanup-20261013`，从该基线继续修改；当前审阅提交及构建文件 SHA-256 记录在本地交付清单中。
- 范围：自写说明清理、错误注释、启售术语、微信登录 code 日志移除、演示图片 V15 迁移、README 与真实截图；续修包含堂食完成规则与 V16 数据库注释迁移。
- 首轮没有修改核心流转；续修只调整订单的派送/完成准入与完成前状态，不修改支付、秒杀、消息补偿、状态值或接口。依赖及 V1—V14 迁移内容保持不变。

## 续修结果（最终审阅代码）

**代码与单元验证完成，集成运行仍受 Docker 故障阻塞，不能作为已全部验收的发布版本。**

- 堂食状态 3 可完成；兼容旧接口曾产生的状态 4 堂食单。外送仍须状态 4 才能完成。新的堂食单不能调用派送接口进入状态 4。
- 完成操作继续使用数据库条件更新；并发取消已赢得状态时不能覆盖它。拒绝未支付、待接单、已取消、重复完成及未知履约方式。
- 新增 V16 只修正 `orders.status`、`pay_status`、`pay_method` 的注释，保留类型、默认值与非空约束；未修改旧迁移。新增定义/数据不变的 MySQL 测试，但本次尚未实际执行。
- 最终 `verify`：OrderFulfillmentTest 29 项、OrderServiceImplTest 20 项、OrderTaskTest 1 项、SensitiveDataExposureTest 3 项，共 **53 项通过，0 失败、0 跳过**。本机 JDK 19.0.2；相关集成测试源码已编译，但不在这 53 项里。
- Maven reactor 和可执行 JAR 打包成功；已逐字节核对 JAR 中的最终订单类、全部托管静态文件、V15/V16 资源与当前构建文件一致；JAR 的 SHA-256 已记入本地构建清单。
- Docker 正常停止/重启无响应；结束报错的 Desktop 进程并重新启动后仍报本地 `dockerInference` 通信对象不可访问。没有删除此对象、重置 Docker 或删除数据卷。
- 因此未执行 V16 新库/升级验证、真实 MySQL 履约集成用例、最终镜像构建及页面复验，未补拍经营概览。旧截图保持真实且明确标为首轮版本。

本次通过的命令（backend 目录）：

```powershell
./mvnw.cmd -B '-Dtest=OrderFulfillmentTest,OrderServiceImplTest,OrderTaskTest,SensitiveDataExposureTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

Docker 恢复后仍须先在隔离环境执行：

```powershell
./mvnw.cmd -B '-Dtest=DemoImageMigrationTest,DineHubInfrastructureIntegrationTest#flywayBuildsTheCompleteSchemaFromEmptyMySql+fulfillmentCompletionPersistsAndRejectsRepeatedOrPrematureCompletion+autoCompletionAndCancellationCannotBothWin' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

随后从该审阅提交构建新的专用镜像，核对运行容器的镜像 ID 与本次构建结果一致，再验证领券、堂食及外送流程。恢复过程中不得复用原演示数据库或数据卷。

## 首轮通过项（V15，续修之前）

| 检查 | 实际结果 |
| --- | --- |
| 前端已有测试 | 3 项通过，无失败 |
| 前端构建 | Vite 8.1.4 构建成功，输出同步到后端 static/h5；本机 Node.js 24.14.0 |
| 后端定向验证 | Maven reactor verify 成功；DemoImageMigrationTest 2 项、SensitiveDataExposureTest 3 项，共 5 项通过，0 失败、0 跳过；本机 JDK 19.0.2 |
| 新库迁移 | Testcontainers MySQL 8.0 执行 V1—V15；24 道菜，其中 22 道使用类别占位图，2 道保留匹配照片 |
| 升级保护 | V14→V15 保留自定义上传图片、改名菜品、改分类菜品及额外新增菜品 |
| 镜像构建与运行 | Dockerfile 的 Java 17 构建成功，专用环境健康检查 UP，启动时完成 V15 |
| 实际页面 | 管理员登录、营业切换、用户演示登录、桌台绑定、菜单及饮品图片、限时领券到账、选口味、优惠券结算、下单、模拟支付、商家接单成功 |
| 真实订单金额 | 菜品 58 元，优惠 10 元，应付 48 元；用户页与商家详情一致，支付状态已支付 |
| 文档与资源检查 | 19 个本地链接及锚点检查通过；无已删除说明的残留引用；git diff --check 通过；连续两次前端构建的全部文件 SHA-256 一致，源素材与托管素材逐字节一致 |
| 原版保护 | 原目录 337 个记录文件 SHA-256 与修改前一致；原 HEAD 和原未跟踪审计文件保持不变 |

后端执行命令（在 backend 目录）：

```powershell
./mvnw.cmd -B '-Dtest=DemoImageMigrationTest,SensitiveDataExposureTest' '-Dsurefire.failIfNoSpecifiedTests=false' verify
```

前端执行 `npm ci --no-audit --no-fund`、`npm run test`、`npm run build`。本轮不将旧的全量 Surefire 记录或历史压测计入通过项。

## 失败与未完成

**首轮发现：堂食订单无法从制作中直接完成。** 本轮订单已支付并由商家接单，状态为 3；点击“完成”得到“订单状态错误”。`frontend/h5/src/ops/Ops.vue` 的 `orderActions` 给堂食状态 3 提供完成按钮，而 `OrderServiceImpl.complete` 仅接受状态 4，数据库条件更新也要求状态 4。此冲突存在于基线；后续代码已修复，见上方续修结果。没有直接改数据库来伪造完成状态。

截图采集中 Docker Desktop 停止，随后启动报错涉及本地 `dockerInference` 监听文件，服务无法恢复。没有重置 Docker、删除数据卷或修改系统配置。商家经营概览截图因此未交付，未用生成图或静态伪造页面补齐。

最终收尾统一了前端源码换行，消除 Windows 构建与 Linux CI 的产物差异，并重新生成静态资源。因 Docker 中断，最后这次换行规范化后的镜像未重新构建和启动；此前运行截图与最终版本的页面文案和逻辑相同，产物哈希不同。前端最终构建已成功。

## 截图来源

截图均由 Playwright 从实际后端页面采集，未经拼接或内容修改。用户端视口 430×932，商家端 1440×1000。数据来自全新隔离库中的演示账号、虚构桌台和测试优惠券；截图不包含登录密码、令牌、桌台二维码、私人地址或配置页面。

- [用户菜单](screenshots/customer-menu.png)：匹配照片与明确的类别占位图。
- [限时领券](screenshots/customer-coupons.png)：真实领取后的“已领取”状态。
- [商家订单详情](screenshots/merchant-orders.png)：已支付、制作中的真实测试订单，不能解释为已完成。
- [饮品图片修正](screenshots/customer-drinks.png)：饮品使用饮品类别示意，避免菜品图片错配。

占位图为 SVG 类别图标，明确标注“类别示意 · 图片待补充”；它们是菜单素材，不是运行截图。

## 未验证范围

- 未重跑全量后端测试、全量端到端测试、压测、失败补偿故障注入和订单超时等待。
- 未验收真实微信支付、支付宝、生产配置、手机实机扫码和外送完整履约。
- 未验证本轮最后时刻的未登录管理员 API 拒绝检查、全站图片 URL 及所有视口溢出；截图中的图片已目视检查。
- 尚未上传 GitHub，未触发远端 CI；仓库可见性未改变。
- 原演示数据库未连接或修改；原数据回滚无须执行。本轮专用库与原库隔离，修改前的新库 SQL 备份仅留在本地审阅材料中。
