# JMeter 压测脚本

结果与口径见仓库根 README「性能压测」章节。复现步骤:

## 准备

1. `docker compose up -d` 启动 MySQL/Redis/RabbitMQ,启动后端(见根 README)。
2. 造数(容器内执行,幂等性自行注意):
   - 菜品:向 `dish` 表补种子数据至 500 条(分类 16 下约 63 条)。
   - 用户:向 `user` 表插入 20000 个压测用户。
   - 秒杀券:管理端创建 `total_stock=100000` 的秒杀券并发布(预热 Redis)。
3. 为压测用户生成 JWT 一行一个写入 `user_tokens.csv`(HS256,密钥与 `dinehub.jwt.user-secret-key` 一致,claim 为 `userId`)。

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
