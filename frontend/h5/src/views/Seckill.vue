<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { api } from '../api'

const coupons = ref([])
const holdings = ref([])
const grabbing = ref(0)
const now = ref(Date.now())
let clock

const holdingByCoupon = computed(() => new Map(holdings.value.map((item) => [item.couponId, item])))
const fen = (value) => (Number(value || 0) / 100).toFixed(0)

function phase(coupon) {
  if (holdingByCoupon.value.has(coupon.id)) return 'owned'
  if (coupon.remainingStock <= 0) return 'soldout'
  if (new Date(coupon.seckillStartTime).getTime() > now.value) return 'pending'
  if (new Date(coupon.seckillEndTime).getTime() < now.value) return 'ended'
  return 'live'
}

function buttonText(coupon) {
  return {
    owned: holdingByCoupon.value.get(coupon.id)?.status === 4 ? '订单占用中' : '已领取',
    soldout: '已抢完',
    pending: '未开始',
    ended: '已结束',
    live: '立即抢'
  }[phase(coupon)]
}

async function load() {
  const [list, mine] = await Promise.all([api.seckillList(), api.myCoupons()])
  coupons.value = list
  holdings.value = mine
}

async function grab(coupon) {
  if (phase(coupon) !== 'live') return
  grabbing.value = coupon.id
  try {
    const ticket = await api.seckill(coupon.id)
    const result = ticket.status === 'SUCCESS' ? 'SUCCESS' : await pollResult(ticket.requestId)
    if (result === 'SUCCESS') showToast({ type: 'success', message: '抢券成功，已放入券包' })
    else if (result === 'FAILED') showToast('处理失败，名额已自动退回')
    else showToast('资格已锁定，后台仍在确认')
    await load()
  } finally {
    grabbing.value = 0
  }
}

async function pollResult(requestId) {
  for (let index = 0; index < 20; index += 1) {
    await new Promise((resolve) => setTimeout(resolve, 500))
    const result = await api.seckillResult(requestId)
    if (result.status !== 'PENDING') return result.status
  }
  return 'PENDING'
}

onMounted(async () => {
  await load()
  clock = setInterval(() => { now.value = Date.now() }, 1000)
})
onBeforeUnmount(() => clearInterval(clock))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div class="eyebrow">FLASH COUPON</div>
      <h1 class="page-title">限时抢券</h1>
      <p class="page-subtitle">先抢资格，再在结算时由服务端校验并抵扣。</p>
    </div>

    <van-empty v-if="!coupons.length" description="暂时没有进行中的活动" />

    <article v-for="coupon in coupons" :key="coupon.id" class="seckill-card">
      <div class="coupon-value">
        <span>¥</span><strong>{{ fen(coupon.discountAmount) }}</strong>
        <small>满 {{ fen(coupon.minAmount) }} 可用</small>
      </div>
      <div class="coupon-body">
        <p class="eyebrow">LIMITED OFFER</p>
        <h2>{{ coupon.title }}</h2>
        <small>{{ String(coupon.seckillEndTime || '').replace('T', ' ') }} 结束</small>
        <div class="progress">
          <i :style="{ width: `${Math.max(0, Math.min(100, 100 * (1 - coupon.remainingStock / coupon.totalStock)))}%` }"></i>
        </div>
        <span>已抢 {{ coupon.totalStock - coupon.remainingStock }} / {{ coupon.totalStock }}</span>
      </div>
      <button :class="{ live: phase(coupon) === 'live', owned: phase(coupon) === 'owned' }"
        :disabled="phase(coupon) !== 'live' || grabbing === coupon.id" @click="grab(coupon)">
        {{ grabbing === coupon.id ? '处理中' : buttonText(coupon) }}
      </button>
    </article>

    <div class="flow-note">
      <b>抢券流程</b>
      <span>Redis 原子校验 → MQ 异步落库 → 结算占用 → 支付核销</span>
    </div>
  </div>
</template>

<style scoped>
.seckill-card { display: grid; grid-template-columns: 78px 1fr 68px; gap: 12px; align-items: center; margin: 12px; padding: 15px 13px; background: var(--card); border: 1px solid rgba(65, 49, 38, .08); border-radius: 4px 17px 17px; box-shadow: 0 7px 24px rgba(67,49,36,.04); }
.coupon-value { min-height: 78px; padding-right: 10px; border-right: 1px dashed #d7cec5; color: var(--brand); }
.coupon-value span { font-size: 13px; }
.coupon-value strong { font-size: 34px; line-height: 1; }
.coupon-value small { display: block; margin-top: 8px; color: var(--muted); font-size: 9px; }
.coupon-body { min-width: 0; }
.coupon-body .eyebrow { margin: 0; font-size: 8px; }
.coupon-body h2 { margin: 4px 0; overflow: hidden; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.coupon-body small, .coupon-body > span { color: var(--muted); font-size: 9px; }
.progress { height: 4px; margin: 9px 0 5px; overflow: hidden; border-radius: 2px; background: #ece7e1; }
.progress i { display: block; height: 100%; background: var(--brand); }
.seckill-card > button { padding: 8px 4px; border: 1px solid var(--line); border-radius: 16px; background: white; color: var(--muted); font-size: 11px; }
.seckill-card > button.live { border-color: var(--brand); background: var(--brand); color: white; }
.seckill-card > button.owned { border-color: #a9c1ae; background: #edf4ee; color: var(--forest); }
.flow-note { margin: 18px 16px; padding: 13px 0; border-top: 1px solid var(--line); color: var(--muted); font-size: 10px; line-height: 1.6; }
.flow-note b, .flow-note span { display: block; }
.flow-note b { color: var(--ink); }
</style>
