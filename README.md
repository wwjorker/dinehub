# DineHub(食汇):本地餐饮一体化经营平台

[![CI](https://github.com/wwjorker/dinehub/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/dinehub/actions/workflows/ci.yml)

DineHub 是一个面向单店/小连锁餐饮商家的一体化经营平台后端,统一支撑**到店扫码点单**与**外卖下单**两种履约方式,并围绕高并发场景完成了限时秒杀、异步削峰、缓存一致性与接口限流的完整工程实践。项目重点不在页面数量,而在订单主链路的正确性与高并发优化的可验证过程——所有性能数字均为本机 JMeter 实测,脚本入库可复现。

## 功能全景

- **商家管理端**:员工、分类、菜品、套餐、订单管理、营业统计、运营报表导出(POI)、来单提醒(WebSocket)。
- **用户端 H5**(`/h5/`,Vue 3 + Vant):扫桌台二维码落地、按设备隔离的演示登录、菜品/套餐与多组口味、购物车、堂食/外送切换、省市区地址与订单内选址、优惠券结算、幂等下单、订单详情与操作、dev 模拟支付、秒杀持券状态和“再来一单”。
- **运营台**(`/h5/ops.html`):营业状态和经营总览、WebSocket 来单、订单详情与完整履约状态机、分类/菜品/套餐、图片上传、结构化口味、桌台二维码查看/复制/换新、优惠券发布与高级对账恢复、员工、报表导出和次级项目说明。
- **用户端(接口)**:菜品浏览(Redis 缓存)、购物车、堂食/外卖下单、服务端计价与优惠券核销、微信支付适配边界。
- **扫码点单**:管理端维护桌台并生成二维码(zxing),顾客扫码后凭桌台令牌下堂食单,与外卖单共用订单主链路(履约方式分支),地址/配送环节自动跳过。
- **限时秒杀优惠券**:Redis+Lua 在一次原子操作中完成库存判定、一人一券、预扣和请求登记;Redis 使用 AOF `everysec` 持久化过程账本;RabbitMQ publisher confirm/return 只推进投递状态,未知结果不误补偿;低频扫描按同一 `requestId` 重发,消费者与补偿者通过 MySQL `seckill_request` 终态行竞争,保证已形成持久化请求事实的准入最终只能“落券”或“退名额”二选一。MySQL 中超时的补偿中记录会被独立扫描恢复,重复消费、重复死信和迟到消息均幂等。DB 行锁方案保留为可切换对照组。宿主机突然掉电时 AOF 最后约 1 秒未刷盘的数据,以及 Redis 数据卷整体丢失且准入尚未进入 RabbitMQ/MySQL 的组合故障,不在当前保证内;商业系统可用数据库 outbox 进一步消除该边界。
- **订单超时取消**:TTL+死信实现延迟消息,消费者做状态条件更新(与支付回调竞态安全);原轮询任务降级为低频兜底扫描,双保险。
- **缓存一致性**:菜品缓存延迟双删,覆盖"删除后读请求回填旧值"的窗口。
- **接口限流**:注解式令牌桶(惰性补充,无后台线程),挂在秒杀入口,支持全局开关。
- **安全与状态机**:用户侧订单/地址全部归属校验(防横向越权);订单金额与打包费全部服务端计算,支付金额取库内订单值,回调实付金额核对不符即拒绝确认;接单/拒单/派送/完成/取消/支付全部为乐观状态迁移(以读取时状态为条件,并发只有一方成功);退款仅在赢得取消迁移后执行,金额取库内值、退款单号独立且固定,每次重试先按退款单号查询,只有微信返回 `SUCCESS` 才标记已退款,受理中或结果未知继续停留“退款中”;迟到支付自动转入退款流程;员工密码 BCrypt 存储(历史 MD5 账户登录时平滑升级);请求级认证上下文自动清理。
- **恢复与对账**:活动中重建 Redis 时使用每券短屏障阻止新准入,重建库存按“DB 剩余库存 − 非终态预留”计算,在途用户并入防重集合;临时快照批量构建后由 Lua 校验锁令牌并原子切换,旧线程即使租约过期也不能覆盖新账本。管理端提供只读库存对账接口,可直接看到 DB/Redis 漂移和三类终态计数。
- **基建与观测**:Flyway 全量 schema 管理、Actuator/Prometheus 指标、HTTP/MQ `requestId` 日志贯穿、多阶段非 root 应用镜像、Docker Compose 全栈启动;GitHub Actions 同时校验前端构建产物和后端测试。

## 性能压测

口径:i7-12700H / 16GB RAM / Windows 11,应用单实例(JVM `-Xmx1g`),MySQL 8.0 / Redis 7 / RabbitMQ 3.12 运行于 Docker Desktop(WSL2);JMeter 5.6.3 与服务端同机发压(发压端抢占 CPU,数字偏保守)。每组 3 轮取中位数,`.jmx` 脚本与汇总工具在 [scripts/jmeter/](scripts/jmeter/)。

### 菜品列表查询:先修 SQL,再谈缓存

100 并发线程持续 30s,查询单分类 63 道菜(库内共 500 道)。最初的直查基线继承了逐菜品查口味的 N+1 问题(1+63 条 SQL/请求)并开着逐请求 SQL 日志;先做最朴素的 SQL 层修复(口味批量 IN 查询、补业务索引、日志降级),再叠加 Redis 缓存:

| 阶段 | QPS | P50 | P99 | 错误率 |
|---|---|---|---|---|
| 原始基线(N+1 + 逐请求日志) | 78 | 1179ms | 1875ms | 0% |
| SQL 修复后直查 DB | 1950 | 47ms | 90ms | 0% |
| SQL 修复 + Redis 缓存 | **5944** | **15ms** | **26ms** | 0% |

约 25 倍提升来自消除 N+1 与索引,再叠加缓存获得约 3 倍——先定位并修复查询本身,才能诚实评估缓存的边际收益;直接在低效基线上贴缓存,会把 SQL 问题的收益错记在缓存头上。

### 秒杀抢券:DB 行锁 vs Redis+Lua+MQ

200 并发线程 × 100 次循环 = 20000 个请求,20000 个独立用户(CSV 逐行分发 JWT);压测期间关闭接口限流以测纯链路。注意口径:Redis 模式的 QPS 是**可靠准入吞吐**(资格判定、请求账本登记、消息发布),券的落库由消费者异步完成。

**吞吐组**(库存 100000,全成功路径):

| 方案 | QPS(准入) | P50 | P99 | 错误率 |
|---|---|---|---|---|
| DB 行锁扣减(当前实现) | 80.8 | 1510ms | 8751ms | 0% |
| Redis+Lua+MQ(早期快路径,无投递账本) | 2126 | 51ms | 127ms | 0.04%* |
| Redis+Lua+MQ(可恢复投递,当前实现) | **1248** | **103ms** | **385ms** | **0%** |

*早期快路径的少量错误来自连接重试触发一人一券拒绝。当前版本于 2026-07-13 重新执行三轮:DB 为 81.2/80.8/77.6 QPS,可靠 Redis 链路为 1031/1248/1552 QPS,表中均取中位数。可靠语义增加请求 Hash/ZSet 写入、发布 attempt CAS 和 confirm 回调,其相对早期快路径的吞吐下降是明确量化的可靠性成本。

**售罄边界组**(库存 5000,20000 个用户争抢——超卖验证只有在真实触达库存边界时才有意义):

| 方案 | 放行 | 拒绝 | DB 最终行数 / 去重用户 / 剩余库存 |
|---|---|---|---|
| DB 行锁扣减 | 恰好 5000 | 15000 | 5000 / 5000 / 0 |
| Redis+Lua+MQ | 恰好 5000 | 15000 | 5000 / 5000 / 0 |

两种方案在售罄边界均无一超卖、无一人多券。当前同版本三轮中位数下,可靠 Redis 准入吞吐约为 DB 行锁的 15.4 倍,P99 从 8751ms 降至 385ms(缩短约 22.7 倍)。早期快路径展示性能上限;当前实现则主动用一部分吞吐换取进程/Broker 重启后可恢复、可查询和可对账的消息语义。

异步化的代价同样量化:当前可靠消费者在本机约 85–90 条/秒(每消息事务内同时写终态、持券记录并扣 DB 库存),2 万条完全到账约 3.8 分钟。C 端先收到 `requestId` 并轮询 SUCCESS/PENDING/FAILED;积压不会被伪装成“已经到账”,失败则进入唯一补偿入口退还名额。

## 技术栈

```text
Java 8 源码兼容 / JDK 17 构建与运行 / Spring Boot 2.7.x
MyBatis + PageHelper / MySQL 8 + Druid / Flyway
Redis(AOF + 缓存 + Lua 原子账本)/ RabbitMQ(confirm/return + 异步落库 + TTL/死信)
WebSocket / JWT 双端认证 / Knife4j / Apache POI / zxing
Actuator + Micrometer + Prometheus / MDC 请求追踪
JUnit 5 + Mockito + Testcontainers(MySQL/Redis/RabbitMQ) / GitHub Actions
```

## 项目结构

```text
dinehub
+-- backend
|   +-- dinehub-common   通用工具、异常、配置属性
|   +-- dinehub-pojo     实体 / DTO / VO
|   +-- dinehub-server   业务模块(controller / service / mapper / mq / ratelimit / cache)
+-- frontend/h5          点单 H5 与运营台(Vue 3 + Vite + Vant)
+-- scripts/jmeter       压测脚本与汇总工具
+-- Dockerfile           多阶段构建、非 root 运行、容器健康检查
+-- docker-compose.yml   应用 / MySQL / Redis / RabbitMQ 完整运行栈
```

## 本地运行

```bash
docker compose up -d --build  # 应用(8080)+MySQL(3307)+Redis+RabbitMQ
```

profile 必须显式指定,没有默认值:本地/演示用 `dev`;生产用 `prod`——prod 的 JWT、数据库、Redis、RabbitMQ 与支付配置均不含可用的敏感默认值,缺少必要环境变量时启动直接失败,而不是悄悄退回开发配置。

首次启动由 Flyway 自动建表并写入非账号类演示数据;`dev` profile 会单独创建演示管理员 admin/123456,`prod` 不创建任何默认管理员。入口:点单 H5 `http://localhost:8080/h5/`、运营台 `http://localhost:8080/h5/ops.html`、接口文档 `http://localhost:8080/doc.html`、健康检查 `/actuator/health`、Prometheus 指标 `/actuator/prometheus`。营业状态缺失时系统按“打烊”处理;先在运营台登录并开启营业。推荐演示动线:商家准备菜品/套餐与桌台 → 顾客扫码或演示登录 → 选口味并加入购物车 → 选择堂食/外送、地址和优惠券 → 幂等下单 → dev 模拟支付 → 商家接单/派送/完成 → 用户订单页查看最终状态;秒杀链路可另行演示发布、抢券、持券状态和库存对账。手机扫码需同一局域网,并用 `DINEHUB_H5_BASE_URL` 将二维码地址指向电脑局域网 IP；弹窗会显示二维码实际内容并在 localhost 配置下提示。dev 图片保存到 Docker `upload-data` 卷，prod 默认关闭本地上传并应接入对象存储。H5 构建产物已入库;改前端后在 `frontend/h5` 内 `npm ci && npm run build` 重新生成,CI 会校验源码与入库产物一致。C 端演示登录按浏览器随机设备标识隔离账号，昵称只作展示；它和模拟支付都仅在 dev profile 开启,prod 固定关闭。MySQL、Redis 和 RabbitMQ 的宿主端口默认只绑定 `127.0.0.1`;所有敏感配置一律环境变量注入,仓库不含任何真实凭据。真实商户支付启用前必须接入微信支付平台证书验签 `Validator`,当前无商户证书的演示配置不会把未验签回调描述为生产可用。需要恢复演示态时执行 `scripts/demo-reset.sql`。

注意:Flyway 社区版要求 MySQL 8.0+;若临时使用 MySQL 5.7,需手动执行 `db/migration` 下的 SQL 并以 `SPRING_FLYWAY_ENABLED=false` 启动。

## 测试

`./mvnw test` 本轮发现 89 项：87 通过、0 failure、0 error，2 个 Testcontainers 类因当前沙箱无权访问 Docker named pipe 而跳过；Docker Compose 真实运行栈与浏览器纵向 E2E 另行完成复验。容器组会启动真实 MySQL 8、Redis 7、RabbitMQ 4.1 与完整 Spring 上下文,覆盖空库 Flyway 迁移、生产迁移不创建默认管理员、退款重试、秒杀首发恢复/重复消费/重复补偿/终态竞争、mandatory return、支付/取消竞态、Redis 重建屏障和并发零超卖等性质;CI 应在可访问 Docker 的 runner 上完整执行且不允许无解释跳过。

当前 `npm audit --omit=dev` 在 PostCSS 8.5.24 上仍报告 7 条 high 依赖路径且上游暂无可用修复。这是需要持续跟踪的构建/开发供应链风险,不等同于已构建静态页面存在已验证的远程利用;项目不使用 `npm audit fix --force` 破坏兼容性。

## 演进方向

- 多实例部署时将进程内限流升级为 Redis+Lua 全局限流
- 需要更强审计保留期时,把当前 Redis 过程账本扩展为持久化 outbox
- 缓存一致性引入 Canal 订阅 binlog,把删除动作从业务代码剥离

## 协作与安全

- 开发环境、分支、测试和 Pull Request 要求见 [CONTRIBUTING.md](CONTRIBUTING.md)。
- 安全问题不要提交公开 Issue，请按 [SECURITY.md](SECURITY.md) 私下报告。
- 自动依赖更新覆盖 Maven、npm、Docker 和 GitHub Actions；所有更新仍须通过现有 CI 后再合并。

## 授权状态

本仓库包含由教学项目改编的内容，尚未取得可公开再分发的完整授权，因此当前保持私有且不提供开源许可证。来源和使用边界见 [NOTICE.md](NOTICE.md)。
