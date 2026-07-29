<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'

const router = useRouter()
const route = useRoute()
const loading = ref(false)
const orders = ref([])
const page = ref(1)
const total = ref(0)
const pageSize = 20
const validFilters = ['all', 'pay', 'making', 'done', 'cancelled']
const activeFilter = ref(validFilters.includes(route.query.filter) ? route.query.filter : 'all')

const filters = [
  { key: 'all', label: '全部' },
  { key: 'pay', label: '待付款' },
  { key: 'making', label: '进行中' },
  { key: 'done', label: '已完成' },
  { key: 'cancelled', label: '已取消' }
]

const statusMeta = {
  1: { text: '待付款', tone: 'warn' },
  2: { text: '待接单', tone: 'brand' },
  3: { text: '制作中', tone: 'brand' },
  4: { text: '配送中', tone: 'brand' },
  5: { text: '已完成', tone: 'success' },
  6: { text: '已取消', tone: 'muted' }
}

const visibleOrders = computed(() => orders.value.filter((order) => {
  if (activeFilter.value === 'all') return true
  if (activeFilter.value === 'pay') return order.status === 1
  if (activeFilter.value === 'making') return [2, 3, 4].includes(order.status)
  if (activeFilter.value === 'done') return order.status === 5
  return order.status === 6
}))

function formatDate(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

function dishSummary(order) {
  if (order.orderDishes) return order.orderDishes
  return (order.orderDetailList || [])
    .map((item) => `${item.name} ×${item.number}`)
    .join('、')
}

async function load(reset = true) {
  loading.value = true
  try {
    if (reset) {
      page.value = 1
      orders.value = []
    }
    const result = await api.historyOrders(page.value, pageSize)
    orders.value.push(...(result?.records || []))
    total.value = Number(result?.total || 0)
  } finally {
    loading.value = false
  }
}

async function loadMore() {
  page.value += 1
  await load(false)
}

onMounted(load)
</script>

<template>
  <div class="page orders-page">
    <header class="page-head orders-head">
      <button class="back-button" aria-label="返回" @click="router.back()">‹</button>
      <div>
        <div class="eyebrow">消费记录</div>
        <h1 class="page-title">我的订单</h1>
        <p class="page-subtitle">从下单、接单到完成，每一步都有记录。</p>
      </div>
    </header>

    <nav class="order-filters" aria-label="订单筛选">
      <button
        v-for="filter in filters"
        :key="filter.key"
        :class="{ active: activeFilter === filter.key }"
        @click="activeFilter = filter.key"
      >
        {{ filter.label }}
      </button>
    </nav>

    <van-loading v-if="loading" class="page-loading" vertical>正在整理订单</van-loading>
    <van-empty v-else-if="!visibleOrders.length" description="这个分类下还没有订单">
      <van-button round type="danger" @click="router.push('/menu')">去点餐</van-button>
    </van-empty>

    <main v-else class="order-list">
      <article
        v-for="order in visibleOrders"
        :key="order.id"
        class="order-card"
        tabindex="0"
        @click="router.push(`/orders/${order.id}`)"
        @keydown.enter="router.push(`/orders/${order.id}`)"
      >
        <div class="order-card-top">
          <div>
            <span class="order-number">订单 {{ order.number }}</span>
            <time>{{ formatDate(order.orderTime) }}</time>
          </div>
          <span class="status-pill" :class="statusMeta[order.status]?.tone">
            {{ statusMeta[order.status]?.text || '处理中' }}
          </span>
        </div>
        <p class="order-dishes">{{ dishSummary(order) || '查看订单明细' }}</p>
        <div class="order-card-bottom">
          <span>{{ order.fulfillmentType === 2 ? '堂食' : '外送' }}</span>
          <strong class="money">¥{{ Number(order.amount || 0).toFixed(2) }}</strong>
        </div>
      </article>
      <van-button
        v-if="orders.length < total"
        class="load-more"
        block
        plain
        round
        :loading="loading"
        @click="loadMore"
      >加载更多订单</van-button>
    </main>
  </div>
</template>

<style scoped>
.orders-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}

.back-button {
  width: 34px;
  height: 34px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: transparent;
  color: var(--ink);
  font-size: 28px;
  line-height: 28px;
}

.order-filters {
  display: flex;
  gap: 7px;
  padding: 12px;
  overflow-x: auto;
  scrollbar-width: none;
}

.order-filters button {
  flex: none;
  padding: 7px 13px;
  border: 1px solid var(--line);
  border-radius: 999px;
  background: var(--card);
  color: var(--muted);
  font-size: 12px;
}

.order-filters button.active {
  border-color: var(--forest);
  background: var(--forest);
  color: #fff;
}

.page-loading { padding-top: 80px; }
.order-list { padding: 0 12px 18px; }

.order-card {
  margin-bottom: 11px;
  padding: 16px;
  border: 1px solid rgba(68, 54, 43, 0.08);
  border-radius: 14px;
  background: var(--card);
  box-shadow: 0 7px 24px rgba(67, 49, 36, 0.04);
}

.order-card-top,
.order-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.order-number {
  display: block;
  margin-bottom: 4px;
  font-size: 13px;
  font-weight: 700;
}

time,
.order-card-bottom span {
  color: var(--muted);
  font-size: 11px;
}

.status-pill {
  padding: 4px 8px;
  border-radius: 999px;
  background: var(--brand-soft);
  color: var(--brand-dark);
  font-size: 11px;
  font-weight: 700;
}

.status-pill.warn { background: #fff0cf; color: #8b5e16; }
.status-pill.success { background: #e4f1e8; color: #336847; }
.status-pill.muted { background: #eeeae5; color: #77716a; }

.order-dishes {
  margin: 16px 0;
  color: #514b45;
  font-size: 13px;
  line-height: 1.7;
}

.order-card-bottom strong { color: var(--brand); }
</style>
