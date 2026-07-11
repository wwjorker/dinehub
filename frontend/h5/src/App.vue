<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { store, saveTable } from './store'
import { api } from './api'

const route = useRoute()
const router = useRouter()

// 扫码入口:二维码内容形如 /h5/index.html?tableToken=xxx,query 落在 hash 之前
const params = new URLSearchParams(location.search)
const scannedToken = params.get('tableToken')
if (scannedToken && scannedToken !== store.tableToken) {
  saveTable(scannedToken, '')
  if (store.token) {
    api.tableInfo(scannedToken).then((t) => saveTable(scannedToken, t.name)).catch(() => {})
  }
}

const showTabbar = computed(() => route.path !== '/login')
const active = computed(() => route.path)
</script>

<template>
  <router-view />
  <van-tabbar v-if="showTabbar" :model-value="active" active-color="#d43f2f" fixed>
    <van-tabbar-item name="/menu" icon="shop-o" @click="router.push('/menu')">点单</van-tabbar-item>
    <van-tabbar-item name="/seckill" icon="fire-o" @click="router.push('/seckill')">秒杀</van-tabbar-item>
    <van-tabbar-item name="/cart" icon="cart-o" :badge="store.cartCount || ''" @click="router.push('/cart')">购物车</van-tabbar-item>
    <van-tabbar-item name="/mine" icon="user-o" @click="router.push('/mine')">我的</van-tabbar-item>
  </van-tabbar>
</template>
