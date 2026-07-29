<script setup>
import { computed, onMounted, ref } from 'vue'
import { showToast } from 'vant'
import { api } from '../api'
import { store } from '../store'

const categories = ref([])
const activeCategory = ref(0)
const products = ref([])
const loading = ref(false)
const cartLoading = ref(0)

const flavorShow = ref(false)
const flavorDish = ref(null)
const picked = ref({})
const setmealShow = ref(false)
const setmealDetail = ref(null)

const currentCategory = computed(() => categories.value[activeCategory.value])

function safeFlavorValues(value) {
  try {
    const result = JSON.parse(value)
    return Array.isArray(result) ? result.filter((item) => typeof item === 'string').slice(0, 12) : []
  } catch {
    return []
  }
}

async function loadProducts(index = activeCategory.value) {
  activeCategory.value = index
  const category = categories.value[index]
  if (!category) return
  loading.value = true
  try {
    const list = category.productType === 'setmeal'
      ? await api.setmeals(category.id)
      : await api.dishes(category.id)
    products.value = list.map((item) => ({ ...item, productType: category.productType }))
  } finally {
    loading.value = false
  }
}

async function refreshCartCount() {
  const list = await api.cartList()
  store.cartCount = list.reduce((count, item) => count + item.number, 0)
}

function addDish(dish) {
  if (!store.shopOpen) return showToast('门店已打烊，暂时不能加菜')
  if ((dish.flavors || []).length) {
    flavorDish.value = dish
    picked.value = {}
    flavorShow.value = true
    return
  }
  addToCart({ dishId: dish.id })
}

async function confirmFlavor() {
  const names = (flavorDish.value.flavors || []).map((flavor) => flavor.name)
  if (names.some((name) => !picked.value[name])) return showToast('还有口味没有选择')
  await addToCart({
    dishId: flavorDish.value.id,
    dishFlavor: Object.values(picked.value).join(',')
  })
  flavorShow.value = false
}

async function showSetmeal(item) {
  const dishes = await api.setmealDishes(item.id)
  setmealDetail.value = { ...item, dishes }
  setmealShow.value = true
}

async function addToCart(payload) {
  cartLoading.value = payload.dishId || payload.setmealId
  try {
    await api.cartAdd(payload)
    await refreshCartCount()
    showToast({ message: '已加入购物车', duration: 700 })
  } finally {
    cartLoading.value = 0
  }
}

onMounted(async () => {
  const [dishCategories, setmealCategories] = await Promise.all([
    api.categories(1),
    api.categories(2)
  ])
  try {
    store.shopOpen = Number(await api.shopStatus()) === 1
  } catch {
    store.shopOpen = false
  }
  categories.value = [
    ...dishCategories.map((item) => ({ ...item, productType: 'dish' })),
    ...setmealCategories.map((item) => ({ ...item, productType: 'setmeal' }))
  ]
  await Promise.all([loadProducts(0), refreshCartCount()])
})
</script>

<template>
  <div class="page menu-page">
    <header class="menu-head">
      <div>
        <div class="eyebrow">食汇 · 今日菜单</div>
        <h1>{{ store.tableName ? `${store.tableName} 桌点餐` : '好好吃一顿' }}</h1>
      </div>
      <div class="shop-state">
        <i class="status-dot" :class="{ closed: !store.shopOpen }"></i>
        {{ store.shopOpen ? '营业中' : '已打烊' }}
      </div>
    </header>

    <div v-if="!store.tableToken" class="scan-note">
      <span>尚未绑定桌台</span>
      <small>堂食请扫描桌台二维码；也可在购物车选择外送。</small>
    </div>

    <div class="menu-layout">
      <aside class="category-list" aria-label="菜单分类">
        <button v-for="(category, index) in categories" :key="`${category.productType}-${category.id}`"
          :class="{ active: activeCategory === index }" @click="loadProducts(index)">
          <span>{{ category.name }}</span>
          <small v-if="category.productType === 'setmeal'">套餐</small>
        </button>
      </aside>

      <main class="product-list">
        <div class="category-heading">
          <span>{{ currentCategory?.name || '菜单' }}</span>
          <small>{{ products.length }} 道可选</small>
        </div>
        <van-loading v-if="loading" class="loading" vertical>正在备菜…</van-loading>
        <van-empty v-else-if="!products.length" description="这个分类暂时没有菜品" />
        <article v-for="item in products" v-else :key="`${item.productType}-${item.id}`" class="product">
          <div class="product-mark" :class="{ clickable: item.productType === 'setmeal' }"
            :role="item.productType === 'setmeal' ? 'button' : undefined"
            :tabindex="item.productType === 'setmeal' ? 0 : undefined"
            @click="item.productType === 'setmeal' && showSetmeal(item)"
            @keydown.enter="item.productType === 'setmeal' && showSetmeal(item)">
            <span>{{ (item.name || '食').slice(0, 1) }}</span>
            <img v-if="item.image" :src="item.image" :alt="item.name" @error="$event.target.remove()" />
          </div>
          <div class="product-info">
            <h2>{{ item.name }}</h2>
            <p>{{ item.description || (item.productType === 'setmeal' ? '搭配完整，适合一起分享' : '现点现做') }}</p>
            <div class="product-bottom">
              <span class="price money">¥{{ Number(item.price).toFixed(2) }}</span>
              <button class="add-button" :disabled="!store.shopOpen || cartLoading === item.id"
                :aria-label="`添加${item.name}`"
                @click="item.productType === 'setmeal' ? addToCart({ setmealId: item.id }) : addDish(item)">
                {{ cartLoading === item.id ? '…' : '+' }}
              </button>
            </div>
          </div>
        </article>
      </main>
    </div>

    <van-popup v-model:show="flavorShow" round position="bottom">
      <section v-if="flavorDish" class="option-sheet">
        <p class="eyebrow">口味选择</p>
        <h2>{{ flavorDish.name }}</h2>
        <div v-for="flavor in flavorDish.flavors" :key="flavor.name" class="option-group">
          <label>{{ flavor.name }}</label>
          <div>
            <button v-for="value in safeFlavorValues(flavor.value)" :key="value"
              :class="{ selected: picked[flavor.name] === value }"
              @click="picked[flavor.name] = value">{{ value }}</button>
          </div>
        </div>
        <van-button type="danger" block round @click="confirmFlavor">选好了，加入购物车</van-button>
      </section>
    </van-popup>

    <van-popup v-model:show="setmealShow" round position="bottom">
      <section v-if="setmealDetail" class="option-sheet">
        <p class="eyebrow">套餐内容</p>
        <h2>{{ setmealDetail.name }}</h2>
        <ul class="setmeal-dishes">
          <li v-for="dish in setmealDetail.dishes" :key="dish.name">
            <span>{{ dish.name }}</span><span>× {{ dish.copies || 1 }}</span>
          </li>
        </ul>
        <van-button type="danger" block round @click="addToCart({ setmealId: setmealDetail.id }); setmealShow = false">
          ¥{{ Number(setmealDetail.price).toFixed(2) }} · 加入购物车
        </van-button>
      </section>
    </van-popup>
  </div>
</template>

<style scoped>
.menu-page { background: var(--card); }
.menu-head { display: flex; justify-content: space-between; align-items: flex-end; padding: 20px 18px 16px; border-bottom: 1px solid var(--line); }
.menu-head h1 { margin: 5px 0 0; font: 600 26px/1.2 "STSong", "Songti SC", serif; }
.shop-state { display: flex; align-items: center; gap: 8px; color: var(--muted); font-size: 12px; }
.scan-note { margin: 12px 12px 0; padding: 11px 13px; border-left: 3px solid #c38b3c; background: #fbf3df; }
.scan-note span { display: block; color: #78551e; font-size: 13px; font-weight: 700; }
.scan-note small { display: block; margin-top: 3px; color: #9a7a48; line-height: 1.4; }
.menu-layout { display: grid; grid-template-columns: 92px 1fr; min-height: calc(100vh - 100px); }
.category-list { background: #f0ece6; padding: 9px 0 100px; }
.category-list button { width: 100%; min-height: 55px; padding: 8px 9px; border: 0; border-left: 3px solid transparent; background: transparent; color: #615b54; text-align: left; cursor: pointer; }
.category-list button.active { border-left-color: var(--brand); background: var(--card); color: var(--ink); font-weight: 700; }
.category-list span, .category-list small { display: block; }
.category-list span { overflow: hidden; font-size: 12px; line-height: 1.35; text-overflow: ellipsis; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; }
.category-list small { margin-top: 3px; color: #8e857c; font-size: 10px; font-weight: 400; }
.product-list { padding: 13px 12px 90px; min-width: 0; }
.category-heading { display: flex; justify-content: space-between; align-items: baseline; margin: 0 2px 12px; }
.category-heading span { font-weight: 700; }
.category-heading small { color: #aaa198; }
.loading { padding: 60px 0; }
.product { display: flex; gap: 12px; padding: 13px 0; border-bottom: 1px solid #eee9e3; }
.product-mark { position: relative; overflow: hidden; border: 0; cursor: default; }
.product-mark.clickable { cursor: pointer; }
.product-mark img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.product:nth-of-type(3n+1) .product-mark { background: #e3ece4; color: var(--forest); }
.product:nth-of-type(3n+2) .product-mark { background: #ede4d6; color: #805d32; }
.product-info { flex: 1; min-width: 0; }
.product-info h2 { margin: 1px 0 4px; font-size: 15px; }
.product-info p { min-height: 34px; margin: 0; color: var(--muted); font-size: 12px; line-height: 1.45; }
.product-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 7px; }
.add-button { width: 40px; height: 40px; border: 0; border-radius: 50%; background: var(--brand); color: white; font-size: 22px; line-height: 1; cursor: pointer; }
.add-button:disabled { background: #c8c1ba; }
.option-sheet { padding: 24px 20px 30px; }
.option-sheet h2 { margin: 6px 0 20px; font-family: "STSong", serif; font-size: 24px; }
.option-group { margin: 0 0 18px; }
.option-group label { display: block; margin-bottom: 8px; color: var(--muted); font-size: 12px; }
.option-group button { margin: 0 7px 7px 0; padding: 7px 13px; border: 1px solid var(--line); border-radius: 18px; background: white; color: #544e48; }
.option-group button.selected { border-color: var(--brand); background: var(--brand-soft); color: var(--brand-dark); }
.setmeal-dishes { padding: 0; margin: 0 0 20px; list-style: none; border-top: 1px solid var(--line); }
.setmeal-dishes li { display: flex; justify-content: space-between; padding: 11px 2px; border-bottom: 1px solid var(--line); color: var(--muted); font-size: 13px; }
</style>
