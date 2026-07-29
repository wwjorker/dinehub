<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { api } from '../api'
import { store } from '../store'

const route = useRoute()
const router = useRouter()
const order = ref(null)
const loading = ref(false)
const actionLoading = ref(false)

const statusMeta = {
  1: { text: '待付款', note: '订单已创建，完成演示支付后进入商家接单流程。' },
  2: { text: '待接单', note: '商家正在查看订单，请稍候。' },
  3: { text: '制作中', note: '后厨正在准备这份餐食。' },
  4: { text: '配送中', note: '餐食已经离店，正在送达。' },
  5: { text: '已完成', note: '这一餐已完成，感谢你的选择。' },
  6: { text: '已取消', note: '订单已结束，优惠券占用已释放。' }
}

const canCancel = computed(() => [1, 2].includes(order.value?.status))
const canRemind = computed(() => [2, 3].includes(order.value?.status))
const canRepeat = computed(() => [5, 6].includes(order.value?.status))

function formatDate(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

async function load() {
  loading.value = true
  try {
    order.value = await api.orderDetail(route.params.id)
  } finally {
    loading.value = false
  }
}

async function runAction(action, successMessage) {
  actionLoading.value = true
  try {
    await action()
    showToast({ type: 'success', message: successMessage })
    await load()
  } finally {
    actionLoading.value = false
  }
}

async function pay() {
  await showConfirmDialog({
    title: '模拟支付',
    message: '这是开发环境演示，不会发起真实扣款。确认将订单标记为已支付吗？'
  })
  await runAction(() => api.demoPay(order.value.id), '模拟支付成功')
}

async function cancel() {
  await showConfirmDialog({ title: '取消订单', message: '确定取消这笔订单吗？' })
  await runAction(() => api.cancelOrder(order.value.id), '订单已取消')
}

async function remind() {
  await runAction(() => api.remindOrder(order.value.id), '已提醒商家')
}

async function repeat() {
  actionLoading.value = true
  try {
    await api.repeatOrder(order.value.id)
    store.cartCount = (order.value.orderDetailList || []).reduce((sum, item) => sum + item.number, 0)
    showToast({ type: 'success', message: '已加入购物车' })
    router.push('/cart')
  } finally {
    actionLoading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page detail-page">
    <header class="detail-hero">
      <button class="hero-back" aria-label="返回" @click="router.back()">‹</button>
      <div class="eyebrow">订单进度</div>
      <h1>{{ statusMeta[order?.status]?.text || '订单详情' }}</h1>
      <p>{{ statusMeta[order?.status]?.note || '正在获取最新状态。' }}</p>
    </header>

    <van-loading v-if="loading" class="page-loading" vertical>正在读取订单</van-loading>
    <template v-else-if="order">
      <section class="section-card">
        <div class="detail-title">
          <h2 class="section-title">本单内容</h2>
          <span>{{ order.fulfillmentType === 2 ? '堂食' : '外送' }}</span>
        </div>
        <div v-for="item in order.orderDetailList" :key="item.id" class="detail-item">
          <div class="product-mark">
            <span>{{ item.name?.slice(0, 1) }}</span>
            <img v-if="item.image" :src="item.image" :alt="item.name" @error="$event.target.remove()" />
          </div>
          <div class="item-copy">
            <strong>{{ item.name }}</strong>
            <small v-if="item.dishFlavor">{{ item.dishFlavor }}</small>
          </div>
          <span>×{{ item.number }}</span>
          <b class="money">¥{{ (Number(item.amount) * item.number).toFixed(2) }}</b>
        </div>
      </section>

      <section class="section-card settlement-card">
        <h2 class="section-title">结算明细</h2>
        <div><span>商品及履约费用</span><span>¥{{ Number(order.originalAmount ?? order.amount).toFixed(2) }}</span></div>
        <div><span>优惠券抵扣</span><span class="discount">-¥{{ Number(order.discountAmount || 0).toFixed(2) }}</span></div>
        <div class="settlement-total"><strong>实付</strong><strong>¥{{ Number(order.amount).toFixed(2) }}</strong></div>
      </section>

      <section class="section-card info-card">
        <h2 class="section-title">订单信息</h2>
        <dl>
          <div><dt>订单编号</dt><dd>{{ order.number }}</dd></div>
          <div><dt>下单时间</dt><dd>{{ formatDate(order.orderTime) }}</dd></div>
          <div v-if="order.fulfillmentType === 2"><dt>桌台</dt><dd>{{ order.tableName || '已绑定桌台' }}</dd></div>
          <template v-else>
            <div><dt>收货人</dt><dd>{{ order.consignee || '—' }}</dd></div>
            <div><dt>联系电话</dt><dd>{{ order.phone || '—' }}</dd></div>
            <div><dt>配送地址</dt><dd>{{ order.address || '—' }}</dd></div>
          </template>
          <div v-if="order.remark"><dt>备注</dt><dd>{{ order.remark }}</dd></div>
          <div v-if="order.cancelReason"><dt>取消原因</dt><dd>{{ order.cancelReason }}</dd></div>
        </dl>
      </section>

      <aside v-if="order.status === 1" class="demo-notice">
        <strong>开发环境支付演示</strong>
        <span>点击下方按钮只推进订单状态，不连接真实支付渠道，也不会产生扣款。</span>
      </aside>

      <div class="action-bar">
        <van-button v-if="canCancel" plain round :loading="actionLoading" @click="cancel">取消订单</van-button>
        <van-button v-if="canRemind" plain round :loading="actionLoading" @click="remind">提醒商家</van-button>
        <van-button v-if="canRepeat" round type="danger" :loading="actionLoading" @click="repeat">再来一单</van-button>
        <van-button v-if="order.status === 1" round type="danger" :loading="actionLoading" @click="pay">
          模拟支付
        </van-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.detail-page { padding-bottom: 132px; }

.detail-hero {
  position: relative;
  padding: 24px 22px 28px 66px;
  background: var(--forest);
  color: #fff;
}

.detail-hero .eyebrow { color: #dfbcae; }
.detail-hero h1 { margin: 7px 0 5px; font-family: "STSong", "Songti SC", serif; font-size: 28px; }
.detail-hero p { margin: 0; color: rgba(255,255,255,.72); font-size: 12px; line-height: 1.6; }

.hero-back {
  position: absolute;
  top: 24px;
  left: 18px;
  width: 34px;
  height: 34px;
  border: 1px solid rgba(255,255,255,.28);
  border-radius: 50%;
  background: transparent;
  color: #fff;
  font-size: 27px;
  line-height: 27px;
}

.page-loading { padding-top: 80px; }
.detail-title { display: flex; justify-content: space-between; align-items: center; }
.detail-title span { color: var(--forest); font-size: 12px; font-weight: 700; }

.detail-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 11px 0;
  border-top: 1px dashed var(--line);
}

.detail-item .product-mark { width: 48px; height: 48px; font-size: 18px; }
.detail-item .product-mark { position: relative; overflow: hidden; }
.detail-item .product-mark img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.item-copy { min-width: 0; }
.item-copy strong, .item-copy small { display: block; }
.item-copy strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.item-copy small, .detail-item > span { margin-top: 4px; color: var(--muted); font-size: 11px; }
.detail-item b { font-size: 12px; }

.settlement-card > div { display: flex; justify-content: space-between; margin-top: 10px; color: var(--muted); font-size: 13px; }
.settlement-card .discount { color: #477657; }
.settlement-card .settlement-total { padding-top: 12px; border-top: 1px solid var(--line); color: var(--ink); font-size: 16px; }
.settlement-total strong:last-child { color: var(--brand); }

dl { margin: 0; }
dl div { display: grid; grid-template-columns: 74px 1fr; gap: 12px; padding: 7px 0; font-size: 12px; line-height: 1.6; }
dt { color: var(--muted); }
dd { margin: 0; text-align: right; overflow-wrap: anywhere; }

.demo-notice {
  display: grid;
  gap: 4px;
  margin: 12px;
  padding: 13px 15px;
  border-left: 3px solid #c18a36;
  background: #fff6df;
  color: #72501d;
  font-size: 11px;
  line-height: 1.55;
}

.action-bar {
  position: fixed;
  z-index: 4;
  left: 50%;
  bottom: 50px;
  width: min(520px, 100%);
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  padding: 11px 14px;
  transform: translateX(-50%);
  border-top: 1px solid var(--line);
  background: rgba(255,253,249,.96);
}

.action-bar .van-button { min-width: 104px; }
</style>
