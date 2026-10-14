# JMeter 压测脚本

用于菜品查询与领券接口的本地压测，结果通过 JTL 文件统计。数据准备与清理应在已备份的专用测试环境中进行。

## 准备

1. 在仓库根目录执行 `docker compose up -d --build`，启动应用、MySQL、Redis 与 RabbitMQ。
   - 正式压测前将 `DINEHUB_RATE_LIMIT_ENABLED=false`,避免把入口保护能力混入业务链路吞吐。
   - DB 对照组设置 `DINEHUB_SECKILL_MODE=db`;可靠异步组设置为 `redis`,切换后重建 app 容器。
2. 在 `scripts/jmeter` 目录准备测试数据（需要 Python 3）:
   - 菜品:向 `dish` 表补种子数据至 500 条(分类 16 下约 63 条)。
   - 用户与令牌可用入库脚本一次生成:

```bash
python prepare_seckill.py --secret <dev profile 的用户 JWT 密钥>
# 将 users_seed.sql 导入 MySQL;user_tokens.csv 已被 gitignore
```
   - 秒杀券:管理端创建 `total_stock=100000` 的秒杀券并发布(预热 Redis)。
3. 管理端创建并发布压测券;每轮必须等待 RabbitMQ 队列排空,再清理该券的 `user_coupon`/`seckill_request` 并重建 Redis 账本。直接在有在途消息时删表会污染下一轮。

## 运行

安装 JMeter 并将其加入 PATH。在 `scripts/jmeter` 目录执行以下命令；示例使用 Bash 的反斜杠续行，PowerShell 中请合并为单行。

```bash
# 菜品查询(缓存开关用环境变量 DINEHUB_CACHE_DISH_ENABLED 控制,重启生效)
jmeter -n -t dish_query.jmx -l out.jtl -Jtoken=<任一用户JWT> \
  -Jthreads=100 -Jduration=30 -Jrampup=5 -JcategoryId=16

# 秒杀(模式用 DINEHUB_SECKILL_MODE=redis|db 控制;压测时关闭限流
# DINEHUB_RATE_LIMIT_ENABLED=false;每轮之间清空 user_coupon 并重置库存、重新发布预热)
jmeter -n -t seckill.jmx -l out.jtl -Jtokencsv=<user_tokens.csv 绝对路径> \
  -Jthreads=200 -Jloops=100 -Jrampup=5 -JcouponId=<券id>

# 汇总 QPS / P50 / P95 / P99 / 错误率
python analyze.py out.jtl
```

## 结果统计

`analyze.py` 读取 CSV 格式 JTL 中的 `timeStamp`、`elapsed` 和 `success` 字段，输出请求数、统计时长、QPS、P50/P95/P99 响应时间和错误率。QPS 为请求数除以首个请求开始至最后一个请求结束的秒数；错误率按 `success` 不为 `true` 的请求占比计算。

每组配置运行 3 轮，分别保存 JTL，再按同一指标取三轮中位数。记录硬件、运行模式、缓存与限流开关、并发数和持续时间，便于比较；应用与 JMeter 同机运行会相互争用资源。

Redis 异步领券的接口响应只代表准入结果，优惠券最终到账时间和失败情况需单独统计，不能用接口 QPS 代替到账吞吐。
