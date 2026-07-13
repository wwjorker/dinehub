# DineHub(食汇):本地餐饮一体化经营平台

[![CI](https://github.com/wwjorker/dinehub/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/dinehub/actions/workflows/ci.yml)

DineHub 是一个面向单店/小连锁餐饮商家的一体化经营平台后端,统一支撑**到店扫码点单**与**外卖下单**两种履约方式,并围绕高并发场景完成了限时秒杀、异步削峰、缓存一致性与接口限流的完整工程实践。项目重点不在页面数量,而在订单主链路的正确性与高并发优化的可验证过程——所有性能数字均为本机 JMeter 实测,脚本入库可复现。

## 功能全景

- **商家管理端**:员工、分类、菜品、套餐、订单管理、营业统计、运营报表导出(POI)、来单提醒(WebSocket)。
- **用户端 H5**(`/h5/`,Vue 3 + Vant):扫桌台二维码落地、演示登录、分类点单与口味选择、购物车、堂食下单、秒杀抢券(实时库存进度)、我的券与订单。
- **运营台**(`/h5/ops.html`):商家登录、建桌台并预览二维码、创建/发布秒杀券、订单一览——演示最小闭环,完整管理能力见接口文档。
- **用户端(接口)**:菜品浏览(Redis 缓存)、购物车、外卖下单、微信支付对接。
- **扫码点单**:管理端维护桌台并生成二维码(zxing),顾客扫码后凭桌台令牌下堂食单,与外卖单共用订单主链路(履约方式分支),地址/配送环节自动跳过。
- **限时秒杀优惠券**:Redis+Lua 在一次原子操作中完成库存判定、一人一券、预扣和请求登记;RabbitMQ publisher confirm/return 只推进投递状态,未知结果不误补偿;低频扫描按同一 `requestId` 重发,消费者与补偿者通过 MySQL `seckill_request` 终态行竞争,保证一个准入最终只能“落券”或“退款”二选一。即使 Redis 过程账本丢失,MySQL 中超时的补偿中记录也会被独立扫描恢复;重复消费、重复死信和迟到消息均幂等。DB 行锁方案保留为可切换对照组。
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

两种方案在售罄边界均无一超卖、无一人多券。当前同版本三轮中位数下,可靠 Redis 准入吞吐约为 DB 行锁的 15.4 倍,P99 从 8751ms 降至 385ms(缩短约 22.7 倍)。早期快路径展示性能上限;当前实现则主动用一部分吞吐换取崩溃后可恢复、可查询和可对账的消息语义。

异步化的代价同样量化:当前可靠消费者在本机约 85–90 条/秒(每消息事务内同时写终态、持券记录并扣 DB 库存),2 万条完全到账约 3.8 分钟。C 端先收到 `requestId` 并轮询 SUCCESS/PENDING/FAILED;积压不会被伪装成“已经到账”,失败则进入唯一补偿入口退还名额。

## 技术栈

```text
Java 8 源码兼容 / JDK 17 构建与运行 / Spring Boot 2.7.x
MyBatis + PageHelper / MySQL 8 + Druid / Flyway
Redis(缓存 + Lua 原子账本)/ RabbitMQ(confirm/return + 异步落库 + TTL/死信)
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

首次启动由 Flyway 自动建表并写入非账号类演示数据;`dev` profile 会单独创建演示管理员 admin/123456,`prod` 不创建任何默认管理员。入口:点单 H5 `http://localhost:8080/h5/`、运营台 `http://localhost:8080/h5/ops.html`、接口文档 `http://localhost:8080/doc.html`、健康检查 `/actuator/health`、Prometheus 指标 `/actuator/prometheus`。演示动线:运营台建桌台 → 查看二维码 → 手机扫码(同一局域网,二维码地址可用 `DINEHUB_H5_BASE_URL` 指到本机 IP)→ 点单下单;运营台创建并发布秒杀券 → H5 秒杀页抢券。H5 构建产物已入库;改前端后在 `frontend/h5` 内 `npm ci && npm run build` 重新生成,CI 会校验源码与入库产物一致。C 端昵称直登仅在 dev profile 开启,prod 固定关闭。宿主端口冲突时可用环境变量覆盖;所有敏感配置一律环境变量注入,仓库不含任何真实凭据。真实商户支付启用前必须接入微信支付平台证书验签 `Validator`,当前无商户证书的演示配置不会把未验签回调描述为生产可用。需要恢复演示态时执行 `scripts/demo-reset.sql`。

注意:Flyway 社区版要求 MySQL 8.0+;若临时使用 MySQL 5.7,需手动执行 `db/migration` 下的 SQL 并以 `SPRING_FLYWAY_ENABLED=false` 启动。

## 测试

`./mvnw test`:CI 当前执行 91 个测试。除普通单测外,Testcontainers 会启动真实 MySQL 8、Redis 7、RabbitMQ 4.1 与完整 Spring 上下文,覆盖:空库 Flyway V1→V9 且生产迁移不创建默认管理员、准入后首发丢失由扫描恢复、Redis 过程账本全丢后由 MySQL 恢复补偿、重复消费只扣一次、重复补偿只退款一次、落券/补偿并发竞争只有一个终态、mandatory 不可路由回调、支付/取消及自动完成/取消的条件更新竞争、失效重建者不能覆盖新账本;独立 Redis 容器还验证 200 并发抢 50 库存零超卖、重建屏障和 TTL。无 Docker 环境自动跳过容器组,GitHub CI 必须完整执行。

## 演进方向

- 多实例部署时将进程内限流升级为 Redis+Lua 全局限流
- 需要更强审计保留期时,把当前 Redis 过程账本扩展为持久化 outbox
- 缓存一致性引入 Canal 订阅 binlog,把删除动作从业务代码剥离
