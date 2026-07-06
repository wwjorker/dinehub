# DineHub(食汇):本地餐饮一体化经营平台

DineHub 是一个面向单店/小连锁餐饮商家的一体化经营平台后端,统一支撑**到店扫码点单**与**外卖下单**两种履约方式,并围绕高并发场景做营销活动(限时秒杀优惠券)的工程实践。项目重点不在页面数量,而在订单主链路的正确性与高并发下的性能优化过程。

## 当前状态

- `backend`:Spring Boot 多模块后端,已实现商家管理端(员工/分类/菜品/套餐/订单管理/营业统计)与用户端(浏览/购物车/下单/支付对接)完整链路。
- 进行中:数据库迁移 Flyway 化、Docker Compose 本地环境、扫码点单(堂食履约)、优惠券限时秒杀、订单超时取消的 MQ 改造。
- 性能数字以本机 JMeter 实测为准,未实测的优化不写数字。

## 技术栈

```text
Java 8 / Spring Boot 2.7.x
MyBatis + PageHelper
MySQL + Druid
Redis(Spring Cache / 秒杀预扣库存)
WebSocket(商家来单提醒)
JWT 双端认证(管理端 / 用户端)
Knife4j 接口文档
Apache POI 运营报表导出
```

## 项目结构

```text
dinehub
+-- backend
    +-- dinehub-common   通用工具、异常、配置属性
    +-- dinehub-pojo     实体 / DTO / VO
    +-- dinehub-server   业务模块(controller / service / mapper)
```

## 本地运行

依赖:MySQL 8.x、Redis(默认 `localhost` 标准端口,可用环境变量覆盖,见 `application-dev.yml`)。

```bash
cd backend
./mvnw clean package
java -jar dinehub-server/target/dinehub-server-1.0-SNAPSHOT.jar
```

启动后接口文档见 `http://localhost:8080/doc.html`。

所有敏感配置(数据库口令、OSS/微信支付密钥)一律走环境变量,仓库中不含任何真实凭据;不配置支付/OSS 相关变量时,除对应功能外其余模块可正常运行。

## 路线图

- [ ] Flyway 管理全量 schema(V1 基线)
- [ ] Docker Compose 一键启动 MySQL / Redis / RabbitMQ
- [ ] 扫码点单:桌台二维码 → 堂食订单,与外卖共用订单主链路
- [ ] 优惠券限时秒杀:Redis + Lua 原子预扣库存、MQ 异步落库、幂等与库存回补
- [ ] 订单超时取消:Spring Task 轮询改造为 RabbitMQ 延迟消息,保留轮询作低频兜底
- [ ] 菜品缓存一致性(延迟双删)与注解式接口限流
- [ ] JMeter 压测报告:缓存前后、秒杀方案前后的 QPS / P50 / P99 对照(本机口径)
