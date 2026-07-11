<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showToast, showConfirmDialog } from 'vant'
import { api } from '../api'
import { store } from '../store'

const router = useRouter()
const items = ref([])
const remark = ref('')
const submitting = ref(false)

const total = computed(() =>
  items.value.reduce((s, c) => s + Number(c.amount) * c.number, 0).toFixed(2))

async function load() {
  items.value = await api.cartList()
  store.cartCount = items.value.reduce((n, c) => n + c.number, 0)
}

async function inc(c) {
  await api.cartAdd({ dishId: c.dishId, setmealId: c.setmealId, dishFlavor: c.dishFlavor })
  load()
}

async function dec(c) {
  await api.cartSub({ dishId: c.dishId, setmealId: c.setmealId, dishFlavor: c.dishFlavor })
  load()
}

async function clean() {
  await showConfirmDialog({ message: '清空购物车?' })
  await api.cartClean()
  load()
}

async function submit() {
  if (!store.tableToken) {
    showToast('请先扫描桌台二维码')
    return
  }
  submitting.value = true
  try {
    const order = await api.submitOrder({
      tableToken: store.tableToken,
      payMethod: 1,
      remark: remark.value
    })
    showToast({ type: 'success', message: `下单成功\n单号 ${order.orderNumber}` })
    remark.value = ''
    store.cartCount = 0
    setTimeout(() => router.push('/mine'), 900)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <van-nav-bar title="购物车">
      <template #right>
        <span v-if="items.length" style="color:#999; font-size:13px" @click="clean">清空</span>
      </template>
    </van-nav-bar>

    <van-empty v-if="items.length === 0" description="购物车空空如也,去点单吧" />

    <template v-else>
      <div class="card" v-for="c in items" :key="c.id">
        <div class="info">
          <div class="name">{{ c.name }}</div>
          <div v-if="c.dishFlavor" class="flavor">{{ c.dishFlavor }}</div>
          <div class="price">￥{{ c.amount }}</div>
        </div>
        <van-stepper :model-value="c.number" min="0" theme="round" button-size="24"
          @plus="inc(c)" @minus="dec(c)" :disable-input="true" />
      </div>

      <div class="card">
        <van-field v-model="remark" label="备注" placeholder="口味偏好、忌口等(选填)" maxlength="50" />
      </div>

      <van-submit-bar :price="Math.round(Number(total) * 100)" button-text="提交订单"
        :loading="submitting" @submit="submit" style="bottom: 50px">
        <template #tip>
          <span v-if="store.tableName">堂食 · 桌台 {{ store.tableName }}</span>
          <span v-else style="color:#d43f2f">未绑定桌台,请先扫码</span>
        </template>
      </van-submit-bar>
    </template>
  </div>
</template>

<style scoped>
.card { display: flex; align-items: center; justify-content: space-between; background: #fff; border-radius: 12px; padding: 12px 14px; margin: 10px; }
.name { font-weight: 600; }
.flavor { color: #999; font-size: 12px; margin-top: 2px; }
</style>
