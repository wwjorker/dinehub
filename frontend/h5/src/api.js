import { showToast } from 'vant'
import { getToken, logout } from './store'

// 统一请求封装:携带用户令牌,剥出业务数据,失败弹出后端错误文案
async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers }
  const token = getToken()
  if (token) headers.authentication = token

  const res = await fetch(path, { ...options, headers })
  if (res.status === 401) {
    logout()
    location.hash = '#/login'
    throw new Error('请先登录')
  }
  const body = await res.json()
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
  myCoupons: () => request('/user/coupon/list')
}
