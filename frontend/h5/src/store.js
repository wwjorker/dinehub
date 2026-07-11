import { reactive } from 'vue'

// 轻量全局状态:登录凭据与桌台上下文持久化在 localStorage
export const store = reactive({
  token: localStorage.getItem('dinehub.token') || '',
  nickname: localStorage.getItem('dinehub.nickname') || '',
  tableToken: localStorage.getItem('dinehub.tableToken') || '',
  tableName: localStorage.getItem('dinehub.tableName') || '',
  cartCount: 0
})

export function getToken() {
  return store.token
}

export function saveLogin(token, nickname) {
  store.token = token
  store.nickname = nickname
  localStorage.setItem('dinehub.token', token)
  localStorage.setItem('dinehub.nickname', nickname)
}

export function logout() {
  store.token = ''
  store.nickname = ''
  localStorage.removeItem('dinehub.token')
  localStorage.removeItem('dinehub.nickname')
}

export function saveTable(tableToken, tableName) {
  store.tableToken = tableToken
  store.tableName = tableName
  localStorage.setItem('dinehub.tableToken', tableToken)
  localStorage.setItem('dinehub.tableName', tableName)
}
