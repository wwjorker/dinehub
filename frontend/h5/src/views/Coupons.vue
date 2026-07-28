<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const coupons = ref([])
const loading = ref(false)
const active = ref('all')

const statusMeta = {
  1: { text: '可使用', tone: 'available' },
  2: { text: '已使用', tone: 'used' },
  3: { text: '已过期', tone: 'expired' },
  4: { text: '订单占用中', tone: 'reserved' }
}

const visible = computed(() => coupons.value.filter((coupon) => {
  if (active.value === 'all') return true
  return coupon.status === Number(active.value)
}))

function yuan(value) {
  return (Number(value || 0) / 100).toFixed(0)
}

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  loading.value = true
  try {
    coupons.value = await api.myCoupons()
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page coupons-page">
    <header class="page-head coupon-head">
      <button class="back-button" aria-label="返回" @click="router.back()">‹</button>
      <div>
        <div class="eyebrow">COUPON WALLET</div>
        <h1 class="page-title">我的优惠券</h1>
        <p class="page-subtitle">抢券、占用、核销与释放状态都能在这里看到。</p>
      </div>
    </header>

    <nav class="coupon-tabs">
      <button :class="{ active: active === 'all' }" @click="active = 'all'">全部</button>
      <button :class="{ active: active === '1' }" @click="active = '1'">可用</button>
      <button :class="{ active: active === '4' }" @click="active = '4'">占用中</button>
      <button :class="{ active: active === '2' }" @click="active = '2'">已使用</button>
    </nav>

    <van-loading v-if="loading" class="page-loading" vertical>正在整理卡券</van-loading>
    <van-empty v-else-if="!visible.length" description="这里暂时没有优惠券">
      <van-button round type="danger" @click="router.push('/seckill')">去秒杀页</van-button>
    </van-empty>
    <main v-else class="coupon-list">
      <article v-for="coupon in visible" :key="coupon.id" class="coupon-card" :class="statusMeta[coupon.status]?.tone">
        <div class="coupon-value">
          <span>¥</span><strong>{{ yuan(coupon.discountAmount) }}</strong>
          <small>满 ¥{{ yuan(coupon.minAmount) }} 可用</small>
        </div>
        <div class="coupon-copy">
          <span class="coupon-type">{{ coupon.type === 2 ? '秒杀券' : '优惠券' }}</span>
          <h2>{{ coupon.title }}</h2>
          <p v-if="coupon.seckillEndTime">活动至 {{ formatDate(coupon.seckillEndTime) }}</p>
          <p v-else>领取后可用于符合门槛的订单</p>
        </div>
        <span class="coupon-status">{{ statusMeta[coupon.status]?.text || '状态未知' }}</span>
      </article>
    </main>

    <aside class="coupon-note">
      待付款订单会暂时占用优惠券；支付后核销，取消或超时后自动释放。
    </aside>
  </div>
</template>

<style scoped>
.coupon-head { display: flex; align-items: flex-start; gap: 12px; }
.back-button {
  width: 34px; height: 34px; padding: 0; border: 1px solid var(--line); border-radius: 50%;
  background: transparent; color: var(--ink); font-size: 28px; line-height: 28px;
}
.coupon-tabs { display: flex; gap: 8px; padding: 12px; }
.coupon-tabs button {
  flex: 1; padding: 8px 4px; border: 0; border-bottom: 2px solid transparent;
  background: transparent; color: var(--muted); font-size: 12px;
}
.coupon-tabs button.active { border-color: var(--brand); color: var(--brand); font-weight: 700; }
.page-loading { padding-top: 80px; }
.coupon-list { padding: 0 12px; }
.coupon-card {
  position: relative;
  display: grid;
  grid-template-columns: 104px 1fr;
  min-height: 116px;
  margin-bottom: 12px;
  overflow: hidden;
  border: 1px solid rgba(68,54,43,.08);
  border-radius: 14px;
  background: var(--card);
}
.coupon-card::before,
.coupon-card::after {
  position: absolute; left: 96px; width: 14px; height: 14px; border-radius: 50%;
  background: var(--paper); content: "";
}
.coupon-card::before { top: -7px; }
.coupon-card::after { bottom: -7px; }
.coupon-value {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border-right: 1px dashed #d7c8bd; background: #f4e3dc; color: var(--brand-dark);
}
.coupon-value > span { position: absolute; margin: -20px 0 0 -48px; font-size: 13px; }
.coupon-value strong { font: 700 35px/1 "STSong", "Songti SC", serif; }
.coupon-value small { margin-top: 7px; font-size: 10px; }
.coupon-copy { align-self: center; min-width: 0; padding: 17px 64px 17px 18px; }
.coupon-type { color: var(--forest); font-size: 10px; letter-spacing: .12em; }
.coupon-copy h2 { margin: 5px 0 8px; font-size: 15px; }
.coupon-copy p { margin: 0; color: var(--muted); font-size: 10px; line-height: 1.5; }
.coupon-status {
  position: absolute; top: 14px; right: 12px; padding: 3px 7px; border-radius: 999px;
  background: #e3f0e6; color: #3d714e; font-size: 9px;
}
.coupon-card.reserved .coupon-status { background: #fff0cf; color: #8b5e16; }
.coupon-card.used, .coupon-card.expired { filter: grayscale(.8); opacity: .66; }
.coupon-note {
  margin: 16px 24px; color: var(--muted); font-size: 11px; line-height: 1.7; text-align: center;
}
</style>
