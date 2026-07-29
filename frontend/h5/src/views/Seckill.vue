<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { api } from '../api'
import { couponThresholdLabel, formatBusinessTime, seckillPhase } from '../domain'

const coupons = ref([])
const holdings = ref([])
const grabbing = ref(0)
const now = ref(Date.now())
const serverOffset = ref(0)
let clock

const holdingByCoupon = computed(() => new Map(holdings.value.map((item) => [item.couponId, item])))
const fen = (value) => (Number(value || 0) / 100).toFixed(0)

function phase(coupon) {
  return seckillPhase(coupon, now.value, holdingByCoupon.value.has(coupon.id))
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
  coupons.value = list || []
  holdings.value = mine || []
  if (coupons.value[0]?.serverTimeEpochMs) {
    serverOffset.value = Number(coupons.value[0].serverTimeEpochMs) - Date.now()
    now.value = Date.now() + serverOffset.value
  }
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
  clock = setInterval(() => { now.value = Date.now() + serverOffset.value }, 1000)
})
onBeforeUnmount(() => clearInterval(clock))
</script>

<template>
  <div class="page">
    <div class="page-head">
      <div class="eyebrow">限时福利</div>
      <h1 class="page-title">限时抢券</h1>
      <p class="page-subtitle">活动时间统一按北京时间计算，抢到后可在结算时使用。</p>
    </div>

    <van-empty v-if="!coupons.length" description="当前没有可参与的抢券活动">
      <van-button round plain type="danger" @click="$router.push('/coupons')">查看我的优惠券</van-button>
    </van-empty>

    <article v-for="coupon in coupons" :key="coupon.id" class="seckill-card">
      <div class="coupon-value">
        <span>¥</span><strong>{{ fen(coupon.discountAmount) }}</strong>
        <small>{{ couponThresholdLabel(coupon.minAmount, coupon.discountAmount) }}</small>
      </div>
      <div class="coupon-body">
        <p class="eyebrow">限量 {{ coupon.totalStock }} 张</p>
        <h2>{{ coupon.title }}</h2>
        <small v-if="phase(coupon) === 'pending'">{{ formatBusinessTime(coupon.startTimeEpochMs) }} 开抢</small>
        <small v-else>{{ formatBusinessTime(coupon.endTimeEpochMs) }} 结束</small>
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
      <b>使用说明</b>
      <span>领取成功后可在“我的优惠券”查看；结算时由服务端再次校验库存、状态与使用门槛。</span>
    </div>
  </div>
</template>

<style scoped>
.seckill-card { display: grid; grid-template-columns: 86px 1fr 72px; gap: 12px; align-items: center; margin: 12px; padding: 16px 14px; background: var(--card); border: 1px solid rgba(65,49,38,.08); border-radius: 6px 18px 18px; box-shadow: 0 7px 24px rgba(67,49,36,.04); }
.coupon-value { min-height: 82px; padding-right: 10px; border-right: 1px dashed #d7cec5; color: var(--brand); }
.coupon-value span { font-size: 14px; }
.coupon-value strong { font-size: 34px; line-height: 1; }
.coupon-value small { display: block; margin-top: 9px; color: var(--muted); font-size: 11px; line-height: 1.35; }
.coupon-body { min-width: 0; }
.coupon-body .eyebrow { margin: 0; font-size: 10px; letter-spacing: .08em; }
.coupon-body h2 { margin: 5px 0; overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.coupon-body small, .coupon-body > span { color: var(--muted); font-size: 11px; }
.progress { height: 4px; margin: 10px 0 6px; overflow: hidden; border-radius: 2px; background: #ece7e1; }
.progress i { display: block; height: 100%; background: var(--brand); }
.seckill-card > button { min-height: 40px; padding: 8px 5px; border: 1px solid var(--line); border-radius: 20px; background: white; color: var(--muted); font-size: 12px; }
.seckill-card > button.live { border-color: var(--brand); background: var(--brand); color: white; }
.seckill-card > button.owned { border-color: #a9c1ae; background: #edf4ee; color: var(--forest); }
.flow-note { margin: 18px 16px; padding: 14px 0; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; line-height: 1.7; }
.flow-note b, .flow-note span { display: block; }
.flow-note b { color: var(--ink); }
</style>
