<script setup>
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api'
import { saveTable, store } from './store'

const route = useRoute()
const router = useRouter()

const params = new URLSearchParams(window.location.search)
const scannedToken = params.get('tableToken')
if (scannedToken && /^[A-Za-z0-9_-]{1,64}$/.test(scannedToken)) {
  saveTable(scannedToken, scannedToken === store.tableToken ? store.tableName : '')
}

const showTabbar = computed(() => route.path !== '/login')
const active = computed(() => {
  if (route.path.startsWith('/orders') || route.path === '/coupons' || route.path === '/addresses') return '/mine'
  return route.path
})

onMounted(async () => {
  try {
    store.shopOpen = Number(await api.shopStatus()) === 1
  } catch {
    store.shopOpen = false
  }
  if (store.token && store.tableToken) {
    try {
      const table = await api.tableInfo(store.tableToken)
      saveTable(store.tableToken, table.name)
    } catch {
      saveTable('', '')
    }
  }
})
</script>

<template>
  <router-view />
  <van-tabbar v-if="showTabbar" :model-value="active" active-color="#b83a2f" fixed safe-area-inset-bottom>
    <van-tabbar-item name="/menu" icon="shop-o" @click="router.push('/menu')">点餐</van-tabbar-item>
    <van-tabbar-item name="/seckill" icon="fire-o" @click="router.push('/seckill')">秒杀</van-tabbar-item>
    <van-tabbar-item name="/cart" icon="cart-o" :badge="store.cartCount || ''" @click="router.push('/cart')">
      购物车
    </van-tabbar-item>
    <van-tabbar-item name="/mine" icon="user-o" @click="router.push('/mine')">我的</van-tabbar-item>
  </van-tabbar>
</template>
