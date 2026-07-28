const TOKEN_KEY = 'dinehub.ops.token'
const PROFILE_KEY = 'dinehub.ops.profile'
const TIMEOUT_MS = 12_000

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY) || ''
}

export function getProfile() {
  try {
    return JSON.parse(sessionStorage.getItem(PROFILE_KEY) || '{}')
  } catch {
    return {}
  }
}

export function saveSession(data) {
  sessionStorage.setItem(TOKEN_KEY, data.token)
  sessionStorage.setItem(PROFILE_KEY, JSON.stringify({
    id: data.id,
    name: data.name,
    userName: data.userName
  }))
}

export function clearSession() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(PROFILE_KEY)
}

export function query(values = {}) {
  const params = new URLSearchParams()
  Object.entries(values).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, value)
  })
  return params.toString()
}

export async function request(path, options = {}) {
  const headers = { ...options.headers }
  if (!(options.body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.token = token

  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS)
  let response
  try {
    response = await fetch(path, { ...options, headers, signal: controller.signal })
  } catch (error) {
    const message = error.name === 'AbortError' ? '请求超时，请稍后重试' : '无法连接服务，请确认项目已经启动'
    window.dispatchEvent(new CustomEvent('ops-error', { detail: message }))
    throw new Error(message)
  } finally {
    clearTimeout(timeout)
  }

  if (response.status === 401) {
    clearSession()
    window.dispatchEvent(new CustomEvent('ops-auth-expired'))
    throw new Error('登录状态已失效')
  }

  const contentType = response.headers.get('content-type') || ''
  let body
  if (contentType.includes('application/json')) {
    try {
      body = await response.json()
    } catch {
      throw new Error('服务响应格式错误')
    }
  }
  if (!response.ok || !body || body.code !== 1) {
    const message = body?.msg || (response.status === 403 ? '无权执行此操作' : '操作失败')
    window.dispatchEvent(new CustomEvent('ops-error', { detail: message }))
    throw new Error(message)
  }
  return body.data
}

export async function fetchBlob(path) {
  const response = await fetch(path, { headers: { token: getToken() } })
  if (response.status === 401) {
    clearSession()
    window.dispatchEvent(new CustomEvent('ops-auth-expired'))
    throw new Error('登录状态已失效')
  }
  if (!response.ok) throw new Error('文件获取失败')
  return response.blob()
}

export const opsApi = {
  login: (payload) => request('/admin/employee/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  }),
  logout: () => request('/admin/employee/logout', { method: 'POST' }),

  shopStatus: () => request('/admin/shop/status'),
  setShopStatus: (status) => request(`/admin/shop/${encodeURIComponent(status)}`, { method: 'PUT' }),
  businessData: () => request('/admin/workspace/businessData'),
  orderOverview: () => request('/admin/workspace/overviewOrders'),
  dishOverview: () => request('/admin/workspace/overviewDishes'),
  setmealOverview: () => request('/admin/workspace/overviewSetmeals'),

  orderPage: (params) => request(`/admin/order/conditionSearch?${query(params)}`),
  orderDetail: (id) => request(`/admin/order/details/${encodeURIComponent(id)}`),
  confirmOrder: (id) => request('/admin/order/confirm', {
    method: 'PUT',
    body: JSON.stringify({ id, status: 3 })
  }),
  rejectOrder: (id, rejectionReason) => request('/admin/order/rejection', {
    method: 'PUT',
    body: JSON.stringify({ id, rejectionReason })
  }),
  cancelOrder: (id, cancelReason) => request('/admin/order/cancel', {
    method: 'PUT',
    body: JSON.stringify({ id, cancelReason })
  }),
  deliverOrder: (id) => request(`/admin/order/delivery/${encodeURIComponent(id)}`, { method: 'PUT' }),
  completeOrder: (id) => request(`/admin/order/complete/${encodeURIComponent(id)}`, { method: 'PUT' }),

  categoryPage: (params) => request(`/admin/category/page?${query(params)}`),
  categoryList: (type) => request(`/admin/category/list?${query({ type })}`),
  saveCategory: (payload) => request('/admin/category', { method: 'POST', body: JSON.stringify(payload) }),
  updateCategory: (payload) => request('/admin/category', { method: 'PUT', body: JSON.stringify(payload) }),
  setCategoryStatus: (id, status) =>
    request(`/admin/category/status/${encodeURIComponent(status)}?${query({ id })}`, { method: 'POST' }),
  deleteCategory: (id) => request(`/admin/category?${query({ id })}`, { method: 'DELETE' }),

  dishPage: (params) => request(`/admin/dish/page?${query(params)}`),
  dishList: (categoryId) => request(`/admin/dish/list?${query({ categoryId })}`),
  dishDetail: (id) => request(`/admin/dish/${encodeURIComponent(id)}`),
  saveDish: (payload) => request('/admin/dish', { method: 'POST', body: JSON.stringify(payload) }),
  updateDish: (payload) => request('/admin/dish', { method: 'PUT', body: JSON.stringify(payload) }),
  setDishStatus: (id, status) =>
    request(`/admin/dish/status/${encodeURIComponent(status)}?${query({ id })}`, { method: 'POST' }),
  deleteDish: (id) => request(`/admin/dish?${query({ ids: id })}`, { method: 'DELETE' }),

  setmealPage: (params) => request(`/admin/setmeal/page?${query(params)}`),
  setmealDetail: (id) => request(`/admin/setmeal/${encodeURIComponent(id)}`),
  saveSetmeal: (payload) => request('/admin/setmeal', { method: 'POST', body: JSON.stringify(payload) }),
  updateSetmeal: (payload) => request('/admin/setmeal', { method: 'PUT', body: JSON.stringify(payload) }),
  setSetmealStatus: (id, status) =>
    request(`/admin/setmeal/status/${encodeURIComponent(status)}?${query({ id })}`, { method: 'POST' }),
  deleteSetmeal: (id) => request(`/admin/setmeal?${query({ ids: id })}`, { method: 'DELETE' }),

  tablePage: (params) => request(`/admin/table/page?${query(params)}`),
  saveTable: (payload) => request('/admin/table', { method: 'POST', body: JSON.stringify(payload) }),
  updateTable: (payload) => request('/admin/table', { method: 'PUT', body: JSON.stringify(payload) }),
  setTableStatus: (id, status) =>
    request(`/admin/table/status/${encodeURIComponent(status)}?${query({ id })}`, { method: 'POST' }),
  deleteTable: (id) => request(`/admin/table/${encodeURIComponent(id)}`, { method: 'DELETE' }),

  couponPage: (params) => request(`/admin/coupon/page?${query(params)}`),
  saveCoupon: (payload) => request('/admin/coupon', { method: 'POST', body: JSON.stringify(payload) }),
  publishCoupon: (id) => request(`/admin/coupon/${encodeURIComponent(id)}/publish`, { method: 'POST' }),
  rebuildCoupon: (id) => request(`/admin/coupon/${encodeURIComponent(id)}/rebuild-cache`, { method: 'POST' }),
  reconcileCoupon: (id) => request(`/admin/coupon/${encodeURIComponent(id)}/reconciliation`),

  employeePage: (params) => request(`/admin/employee/page?${query(params)}`),
  employeeDetail: (id) => request(`/admin/employee/${encodeURIComponent(id)}`),
  saveEmployee: (payload) => request('/admin/employee', { method: 'POST', body: JSON.stringify(payload) }),
  updateEmployee: (payload) => request('/admin/employee', { method: 'PUT', body: JSON.stringify(payload) }),
  setEmployeeStatus: (id, status) =>
    request(`/admin/employee/status/${encodeURIComponent(status)}?${query({ id })}`, { method: 'POST' }),

  turnoverReport: (begin, end) =>
    request(`/admin/report/turnoverStatistics?${query({ begin, end })}`),
  userReport: (begin, end) => request(`/admin/report/userStatistics?${query({ begin, end })}`),
  orderReport: (begin, end) => request(`/admin/report/ordersStatistics?${query({ begin, end })}`),
  top10Report: (begin, end) => request(`/admin/report/top10?${query({ begin, end })}`)
}
