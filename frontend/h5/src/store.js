import { reactive } from 'vue'

const session = window.sessionStorage
const local = window.localStorage

export const store = reactive({
  token: session.getItem('dinehub.token') || '',
  nickname: session.getItem('dinehub.nickname') || '',
  tableToken: session.getItem('dinehub.tableToken') || '',
  tableName: session.getItem('dinehub.tableName') || '',
  shopOpen: true,
  cartCount: 0
})

export function getToken() {
  return store.token
}

export function saveLogin(token, nickname) {
  store.token = token
  store.nickname = nickname
  session.setItem('dinehub.token', token)
  session.setItem('dinehub.nickname', nickname)
}

export function demoDeviceId() {
  let value = local.getItem('dinehub.demoDeviceId')
  if (!value) {
    value = typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}_${Math.random().toString(36).slice(2)}_${Math.random().toString(36).slice(2)}`
    local.setItem('dinehub.demoDeviceId', value)
  }
  return value
}

export function logout() {
  store.token = ''
  store.nickname = ''
  session.removeItem('dinehub.token')
  session.removeItem('dinehub.nickname')
  session.removeItem('dinehub.checkoutRequestId')
}

export function saveTable(tableToken, tableName) {
  store.tableToken = tableToken || ''
  store.tableName = tableName || ''
  if (tableToken) {
    session.setItem('dinehub.tableToken', tableToken)
    session.setItem('dinehub.tableName', tableName || '')
  } else {
    session.removeItem('dinehub.tableToken')
    session.removeItem('dinehub.tableName')
  }
}

export function checkoutRequestId() {
  let value = session.getItem('dinehub.checkoutRequestId')
  if (!value) {
    value = typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`
    session.setItem('dinehub.checkoutRequestId', value)
  }
  return value
}

export function completeCheckout() {
  session.removeItem('dinehub.checkoutRequestId')
}
