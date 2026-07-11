<script setup>
import { onMounted, reactive, ref } from 'vue'

// 极简运营台:登录、桌台与二维码、秒杀券发布、订单一览。
// 面向演示的最小闭环,完整管理能力见接口文档 /doc.html

const token = ref(localStorage.getItem('dinehub.ops.token') || '')
const login = reactive({ username: 'admin', password: '' })
const msg = ref('')

const tables = ref([])
const newTable = ref('')
const qr = reactive({ show: false, name: '', url: '' })

const coupons = ref([])
const newCoupon = reactive({ title: '', discountAmount: 10, minAmount: 30, totalStock: 100, minutes: 60 })

const orders = ref([])

function toast(t) {
  msg.value = t
  setTimeout(() => { if (msg.value === t) msg.value = '' }, 2500)
}

async function req(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', token: token.value, ...options.headers }
  })
  if (res.status === 401) { token.value = ''; localStorage.removeItem('dinehub.ops.token'); throw new Error('登录过期') }
  const body = await res.json()
  if (body.code !== 1) { toast(body.msg || '操作失败'); throw new Error(body.msg) }
  return body.data
}

async function doLogin() {
  const data = await req('/admin/employee/login', {
    method: 'POST', body: JSON.stringify(login), headers: { 'Content-Type': 'application/json' }
  })
  token.value = data.token
  localStorage.setItem('dinehub.ops.token', data.token)
  refresh()
}

async function refresh() {
  tables.value = (await req('/admin/table/page?page=1&pageSize=50')).records || []
  coupons.value = (await req('/admin/coupon/page?page=1&pageSize=50')).records || []
  orders.value = (await req('/admin/order/conditionSearch?page=1&pageSize=15')).records || []
}

async function addTable() {
  if (!newTable.value.trim()) return
  await req('/admin/table', { method: 'POST', body: JSON.stringify({ name: newTable.value.trim() }) })
  newTable.value = ''
  toast('桌台已创建')
  refresh()
}

// 二维码接口在管理端鉴权之后,img 标签带不上 token,用 fetch 转 blob 展示
async function showQr(t) {
  const res = await fetch(`/admin/table/qrcode/${t.id}`, { headers: { token: token.value } })
  qr.url = URL.createObjectURL(await res.blob())
  qr.name = t.name
  qr.show = true
}

async function addCoupon() {
  if (!newCoupon.title.trim()) { toast('填写券标题'); return }
  const now = new Date()
  const fmt = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:00`
  await req('/admin/coupon', {
    method: 'POST',
    body: JSON.stringify({
      title: newCoupon.title.trim(),
      type: 2,
      discountAmount: Math.round(newCoupon.discountAmount * 100),
      minAmount: Math.round(newCoupon.minAmount * 100),
      totalStock: newCoupon.totalStock,
      seckillStartTime: fmt(now),
      seckillEndTime: fmt(new Date(now.getTime() + newCoupon.minutes * 60000))
    })
  })
  toast('秒杀券已创建,记得发布预热')
  refresh()
}

async function publish(c) {
  await req(`/admin/coupon/${c.id}/publish`, { method: 'POST' })
  toast('已发布并预热库存')
  refresh()
}

const orderStatus = { 1: '待付款', 2: '待接单', 3: '已接单', 4: '派送中', 5: '已完成', 6: '已取消' }

onMounted(() => { if (token.value) refresh().catch(() => {}) })
</script>

<template>
  <div class="wrap">
    <header>
      <h1>食汇 · 运营台</h1>
      <span class="hint">演示用最小闭环 · 完整能力见 <a href="/doc.html" target="_blank">/doc.html</a></span>
    </header>

    <div v-if="msg" class="toast">{{ msg }}</div>

    <section v-if="!token" class="card login">
      <h2>商家登录</h2>
      <input v-model="login.username" placeholder="账号" />
      <input v-model="login.password" type="password" placeholder="密码" @keyup.enter="doLogin" />
      <button class="primary" @click="doLogin">登录</button>
    </section>

    <template v-else>
      <section class="card">
        <h2>桌台与扫码点单</h2>
        <div class="row">
          <input v-model="newTable" placeholder="桌台名称,如 A01" @keyup.enter="addTable" />
          <button class="primary" @click="addTable">新增桌台</button>
        </div>
        <table>
          <tr><th>桌台</th><th>状态</th><th>二维码</th></tr>
          <tr v-for="t in tables" :key="t.id">
            <td>{{ t.name }}</td>
            <td>{{ t.status === 1 ? '启用' : '停用' }}</td>
            <td><button @click="showQr(t)">查看二维码</button></td>
          </tr>
        </table>
      </section>

      <section class="card">
        <h2>限时秒杀券</h2>
        <div class="row">
          <input v-model="newCoupon.title" placeholder="标题,如 午市立减10元" style="flex:2" />
          <label>减<input v-model.number="newCoupon.discountAmount" type="number" class="num" />元</label>
          <label>满<input v-model.number="newCoupon.minAmount" type="number" class="num" />元</label>
          <label>库存<input v-model.number="newCoupon.totalStock" type="number" class="num" /></label>
          <label>时长<input v-model.number="newCoupon.minutes" type="number" class="num" />分钟</label>
          <button class="primary" @click="addCoupon">创建</button>
        </div>
        <table>
          <tr><th>标题</th><th>面额</th><th>库存(剩/总)</th><th>状态</th><th>操作</th></tr>
          <tr v-for="c in coupons" :key="c.id">
            <td>{{ c.title }}</td>
            <td>满{{ c.minAmount / 100 }}减{{ c.discountAmount / 100 }}</td>
            <td>{{ c.remainingStock }} / {{ c.totalStock }}</td>
            <td>{{ c.status === 1 ? '已发布' : '未发布' }}</td>
            <td><button v-if="c.status === 0" class="primary" @click="publish(c)">发布预热</button></td>
          </tr>
        </table>
      </section>

      <section class="card">
        <h2>最新订单</h2>
        <table>
          <tr><th>单号</th><th>履约</th><th>金额</th><th>状态</th><th>时间</th></tr>
          <tr v-for="o in orders" :key="o.id">
            <td class="mono">{{ o.number }}</td>
            <td>{{ o.fulfillmentType === 2 ? `堂食·${o.address}` : '外卖' }}</td>
            <td>￥{{ o.amount }}</td>
            <td>{{ orderStatus[o.status] }}</td>
            <td>{{ (o.orderTime || '').replace('T', ' ') }}</td>
          </tr>
        </table>
      </section>
    </template>

    <div v-if="qr.show" class="mask" @click="qr.show = false">
      <div class="qrbox">
        <img :src="qr.url" alt="qr" />
        <div>桌台 {{ qr.name }} · 手机扫码点单</div>
      </div>
    </div>
  </div>
</template>

<style>
* { box-sizing: border-box; }
body { margin: 0; background: #f3f0ec; font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif; color: #2b2018; }
.wrap { max-width: 960px; margin: 0 auto; padding: 24px 16px 60px; }
header { display: flex; align-items: baseline; gap: 14px; margin-bottom: 18px; }
h1 { font-size: 22px; margin: 0; }
.hint { color: #999; font-size: 13px; }
.hint a { color: #d43f2f; }
.card { background: #fff; border-radius: 12px; padding: 18px 20px; margin-bottom: 18px; box-shadow: 0 2px 10px rgba(0,0,0,0.04); }
.card h2 { font-size: 16px; margin: 0 0 14px; }
.login { max-width: 380px; margin: 80px auto; display: flex; flex-direction: column; gap: 12px; }
input { border: 1px solid #ddd; border-radius: 8px; padding: 9px 12px; font-size: 14px; }
.num { width: 64px; margin: 0 4px; }
label { color: #666; font-size: 13px; white-space: nowrap; }
button { border: 1px solid #ddd; background: #fff; border-radius: 8px; padding: 8px 14px; cursor: pointer; font-size: 13px; }
button.primary { background: #d43f2f; border-color: #d43f2f; color: #fff; }
.row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 14px; }
.row input:first-child { flex: 1; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { text-align: left; color: #999; font-weight: 500; padding: 6px 8px; border-bottom: 1px solid #eee; }
td { padding: 8px; border-bottom: 1px solid #f5f5f5; }
.mono { font-family: Consolas, monospace; font-size: 12px; }
.toast { position: fixed; top: 18px; left: 50%; transform: translateX(-50%); background: #2b2018; color: #fff; padding: 9px 18px; border-radius: 20px; font-size: 13px; z-index: 10; }
.mask { position: fixed; inset: 0; background: rgba(0,0,0,0.5); display: flex; align-items: center; justify-content: center; z-index: 9; }
.qrbox { background: #fff; border-radius: 14px; padding: 26px; text-align: center; font-size: 14px; }
.qrbox img { width: 240px; height: 240px; }
</style>
