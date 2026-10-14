<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { api } from '../api'
import { checkoutRequestId, completeCheckout, reconcileCheckoutAddress, store } from '../store'

const router = useRouter()
const items = ref([])
const coupons = ref([])
const addresses = ref([])
const remark = ref('')
const fulfillment = computed({
  get: () => store.checkoutFulfillment || (store.tableToken ? 'dineIn' : 'delivery'),
  set: (value) => { store.checkoutFulfillment = value }
})
const selectedAddressId = computed({
  get: () => store.checkoutAddressId,
  set: (value) => { store.checkoutAddressId = value }
})
const selectedCouponId = ref(null)
const couponSheet = ref(false)
const addressSheet = ref(false)
const submitting = ref(false)

const itemCount = computed(() => items.value.reduce((sum, item) => sum + item.number, 0))
const subtotal = computed(() => items.value.reduce(
  (sum, item) => sum + Number(item.amount) * item.number, 0))
const packFee = computed(() => fulfillment.value === 'delivery' ? itemCount.value : 0)
const deliveryFee = computed(() => fulfillment.value === 'delivery' ? 6 : 0)
const originalAmount = computed(() => subtotal.value + packFee.value + deliveryFee.value)
const selectedCoupon = computed(() =>
  coupons.value.find((coupon) => coupon.id === selectedCouponId.value))
const discount = computed(() => {
  const coupon = selectedCoupon.value
  if (!coupon || coupon.status !== 1 || originalAmount.value * 100 < coupon.minAmount) return 0
  return Math.min(originalAmount.value, coupon.discountAmount / 100)
})
const payable = computed(() => Math.max(0, originalAmount.value - discount.value))
const availableCoupons = computed(() => coupons.value.filter((coupon) => {
  const notExpired = !coupon.seckillEndTime || new Date(coupon.seckillEndTime).getTime() > Date.now()
  return coupon.status === 1 && notExpired
}))
const selectedAddress = computed(() =>
  addresses.value.find((address) => address.id === selectedAddressId.value))

async function load() {
  const [cart, couponList, addressList] = await Promise.all([
    api.cartList(),
    api.myCoupons(),
    api.addressList()
  ])
  items.value = cart
  coupons.value = couponList
  addresses.value = addressList
  store.cartCount = itemCount.value
  if (reconcileCheckoutAddress(addressList) && fulfillment.value === 'delivery') {
    showToast('原收货地址已失效，请重新选择')
  }
  if (selectedCouponId.value && !availableCoupons.value.some((coupon) => coupon.id === selectedCouponId.value)) {
    selectedCouponId.value = null
  }
}

async function changeQuantity(item, direction) {
  const method = direction > 0 ? api.cartAdd : api.cartSub
  await method({ dishId: item.dishId, setmealId: item.setmealId, dishFlavor: item.dishFlavor })
  await load()
}

async function clean() {
  await showConfirmDialog({ title: '清空购物车', message: '已选菜品会全部移除，确定继续吗？' })
  await api.cartClean()
  completeCheckout()
  await load()
}

function chooseCoupon(coupon) {
  if (originalAmount.value * 100 < coupon.minAmount) {
    return showToast(`还差 ¥${((coupon.minAmount - originalAmount.value * 100) / 100).toFixed(2)} 可用`)
  }
  selectedCouponId.value = coupon.id
  couponSheet.value = false
}

function chooseAddress(address) {
  selectedAddressId.value = address.id
  store.checkoutAddressInvalid = false
  addressSheet.value = false
}

function addressText(address) {
  return [address.provinceName, address.cityName, address.districtName, address.detail]
    .filter(Boolean)
    .join(' ')
}

async function submit() {
  if (!items.value.length) return
  if (fulfillment.value === 'dineIn' && !store.tableToken) return showToast('请先扫描桌台二维码')
  if (fulfillment.value === 'delivery' && !selectedAddressId.value) return showToast('请先添加并选择收货地址')

  submitting.value = true
  try {
    const payload = {
      requestId: checkoutRequestId(),
      userCouponId: selectedCouponId.value,
      payMethod: 1,
      remark: remark.value.trim(),
      deliveryStatus: 1,
      tablewareStatus: 1
    }
    if (fulfillment.value === 'dineIn') payload.tableToken = store.tableToken
    else payload.addressBookId = selectedAddressId.value

    const order = await api.submitOrder(payload)
    completeCheckout()
    store.cartCount = 0
    showToast({ type: 'success', message: '订单已提交' })
    router.replace(`/orders/${order.id}`)
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page checkout-page">
    <div class="page-head checkout-head">
      <div>
        <div class="eyebrow">CHECKOUT</div>
        <h1 class="page-title">确认这份菜单</h1>
      </div>
      <button v-if="items.length" class="text-button" @click="clean">清空</button>
    </div>

    <van-empty v-if="!items.length" description="购物车还是空的">
      <van-button class="empty-action" round type="danger" @click="router.push('/menu')">去看看菜单</van-button>
    </van-empty>

    <template v-else>
      <section class="mode-switch section-card">
        <button :class="{ active: fulfillment === 'dineIn' }" :disabled="!store.tableToken"
          @click="fulfillment = 'dineIn'">
          <strong>堂食</strong>
          <span>{{ store.tableName ? `${store.tableName} 桌` : '需扫描桌台码' }}</span>
        </button>
        <button :class="{ active: fulfillment === 'delivery' }" @click="fulfillment = 'delivery'">
          <strong>外送</strong>
          <span>预计 30–45 分钟</span>
        </button>
      </section>

      <section v-if="fulfillment === 'delivery'" class="section-card address-card"
        @click="addressSheet = true">
        <template v-if="selectedAddress">
          <div><strong>{{ selectedAddress.consignee }}</strong><span>{{ selectedAddress.phone }}</span></div>
          <p>{{ addressText(selectedAddress) }}</p>
        </template>
        <template v-else>
          <strong>选择收货地址</strong><p>{{ store.checkoutAddressInvalid ? '原收货地址已失效，请重新选择' : '外送订单需要一个有效地址' }}</p>
        </template>
        <van-icon name="arrow" />
      </section>

      <section class="section-card cart-list">
        <h2 class="section-title">已选菜品 <small>{{ itemCount }} 份</small></h2>
        <article v-for="item in items" :key="item.id" class="cart-item">
          <div class="product-mark">
            <span>{{ (item.name || '食').slice(0, 1) }}</span>
            <img v-if="item.image" :src="item.image" :alt="item.name" @error="$event.target.remove()" />
          </div>
          <div class="cart-info">
            <strong>{{ item.name }}</strong>
            <small v-if="item.dishFlavor">{{ item.dishFlavor }}</small>
            <span class="price money">¥{{ Number(item.amount).toFixed(2) }}</span>
          </div>
          <div class="quantity">
            <button @click="changeQuantity(item, -1)">−</button>
            <span>{{ item.number }}</span>
            <button @click="changeQuantity(item, 1)">＋</button>
          </div>
        </article>
      </section>

      <section class="section-card checkout-options">
        <button class="option-row" @click="couponSheet = true">
          <span>优惠券</span>
          <span :class="{ highlight: selectedCoupon }">
            {{ selectedCoupon ? `-${discount.toFixed(2)} 元` : `${availableCoupons.length} 张可用` }}
            <van-icon name="arrow" />
          </span>
        </button>
        <label class="remark-row">
          <span>订单备注</span>
          <input v-model="remark" maxlength="50" placeholder="少辣、不要香菜等" />
        </label>
      </section>

      <section class="section-card settlement">
        <div><span>菜品小计</span><span>¥{{ subtotal.toFixed(2) }}</span></div>
        <div v-if="fulfillment === 'delivery'"><span>餐盒费</span><span>¥{{ packFee.toFixed(2) }}</span></div>
        <div v-if="fulfillment === 'delivery'"><span>配送费</span><span>¥{{ deliveryFee.toFixed(2) }}</span></div>
        <div v-if="discount"><span>优惠券</span><span class="price">−¥{{ discount.toFixed(2) }}</span></div>
        <div class="total"><span>应付</span><strong class="price">¥{{ payable.toFixed(2) }}</strong></div>
        <p>价格由服务端重新计算，以订单结果为准。</p>
      </section>

      <div class="checkout-bar">
        <div><small>合计</small><strong>¥{{ payable.toFixed(2) }}</strong></div>
        <button :disabled="submitting || !store.shopOpen" @click="submit">
          {{ !store.shopOpen ? '门店已打烊' : submitting ? '正在提交…' : '提交订单' }}
        </button>
      </div>
    </template>

    <van-action-sheet v-model:show="couponSheet" title="选择优惠券">
      <div class="coupon-sheet">
        <button class="no-coupon" @click="selectedCouponId = null; couponSheet = false">本单不使用优惠券</button>
        <button v-for="coupon in availableCoupons" :key="coupon.id" class="coupon-option"
          :class="{ disabled: originalAmount * 100 < coupon.minAmount }" @click="chooseCoupon(coupon)">
          <strong>¥{{ (coupon.discountAmount / 100).toFixed(0) }}</strong>
          <span><b>{{ coupon.title }}</b><small>满 ¥{{ (coupon.minAmount / 100).toFixed(0) }} 可用</small></span>
          <van-icon name="success" v-if="selectedCouponId === coupon.id" />
        </button>
      </div>
    </van-action-sheet>

    <van-action-sheet v-model:show="addressSheet" title="选择收货地址">
      <div class="address-sheet">
        <button
          v-for="address in addresses"
          :key="address.id"
          class="address-option"
          :class="{ selected: selectedAddressId === address.id }"
          @click="chooseAddress(address)"
        >
          <span><b>{{ address.consignee }} · {{ address.phone }}</b><small>{{ addressText(address) }}</small></span>
          <van-icon v-if="selectedAddressId === address.id" name="success" />
        </button>
        <button class="manage-address" @click="addressSheet = false; router.push('/addresses')">
          ＋ 新增或管理地址
        </button>
      </div>
    </van-action-sheet>
  </div>
</template>

<style scoped>
.checkout-page { padding-bottom: 145px; }
.checkout-head { display: flex; align-items: end; justify-content: space-between; }
.text-button { border: 0; background: transparent; color: var(--muted); cursor: pointer; }
.mode-switch { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; padding: 7px; }
.mode-switch button { padding: 11px; border: 1px solid transparent; border-radius: 10px; background: transparent; text-align: left; color: var(--muted); }
.mode-switch button.active { border-color: #d9b6ae; background: var(--brand-soft); color: var(--brand-dark); }
.mode-switch button:disabled { opacity: .42; }
.mode-switch strong, .mode-switch span { display: block; }
.mode-switch span { margin-top: 3px; font-size: 10px; }
.address-card { position: relative; padding-right: 40px; cursor: pointer; }
.address-card div span { margin-left: 10px; color: var(--muted); font-size: 12px; }
.address-card p { margin: 6px 0 0; color: var(--muted); font-size: 12px; line-height: 1.5; }
.address-card > i { position: absolute; right: 16px; top: 50%; transform: translateY(-50%); }
.section-title small { margin-left: 5px; color: var(--muted); font-size: 11px; font-weight: 400; }
.cart-item { display: flex; align-items: center; gap: 11px; padding: 11px 0; border-bottom: 1px solid var(--line); }
.cart-item:last-child { border-bottom: 0; }
.cart-item .product-mark { width: 48px; height: 48px; font-size: 18px; }
.cart-item .product-mark { position: relative; overflow: hidden; }
.cart-item .product-mark img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; }
.cart-info { flex: 1; min-width: 0; }
.cart-info strong, .cart-info small, .cart-info span { display: block; }
.cart-info small { margin: 2px 0; color: var(--muted); font-size: 10px; }
.cart-info span { font-size: 13px; }
.quantity { display: flex; align-items: center; gap: 9px; }
.quantity button { width: 25px; height: 25px; padding: 0; border: 1px solid var(--line); border-radius: 50%; background: white; color: var(--brand); }
.quantity span { min-width: 14px; text-align: center; font-size: 13px; }
.checkout-options { padding-top: 6px; padding-bottom: 6px; }
.option-row, .remark-row { display: flex; width: 100%; align-items: center; justify-content: space-between; padding: 12px 0; border: 0; border-bottom: 1px solid var(--line); background: transparent; }
.option-row > span:last-child { color: var(--muted); font-size: 12px; }
.option-row .highlight { color: var(--brand) !important; }
.remark-row { border-bottom: 0; font-size: 13px; }
.remark-row input { width: 70%; border: 0; outline: 0; background: transparent; text-align: right; color: var(--muted); }
.settlement > div { display: flex; justify-content: space-between; margin: 9px 0; color: var(--muted); font-size: 12px; }
.settlement .total { margin-top: 13px; padding-top: 12px; border-top: 1px solid var(--line); color: var(--ink); }
.settlement .total strong { font-size: 20px; }
.settlement p { margin: 8px 0 0; color: #aaa198; font-size: 10px; text-align: right; }
.checkout-bar { position: fixed; z-index: 4; bottom: 50px; left: 50%; transform: translateX(-50%); width: min(100%, 520px); display: flex; align-items: center; justify-content: space-between; padding: 11px 15px; background: var(--card); border-top: 1px solid var(--line); }
.checkout-bar small { display: block; color: var(--muted); font-size: 10px; }
.checkout-bar strong { color: var(--brand); font-size: 22px; }
.checkout-bar button { min-width: 145px; padding: 12px 19px; border: 0; border-radius: 9px; background: var(--brand); color: white; font-weight: 700; }
.checkout-bar button:disabled { background: #b9b2ab; }
.coupon-sheet { padding: 0 14px 26px; }
.coupon-sheet button { width: 100%; border: 0; background: transparent; }
.no-coupon { padding: 13px; border-bottom: 1px solid var(--line) !important; color: var(--muted); text-align: left; }
.coupon-option { display: grid; grid-template-columns: 64px 1fr 24px; align-items: center; padding: 14px 2px; border-bottom: 1px solid var(--line) !important; text-align: left; }
.coupon-option > strong { color: var(--brand); font-size: 24px; }
.coupon-option span b, .coupon-option span small { display: block; }
.coupon-option span small { margin-top: 3px; color: var(--muted); font-size: 10px; }
.coupon-option.disabled { opacity: .45; }
.address-sheet { padding: 0 14px 28px; }
.address-option { display: grid; grid-template-columns: 1fr 24px; gap: 12px; align-items: center; width: 100%; padding: 14px 3px; border: 0; border-bottom: 1px solid var(--line); background: transparent; text-align: left; }
.address-option b, .address-option small { display: block; }
.address-option small { margin-top: 5px; color: var(--muted); font-size: 11px; line-height: 1.55; }
.address-option.selected b, .address-option.selected > i { color: var(--brand); }
.manage-address { width: 100%; margin-top: 12px; padding: 12px; border: 1px dashed var(--line); border-radius: 9px; background: transparent; color: var(--brand); }
</style>
