# JMeter 压测脚本

结果与口径见仓库根 README「性能压测」章节。复现步骤:

## 准备

1. `docker compose up -d --build` 启动应用、MySQL、Redis 与 RabbitMQ(见根 README)。
   - 正式压测前将 `DINEHUB_RATE_LIMIT_ENABLED=false`,避免把入口保护能力混入业务链路吞吐。
   - DB 对照组设置 `DINEHUB_SECKILL_MODE=db`;可靠异步组设置为 `redis`,切换后重建 app 容器。
2. 造数:
   - 菜品:向 `dish` 表补种子数据至 500 条(分类 16 下约 63 条)。
   - 用户与令牌可用入库脚本一次生成:

```bash
python prepare_seckill.py --secret <dev profile 的用户 JWT 密钥>
# 将 users_seed.sql 导入 MySQL;user_tokens.csv 已被 gitignore
```
   - 秒杀券:管理端创建 `total_stock=100000` 的秒杀券并发布(预热 Redis)。
3. 管理端创建并发布压测券;每轮必须等待 RabbitMQ 队列排空,再清理该券的 `user_coupon`/`seckill_request` 并重建 Redis 账本。直接在有在途消息时删表会污染下一轮。

## 运行

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

每组配置跑 3 轮取中位数;应用与 JMeter 同机运行,发压端与服务端抢占 CPU,数字口径偏保守。

当前版本最近一次三轮结果(2026-07-13):DB 行锁为 81.2/80.8/77.6 QPS,中位数 80.8 QPS、P50 1510ms、P99 8751ms;可恢复 Redis 投递为 1031/1248/1552 QPS,中位数 1248 QPS、P50 103ms、P99 385ms。两组错误率均为 0%。Redis 数字只代表可靠准入,异步到账速度单独统计。
