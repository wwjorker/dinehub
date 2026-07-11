<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog } from 'vant'
import { api } from '../api'
import { store, logout } from '../store'

const router = useRouter()
const tab = ref(0)
const coupons = ref([])
const orders = ref([])

const fen2yuan = (fen) => (fen / 100).toFixed(0)
const couponStatus = { 1: ['未使用', 'success'], 2: ['已使用', 'default'], 3: ['已过期', 'default'] }
const orderStatus = { 1: '待付款', 2: '待接单', 3: '已接单', 4: '派送中', 5: '已完成', 6: '已取消' }

async function load() {
  coupons.value = await api.myCoupons()
  const page = await api.historyOrders(1, 20)
  orders.value = page.records || []
}

async function doLogout() {
  await showConfirmDialog({ message: '退出登录?' })
  logout()
  router.replace('/login')
}

onMounted(load)
</script>

<template>
  <div class="page">
    <div class="head">
      <div class="avatar">{{ (store.nickname || '客')[0] }}</div>
      <div>
        <div class="nick">{{ store.nickname || '顾客' }}</div>
        <div class="sub" v-if="store.tableName">当前桌台:{{ store.tableName }}</div>
      </div>
      <van-button size="mini" plain style="margin-left:auto" @click="doLogout">退出</van-button>
    </div>

    <van-tabs v-model:active="tab" color="#d43f2f">
      <van-tab title="我的优惠券">
        <van-empty v-if="coupons.length === 0" description="还没有优惠券,去秒杀页抢一张" />
        <div class="coupon" v-for="c in coupons" :key="c.id">
          <div class="amount">￥<b>{{ fen2yuan(c.discountAmount) }}</b></div>
          <div class="info">
            <div class="title">{{ c.title }}</div>
            <div class="sub">满{{ fen2yuan(c.minAmount) }}可用 · {{ (c.createTime || '').replace('T', ' ') }}领取</div>
          </div>
          <van-tag :type="couponStatus[c.status]?.[1]">{{ couponStatus[c.status]?.[0] }}</van-tag>
        </div>
      </van-tab>

      <van-tab title="我的订单">
        <van-empty v-if="orders.length === 0" description="暂无订单" />
        <div class="order" v-for="o in orders" :key="o.id">
          <div class="row">
            <span class="no">单号 {{ o.number }}</span>
            <van-tag :type="o.status === 6 ? 'default' : 'danger'" plain>{{ orderStatus[o.status] }}</van-tag>
          </div>
          <div class="row sub">
            <span>{{ o.fulfillmentType === 2 ? `堂食 · ${o.address}` : '外卖配送' }}</span>
            <span>{{ (o.orderTime || '').replace('T', ' ') }}</span>
          </div>
          <div class="row items" v-if="o.orderDetailList">
            {{ o.orderDetailList.map(d => `${d.name}×${d.number}`).join('、') }}
          </div>
          <div class="row">
            <span></span>
            <span class="price">￥{{ o.amount }}</span>
          </div>
        </div>
      </van-tab>
    </van-tabs>
  </div>
</template>

<style scoped>
.head { display: flex; align-items: center; gap: 12px; padding: 26px 16px; background: #fff; }
.avatar { width: 52px; height: 52px; border-radius: 50%; background: #d43f2f; color: #fff; display: flex; align-items: center; justify-content: center; font-size: 22px; }
.nick { font-weight: 600; font-size: 17px; }
.sub { color: #999; font-size: 12px; margin-top: 2px; }
.coupon { display: flex; align-items: center; gap: 12px; background: #fff; border-radius: 12px; padding: 14px; margin: 10px; }
.coupon .amount { color: #d43f2f; }
.coupon .amount b { font-size: 26px; }
.coupon .info { flex: 1; }
.title { font-weight: 600; }
.order { background: #fff; border-radius: 12px; padding: 12px 14px; margin: 10px; }
.row { display: flex; justify-content: space-between; align-items: center; margin: 3px 0; }
.no { font-size: 13px; color: #666; }
.items { color: #999; font-size: 12px; }
</style>
