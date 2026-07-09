# DineHub(食汇):本地餐饮一体化经营平台

[![CI](https://github.com/wwjorker/dinehub/actions/workflows/ci.yml/badge.svg)](https://github.com/wwjorker/dinehub/actions/workflows/ci.yml)

DineHub 是一个面向单店/小连锁餐饮商家的一体化经营平台后端,统一支撑**到店扫码点单**与**外卖下单**两种履约方式,并围绕高并发场景完成了限时秒杀、异步削峰、缓存一致性与接口限流的完整工程实践。项目重点不在页面数量,而在订单主链路的正确性与高并发优化的可验证过程——所有性能数字均为本机 JMeter 实测,脚本入库可复现。

## 功能全景

- **商家管理端**:员工、分类、菜品、套餐、订单管理、营业统计、运营报表导出(POI)、来单提醒(WebSocket)。
- **用户端**:菜品浏览(Redis 缓存)、购物车、外卖下单、微信支付对接。
- **扫码点单**:管理端维护桌台并生成二维码(zxing),顾客扫码后凭桌台令牌下堂食单,与外卖单共用订单主链路(履约方式分支),地址/配送环节自动跳过。
- **限时秒杀优惠券**:Redis+Lua 原子准入(查库存/判重/扣减/记名一次往返)→ RabbitMQ 异步落库 → 唯一键幂等 → 重试→死信→库存回补补偿;DB 行锁方案保留为可切换的压测对照组(`dinehub.seckill.mode`)。
- **订单超时取消**:TTL+死信实现延迟消息,消费者做状态条件更新(与支付回调竞态安全);原轮询任务降级为低频兜底扫描,双保险。
- **缓存一致性**:菜品缓存延迟双删,覆盖"删除后读请求回填旧值"的窗口。
- **接口限流**:注解式令牌桶(惰性补充,无后台线程),挂在秒杀入口,支持全局开关。
- **基建**:Flyway 全量 schema 管理、Docker Compose 一键中间件、GitHub Actions CI(含 Testcontainers 真 Redis 的 Lua 并发无超卖测试)。

## 性能压测

口径:i7-12700H / 16GB RAM / Windows 11,应用单实例(JVM `-Xmx1g`),MySQL 8.0 / Redis 7 / RabbitMQ 3.12 运行于 Docker Desktop(WSL2);JMeter 5.6.3 与服务端同机发压(发压端抢占 CPU,数字偏保守)。每组 3 轮取中位数,`.jmx` 脚本与汇总工具在 [scripts/jmeter/](scripts/jmeter/)。

### 菜品列表查询:直查 DB vs Redis 缓存

100 并发线程持续 30s,查询单分类 63 道菜(库内共 500 道);直查 DB 路径含菜品口味的 N+1 查询(1+63 条 SQL/请求),这是课程型代码的真实形态,也是缓存收益如此显著的原因。

| 方案 | QPS | P50 | P99 | 错误率 |
|---|---|---|---|---|
| 直查 DB | 78 | 1179ms | 1875ms | 0% |
| Redis 缓存 | **4084** | **20ms** | **40ms** | 0% |

100 并发下 QPS 从 78 提升到 4084(约 52 倍),P99 从 1.9s 降到 40ms;瓶颈从 DB 查询(N+1 放大)转移到本机 CPU 与网络栈。

### 秒杀抢券:DB 行锁 vs Redis+Lua+MQ

200 并发线程 × 100 次循环 = 20000 个请求,20000 个独立用户(CSV 逐行分发 JWT,不触发一人一单拒绝),抢购库存 100000 的秒杀券(全成功路径);压测期间关闭接口限流以测纯链路。

| 方案 | QPS | P50 | P99 | 错误率 | 超卖 |
|---|---|---|---|---|---|
| DB 行锁扣减 | 55.9 | 2133ms | 12919ms | 0.77%(客户端超时) | 0 |
| Redis+Lua+MQ | **2772** | **31ms** | **106ms** | 0% | 0 |

200 并发下 QPS 从 55.9 提升到 2772(约 50 倍),P99 从 12.9s 降到 106ms;瓶颈从 DB 行锁串行化(所有请求排队更新同一行,长尾达 26s 并出现约 0.8% 客户端超时——但服务端条件更新保证了零超卖)转移到 Redis 单线程往返。两方案在三轮压测中 `user_coupon` 行数与库存扣减均严格相等,无一超卖、无一人多券。

异步化的真实代价也在压测中观测到:抢购 7 秒打完,单消费者落库约 85 条/秒(每消息一个事务,受磁盘 fsync 限制),队列积压需数分钟消化——已将消费端并发提升至 4,并在文档中保留该观测作为"抢到≠到账"窗口的量化依据。

## 技术栈

```text
Java 8 / Spring Boot 2.7.x
MyBatis + PageHelper / MySQL 8 + Druid / Flyway
Redis(缓存 + Lua 秒杀预扣)/ RabbitMQ(异步落库 + TTL/死信延迟消息)
WebSocket / JWT 双端认证 / Knife4j / Apache POI / zxing
JUnit 5 + Mockito + Testcontainers / GitHub Actions
```

## 项目结构

```text
dinehub
+-- backend
|   +-- dinehub-common   通用工具、异常、配置属性
|   +-- dinehub-pojo     实体 / DTO / VO
|   +-- dinehub-server   业务模块(controller / service / mapper / mq / ratelimit / cache)
+-- scripts/jmeter       压测脚本与汇总工具
+-- docker-compose.yml   MySQL / Redis / RabbitMQ
```

## 本地运行

```bash
docker compose up -d          # MySQL(3306)/Redis(6379)/RabbitMQ(5672,管理台15672)
cd backend
./mvnw clean package
java -jar dinehub-server/target/dinehub-server-1.0-SNAPSHOT.jar
```

首次启动由 Flyway 自动建表并写入开发种子数据;接口文档见 `http://localhost:8080/doc.html`(管理端账号 admin/123456)。宿主端口冲突时可用 `DINEHUB_DB_PORT` 等变量覆盖(见 `docker-compose.yml` 与 `application-dev.yml`);所有敏感配置(数据库口令、OSS/微信支付密钥)一律环境变量注入,仓库不含任何真实凭据。

注意:Flyway 社区版要求 MySQL 8.0+;若临时使用 MySQL 5.7,需手动执行 `db/migration` 下的 SQL 并以 `SPRING_FLYWAY_ENABLED=false` 启动。

## 测试

`./mvnw test`:34 个测试,覆盖秒杀准入三分支、消费幂等(同消息两次只落一行)、订单超时取消与支付回调的竞态、堂食/外卖双履约、令牌桶补充与突发、缓存双删时序;其中 Lua 脚本在 Testcontainers 拉起的真实 Redis 上验证 200 并发抢 50 库存零超卖(无 Docker 环境自动跳过)。CI 于每次 push 运行全量测试。

## 演进方向

- 秒杀消息投递升级 publisher confirm + 本地消息表;活动结束后的 Redis/DB 库存对账任务
- 缓存一致性引入 Canal 订阅 binlog,把删除动作从业务代码剥离
- 限流升级 Redis+Lua 全局令牌桶以支持多实例部署
- 堂食 H5 点单前端
