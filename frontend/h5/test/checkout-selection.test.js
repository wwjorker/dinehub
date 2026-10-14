import test, { beforeEach } from 'node:test'
import assert from 'node:assert/strict'

const storage = () => {
  const values = new Map()
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, value), removeItem: (key) => values.delete(key) }
}
globalThis.window = { sessionStorage: storage(), localStorage: storage() }
const { store, reconcileCheckoutAddress, completeCheckout, logout, checkoutRequestId } = await import('../src/store.js')
const addresses = [{ id: 1, isDefault: 1 }, { id: 2, isDefault: 0 }]
beforeEach(() => completeCheckout())

test('进入结算选择默认地址，无默认时选首个，空列表保持未选择', () => {
  reconcileCheckoutAddress([])
  assert.equal(store.checkoutAddressId, null)
  reconcileCheckoutAddress([...addresses].reverse())
  assert.equal(store.checkoutAddressId, 1)
  completeCheckout()
  reconcileCheckoutAddress([{ id: 2, isDefault: 0 }])
  assert.equal(store.checkoutAddressId, 2)
})

test('绑定桌台后选择外送，刷新地址列表仍保留外送和有效的非默认地址', () => {
  store.tableToken = 'test-table'
  store.checkoutFulfillment = 'delivery'
  store.checkoutAddressId = 2
  assert.equal(reconcileCheckoutAddress(addresses), false)
  assert.equal(store.checkoutFulfillment, 'delivery')
  assert.equal(store.checkoutAddressId, 2)
})

test('地址删除后不静默改用默认地址，重复加载仍要求重新选择', () => {
  store.checkoutFulfillment = 'delivery'
  store.checkoutAddressId = 2
  assert.equal(reconcileCheckoutAddress([addresses[0]]), true)
  assert.equal(store.checkoutAddressId, null)
  assert.equal(store.checkoutAddressInvalid, true)
  assert.equal(reconcileCheckoutAddress([addresses[0]]), false)
  assert.equal(store.checkoutAddressId, null)
  assert.equal(store.checkoutFulfillment, 'delivery')
})

test('堂食选择不因地址列表变化切换为外送', () => {
  store.checkoutFulfillment = 'dineIn'
  reconcileCheckoutAddress(addresses)
  assert.equal(store.checkoutFulfillment, 'dineIn')
})

test('完成结算和退出登录清理选择，保留现有请求编号生命周期', () => {
  const requestId = checkoutRequestId()
  store.checkoutFulfillment = 'delivery'
  store.checkoutAddressId = 2
  reconcileCheckoutAddress(addresses)
  assert.equal(checkoutRequestId(), requestId)
  completeCheckout()
  assert.equal(store.checkoutFulfillment, null)
  assert.equal(store.checkoutAddressId, null)
  assert.equal(store.checkoutAddressInvalid, false)
  assert.equal(window.sessionStorage.getItem('dinehub.checkoutRequestId'), null)
  store.checkoutFulfillment = 'delivery'
  store.checkoutAddressId = 2
  logout()
  assert.equal(store.checkoutFulfillment, null)
  assert.equal(store.checkoutAddressId, null)
})
