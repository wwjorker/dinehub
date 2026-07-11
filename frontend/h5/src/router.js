import { createRouter, createWebHashHistory } from 'vue-router'
import { store } from './store'
import Login from './views/Login.vue'
import Menu from './views/Menu.vue'
import Cart from './views/Cart.vue'
import Seckill from './views/Seckill.vue'
import Mine from './views/Mine.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/menu' },
    { path: '/login', component: Login },
    { path: '/menu', component: Menu },
    { path: '/cart', component: Cart },
    { path: '/seckill', component: Seckill },
    { path: '/mine', component: Mine }
  ]
})

// 除登录页外都需要用户身份
router.beforeEach((to) => {
  if (to.path !== '/login' && !store.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
})

export default router
