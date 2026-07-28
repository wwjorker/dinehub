import { createRouter, createWebHashHistory } from 'vue-router'
import { store } from './store'
import Login from './views/Login.vue'
import Menu from './views/Menu.vue'
import Cart from './views/Cart.vue'
import Seckill from './views/Seckill.vue'
import Mine from './views/Mine.vue'
import Orders from './views/Orders.vue'
import OrderDetail from './views/OrderDetail.vue'
import Coupons from './views/Coupons.vue'
import Addresses from './views/Addresses.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/menu' },
    { path: '/login', component: Login },
    { path: '/menu', component: Menu },
    { path: '/cart', component: Cart },
    { path: '/seckill', component: Seckill },
    { path: '/mine', component: Mine },
    { path: '/orders', component: Orders },
    { path: '/orders/:id', component: OrderDetail },
    { path: '/coupons', component: Coupons },
    { path: '/addresses', component: Addresses }
  ]
})

router.beforeEach((to) => {
  if (to.path !== '/login' && !store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
