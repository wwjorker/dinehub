<script setup>
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { api } from '../api'

const coupons = ref([])
const grabbing = ref(0)

const fen2yuan = (fen) => (fen / 100).toFixed(0)

function phase(c) {
  const now = Date.now()
  if (new Date(c.seckillStartTime).getTime() > now) return 'pending'
  if (new Date(c.seckillEndTime).getTime() < now) return 'ended'
  return 'live'
}

async function load() {
  coupons.value = await api.seckillList()
}

async function grab(c) {
  grabbing.value = c.id
  try {
    await api.seckill(c.id)
    showToast({ type: 'success', message: '抢到了!正在到账,稍后可在"我的"查看' })
    load()
  } catch (e) {
    /* 已抢完/重复抢购等文案由统一请求层弹出 */
  } finally {
    grabbing.value = 0
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <van-nav-bar title="限时秒杀" />
    <van-empty v-if="coupons.length === 0" description="暂无进行中的秒杀活动" />

    <div class="coupon" v-for="c in coupons" :key="c.id">
      <div class="left">
        <div class="amount">￥<b>{{ fen2yuan(c.discountAmount) }}</b></div>
        <div class="min">满{{ fen2yuan(c.minAmount) }}可用</div>
      </div>
      <div class="mid">
        <div class="title">{{ c.title }}</div>
        <div class="time">{{ c.seckillStartTime.replace('T', ' ') }} 开抢</div>
        <van-progress :percentage="Math.max(0, Math.round(100 * (1 - c.remainingStock / c.totalStock)))"
          stroke-width="6" color="#d43f2f" :show-pivot="false" style="margin-top: 8px" />
        <div class="stock">已抢 {{ c.totalStock - c.remainingStock }} / {{ c.totalStock }}</div>
      </div>
      <div class="right">
        <van-button v-if="phase(c) === 'live'" type="danger" size="small" round
          :loading="grabbing === c.id" @click="grab(c)">立即抢</van-button>
        <van-tag v-else-if="phase(c) === 'pending'" type="warning">未开始</van-tag>
        <van-tag v-else type="default">已结束</van-tag>
      </div>
    </div>

    <div class="note">抢购成功后异步到账,以"我的-优惠券"为准</div>
  </div>
</template>

<style scoped>
.coupon { display: flex; align-items: center; gap: 12px; background: #fff; border-radius: 12px; padding: 14px; margin: 10px; }
.left { text-align: center; color: #d43f2f; flex: none; width: 74px; border-right: 1px dashed #eee; }
.amount b { font-size: 30px; }
.min { font-size: 11px; color: #999; margin-top: 2px; }
.mid { flex: 1; min-width: 0; }
.title { font-weight: 600; }
.time { color: #999; font-size: 12px; margin-top: 2px; }
.stock { color: #bbb; font-size: 11px; margin-top: 4px; }
.right { flex: none; }
.note { text-align: center; color: #bbb; font-size: 12px; margin: 16px 0; }
</style>
