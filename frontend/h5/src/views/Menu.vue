<script setup>
import { onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { api } from '../api'
import { store } from '../store'

const categories = ref([])
const activeCat = ref(0)
const dishes = ref([])
const loading = ref(false)

// 口味选择弹层
const flavorShow = ref(false)
const flavorDish = ref(null)
const picked = ref({})

const palette = ['#d43f2f', '#c07a2d', '#4f7a4f', '#3d6a8a', '#7a4f8a', '#8a5a3d']
const colorOf = (name) => palette[(name || '').charCodeAt(0) % palette.length]

async function loadDishes(categoryId) {
  loading.value = true
  try {
    dishes.value = await api.dishes(categoryId)
  } finally {
    loading.value = false
  }
}

async function refreshCartCount() {
  const list = await api.cartList()
  store.cartCount = list.reduce((n, c) => n + c.number, 0)
}

function addToCart(dish) {
  const flavors = dish.flavors || []
  if (flavors.length > 0) {
    flavorDish.value = dish
    picked.value = {}
    flavorShow.value = true
    return
  }
  doAdd(dish, '')
}

function confirmFlavor() {
  const names = (flavorDish.value.flavors || []).map(f => f.name)
  if (names.some(n => !picked.value[n])) {
    showToast('还有口味没选哦')
    return
  }
  doAdd(flavorDish.value, Object.values(picked.value).join(','))
  flavorShow.value = false
}

async function doAdd(dish, dishFlavor) {
  await api.cartAdd({ dishId: dish.id, dishFlavor })
  showToast({ message: '已加入购物车', duration: 800 })
  refreshCartCount()
}

onMounted(async () => {
  categories.value = await api.categories()
  if (categories.value.length > 0) {
    activeCat.value = 0
    loadDishes(categories.value[0].id)
  }
  refreshCartCount()
})
</script>

<template>
  <div class="page">
    <van-nav-bar :title="store.tableName ? `桌台 ${store.tableName} · 堂食点单` : '食汇点单'" />
    <van-notice-bar v-if="!store.tableToken" left-icon="info-o"
      text="当前未绑定桌台,请扫描桌台二维码进入堂食点单" />

    <div class="layout">
      <van-sidebar v-model="activeCat" class="side">
        <van-sidebar-item v-for="c in categories" :key="c.id" :title="c.name"
          @click="loadDishes(c.id)" />
      </van-sidebar>

      <div class="list">
        <van-loading v-if="loading" style="padding: 40px" vertical>加载中</van-loading>
        <van-empty v-else-if="dishes.length === 0" description="该分类暂无菜品" />
        <div v-else class="dish" v-for="d in dishes" :key="d.id">
          <div class="dish-avatar" :style="{ background: colorOf(d.name) }">{{ d.name[0] }}</div>
          <div class="info">
            <div class="name">{{ d.name }}</div>
            <div class="desc">{{ d.description }}</div>
            <div class="price">￥{{ d.price }}</div>
          </div>
          <van-button size="small" round type="danger" icon="plus" @click="addToCart(d)" />
        </div>
      </div>
    </div>

    <van-popup v-model:show="flavorShow" round position="bottom" class="flavor-pop">
      <div v-if="flavorDish" style="padding: 20px 16px 28px">
        <h3 style="margin: 0 0 4px">{{ flavorDish.name }}</h3>
        <div v-for="f in flavorDish.flavors" :key="f.name" style="margin-top: 12px">
          <div style="color:#666; font-size: 13px; margin-bottom: 8px">{{ f.name }}</div>
          <van-tag v-for="v in JSON.parse(f.value)" :key="v" round size="large"
            :type="picked[f.name] === v ? 'danger' : 'default'"
            :plain="picked[f.name] !== v"
            style="margin: 0 8px 8px 0; padding: 6px 14px"
            @click="picked[f.name] = v">{{ v }}</van-tag>
        </div>
        <van-button type="danger" block round style="margin-top: 16px" @click="confirmFlavor">
          加入购物车
        </van-button>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.layout { display: flex; }
.side { width: 92px; flex: none; }
.list { flex: 1; padding: 10px; }
.dish { display: flex; gap: 10px; align-items: center; background: #fff; border-radius: 12px; padding: 12px; margin-bottom: 10px; }
.info { flex: 1; min-width: 0; }
.name { font-weight: 600; }
.desc { color: #999; font-size: 12px; margin: 3px 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
