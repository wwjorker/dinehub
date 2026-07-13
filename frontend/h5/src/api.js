import { showToast } from 'vant'
import { getToken, logout } from './store'

// 统一请求封装:携带用户令牌,剥出业务数据,失败弹出后端错误文案
async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  const token = getToken()
  if (token) headers.authentication = token

  let res
  try {
    res = await fetch(path, { ...options, headers })
  } catch (error) {
    showToast('网络异常,请稍后重试')
    throw error
  }
  if (res.status === 401) {
    logout()
    location.hash = '#/login'
    throw new Error('请先登录')
  }
  const contentType = res.headers.get('content-type') || ''
  let body
  if (contentType.includes('application/json')) {
    try {
      body = await res.json()
    } catch (_) {
      showToast('服务响应格式错误')
      throw new Error('服务响应格式错误')
    }
  }
  if (!res.ok) {
    const fallback = res.status === 429 ? '请求过于频繁' : res.status === 403 ? '无权执行此操作' : '服务暂时不可用'
    const msg = body?.msg || fallback
    showToast(msg)
    throw new Error(msg)
  }
  if (!body) {
    showToast('服务响应格式错误')
    throw new Error('服务响应格式错误')
  }
  if (body.code !== 1) {
    const msg = body.msg || '请求失败'
    showToast(msg)
    throw new Error(msg)
  }
  return body.data
}

export const api = {
  demoLogin: (nickname) =>
    request('/user/user/demo-login', { method: 'POST', body: JSON.stringify({ nickname }) }),
  tableInfo: (tableToken) =>
    request(`/user/table/info?tableToken=${encodeURIComponent(tableToken)}`),
  categories: () => request('/user/category/list?type=1'),
  dishes: (categoryId) => request(`/user/dish/list?categoryId=${categoryId}`),
  cartList: () => request('/user/shoppingCart/list'),
  cartAdd: (item) => request('/user/shoppingCart/add', { method: 'POST', body: JSON.stringify(item) }),
  cartSub: (item) => request('/user/shoppingCart/sub', { method: 'POST', body: JSON.stringify(item) }),
  cartClean: () => request('/user/shoppingCart/clean', { method: 'DELETE' }),
  submitOrder: (payload) => request('/user/order/submit', { method: 'POST', body: JSON.stringify(payload) }),
  historyOrders: (page = 1, pageSize = 10) => request(`/user/order/historyOrders?page=${page}&pageSize=${pageSize}`),
  seckillList: () => request('/user/coupon/available'),
  seckill: (couponId) => request(`/user/coupon/seckill/${couponId}`, { method: 'POST' }),
  seckillResult: (requestId) => request(`/user/coupon/seckill/result/${requestId}`),
  myCoupons: () => request('/user/coupon/list')
}
