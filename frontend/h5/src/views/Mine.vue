<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { api } from '../api'
import { logout, store } from '../store'

const router = useRouter()
const summary = ref({ coupons: 0, orders: 0, pending: 0 })

async function load() {
  const [coupons, orders] = await Promise.all([api.myCoupons(), api.historyOrders(1, 50)])
  const records = orders.records || []
  summary.value = {
    coupons: coupons.filter((item) => item.status === 1).length,
    orders: records.length,
    pending: records.filter((item) => [1, 2, 3, 4].includes(item.status)).length
  }
}

async function doLogout() {
  await showConfirmDialog({ title: '退出登录', message: '退出后需要重新输入演示昵称。' })
  logout()
  router.replace('/login')
}

onMounted(load)
</script>

<template>
  <div class="page mine-page">
    <header class="profile">
      <div class="profile-top">
        <div class="avatar">{{ (store.nickname || '客').slice(0, 1) }}</div>
        <div>
          <p class="eyebrow">DINER PROFILE</p>
          <h1>{{ store.nickname || '顾客' }}</h1>
          <span>{{ store.tableName ? `当前绑定 ${store.tableName} 桌` : '当前未绑定堂食桌台' }}</span>
        </div>
      </div>
      <div class="profile-summary">
        <button @click="router.push('/orders?filter=all')"><strong>{{ summary.orders }}</strong><span>全部订单</span></button>
        <button @click="router.push('/orders?filter=making')"><strong>{{ summary.pending }}</strong><span>进行中</span></button>
        <button @click="router.push('/coupons?filter=1')"><strong>{{ summary.coupons }}</strong><span>可用券</span></button>
      </div>
    </header>

    <section class="service-list">
      <button @click="router.push('/orders')"><van-icon name="orders-o" /><span><b>我的订单</b><small>付款、取消、催单与再来一单</small></span><van-icon name="arrow" /></button>
      <button @click="router.push('/coupons')"><van-icon name="coupon-o" /><span><b>我的优惠券</b><small>查看可用、占用和已使用状态</small></span><van-icon name="arrow" /></button>
      <button @click="router.push('/addresses')"><van-icon name="location-o" /><span><b>收货地址</b><small>外送订单的地址管理</small></span><van-icon name="arrow" /></button>
    </section>

    <p class="environment-note">当前为本地体验环境，支付不会产生真实扣款。</p>

    <button class="logout" @click="doLogout">退出当前账号</button>
  </div>
</template>

<style scoped>
.profile { padding: 25px 20px 0; background: #263c31; color: white; }
.profile-top { display: flex; align-items: center; gap: 14px; }
.avatar { width: 56px; height: 56px; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.35); border-radius: 50%; font: 26px "STSong", serif; }
.profile .eyebrow { margin: 0; color: #d8b892; font-size: 9px; }
.profile h1 { margin: 3px 0; font: 600 23px "STSong", serif; }
.profile-top span { color: rgba(255,255,255,.62); font-size: 11px; }
.profile-summary { display: grid; grid-template-columns: repeat(3, 1fr); margin-top: 23px; padding: 15px 0; border-top: 1px solid rgba(255,255,255,.14); }
.profile-summary button { border: 0; border-right: 1px solid rgba(255,255,255,.1); background: transparent; color: white; text-align: center; cursor: pointer; }
.profile-summary button:last-child { border: 0; }
.profile-summary strong, .profile-summary span { display: block; }
.profile-summary strong { font-size: 19px; }
.profile-summary span { margin-top: 2px; color: rgba(255,255,255,.55); font-size: 9px; }
.service-list { margin: 12px; border: 1px solid rgba(65,49,38,.08); border-radius: 14px; overflow: hidden; background: var(--card); }
.service-list button { display: grid; grid-template-columns: 28px 1fr 18px; align-items: center; width: 100%; padding: 15px; border: 0; border-bottom: 1px solid var(--line); background: transparent; text-align: left; }
.service-list button:last-child { border-bottom: 0; }
.service-list > button > i:first-child { color: var(--brand); font-size: 20px; }
.service-list b, .service-list small { display: block; }
.service-list b { font-size: 14px; }
.service-list small { margin-top: 3px; color: var(--muted); font-size: 10px; }
.service-list > button > i:last-child { color: #b7afa7; }
.environment-note { margin: 18px 12px 0; color: #a39b93; font-size: 10px; text-align: center; }
.logout { display: block; margin: 22px auto; border: 0; background: transparent; color: var(--muted); font-size: 12px; }
</style>
