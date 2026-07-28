import { showToast } from 'vant'
import { getToken, logout } from './store'

const REQUEST_TIMEOUT_MS = 12_000

function query(values) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, value)
  })
  return params.toString()
}

async function request(path, options = {}) {
  const headers = { ...options.headers }
  if (!(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.authentication = token

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS)
  let response
  try {
    response = await fetch(path, { ...options, headers, signal: controller.signal })
  } catch (error) {
    const message = error.name === 'AbortError' ? '请求超时，请稍后重试' : '网络连接失败，请检查服务是否启动'
    showToast(message)
    throw new Error(message)
  } finally {
    clearTimeout(timeout)
  }

  if (response.status === 401) {
    logout()
    window.location.hash = '#/login'
    throw new Error('登录状态已失效')
  }

  const contentType = response.headers.get('content-type') || ''
  let body
  if (contentType.includes('application/json')) {
    try {
      body = await response.json()
    } catch {
      showToast('服务响应格式错误')
      throw new Error('服务响应格式错误')
    }
  }
  if (!response.ok) {
    const fallback = response.status === 429
      ? '请求过于频繁，请稍后再试'
      : response.status === 403 ? '无权执行此操作' : '服务暂时不可用'
    const message = body?.msg || fallback
    showToast(message)
    throw new Error(message)
  }
  if (!body || body.code !== 1) {
    const message = body?.msg || '请求失败'
    showToast(message)
    throw new Error(message)
  }
  return body.data
}

export const api = {
  demoLogin: (nickname) =>
    request('/user/user/demo-login', { method: 'POST', body: JSON.stringify({ nickname }) }),
  shopStatus: () => request('/user/shop/status'),
  tableInfo: (tableToken) => request(`/user/table/info?${query({ tableToken })}`),

  categories: (type = 1) => request(`/user/category/list?${query({ type })}`),
  dishes: (categoryId) => request(`/user/dish/list?${query({ categoryId })}`),
  setmeals: (categoryId) => request(`/user/setmeal/list?${query({ categoryId })}`),
  setmealDishes: (id) => request(`/user/setmeal/dish/${encodeURIComponent(id)}`),

  cartList: () => request('/user/shoppingCart/list'),
  cartAdd: (item) => request('/user/shoppingCart/add', { method: 'POST', body: JSON.stringify(item) }),
  cartSub: (item) => request('/user/shoppingCart/sub', { method: 'POST', body: JSON.stringify(item) }),
  cartClean: () => request('/user/shoppingCart/clean', { method: 'DELETE' }),

  submitOrder: (payload) => request('/user/order/submit', { method: 'POST', body: JSON.stringify(payload) }),
  historyOrders: (page = 1, pageSize = 20, status) =>
    request(`/user/order/historyOrders?${query({ page, pageSize, status })}`),
  orderDetail: (id) => request(`/user/order/orderDetail/${encodeURIComponent(id)}`),
  cancelOrder: (id) => request(`/user/order/cancel/${encodeURIComponent(id)}`, { method: 'PUT' }),
  repeatOrder: (id) => request(`/user/order/repetition/${encodeURIComponent(id)}`, { method: 'POST' }),
  remindOrder: (id) => request(`/user/order/reminder/${encodeURIComponent(id)}`),
  demoPay: (id) => request(`/user/order/demo-payment/${encodeURIComponent(id)}`, { method: 'PUT' }),

  seckillList: () => request('/user/coupon/available'),
  seckill: (couponId) => request(`/user/coupon/seckill/${encodeURIComponent(couponId)}`, { method: 'POST' }),
  seckillResult: (requestId) =>
    request(`/user/coupon/seckill/result/${encodeURIComponent(requestId)}`),
  myCoupons: () => request('/user/coupon/list'),

  addressList: () => request('/user/addressBook/list'),
  defaultAddress: () => request('/user/addressBook/default'),
  addressDetail: (id) => request(`/user/addressBook/${encodeURIComponent(id)}`),
  saveAddress: (payload) => request('/user/addressBook', { method: 'POST', body: JSON.stringify(payload) }),
  updateAddress: (payload) => request('/user/addressBook', { method: 'PUT', body: JSON.stringify(payload) }),
  setDefaultAddress: (id) =>
    request('/user/addressBook/default', { method: 'PUT', body: JSON.stringify({ id }) }),
  deleteAddress: (id) => request(`/user/addressBook?${query({ id })}`, { method: 'DELETE' })
}

export { request, query }
