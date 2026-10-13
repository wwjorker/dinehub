<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { clearSession, fetchBlob, getProfile, getToken, opsApi, request, saveSession } from './api'

const token = ref(getToken())
const profile = ref(getProfile())
const login = reactive({ username: 'admin', password: '' })
const loggingIn = ref(false)
const toastMessage = ref('')
const activePanel = ref('dashboard')
const busy = ref(false)
const passwordEditor = reactive({
  open: Boolean(profile.value.mustChangePassword),
  required: Boolean(profile.value.mustChangePassword),
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
let toastTimer
let orderSocket
let reconnectTimer
let socketClosing = false

const navItems = [
  { key: 'dashboard', label: '经营概览', mark: '览' },
  { key: 'orders', label: '订单中枢', mark: '单' },
  { key: 'catalog', label: '菜单管理', mark: '菜' },
  { key: 'tables', label: '桌台点单', mark: '桌' },
  { key: 'coupons', label: '秒杀与卡券', mark: '券' },
  { key: 'employees', label: '员工管理', mark: '员', adminOnly: true },
  { key: 'reports', label: '经营报表', mark: '报' }
]

const panelMeta = computed(() => navItems.find((item) => item.key === activePanel.value) || navItems[0])
const primaryNavItems = computed(() => navItems.filter((item) => !item.adminOnly || profile.value.role === 'ADMIN'))

function toast(message) {
  clearTimeout(toastTimer)
  toastMessage.value = message
  toastTimer = setTimeout(() => { toastMessage.value = '' }, 2600)
}

window.addEventListener('ops-error', (event) => toast(event.detail))
window.addEventListener('ops-auth-expired', handleAuthExpired)
window.addEventListener('ops-password-required', openRequiredPasswordEditor)

function openRequiredPasswordEditor() {
  passwordEditor.open = true
  passwordEditor.required = true
}

function handleAuthExpired() {
  closeOrderSocket()
  token.value = ''
  profile.value = {}
  activePanel.value = 'dashboard'
  toast('登录状态已失效，请重新登录')
}

async function doLogin() {
  if (!login.username.trim() || !login.password) return toast('请输入账号和密码')
  loggingIn.value = true
  try {
    const data = await opsApi.login(login)
    saveSession(data)
    token.value = data.token
    profile.value = getProfile()
    login.password = ''
    if (data.mustChangePassword) {
      openRequiredPasswordEditor()
      toast('首次登录，请先修改临时密码')
      return
    }
    toast('登录成功')
    await loadPanel('dashboard')
    connectOrderSocket()
  } finally {
    loggingIn.value = false
  }
}

async function changeOwnPassword() {
  if (passwordEditor.newPassword.length < 8) return toast('新密码至少8位')
  if (passwordEditor.newPassword !== passwordEditor.confirmPassword) return toast('两次输入的新密码不一致')
  await opsApi.changePassword({
    oldPassword: passwordEditor.oldPassword,
    newPassword: passwordEditor.newPassword,
    confirmPassword: passwordEditor.confirmPassword
  })
  passwordEditor.open = false
  passwordEditor.required = false
  passwordEditor.oldPassword = ''
  passwordEditor.newPassword = ''
  passwordEditor.confirmPassword = ''
  profile.value = { ...profile.value, mustChangePassword: false }
  sessionStorage.setItem('dinehub.ops.profile', JSON.stringify(profile.value))
  toast('密码已更新')
  await loadPanel('dashboard')
  connectOrderSocket()
}

async function logout() {
  try {
    await opsApi.logout()
  } catch {
    // Local session still needs to be cleared if the server session is already invalid.
  }
  closeOrderSocket()
  clearSession()
  token.value = ''
  profile.value = {}
}

async function switchPanel(key) {
  activePanel.value = key
  await loadPanel(key)
}

async function loadPanel(key = activePanel.value) {
  if (!token.value) return
  busy.value = true
  try {
    if (key === 'dashboard') await loadDashboard()
    if (key === 'orders') await loadOrders()
    if (key === 'catalog') await loadCatalog()
    if (key === 'tables') await loadTables()
    if (key === 'coupons') await loadCoupons()
    if (key === 'employees') await loadEmployees()
    if (key === 'reports') await loadReports()
  } finally {
    busy.value = false
  }
}

function closeOrderSocket() {
  socketClosing = true
  clearTimeout(reconnectTimer)
  if (orderSocket) orderSocket.close()
  orderSocket = undefined
}

async function connectOrderSocket() {
  if (!token.value || (orderSocket && orderSocket.readyState <= WebSocket.OPEN)) return
  socketClosing = false
  try {
    const ticket = await request('/admin/ws/ticket')
    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
    orderSocket = new WebSocket(`${protocol}//${location.host}/ws/order?ticket=${encodeURIComponent(ticket)}`)
    orderSocket.onmessage = async (event) => {
      try {
        const notice = JSON.parse(event.data)
        toast(notice.content || (notice.type === 1 ? '收到新订单' : '收到催单提醒'))
      } catch {
        toast('收到订单状态更新')
      }
      if (activePanel.value === 'orders') await loadOrders()
      if (activePanel.value === 'dashboard') await loadDashboard()
    }
    orderSocket.onclose = () => {
      orderSocket = undefined
      if (!socketClosing && token.value) reconnectTimer = setTimeout(connectOrderSocket, 3000)
    }
  } catch {
    if (!socketClosing && token.value) reconnectTimer = setTimeout(connectOrderSocket, 5000)
  }
}

// Dashboard
const dashboard = reactive({
  shopOpen: false,
  business: {},
  orders: {},
  dishes: {},
  setmeals: {},
  recentOrders: []
})

async function loadDashboard() {
  const [shop, business, orders, dishes, setmeals, recent] = await Promise.all([
    opsApi.shopStatus(),
    opsApi.businessData(),
    opsApi.orderOverview(),
    opsApi.dishOverview(),
    opsApi.setmealOverview(),
    opsApi.orderPage({ page: 1, pageSize: 6 })
  ])
  dashboard.shopOpen = Number(shop) === 1
  dashboard.business = business || {}
  dashboard.orders = orders || {}
  dashboard.dishes = dishes || {}
  dashboard.setmeals = setmeals || {}
  dashboard.recentOrders = recent?.records || []
}

async function toggleShop() {
  await opsApi.setShopStatus(dashboard.shopOpen ? 0 : 1)
  dashboard.shopOpen = !dashboard.shopOpen
  toast(dashboard.shopOpen ? '门店已开始营业' : '门店已暂停接单')
}

// Orders
const orders = ref([])
const orderFilter = ref('')
const orderSearch = ref('')
const orderDetail = ref(null)
const orderPage = reactive({ page: 1, pageSize: 20, total: 0 })
const orderStatus = {
  1: '待付款',
  2: '待接单',
  3: '制作中',
  4: '配送中',
  5: '已完成',
  6: '已取消'
}

async function loadOrders() {
  const result = await opsApi.orderPage({
    page: orderPage.page,
    pageSize: orderPage.pageSize,
    status: orderFilter.value,
    number: orderSearch.value.trim()
  })
  orders.value = result?.records || []
  orderPage.total = Number(result?.total || 0)
}

async function searchOrders() {
  orderPage.page = 1
  await loadOrders()
}

async function changeOrderPage(delta) {
  const next = orderPage.page + delta
  const totalPages = Math.max(1, Math.ceil(orderPage.total / orderPage.pageSize))
  if (next < 1 || next > totalPages) return
  orderPage.page = next
  await loadOrders()
}

async function viewOrder(id) {
  orderDetail.value = await opsApi.orderDetail(id)
}

async function advanceOrder(order, action) {
  if (action === 'confirm') await opsApi.confirmOrder(order.id)
  if (action === 'reject') {
    const reason = window.prompt('请输入拒单原因（顾客可见）', '当前菜品暂时无法制作')
    if (!reason?.trim()) return
    await opsApi.rejectOrder(order.id, reason.trim())
  }
  if (action === 'cancel') {
    const reason = window.prompt('请输入取消原因（顾客可见）', '门店临时无法履约')
    if (!reason?.trim()) return
    await opsApi.cancelOrder(order.id, reason.trim())
  }
  if (action === 'deliver') await opsApi.deliverOrder(order.id)
  if (action === 'complete') await opsApi.completeOrder(order.id)
  toast('订单状态已更新')
  orderDetail.value = null
  await loadOrders()
}

function orderActions(order) {
  if (order.status === 2) return ['confirm', 'reject']
  if (order.status === 3) return order.fulfillmentType === 1 ? ['deliver', 'cancel'] : ['complete', 'cancel']
  if (order.status === 4) return ['complete', 'cancel']
  return []
}

const actionText = {
  confirm: '接单',
  reject: '拒单',
  cancel: '取消',
  deliver: '开始配送',
  complete: '完成'
}

// Catalog
const catalogTab = ref('dish')
const categories = ref([])
const dishes = ref([])
const setmeals = ref([])
const catalogSearch = ref('')
const editor = reactive({ open: false, kind: '', mode: 'create', data: {} })
const imageUploading = ref(false)

async function loadCatalog() {
  const [categoryPage, dishPage, setmealPage] = await Promise.all([
    opsApi.categoryPage({ page: 1, pageSize: 100 }),
    opsApi.dishPage({ page: 1, pageSize: 100, name: catalogSearch.value.trim() }),
    opsApi.setmealPage({ page: 1, pageSize: 100, name: catalogSearch.value.trim() })
  ])
  categories.value = categoryPage?.records || []
  dishes.value = dishPage?.records || []
  setmeals.value = setmealPage?.records || []
}

function categoriesByType(type) {
  return categories.value.filter((item) => item.type === type)
}

async function openCatalogEditor(kind, item) {
  editor.kind = kind
  editor.mode = item ? 'edit' : 'create'
  if (kind === 'category') {
    editor.data = item
      ? { id: item.id, name: item.name, type: item.type, sort: item.sort }
      : { name: '', type: 1, sort: 1 }
  }
  if (kind === 'dish') {
    const detail = item ? await opsApi.dishDetail(item.id) : null
    editor.data = detail
      ? {
          ...detail,
          flavors: (detail.flavors || []).map((flavor) => ({
            id: flavor.id,
            dishId: flavor.dishId,
            name: flavor.name,
            optionsText: safeFlavorOptions(flavor.value).join(' / ')
          }))
        }
      : { name: '', categoryId: categoriesByType(1)[0]?.id, price: 0, description: '', image: '', status: 0, flavors: [] }
  }
  if (kind === 'setmeal') {
    const detail = item ? await opsApi.setmealDetail(item.id) : null
    editor.data = detail
      ? { ...detail, setmealDishes: (detail.setmealDishes || []).map((dish) => ({ dishId: dish.dishId, copies: Number(dish.copies || 1) })) }
      : { name: '', categoryId: categoriesByType(2)[0]?.id, price: 0, description: '', image: '', status: 0, setmealDishes: [] }
  }
  editor.open = true
}

function selectedSetmealDish(dishId) {
  return editor.data.setmealDishes?.some((item) => item.dishId === dishId)
}

function toggleSetmealDish(dishId, checked) {
  editor.data.setmealDishes ||= []
  if (checked) editor.data.setmealDishes.push({ dishId, copies: 1 })
  else editor.data.setmealDishes = editor.data.setmealDishes.filter((item) => item.dishId !== dishId)
}

function dishById(dishId) {
  return dishes.value.find((dish) => dish.id === dishId) || {}
}

function safeFlavorOptions(value) {
  try {
    const options = JSON.parse(value)
    return Array.isArray(options) ? options.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
}

function addFlavorGroup() {
  editor.data.flavors ||= []
  editor.data.flavors.push({ name: '', optionsText: '' })
}

function removeFlavorGroup(index) {
  editor.data.flavors.splice(index, 1)
}

function buildFlavors(flavors, dishId) {
  return (flavors || [])
    .map((flavor) => ({
      id: flavor.id,
      dishId,
      name: flavor.name?.trim(),
      value: JSON.stringify(String(flavor.optionsText || '')
        .split('/')
        .map((value) => value.trim())
        .filter(Boolean))
    }))
    .filter((flavor) => flavor.name && flavor.value !== '[]')
}

async function uploadCatalogImage(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!['image/jpeg', 'image/png'].includes(file.type)) return toast('只支持 JPG 或 PNG 图片')
  if (file.size > 5 * 1024 * 1024) return toast('图片不能超过 5MB')
  imageUploading.value = true
  try {
    editor.data.image = await opsApi.uploadImage(file)
    toast('图片已上传')
  } finally {
    imageUploading.value = false
  }
}

async function saveCatalog() {
  const data = editor.data
  if (!data.name?.trim()) return toast('请填写名称')
  if (editor.kind === 'category') {
    const payload = { id: data.id, name: data.name.trim(), type: Number(data.type), sort: Number(data.sort) }
    if (editor.mode === 'edit') await opsApi.updateCategory(payload)
    else await opsApi.saveCategory(payload)
  }
  if (editor.kind === 'dish') {
    if (!data.categoryId || Number(data.price) <= 0) return toast('请选择分类并填写正确价格')
    const payload = {
      id: data.id,
      name: data.name.trim(),
      categoryId: Number(data.categoryId),
      price: Number(data.price),
      image: data.image?.trim() || '',
      description: data.description?.trim() || '',
      status: Number(data.status ?? 0),
      flavors: buildFlavors(data.flavors, data.id)
    }
    if (editor.mode === 'edit') await opsApi.updateDish(payload)
    else await opsApi.saveDish(payload)
  }
  if (editor.kind === 'setmeal') {
    if (!data.categoryId || Number(data.price) <= 0 || !data.setmealDishes?.length) {
      return toast('请选择套餐分类、价格和至少一道菜品')
    }
    if (data.setmealDishes.some((item) => Number(item.copies) < 1 || Number(item.copies) > 99)) {
      return toast('套餐内每道菜的份数应为1到99')
    }
    const payload = {
      id: data.id,
      name: data.name.trim(),
      categoryId: Number(data.categoryId),
      price: Number(data.price),
      image: data.image?.trim() || '',
      description: data.description?.trim() || '',
      status: Number(data.status ?? 0),
      setmealDishes: data.setmealDishes.map((item) => ({
        dishId: item.dishId,
        name: dishById(item.dishId).name,
        price: dishById(item.dishId).price,
        copies: Number(item.copies)
      }))
    }
    if (editor.mode === 'edit') await opsApi.updateSetmeal(payload)
    else await opsApi.saveSetmeal(payload)
  }
  editor.open = false
  toast(editor.mode === 'edit' ? '内容已更新' : '内容已创建')
  await loadCatalog()
}

async function toggleCatalog(kind, item) {
  const status = item.status === 1 ? 0 : 1
  if (kind === 'category') await opsApi.setCategoryStatus(item.id, status)
  if (kind === 'dish') await opsApi.setDishStatus(item.id, status)
  if (kind === 'setmeal') await opsApi.setSetmealStatus(item.id, status)
  toast(status === 1 ? '已启用' : '已停用')
  await loadCatalog()
}

async function deleteCatalog(kind, item) {
  if (!window.confirm(`确定删除“${item.name}”吗？`)) return
  if (kind === 'category') await opsApi.deleteCategory(item.id)
  if (kind === 'dish') await opsApi.deleteDish(item.id)
  if (kind === 'setmeal') await opsApi.deleteSetmeal(item.id)
  toast('已删除')
  await loadCatalog()
}

// Tables
const tables = ref([])
const tableName = ref('')
const qr = reactive({ open: false, id: null, name: '', imageUrl: '', orderUrl: '' })

async function loadTables() {
  const result = await opsApi.tablePage({ page: 1, pageSize: 100 })
  tables.value = result?.records || []
}

async function addTable() {
  if (!tableName.value.trim()) return toast('请填写桌台名称')
  await opsApi.saveTable({ name: tableName.value.trim() })
  tableName.value = ''
  toast('桌台已创建')
  await loadTables()
}

async function toggleTable(table) {
  await opsApi.setTableStatus(table.id, table.status === 1 ? 0 : 1)
  toast(table.status === 1 ? '桌台已停用' : '桌台已启用')
  await loadTables()
}

async function deleteTable(table) {
  if (!window.confirm(`确定删除桌台“${table.name}”吗？`)) return
  await opsApi.deleteTable(table.id)
  toast('桌台已删除')
  await loadTables()
}

async function showQr(table) {
  if (qr.imageUrl) URL.revokeObjectURL(qr.imageUrl)
  const [blob, info] = await Promise.all([
    fetchBlob(`/admin/table/qrcode/${encodeURIComponent(table.id)}`),
    opsApi.tableQrInfo(table.id)
  ])
  qr.imageUrl = URL.createObjectURL(blob)
  qr.id = table.id
  qr.name = info.name
  qr.orderUrl = info.orderUrl
  qr.open = true
}

async function copyQrUrl() {
  await navigator.clipboard.writeText(qr.orderUrl)
  toast('点餐链接已复制')
}

async function rotateQr() {
  if (!window.confirm('换新后旧二维码会立即失效，确定继续吗？')) return
  await opsApi.rotateTableQr(qr.id)
  await showQr({ id: qr.id })
  toast('二维码已换新，请重新打印或分享')
}

// Coupons
const coupons = ref([])
const couponEditor = reactive({
  open: false,
  data: { title: '', discountAmount: 10, minAmount: 30, totalStock: 100, minutes: 60 }
})
const reconciliation = ref(null)

function formatDateTime(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

async function loadCoupons() {
  const result = await opsApi.couponPage({ page: 1, pageSize: 100 })
  coupons.value = result?.records || []
}

async function createCoupon() {
  const form = couponEditor.data
  if (!form.title.trim() || form.discountAmount <= 0 || form.minAmount < 0
      || (form.minAmount > 0 && form.minAmount < form.discountAmount)) {
    return toast('门槛可填0（无门槛）；非0门槛不能低于优惠金额')
  }
  const now = new Date()
  await opsApi.saveCoupon({
    title: form.title.trim(),
    type: 2,
    discountAmount: Math.round(form.discountAmount * 100),
    minAmount: Math.round(form.minAmount * 100),
    totalStock: Number(form.totalStock),
    seckillStartTime: formatDateTime(now),
    seckillEndTime: formatDateTime(new Date(now.getTime() + Number(form.minutes) * 60_000))
  })
  couponEditor.open = false
  couponEditor.data = { title: '', discountAmount: 10, minAmount: 30, totalStock: 100, minutes: 60 }
  toast('秒杀券已创建，发布后才会开放抢券')
  await loadCoupons()
}

function couponBenefit(coupon) {
  return coupon.minAmount <= 0
    ? `无门槛减 ${coupon.discountAmount / 100}`
    : `满 ${coupon.minAmount / 100} 减 ${coupon.discountAmount / 100}`
}

function couponLifecycle(coupon) {
  if (coupon.status !== 1) return '草稿'
  const now = Date.now()
  const start = new Date(String(coupon.seckillStartTime).replace(' ', 'T') + '+08:00').getTime()
  const end = new Date(String(coupon.seckillEndTime).replace(' ', 'T') + '+08:00').getTime()
  if (coupon.remainingStock <= 0) return '已抢完'
  if (now < start) return '待开始'
  if (now >= end) return '已结束'
  return '进行中'
}

async function publishCoupon(coupon) {
  await opsApi.publishCoupon(coupon.id)
  toast('已发布并预热 Redis 库存')
  await loadCoupons()
}

async function rebuildCoupon(coupon) {
  if (!window.confirm('仅在 Redis 缓存丢失或库存异常时执行。确定重建该活动缓存吗？')) return
  await opsApi.rebuildCoupon(coupon.id)
  toast('缓存已幂等重建')
}

async function reconcileCoupon(coupon) {
  reconciliation.value = await opsApi.reconcileCoupon(coupon.id)
}

// Employees
const employees = ref([])
const employeeSearch = ref('')
const employeeEditor = reactive({ open: false, mode: 'create', data: {} })
const employeePasswordReset = reactive({ open: false, employee: null, newPassword: '' })

async function loadEmployees() {
  const result = await opsApi.employeePage({
    page: 1,
    pageSize: 100,
    name: employeeSearch.value.trim()
  })
  employees.value = result?.records || []
}

async function openEmployeeEditor(item) {
  employeeEditor.mode = item ? 'edit' : 'create'
  employeeEditor.data = item
    ? await opsApi.employeeDetail(item.id)
    : { username: '', name: '', phone: '', sex: '1', idNumber: '', role: 'OPERATOR', password: '', confirmPassword: '' }
  employeeEditor.open = true
}

async function saveEmployee() {
  const data = employeeEditor.data
  if (!data.username?.trim() || !data.name?.trim() || !/^1\d{10}$/.test(data.phone || '')) {
    return toast('请填写账号、姓名和正确手机号')
  }
  if (!/^[0-9]{17}[0-9Xx]$/.test(data.idNumber || '')) return toast('请填写正确的身份证号')
  if (employeeEditor.mode === 'create' && (data.password?.length < 8 || data.password !== data.confirmPassword)) {
    return toast('请设置至少8位且两次一致的临时密码')
  }
  const payload = {
    id: data.id,
    username: data.username.trim(),
    name: data.name.trim(),
    phone: data.phone,
    sex: data.sex,
    idNumber: data.idNumber?.trim() || '',
    role: data.role || 'OPERATOR',
    password: employeeEditor.mode === 'create' ? data.password : undefined
  }
  if (employeeEditor.mode === 'edit') await opsApi.updateEmployee(payload)
  else await opsApi.saveEmployee(payload)
  employeeEditor.open = false
  toast(employeeEditor.mode === 'edit' ? '员工信息已更新' : '员工已创建，首次登录需修改临时密码')
  await loadEmployees()
}

function openEmployeePasswordReset(employee) {
  employeePasswordReset.employee = employee
  employeePasswordReset.newPassword = ''
  employeePasswordReset.open = true
}

async function resetEmployeePassword() {
  if (employeePasswordReset.newPassword.length < 8) return toast('临时密码至少8位')
  await opsApi.resetEmployeePassword(employeePasswordReset.employee.id, employeePasswordReset.newPassword)
  employeePasswordReset.open = false
  toast('临时密码已重置，该员工下次登录必须修改密码')
}

async function toggleEmployee(employee) {
  await opsApi.setEmployeeStatus(employee.id, employee.status === 1 ? 0 : 1)
  toast(employee.status === 1 ? '账号已禁用' : '账号已启用')
  await loadEmployees()
}

// Reports
function localDate(offsetDays = 0) {
  const date = new Date()
  date.setDate(date.getDate() + offsetDays)
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

const reportRange = reactive({ begin: localDate(-6), end: localDate(0) })
const reports = reactive({ turnover: {}, users: {}, orders: {}, top10: {} })

async function loadReports() {
  const [turnover, users, orderData, top10] = await Promise.all([
    opsApi.turnoverReport(reportRange.begin, reportRange.end),
    opsApi.userReport(reportRange.begin, reportRange.end),
    opsApi.orderReport(reportRange.begin, reportRange.end),
    opsApi.top10Report(reportRange.begin, reportRange.end)
  ])
  reports.turnover = turnover || {}
  reports.users = users || {}
  reports.orders = orderData || {}
  reports.top10 = top10 || {}
}

function csv(value) {
  return String(value || '').split(',').filter(Boolean)
}

const turnoverSeries = computed(() => {
  const dates = csv(reports.turnover.dateList)
  const values = csv(reports.turnover.turnoverList).map(Number)
  const max = Math.max(...values, 1)
  return dates.map((date, index) => ({ date, value: values[index] || 0, width: ((values[index] || 0) / max) * 100 }))
})

const top10Series = computed(() => {
  const names = csv(reports.top10.nameList)
  const values = csv(reports.top10.numberList).map(Number)
  const max = Math.max(...values, 1)
  return names.map((name, index) => ({ name, value: values[index] || 0, width: ((values[index] || 0) / max) * 100 }))
})

async function exportReport() {
  const blob = await fetchBlob('/admin/report/export')
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `DineHub运营报表-${localDate()}.xlsx`
  link.click()
  URL.revokeObjectURL(url)
  toast('报表已下载')
}

function formatOrderTime(value) {
  return value ? String(value).replace('T', ' ').slice(0, 16) : '—'
}

function money(value) {
  return Number(value || 0).toFixed(2)
}

onMounted(async () => {
  if (!token.value) return
  if (profile.value.mustChangePassword) {
    openRequiredPasswordEditor()
    return
  }
  try {
    await loadPanel('dashboard')
    connectOrderSocket()
  } catch {
    // The global error event already explains the failure.
  }
})

onBeforeUnmount(() => {
  closeOrderSocket()
  clearTimeout(toastTimer)
  if (qr.imageUrl) URL.revokeObjectURL(qr.imageUrl)
  window.removeEventListener('ops-auth-expired', handleAuthExpired)
  window.removeEventListener('ops-password-required', openRequiredPasswordEditor)
})
</script>

<template>
  <div v-if="toastMessage" class="ops-toast">{{ toastMessage }}</div>

  <main v-if="!token" class="login-shell">
    <section class="login-story">
      <div class="brand-seal">食</div>
      <div class="login-kicker">食汇运营台</div>
      <h1>让一顿饭，从前台到后厨都有迹可循。</h1>
      <p>扫码点餐、秒杀抢券、订单流转和经营数据，统一收进这间数字店铺。</p>
      <div class="story-points">
        <span>实时订单通知</span>
        <span>Redis 秒杀对账</span>
        <span>堂食 / 外送双履约</span>
      </div>
    </section>
    <section class="login-card ops-card">
      <div class="panel-kicker">员工登录</div>
      <h2>商家运营台</h2>
      <p>使用项目初始化的管理员账号登录。</p>
      <label class="field-label">
        账号
        <input v-model.trim="login.username" class="ops-input" autocomplete="username" maxlength="32" />
      </label>
      <label class="field-label">
        密码
        <input
          v-model="login.password"
          class="ops-input"
          type="password"
          autocomplete="current-password"
          maxlength="64"
          @keyup.enter="doLogin"
        />
      </label>
      <button class="ops-button primary login-button" :disabled="loggingIn" @click="doLogin">
        {{ loggingIn ? '正在登录…' : '进入运营台' }}
      </button>
      <small>演示环境默认账号可在项目 README 中查看；令牌仅保存在当前标签页会话。</small>
    </section>
  </main>

  <div v-else class="ops-shell">
    <aside class="sidebar">
      <div class="side-brand">
        <span>食</span>
        <div><strong>DineHub</strong><small>食汇运营台</small></div>
      </div>
      <nav>
        <button
          v-for="item in primaryNavItems"
          :key="item.key"
          :class="{ active: activePanel === item.key }"
          @click="switchPanel(item.key)"
        >
          <span>{{ item.mark }}</span>{{ item.label }}
        </button>
      </nav>
      <div class="side-user">
        <div class="avatar">{{ (profile.name || profile.userName || '管').slice(0, 1) }}</div>
        <div><strong>{{ profile.name || '管理员' }}</strong><small>{{ profile.role === 'ADMIN' ? '管理员' : '运营员' }} · {{ profile.userName }}</small></div>
        <button aria-label="修改密码" @click="passwordEditor.open = true">改密</button>
        <button aria-label="退出登录" @click="logout">退出</button>
      </div>
    </aside>

    <section class="workspace">
      <header class="topbar">
        <div>
          <span class="panel-kicker">运营管理</span>
          <h1 class="panel-title">{{ panelMeta.label }}</h1>
        </div>
        <div class="top-actions">
          <a href="/h5/index.html" target="_blank" rel="noopener">打开用户端</a>
          <a href="/doc.html" target="_blank" rel="noopener">接口文档</a>
          <button class="ops-button" :disabled="busy" @click="loadPanel()">刷新</button>
        </div>
      </header>

      <div v-if="busy" class="loading-line"><span /></div>

      <div v-if="activePanel === 'dashboard'" class="panel dashboard-panel">
        <section class="shop-banner" :class="{ closed: !dashboard.shopOpen }">
          <div>
            <span class="panel-kicker">STORE STATUS</span>
            <h2>{{ dashboard.shopOpen ? '门店正在营业' : '门店暂停接单' }}</h2>
            <p>{{ dashboard.shopOpen ? '用户端可提交新订单，实时通知通道已连接。' : '用户仍可浏览菜单，但服务端会拒绝新订单。' }}</p>
          </div>
          <button class="ops-button" @click="toggleShop">{{ dashboard.shopOpen ? '暂停营业' : '开始营业' }}</button>
        </section>

        <section class="metric-grid">
          <article class="metric-card ops-card">
            <span>今日营业额</span><strong>¥{{ money(dashboard.business.turnover) }}</strong><small>有效成交金额</small>
          </article>
          <article class="metric-card ops-card">
            <span>有效订单</span><strong>{{ dashboard.business.validOrderCount || 0 }}</strong><small>完成率 {{ ((dashboard.business.orderCompletionRate || 0) * 100).toFixed(1) }}%</small>
          </article>
          <article class="metric-card ops-card">
            <span>平均客单价</span><strong>¥{{ money(dashboard.business.unitPrice) }}</strong><small>今日有效订单</small>
          </article>
          <article class="metric-card ops-card">
            <span>新增用户</span><strong>{{ dashboard.business.newUsers || 0 }}</strong><small>今日首次访问</small>
          </article>
        </section>

        <section class="dashboard-grid">
          <article class="ops-card overview-card">
            <div class="section-head"><h2>订单总览</h2><button @click="switchPanel('orders')">查看全部</button></div>
            <div class="overview-numbers">
              <div><strong>{{ dashboard.orders.waitingOrders || 0 }}</strong><span>待接单</span></div>
              <div><strong>{{ dashboard.orders.deliveredOrders || 0 }}</strong><span>待派送</span></div>
              <div><strong>{{ dashboard.orders.completedOrders || 0 }}</strong><span>已完成</span></div>
              <div><strong>{{ dashboard.orders.cancelledOrders || 0 }}</strong><span>已取消</span></div>
            </div>
          </article>
          <article class="ops-card overview-card">
            <div class="section-head"><h2>菜单供给</h2><button @click="switchPanel('catalog')">去管理</button></div>
            <div class="overview-numbers">
              <div><strong>{{ dashboard.dishes.sold || 0 }}</strong><span>启售菜品</span></div>
              <div><strong>{{ dashboard.dishes.discontinued || 0 }}</strong><span>停售菜品</span></div>
              <div><strong>{{ dashboard.setmeals.sold || 0 }}</strong><span>启售套餐</span></div>
              <div><strong>{{ dashboard.setmeals.discontinued || 0 }}</strong><span>停售套餐</span></div>
            </div>
          </article>
        </section>

        <section class="ops-card recent-card">
          <div class="section-head"><h2>最近订单</h2><span>WebSocket 到单后自动更新</span></div>
          <div class="ops-table-wrap">
            <table class="ops-table">
              <thead><tr><th>订单号</th><th>履约</th><th>金额</th><th>状态</th><th>时间</th></tr></thead>
              <tbody>
                <tr v-for="item in dashboard.recentOrders" :key="item.id">
                  <td class="mono">{{ item.number }}</td>
                  <td>{{ item.fulfillmentType === 2 ? '堂食' : '外送' }}</td>
                  <td>¥{{ money(item.amount) }}</td>
                  <td><span class="status-tag" :class="{ off: item.status === 6, warn: item.status === 1 }">{{ orderStatus[item.status] }}</span></td>
                  <td>{{ formatOrderTime(item.orderTime) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <div v-if="activePanel === 'orders'" class="panel">
        <p class="panel-description">接单、拒单、制作、配送和完成形成可操作的订单状态机。</p>
        <div class="toolbar">
          <input v-model="orderSearch" class="ops-input" placeholder="按订单号查询" @keyup.enter="searchOrders" />
          <select v-model="orderFilter" class="ops-select" @change="searchOrders">
            <option value="">全部状态</option>
            <option v-for="(label, value) in orderStatus" :key="value" :value="value">{{ label }}</option>
          </select>
          <button class="ops-button dark" @click="searchOrders">查询</button>
        </div>
        <section class="ops-card">
          <div class="ops-table-wrap">
            <table class="ops-table">
              <thead><tr><th>订单号</th><th>内容</th><th>履约</th><th>金额</th><th>状态</th><th>下单时间</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in orders" :key="item.id">
                  <td class="mono">{{ item.number }}</td>
                  <td class="wrap-cell">{{ item.orderDishes || '查看详情' }}</td>
                  <td>{{ item.fulfillmentType === 2 ? '堂食' : '外送' }}</td>
                  <td>¥{{ money(item.amount) }}</td>
                  <td><span class="status-tag" :class="{ off: item.status === 6, warn: item.status === 1, brand: [2,3,4].includes(item.status) }">{{ orderStatus[item.status] }}</span></td>
                  <td>{{ formatOrderTime(item.orderTime) }}</td>
                  <td><button class="table-link" @click="viewOrder(item.id)">处理 / 详情</button></td>
                </tr>
              </tbody>
            </table>
            <div v-if="!orders.length" class="empty-state">当前筛选条件下没有订单</div>
          </div>
          <div class="pagination">
            <span>共 {{ orderPage.total }} 条</span>
            <button class="ops-button" :disabled="orderPage.page <= 1" @click="changeOrderPage(-1)">上一页</button>
            <strong>{{ orderPage.page }} / {{ Math.max(1, Math.ceil(orderPage.total / orderPage.pageSize)) }}</strong>
            <button class="ops-button" :disabled="orderPage.page >= Math.ceil(orderPage.total / orderPage.pageSize)" @click="changeOrderPage(1)">下一页</button>
          </div>
        </section>
      </div>

      <div v-if="activePanel === 'catalog'" class="panel">
        <p class="panel-description">分类、菜品和套餐共用同一套供给状态，启售后才会出现在用户端。</p>
        <div class="catalog-tabs">
          <button :class="{ active: catalogTab === 'category' }" @click="catalogTab = 'category'">分类</button>
          <button :class="{ active: catalogTab === 'dish' }" @click="catalogTab = 'dish'">菜品</button>
          <button :class="{ active: catalogTab === 'setmeal' }" @click="catalogTab = 'setmeal'">套餐</button>
        </div>
        <div class="toolbar">
          <input v-model="catalogSearch" class="ops-input" placeholder="按名称查询" @keyup.enter="loadCatalog" />
          <button class="ops-button" @click="loadCatalog">查询</button>
          <span class="toolbar-spacer" />
          <button class="ops-button primary" @click="openCatalogEditor(catalogTab)">新增{{ catalogTab === 'category' ? '分类' : catalogTab === 'dish' ? '菜品' : '套餐' }}</button>
        </div>
        <section class="ops-card">
          <div class="ops-table-wrap">
            <table v-if="catalogTab === 'category'" class="ops-table">
              <thead><tr><th>分类</th><th>类型</th><th>排序</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in categories" :key="item.id">
                  <td>{{ item.name }}</td><td>{{ item.type === 1 ? '菜品分类' : '套餐分类' }}</td><td>{{ item.sort }}</td>
                  <td><span class="status-tag" :class="{ off: item.status !== 1 }">{{ item.status === 1 ? '启用' : '停用' }}</span></td>
                  <td class="row-actions">
                    <button @click="openCatalogEditor('category', item)">编辑</button>
                    <button @click="toggleCatalog('category', item)">{{ item.status === 1 ? '停用' : '启用' }}</button>
                    <button @click="deleteCatalog('category', item)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <table v-if="catalogTab === 'dish'" class="ops-table">
              <thead><tr><th>菜品</th><th>分类</th><th>价格</th><th>描述</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in dishes" :key="item.id">
                  <td><div class="catalog-name"><img v-if="item.image" :src="item.image" alt="" @error="$event.target.style.display='none'" /><span>{{ item.name }}</span></div></td><td>{{ item.categoryName }}</td><td>¥{{ money(item.price) }}</td>
                  <td class="wrap-cell">{{ item.description || '—' }}</td>
                  <td><span class="status-tag" :class="{ off: item.status !== 1 }">{{ item.status === 1 ? '启售' : '停售' }}</span></td>
                  <td class="row-actions">
                    <button @click="openCatalogEditor('dish', item)">编辑</button>
                    <button @click="toggleCatalog('dish', item)">{{ item.status === 1 ? '停售' : '启售' }}</button>
                    <button @click="deleteCatalog('dish', item)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
            <table v-if="catalogTab === 'setmeal'" class="ops-table">
              <thead><tr><th>套餐</th><th>分类</th><th>价格</th><th>描述</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="item in setmeals" :key="item.id">
                  <td><div class="catalog-name"><img v-if="item.image" :src="item.image" alt="" @error="$event.target.style.display='none'" /><span>{{ item.name }}</span></div></td><td>{{ item.categoryName }}</td><td>¥{{ money(item.price) }}</td>
                  <td class="wrap-cell">{{ item.description || '—' }}</td>
                  <td><span class="status-tag" :class="{ off: item.status !== 1 }">{{ item.status === 1 ? '启售' : '停售' }}</span></td>
                  <td class="row-actions">
                    <button @click="openCatalogEditor('setmeal', item)">编辑</button>
                    <button @click="toggleCatalog('setmeal', item)">{{ item.status === 1 ? '停售' : '启售' }}</button>
                    <button @click="deleteCatalog('setmeal', item)">删除</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <div v-if="activePanel === 'tables'" class="panel">
        <p class="panel-description">每个桌台拥有不可预测的 token，二维码只负责传递桌台上下文。</p>
        <div class="toolbar">
          <input v-model="tableName" class="ops-input" maxlength="30" placeholder="桌台名称，例如 A01" @keyup.enter="addTable" />
          <button class="ops-button primary" @click="addTable">新增桌台</button>
        </div>
        <section class="table-card-grid">
          <article v-for="table in tables" :key="table.id" class="ops-card table-card">
            <div class="table-badge">{{ table.name.slice(0, 3) }}</div>
            <div>
              <h2>{{ table.name }}</h2>
              <span class="status-tag" :class="{ off: table.status !== 1 }">{{ table.status === 1 ? '可扫码点餐' : '已停用' }}</span>
            </div>
            <div class="table-actions">
              <button @click="showQr(table)">二维码</button>
              <button @click="toggleTable(table)">{{ table.status === 1 ? '停用' : '启用' }}</button>
              <button @click="deleteTable(table)">删除</button>
            </div>
          </article>
        </section>
      </div>

      <div v-if="activePanel === 'coupons'" class="panel">
        <p class="panel-description">日常运营只需创建和发布活动；库存对账与缓存重建仅供管理员在故障处理时使用。</p>
        <div class="toolbar">
          <span class="toolbar-spacer" />
          <button class="ops-button primary" @click="couponEditor.open = true">创建秒杀券</button>
        </div>
        <section class="ops-card">
          <div class="ops-table-wrap">
            <table class="ops-table">
              <thead><tr><th>活动</th><th>优惠</th><th>库存</th><th>时间</th><th>状态</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="coupon in coupons" :key="coupon.id">
                  <td>{{ coupon.title }}</td>
                  <td>{{ couponBenefit(coupon) }}</td>
                  <td>{{ coupon.remainingStock }} / {{ coupon.totalStock }}</td>
                  <td class="wrap-cell">{{ formatOrderTime(coupon.seckillStartTime) }} 至 {{ formatOrderTime(coupon.seckillEndTime) }}</td>
                  <td><span class="status-tag" :class="{ off: !['进行中', '待开始'].includes(couponLifecycle(coupon)) }">{{ couponLifecycle(coupon) }}</span></td>
                  <td class="row-actions">
                    <button v-if="coupon.status !== 1" @click="publishCoupon(coupon)">发布活动</button>
                    <details v-else-if="profile.role === 'ADMIN'" class="advanced-actions">
                      <summary>故障处理</summary>
                      <button @click="reconcileCoupon(coupon)">库存对账</button>
                      <button @click="rebuildCoupon(coupon)">重建缓存</button>
                    </details>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <div v-if="activePanel === 'employees'" class="panel">
        <p class="panel-description">管理员可创建运营账号、分配两级角色并重置临时密码；员工首次登录必须修改密码。</p>
        <div class="toolbar">
          <input v-model="employeeSearch" class="ops-input" placeholder="按姓名查询" @keyup.enter="loadEmployees" />
          <button class="ops-button" @click="loadEmployees">查询</button>
          <span class="toolbar-spacer" />
          <button class="ops-button primary" @click="openEmployeeEditor()">新增员工</button>
        </div>
        <section class="ops-card">
          <div class="ops-table-wrap">
            <table class="ops-table">
              <thead><tr><th>账号</th><th>姓名</th><th>角色</th><th>手机号</th><th>状态</th><th>最近更新</th><th>操作</th></tr></thead>
              <tbody>
                <tr v-for="employee in employees" :key="employee.id">
                  <td>{{ employee.username }}</td><td>{{ employee.name }}</td>
                  <td>{{ employee.role === 'ADMIN' ? '管理员' : '运营员' }}</td><td>{{ employee.phone }}</td>
                  <td><span class="status-tag" :class="{ off: employee.status !== 1 }">{{ employee.status === 1 ? '启用' : '禁用' }}</span></td>
                  <td>{{ formatOrderTime(employee.updateTime) }}</td>
                  <td class="row-actions">
                    <button @click="openEmployeeEditor(employee)">编辑</button>
                    <button @click="openEmployeePasswordReset(employee)">重置密码</button>
                    <button :disabled="employee.id === profile.id" @click="toggleEmployee(employee)">{{ employee.status === 1 ? '禁用' : '启用' }}</button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>

      <div v-if="activePanel === 'reports'" class="panel">
        <p class="panel-description">所有图表都由真实报表接口生成，不使用前端写死的演示数字。</p>
        <div class="toolbar">
          <label class="inline-field">开始<input v-model="reportRange.begin" class="ops-input" type="date" /></label>
          <label class="inline-field">结束<input v-model="reportRange.end" class="ops-input" type="date" /></label>
          <button class="ops-button dark" @click="loadReports">生成报表</button>
          <span class="toolbar-spacer" />
          <button class="ops-button primary" @click="exportReport">导出 Excel</button>
        </div>
        <section class="report-grid">
          <article class="ops-card chart-card">
            <div class="section-head"><h2>营业额趋势</h2><span>{{ reportRange.begin }} — {{ reportRange.end }}</span></div>
            <div class="bar-list">
              <div v-for="point in turnoverSeries" :key="point.date" class="bar-row">
                <span>{{ point.date.slice(5) }}</span><i><b :style="{ width: `${point.width}%` }" /></i><strong>¥{{ money(point.value) }}</strong>
              </div>
            </div>
          </article>
          <article class="ops-card chart-card">
            <div class="section-head"><h2>销量 Top 10</h2><span>已完成订单</span></div>
            <div class="bar-list green">
              <div v-for="point in top10Series" :key="point.name" class="bar-row">
                <span>{{ point.name }}</span><i><b :style="{ width: `${point.width}%` }" /></i><strong>{{ point.value }}</strong>
              </div>
              <div v-if="!top10Series.length" class="empty-state">所选时间段暂无成交数据</div>
            </div>
          </article>
        </section>
        <section class="metric-grid report-metrics">
          <article class="metric-card ops-card"><span>订单总数</span><strong>{{ reports.orders.totalOrderCount || 0 }}</strong><small>有效 {{ reports.orders.validOrderCount || 0 }}</small></article>
          <article class="metric-card ops-card"><span>订单完成率</span><strong>{{ ((reports.orders.orderCompletionRate || 0) * 100).toFixed(1) }}%</strong><small>所选时间段</small></article>
          <article class="metric-card ops-card"><span>新增用户</span><strong>{{ csv(reports.users.newUserList).reduce((sum, value) => sum + Number(value), 0) }}</strong><small>累计 {{ csv(reports.users.totalUserList).at(-1) || 0 }}</small></article>
        </section>
      </div>

      <div v-if="activePanel === 'engineering'" class="panel engineering-panel">
        <p class="panel-description">把面试时最值得讲的系统设计集中展示，页面内容均对应当前代码实现。</p>
        <section class="architecture-flow">
          <article><span>01</span><strong>Vue 3 双端</strong><p>顾客 H5 与运营台，共享同一套 Spring MVC API。</p></article>
          <i>→</i>
          <article><span>02</span><strong>Spring Boot</strong><p>JWT、订单状态机、服务端计价与幂等提交。</p></article>
          <i>→</i>
          <article><span>03</span><strong>数据与消息</strong><p>MySQL 持久化，Redis 抢券，RabbitMQ 超时关单。</p></article>
        </section>
        <section class="engineering-grid">
          <article class="ops-card engineering-card">
            <div class="panel-kicker">ORDER RELIABILITY</div>
            <h2>订单可靠性</h2>
            <ul>
              <li><strong>客户端 requestId</strong><span>同一请求重复提交只返回原订单</span></li>
              <li><strong>服务端结算</strong><span>不信任前端金额和优惠结果</span></li>
              <li><strong>优惠券预占</strong><span>支付核销，取消和超时自动释放</span></li>
              <li><strong>状态机</strong><span>顾客与运营端按角色推进订单</span></li>
            </ul>
          </article>
          <article class="ops-card engineering-card">
            <div class="panel-kicker">HIGH CONCURRENCY</div>
            <h2>秒杀链路</h2>
            <ul>
              <li><strong>Redis Lua</strong><span>原子校验库存和重复抢券</span></li>
              <li><strong>RabbitMQ</strong><span>异步落库并查询最终结果</span></li>
              <li><strong>补偿与对账</strong><span>库存漂移可见、缓存可幂等重建</span></li>
              <li><strong>用户态回显</strong><span>已抢、占用、核销不再只显示“立即抢”</span></li>
            </ul>
          </article>
          <article class="ops-card engineering-card">
            <div class="panel-kicker">SECURE DEFAULTS</div>
            <h2>安全边界</h2>
            <ul>
              <li><strong>资源归属校验</strong><span>订单和地址避免 IDOR 越权</span></li>
              <li><strong>图片重编码</strong><span>限制大小、像素和真实文件类型</span></li>
              <li><strong>浏览器安全头</strong><span>CSP、nosniff、frame deny</span></li>
              <li><strong>基础设施端口</strong><span>数据库和中间件默认只绑定本机</span></li>
            </ul>
          </article>
          <article class="ops-card engineering-card deferred">
            <div class="panel-kicker">ROADMAP</div>
            <h2>明确未伪装完成</h2>
            <ul>
              <li><strong>真实微信登录 / 支付</strong><span>当前开发模式用演示入口替代</span></li>
              <li><strong>RBAC 权限模型</strong><span>现阶段只有管理员统一权限</span></li>
              <li><strong>Spring Boot 3</strong><span>作为独立升级迭代，不混入功能改造</span></li>
              <li><strong>公网部署</strong><span>本地 Docker 演示，生产需域名与 HTTPS</span></li>
            </ul>
          </article>
        </section>
      </div>
    </section>
  </div>

  <div v-if="orderDetail" class="modal-mask" @click.self="orderDetail = null">
    <section class="modal">
      <div class="modal-head">
        <div><span class="panel-kicker">订单详情</span><h2>{{ orderStatus[orderDetail.status] }} · {{ orderDetail.number }}</h2></div>
        <button class="modal-close" @click="orderDetail = null">×</button>
      </div>
      <div class="order-detail-summary">
        <div><span>履约方式</span><strong>{{ orderDetail.fulfillmentType === 2 ? '堂食' : '外送' }}</strong></div>
        <div><span>订单金额</span><strong>¥{{ money(orderDetail.amount) }}</strong></div>
        <div><span>下单时间</span><strong>{{ formatOrderTime(orderDetail.orderTime) }}</strong></div>
        <div><span>支付状态</span><strong>{{ orderDetail.payStatus === 1 ? '已支付' : orderDetail.payStatus === 2 ? '已退款' : '未支付' }}</strong></div>
      </div>
      <div class="detail-list">
        <div v-for="item in orderDetail.orderDetailList" :key="item.id">
          <span>{{ item.name }} <small v-if="item.dishFlavor">{{ item.dishFlavor }}</small></span>
          <span>×{{ item.number }}</span><strong>¥{{ money(Number(item.amount) * item.number) }}</strong>
        </div>
      </div>
      <dl class="detail-dl">
        <div v-if="orderDetail.address"><dt>地址 / 桌台</dt><dd>{{ orderDetail.address }}</dd></div>
        <div v-if="orderDetail.consignee"><dt>联系人</dt><dd>{{ orderDetail.consignee }} · {{ orderDetail.phone }}</dd></div>
        <div v-if="orderDetail.originalAmount"><dt>金额明细</dt><dd>原价 ¥{{ money(orderDetail.originalAmount) }}，优惠 ¥{{ money(orderDetail.discountAmount) }}，实付 ¥{{ money(orderDetail.amount) }}</dd></div>
        <div v-if="orderDetail.remark"><dt>备注</dt><dd>{{ orderDetail.remark }}</dd></div>
        <div v-if="orderDetail.cancelReason"><dt>取消原因</dt><dd>{{ orderDetail.cancelReason }}</dd></div>
        <div v-if="orderDetail.rejectionReason"><dt>拒单原因</dt><dd>{{ orderDetail.rejectionReason }}</dd></div>
      </dl>
      <div class="modal-actions">
        <button
          v-for="action in orderActions(orderDetail)"
          :key="action"
          class="ops-button"
          :class="{ primary: ['confirm','deliver','complete'].includes(action), danger: ['reject','cancel'].includes(action) }"
          @click="advanceOrder(orderDetail, action)"
        >
          {{ actionText[action] }}
        </button>
      </div>
    </section>
  </div>

  <div v-if="editor.open" class="modal-mask" @click.self="editor.open = false">
    <section class="modal">
      <div class="modal-head">
        <div><span class="panel-kicker">菜单编辑</span><h2>{{ editor.mode === 'edit' ? '编辑' : '新增' }}{{ editor.kind === 'category' ? '分类' : editor.kind === 'dish' ? '菜品' : '套餐' }}</h2></div>
        <button class="modal-close" @click="editor.open = false">×</button>
      </div>
      <div class="form-grid">
        <label class="field-label full">名称<input v-model="editor.data.name" class="ops-input" maxlength="40" /></label>
        <template v-if="editor.kind === 'category'">
          <label class="field-label">类型<select v-model.number="editor.data.type" class="ops-select"><option :value="1">菜品分类</option><option :value="2">套餐分类</option></select></label>
          <label class="field-label">排序<input v-model.number="editor.data.sort" class="ops-input" type="number" min="0" /></label>
        </template>
        <template v-else>
          <label class="field-label">分类
            <select v-model.number="editor.data.categoryId" class="ops-select">
              <option v-for="category in categoriesByType(editor.kind === 'dish' ? 1 : 2)" :key="category.id" :value="category.id">{{ category.name }}</option>
            </select>
          </label>
          <label class="field-label">价格（元）<input v-model.number="editor.data.price" class="ops-input" type="number" min="0.01" step="0.01" /></label>
          <label class="field-label full">描述<textarea v-model="editor.data.description" class="ops-textarea" maxlength="200" rows="3" /></label>
          <div class="field-label full image-editor">
            <span>{{ editor.kind === 'dish' ? '菜品图片' : '套餐图片' }}</span>
            <div class="image-editor-row">
              <div class="image-preview">
                <img v-if="editor.data.image" :src="editor.data.image" :alt="`${editor.kind === 'dish' ? '菜品' : '套餐'}图片预览`" @error="$event.target.style.display='none'" />
                <span v-else>暂无图片</span>
              </div>
              <label class="ops-button upload-button">
                {{ imageUploading ? '上传中…' : '上传 JPG / PNG' }}
                <input type="file" accept="image/jpeg,image/png" :disabled="imageUploading" @change="uploadCatalogImage" />
              </label>
              <button v-if="editor.data.image" class="ops-button" type="button" @click="editor.data.image = ''">移除</button>
            </div>
            <small>最大 5MB；服务端会解码并重新编码图片。本地开发保存到 Docker 数据卷。</small>
          </div>
        </template>
        <fieldset v-if="editor.kind === 'dish'" class="flavor-editor full">
          <legend>规格与口味</legend>
          <div v-for="(flavor, index) in editor.data.flavors" :key="flavor.id || index" class="flavor-row">
            <input v-model="flavor.name" class="ops-input" maxlength="20" placeholder="分组名，如：辣度" />
            <input v-model="flavor.optionsText" class="ops-input" maxlength="160" placeholder="选项用 / 分隔，如：微辣 / 中辣 / 特辣" />
            <button type="button" aria-label="删除口味分组" @click="removeFlavorGroup(index)">删除</button>
          </div>
          <button type="button" class="ops-button" @click="addFlavorGroup">＋ 添加口味分组</button>
          <small>每个分组在用户端必须选择一项；可同时设置“辣度”“温度”“忌口”等多个分组。</small>
        </fieldset>
        <fieldset v-if="editor.kind === 'setmeal'" class="dish-picker full">
          <legend>选择套餐菜品</legend>
          <label v-for="dish in dishes" :key="dish.id">
            <input type="checkbox" :checked="selectedSetmealDish(dish.id)" @change="toggleSetmealDish(dish.id, $event.target.checked)" />{{ dish.name }} · ¥{{ money(dish.price) }}
          </label>
          <div v-for="item in editor.data.setmealDishes" :key="item.dishId" class="setmeal-quantity">
            <span>{{ dishById(item.dishId).name }}</span>
            <label>份数 <input v-model.number="item.copies" class="ops-input" type="number" min="1" max="99" /></label>
          </div>
        </fieldset>
      </div>
      <div class="modal-actions"><button class="ops-button" @click="editor.open = false">取消</button><button class="ops-button primary" @click="saveCatalog">保存</button></div>
    </section>
  </div>

  <div v-if="couponEditor.open" class="modal-mask" @click.self="couponEditor.open = false">
    <section class="modal">
      <div class="modal-head">
        <div><span class="panel-kicker">活动设置</span><h2>创建秒杀券</h2></div>
        <button class="modal-close" @click="couponEditor.open = false">×</button>
      </div>
      <div class="form-grid">
        <label class="field-label full">活动标题<input v-model="couponEditor.data.title" class="ops-input" maxlength="50" placeholder="例如：午市立减 10 元" /></label>
        <label class="field-label">优惠金额（元）<input v-model.number="couponEditor.data.discountAmount" class="ops-input" type="number" min="1" /></label>
        <label class="field-label">使用门槛（元）<input v-model.number="couponEditor.data.minAmount" class="ops-input" type="number" min="0" /><small>填 0 表示无门槛</small></label>
        <label class="field-label">发行库存<input v-model.number="couponEditor.data.totalStock" class="ops-input" type="number" min="1" /></label>
        <label class="field-label">持续时间（分钟）<input v-model.number="couponEditor.data.minutes" class="ops-input" type="number" min="1" /></label>
      </div>
      <div class="modal-actions"><button class="ops-button" @click="couponEditor.open = false">取消</button><button class="ops-button primary" @click="createCoupon">创建活动</button></div>
    </section>
  </div>

  <div v-if="reconciliation" class="modal-mask" @click.self="reconciliation = null">
    <section class="modal reconciliation-modal">
      <div class="modal-head">
        <div><span class="panel-kicker">故障诊断</span><h2>秒杀库存对账</h2></div>
        <button class="modal-close" @click="reconciliation = null">×</button>
      </div>
      <div class="reconcile-status" :class="{ danger: !reconciliation.consistent }">
        <strong>{{ reconciliation.consistent ? '数据一致' : '发现库存漂移' }}</strong>
        <span>漂移量 {{ reconciliation.stockDrift }}</span>
      </div>
      <div class="reconcile-grid">
        <div><span>数据库剩余</span><strong>{{ reconciliation.dbRemainingStock }}</strong></div>
        <div><span>Redis 可用</span><strong>{{ reconciliation.redisAvailableStock }}</strong></div>
        <div><span>用户持券</span><strong>{{ reconciliation.dbHolderCount }}</strong></div>
        <div><span>活跃预占</span><strong>{{ reconciliation.activeReservationCount }}</strong></div>
        <div><span>已结算</span><strong>{{ reconciliation.settledCount }}</strong></div>
        <div><span>补偿处理中</span><strong>{{ reconciliation.compensatingCount }}</strong></div>
      </div>
    </section>
  </div>

  <div v-if="employeeEditor.open" class="modal-mask" @click.self="employeeEditor.open = false">
    <section class="modal">
      <div class="modal-head">
        <div><span class="panel-kicker">账号资料</span><h2>{{ employeeEditor.mode === 'edit' ? '编辑员工' : '新增员工' }}</h2></div>
        <button class="modal-close" @click="employeeEditor.open = false">×</button>
      </div>
      <div class="form-grid">
        <label class="field-label">登录账号<input v-model="employeeEditor.data.username" class="ops-input" maxlength="32" :disabled="employeeEditor.mode === 'edit'" /></label>
        <label class="field-label">姓名<input v-model="employeeEditor.data.name" class="ops-input" maxlength="32" /></label>
        <label class="field-label">手机号<input v-model="employeeEditor.data.phone" class="ops-input" maxlength="11" /></label>
        <label class="field-label">性别<select v-model="employeeEditor.data.sex" class="ops-select"><option value="1">男</option><option value="0">女</option></select></label>
        <label class="field-label">角色<select v-model="employeeEditor.data.role" class="ops-select"><option value="OPERATOR">运营员</option><option value="ADMIN">管理员</option></select></label>
        <label class="field-label full">身份证号<input v-model="employeeEditor.data.idNumber" class="ops-input" maxlength="18" /></label>
        <label v-if="employeeEditor.mode === 'create'" class="field-label">临时密码<input v-model="employeeEditor.data.password" class="ops-input" type="password" maxlength="64" autocomplete="new-password" /></label>
        <label v-if="employeeEditor.mode === 'create'" class="field-label">确认临时密码<input v-model="employeeEditor.data.confirmPassword" class="ops-input" type="password" maxlength="64" autocomplete="new-password" /></label>
      </div>
      <div class="modal-actions"><button class="ops-button" @click="employeeEditor.open = false">取消</button><button class="ops-button primary" @click="saveEmployee">保存</button></div>
    </section>
  </div>

  <div v-if="employeePasswordReset.open" class="modal-mask" @click.self="employeePasswordReset.open = false">
    <section class="modal compact-modal">
      <div class="modal-head">
        <div><span class="panel-kicker">账号安全</span><h2>重置 {{ employeePasswordReset.employee?.name }} 的密码</h2></div>
        <button class="modal-close" @click="employeePasswordReset.open = false">×</button>
      </div>
      <label class="field-label">新临时密码
        <input v-model="employeePasswordReset.newPassword" class="ops-input" type="password" minlength="8" maxlength="64" autocomplete="new-password" />
      </label>
      <p class="form-hint">保存后原密码立即失效，该员工下次登录必须再次修改密码。</p>
      <div class="modal-actions"><button class="ops-button" @click="employeePasswordReset.open = false">取消</button><button class="ops-button primary" @click="resetEmployeePassword">确认重置</button></div>
    </section>
  </div>

  <div v-if="passwordEditor.open" class="modal-mask" @click.self="!passwordEditor.required && (passwordEditor.open = false)">
    <section class="modal compact-modal">
      <div class="modal-head">
        <div><span class="panel-kicker">账号安全</span><h2>{{ passwordEditor.required ? '首次登录，请修改密码' : '修改登录密码' }}</h2></div>
        <button v-if="!passwordEditor.required" class="modal-close" @click="passwordEditor.open = false">×</button>
      </div>
      <label class="field-label">当前密码<input v-model="passwordEditor.oldPassword" class="ops-input" type="password" autocomplete="current-password" /></label>
      <label class="field-label">新密码<input v-model="passwordEditor.newPassword" class="ops-input" type="password" minlength="8" maxlength="64" autocomplete="new-password" /></label>
      <label class="field-label">确认新密码<input v-model="passwordEditor.confirmPassword" class="ops-input" type="password" minlength="8" maxlength="64" autocomplete="new-password" /></label>
      <p class="form-hint">至少8位，建议同时包含字母和数字。</p>
      <div class="modal-actions"><button v-if="!passwordEditor.required" class="ops-button" @click="passwordEditor.open = false">取消</button><button class="ops-button primary" @click="changeOwnPassword">保存新密码</button></div>
    </section>
  </div>

  <div v-if="qr.open" class="modal-mask" @click.self="qr.open = false">
    <section class="qr-modal">
      <button class="modal-close" @click="qr.open = false">×</button>
      <div class="panel-kicker">扫码点餐</div>
      <h2>桌台 {{ qr.name }}</h2>
      <img :src="qr.imageUrl" :alt="`${qr.name} 桌台点餐二维码`" />
      <p class="qr-url">{{ qr.orderUrl }}</p>
      <p v-if="/localhost|127\.0\.0\.1/.test(qr.orderUrl)" class="qr-warning">
        当前链接只适合本机测试；真实手机扫码前请把 DINEHUB_H5_BASE_URL 改为电脑局域网地址或公网域名。
      </p>
      <p v-else>手机与电脑处于可互访网络时，可直接扫码进入用户端。</p>
      <div class="qr-actions">
        <button class="ops-button primary" @click="copyQrUrl">复制链接</button>
        <button class="ops-button" @click="rotateQr">换新二维码</button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-shell {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(360px, .85fr);
}
.login-story {
  display: flex; flex-direction: column; justify-content: center; padding: 9vw;
  background: var(--ops-forest); color: #fff;
}
.brand-seal {
  width: 58px; height: 58px; display: grid; place-items: center; margin-bottom: 54px;
  border: 1px solid rgba(255,255,255,.35); border-radius: 50% 50% 50% 18%; font: 700 27px/1 "STSong", serif;
}
.login-kicker { color: #d8b4a5; font-size: 11px; font-weight: 700; letter-spacing: .18em; }
.login-story h1 { max-width: 650px; margin: 14px 0; font: 700 clamp(34px, 4vw, 62px)/1.23 "STSong", "Songti SC", serif; }
.login-story > p { max-width: 560px; color: rgba(255,255,255,.7); font-size: 14px; line-height: 1.9; }
.story-points { display: flex; gap: 22px; flex-wrap: wrap; margin-top: 44px; color: rgba(255,255,255,.82); font-size: 12px; }
.story-points span::before { margin-right: 8px; color: #d8b4a5; content: "—"; }
.login-card { width: min(390px, calc(100% - 48px)); align-self: center; justify-self: center; padding: 34px; }
.login-card h2 { margin: 8px 0; font: 700 28px/1.3 "STSong", "Songti SC", serif; }
.login-card > p { margin: 0 0 27px; color: var(--ops-muted); font-size: 12px; }
.login-card .field-label { margin-top: 14px; }
.login-button { width: 100%; margin-top: 22px; }
.login-card small { display: block; margin-top: 13px; color: var(--ops-muted); font-size: 10px; line-height: 1.6; }

.ops-shell { min-height: 100vh; display: grid; grid-template-columns: 214px minmax(0, 1fr); }
.sidebar {
  position: sticky; top: 0; height: 100vh; display: flex; flex-direction: column;
  padding: 23px 16px 16px; background: #1f3d32; color: #fff;
}
.side-brand { display: flex; align-items: center; gap: 11px; padding: 0 8px 24px; border-bottom: 1px solid rgba(255,255,255,.1); }
.side-brand > span {
  width: 38px; height: 38px; display: grid; place-items: center; border-radius: 50% 50% 50% 13px;
  background: #f0ddd2; color: var(--ops-brand-dark); font: 700 20px/1 "STSong", serif;
}
.side-brand strong, .side-brand small { display: block; }
.side-brand strong { font: 700 18px/1.2 Georgia, serif; }
.side-brand small { margin-top: 3px; color: rgba(255,255,255,.56); font-size: 9px; letter-spacing: .12em; }
.sidebar nav { display: grid; gap: 5px; margin-top: 22px; }
.sidebar nav button {
  display: flex; align-items: center; gap: 11px; padding: 10px; border: 0; border-radius: 8px;
  background: transparent; color: rgba(255,255,255,.65); font-size: 12px; text-align: left;
}
.sidebar nav button span { width: 25px; height: 25px; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.12); border-radius: 7px; font: 700 11px/1 "STSong", serif; }
.sidebar nav button:hover, .sidebar nav button.active { background: rgba(255,255,255,.1); color: #fff; }
.secondary-nav { margin-top: auto; padding-top: 14px; border-top: 1px solid rgba(255,255,255,.1); }
.secondary-nav > span { display: block; margin: 0 9px 6px; color: rgba(255,255,255,.38); font-size: 9px; letter-spacing: .12em; }
.secondary-nav button { display: flex; align-items: center; gap: 11px; width: 100%; padding: 8px 10px; border: 0; border-radius: 9px; background: transparent; color: rgba(255,255,255,.58); text-align: left; }
.secondary-nav button > span { width: 25px; height: 25px; display: grid; place-items: center; border: 1px solid rgba(255,255,255,.12); border-radius: 7px; }
.secondary-nav button:hover, .secondary-nav button.active { background: rgba(255,255,255,.1); color: #fff; }
.side-user {
  display: grid; grid-template-columns: 34px 1fr auto auto; align-items: center; gap: 9px;
  margin-top: auto; padding: 14px 4px 2px; border-top: 1px solid rgba(255,255,255,.1);
}
.avatar { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 50%; background: #d9b9a8; color: var(--ops-brand-dark); font-weight: 700; }
.side-user strong, .side-user small { display: block; }
.side-user strong { font-size: 12px; }.side-user small { margin-top: 3px; color: rgba(255,255,255,.58); font-size: 10px; }
.side-user button { padding: 3px 0; border: 0; background: transparent; color: rgba(255,255,255,.65); font-size: 11px; }
.compact-modal { max-width: 520px; }
.form-hint { margin: 10px 0 0; color: var(--ops-muted); font-size: 12px; line-height: 1.6; }

.workspace { min-width: 0; }
.topbar {
  position: sticky; z-index: 10; top: 0; display: flex; align-items: center; justify-content: space-between;
  min-height: 79px; padding: 14px 28px; border-bottom: 1px solid var(--ops-line); background: rgba(242,238,232,.94); backdrop-filter: blur(12px);
}
.top-actions { display: flex; align-items: center; gap: 13px; }
.top-actions a { color: var(--ops-muted); font-size: 11px; text-decoration: none; }
.loading-line { position: fixed; z-index: 20; top: 78px; right: 0; left: 214px; height: 2px; overflow: hidden; }
.loading-line span { display: block; width: 35%; height: 100%; background: var(--ops-brand); animation: loading 1.1s infinite ease-in-out; }
@keyframes loading { from { transform: translateX(-100%); } to { transform: translateX(390%); } }
.panel { padding: 24px 28px 50px; }

.shop-banner {
  display: flex; align-items: center; justify-content: space-between; gap: 20px; padding: 23px 25px;
  border-radius: 14px; background: var(--ops-forest); color: #fff;
}
.shop-banner.closed { background: #69635d; }
.shop-banner .panel-kicker { color: #dbb7a7; }
.shop-banner h2 { margin: 6px 0; font: 700 24px/1.2 "STSong", "Songti SC", serif; }
.shop-banner p { margin: 0; color: rgba(255,255,255,.65); font-size: 11px; }
.shop-banner .ops-button { border-color: rgba(255,255,255,.35); background: transparent; color: #fff; }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 14px; }
.metric-card { padding: 17px; }
.metric-card span, .metric-card strong, .metric-card small { display: block; }
.metric-card span { color: var(--ops-muted); font-size: 10px; }
.metric-card strong { margin: 9px 0 4px; font: 700 25px/1 Georgia, serif; }
.metric-card small { color: #99918a; font-size: 9px; }
.dashboard-grid, .report-grid, .engineering-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px; margin-top: 13px; }
.overview-card, .recent-card, .chart-card { padding: 18px; }
.section-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.section-head h2 { margin: 0; font-size: 14px; }
.section-head button, .section-head span { border: 0; background: transparent; color: var(--ops-muted); font-size: 10px; }
.overview-numbers { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 25px; text-align: center; }
.overview-numbers strong, .overview-numbers span { display: block; }
.overview-numbers strong { font: 700 22px/1 Georgia, serif; }.overview-numbers span { margin-top: 7px; color: var(--ops-muted); font-size: 9px; }
.recent-card { margin-top: 13px; }
.mono { font-family: Consolas, monospace; font-size: 11px; }
.table-link, .row-actions button { padding: 0; border: 0; background: transparent; color: var(--ops-brand); font-size: 10px; }
.row-actions { display: flex; gap: 10px; }
.row-actions button:disabled { color: #bcb5af; }

.catalog-tabs { display: inline-flex; gap: 4px; margin-top: 14px; padding: 4px; border-radius: 10px; background: #e8e1d9; }
.catalog-tabs button { padding: 7px 18px; border: 0; border-radius: 7px; background: transparent; color: var(--ops-muted); font-size: 11px; }
.catalog-tabs button.active { background: #fff; color: var(--ops-ink); box-shadow: 0 2px 8px rgba(0,0,0,.06); }
.dish-picker { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; max-height: 180px; overflow-y: auto; padding: 12px; border: 1px solid var(--ops-line); border-radius: 8px; }
.dish-picker legend { color: var(--ops-muted); font-size: 11px; }.dish-picker label { font-size: 11px; }.dish-picker input { margin-right: 6px; }

.table-card-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 13px; }
.table-card { display: grid; grid-template-columns: 50px 1fr; gap: 13px; padding: 17px; }
.table-badge { width: 50px; height: 50px; display: grid; place-items: center; border-radius: 12px 12px 12px 5px; background: #f0ddd2; color: var(--ops-brand-dark); font: 700 17px/1 "STSong", serif; }
.table-card h2 { margin: 3px 0 8px; font-size: 15px; }
.table-actions { grid-column: 1 / -1; display: flex; gap: 10px; padding-top: 11px; border-top: 1px solid var(--ops-line); }
.table-actions button { padding: 0; border: 0; background: transparent; color: var(--ops-muted); font-size: 10px; }

.inline-field { display: flex; align-items: center; gap: 7px; color: var(--ops-muted); font-size: 10px; }
.bar-list { display: grid; gap: 11px; margin-top: 19px; }
.bar-row { display: grid; grid-template-columns: 70px 1fr 58px; align-items: center; gap: 9px; font-size: 9px; }
.bar-row > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--ops-muted); }
.bar-row i { height: 7px; overflow: hidden; border-radius: 999px; background: #eee8e2; }
.bar-row i b { display: block; height: 100%; min-width: 2px; border-radius: inherit; background: var(--ops-brand); }
.bar-row > strong { text-align: right; font-size: 10px; }
.bar-list.green i b { background: var(--ops-forest); }
.report-metrics { grid-template-columns: repeat(3, minmax(0,1fr)); }

.architecture-flow { display: grid; grid-template-columns: 1fr auto 1fr auto 1fr; align-items: center; gap: 13px; margin-top: 18px; }
.architecture-flow article { min-height: 150px; padding: 20px; border: 1px solid rgba(73,57,44,.08); border-radius: 13px; background: var(--ops-card); }
.architecture-flow article > span { color: var(--ops-brand); font: 700 11px/1 Georgia, serif; }
.architecture-flow article strong { display: block; margin: 22px 0 8px; font: 700 18px/1 "STSong", serif; }
.architecture-flow article p { margin: 0; color: var(--ops-muted); font-size: 10px; line-height: 1.7; }
.architecture-flow > i { color: #a49b93; font-style: normal; }
.engineering-card { padding: 21px; }.engineering-card h2 { margin: 7px 0 16px; font: 700 19px/1 "STSong", serif; }
.engineering-card ul { display: grid; gap: 13px; margin: 0; padding: 0; list-style: none; }
.engineering-card li { display: grid; grid-template-columns: 105px 1fr; gap: 12px; font-size: 10px; }
.engineering-card li span { color: var(--ops-muted); line-height: 1.6; }
.engineering-card.deferred { border-style: dashed; }

.order-detail-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.order-detail-summary div, .reconcile-grid div { padding: 12px; border-radius: 9px; background: #f2eee8; }
.order-detail-summary span, .order-detail-summary strong, .reconcile-grid span, .reconcile-grid strong { display: block; }
.order-detail-summary span, .reconcile-grid span { color: var(--ops-muted); font-size: 9px; }.order-detail-summary strong, .reconcile-grid strong { margin-top: 6px; font-size: 12px; }
.detail-list { margin-top: 18px; border-top: 1px solid var(--ops-line); }
.detail-list > div { display: grid; grid-template-columns: 1fr auto 70px; gap: 10px; padding: 11px 0; border-bottom: 1px solid var(--ops-line); font-size: 11px; }
.detail-list small { color: var(--ops-muted); }.detail-list strong { text-align: right; }
.detail-dl { margin: 14px 0 0; }.detail-dl div { display: grid; grid-template-columns: 80px 1fr; padding: 5px 0; font-size: 10px; }.detail-dl dt { color: var(--ops-muted); }.detail-dl dd { margin: 0; text-align: right; }
.reconcile-status { display: flex; justify-content: space-between; padding: 14px; border-radius: 10px; background: #e3efe6; color: #356447; font-size: 12px; }
.reconcile-status.danger { background: #f5dfda; color: var(--ops-brand-dark); }
.reconcile-grid { display: grid; grid-template-columns: repeat(3,1fr); gap: 9px; margin-top: 12px; }
.catalog-name { display: flex; align-items: center; gap: 9px; min-width: 130px; }
.catalog-name img { width: 38px; height: 38px; border-radius: 8px; object-fit: cover; background: #eee8e1; }
.image-editor > span { display: block; margin-bottom: 7px; }
.image-editor-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.image-preview { width: 90px; height: 68px; display: grid; place-items: center; overflow: hidden; border: 1px dashed var(--ops-line); border-radius: 10px; background: #f6f2ec; color: var(--ops-muted); font-size: 10px; }
.image-preview img { width: 100%; height: 100%; object-fit: cover; }
.upload-button { position: relative; overflow: hidden; cursor: pointer; }
.upload-button input { position: absolute; width: 1px; height: 1px; opacity: 0; }
.image-editor small, .flavor-editor small { display: block; margin-top: 8px; color: var(--ops-muted); font-size: 10px; line-height: 1.55; }
.flavor-editor { margin: 0; padding: 14px; border: 1px solid var(--ops-line); border-radius: 11px; }
.flavor-editor legend { padding: 0 6px; color: var(--ops-muted); font-size: 11px; }
.flavor-row { display: grid; grid-template-columns: 150px 1fr auto; gap: 8px; margin-bottom: 9px; }
.flavor-row button { border: 0; background: transparent; color: var(--ops-brand); font-size: 11px; }
.setmeal-quantity { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 10px; padding: 9px 11px; border-radius: 8px; background: #f5f1ec; }
.setmeal-quantity label { display: flex; align-items: center; gap: 8px; }
.setmeal-quantity .ops-input { width: 76px; min-height: 34px; }
.advanced-actions { position: relative; }
.advanced-actions summary { cursor: pointer; color: var(--ops-brand); list-style: none; }
.advanced-actions[open] { display: flex; gap: 8px; align-items: center; }
.advanced-actions[open] summary { color: var(--ops-muted); }
.qr-modal { position: relative; width: min(400px, 100%); padding: 28px; border-radius: 15px; background: var(--ops-card); text-align: center; }
.qr-modal .modal-close { position: absolute; top: 14px; right: 14px; }
.qr-modal h2 { font: 700 23px/1 "STSong", serif; }
.qr-modal img { width: 250px; height: 250px; max-width: 100%; }
.qr-modal p { color: var(--ops-muted); font-size: 10px; line-height: 1.6; }
.qr-modal .qr-url { padding: 8px 10px; border-radius: 7px; background: #f4f0ea; overflow-wrap: anywhere; color: var(--ops-ink); }
.qr-modal .qr-warning { padding: 8px 10px; border-left: 3px solid #c88a35; background: #fff6df; color: #72501d; text-align: left; }
.qr-actions { display: flex; justify-content: center; gap: 8px; margin-top: 12px; }

@media (max-width: 1040px) {
  .metric-grid { grid-template-columns: repeat(2, 1fr); }
  .dashboard-grid, .report-grid { grid-template-columns: 1fr; }
}
@media (max-width: 760px) {
  .login-shell { grid-template-columns: 1fr; }
  .login-story { display: none; }
  .ops-shell { grid-template-columns: 1fr; }
  .sidebar { position: static; height: auto; padding: 14px; }
  .side-brand { padding-bottom: 12px; }
  .sidebar nav { grid-template-columns: repeat(4, 1fr); margin-top: 12px; }
  .sidebar nav button { justify-content: center; padding: 7px; font-size: 9px; }
  .sidebar nav button span { display: none; }
  .secondary-nav { display: none; }
  .flavor-row { grid-template-columns: 1fr; }
  .side-user { display: none; }
  .topbar { top: 0; padding: 12px 16px; }
  .top-actions a { display: none; }
  .loading-line { left: 0; top: 68px; }
  .panel { padding: 18px 14px 40px; }
  .metric-grid, .report-metrics, .engineering-grid { grid-template-columns: 1fr; }
  .architecture-flow { grid-template-columns: 1fr; }
  .architecture-flow > i { transform: rotate(90deg); text-align: center; }
  .overview-numbers { grid-template-columns: repeat(2, 1fr); }
  .order-detail-summary, .reconcile-grid { grid-template-columns: 1fr; }
}
</style>
