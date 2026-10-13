# 运行与演示

## 全栈启动

需要可用的 Docker Engine 和 Docker Compose。在没有其他 DineHub 实例占用容器名称、端口的目录中运行：

```bash
docker compose up -d --build
```

Compose 显式使用 `dev`，通过 Flyway 建表并加载演示菜单；开发管理员为 `admin / 123456`。这些是本地演示默认值。`prod` 不创建默认管理员，JWT、数据库等必要配置必须另行提供。

| 入口 | 默认地址 |
| --- | --- |
| 用户端 | http://localhost:8080/h5/ |
| 运营台 | http://localhost:8080/h5/ops.html |
| 接口文档 | http://localhost:8080/doc.html |
| 健康检查 | http://localhost:8080/actuator/health |
| 开发指标 | http://localhost:8080/actuator/prometheus |

首次登录运营台后开启营业。未设置营业状态时按打烊处理。客户端昵称只用于显示，演示用户按浏览器设备标识隔离。

## 演示流程

1. 商家登录运营台，开启营业，检查菜品与口味；可发布优惠券并维护桌台。
2. 用户使用演示昵称进入，选择菜品和口味后加入购物车。
3. 堂食需先扫描桌台二维码；外送需选择收货地址。下单时由服务端计算价格、打包费和优惠。
4. 开发环境使用模拟支付；外送订单由商家接单、派送、完成。堂食由商家接单后完成；该路径的后端修复已通过单元测试，最终隔离运行复验尚未完成，见[本轮验证记录](VERIFICATION-20261013.md)。
5. 用户在订单页查看状态、详情；限时领券可以单独演示等待结果、到账和重复领取提示。

普通下单和秒杀是两条可分别展示的流程。开发模拟支付不代表真实微信商户支付已经验收。

## 手机访问与环境隔离

默认 `localhost` 只适用于运行服务的电脑。手机演示时，在自己的 `.env` 中设置 `DINEHUB_H5_BASE_URL` 为手机能够访问的服务地址；不要提交 `.env`，也不要把带桌台令牌的二维码截图用于公开文档。

`scripts/refresh-lan-url.ps1` 会修改 `.env` 并可能重建容器，运行前应确认目标目录和地址。它不适用于本轮独立验证环境。

现有 `docker-compose.yml` 使用固定容器名。仅改变 Compose 项目名不能安全地并行启动另一份；副本验证必须使用独立 Compose 文件、不同容器名/端口及全新的 MySQL、Redis、RabbitMQ 和上传数据卷。不要让副本指向原演示数据库。

## 前端与后端开发

- 前端：Node.js 22，进入 `frontend/h5`，运行 `npm ci`、`npm run test`、`npm run build`。
- 构建输出直接写到 `backend/dinehub-server/src/main/resources/static/h5`，源码修改须与产物一起审查。
- 后端：JDK 17，进入 `backend`，使用 `./mvnw -B clean verify`；PowerShell 使用 `./mvnw.cmd`。
- 完整后端测试需要 Docker，Testcontainers 会创建临时数据库和消息组件；跳过容器测试不等于完整通过。
- Flyway 迁移要求 MySQL 8.0+；已发布迁移不直接改写，数据修正新增版本。
- 演示上传使用 `upload-data-v2` 卷。备份与迁移时应同时保留数据库和上传目录。

## 重置与停止

停止服务可使用 `docker compose stop`，它会保留数据卷。再次启动用 `docker compose up -d`。

`scripts/demo-reset.sql` 会删除交易数据，仅适合已备份的专用演示库。它不是本轮图片修正的执行步骤，不要在原运行库直接尝试，也不要用 `down -v` 代替停止。

详细协作约定见 [CONTRIBUTING](../CONTRIBUTING.md)，历史压测口径见 [历史记录](BENCHMARK-HISTORY.md)。
