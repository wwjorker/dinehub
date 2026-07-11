<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { api } from '../api'
import { saveLogin, saveTable, store } from '../store'

const route = useRoute()
const router = useRouter()
const nickname = ref('')
const loading = ref(false)

async function login() {
  if (!nickname.value.trim()) {
    showToast('给自己起个昵称吧')
    return
  }
  loading.value = true
  try {
    const data = await api.demoLogin(nickname.value.trim())
    saveLogin(data.token, nickname.value.trim())
    if (store.tableToken) {
      try {
        const t = await api.tableInfo(store.tableToken)
        saveTable(store.tableToken, t.name)
      } catch (e) { /* 桌台可能已停用,点单页会提示 */ }
    }
    router.replace(route.query.redirect || '/menu')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <div class="hero">
      <div class="logo">食汇</div>
      <div class="slogan">扫码点单 · 限时秒杀</div>
    </div>
    <div class="form">
      <van-field v-model="nickname" label="昵称" placeholder="输入昵称即可开始" maxlength="12" />
      <div style="padding: 20px 16px">
        <van-button type="danger" block round :loading="loading" @click="login">进入食汇</van-button>
      </div>
      <div class="tip">演示环境昵称直登;正式环境将接入微信授权登录</div>
    </div>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; background: linear-gradient(160deg, #d43f2f 0%, #a93226 42%, #f7f4f0 42.1%); }
.hero { padding: 90px 24px 40px; color: #fff; }
.logo { font-size: 44px; font-weight: 700; letter-spacing: 6px; }
.slogan { margin-top: 10px; opacity: 0.85; letter-spacing: 2px; }
.form { margin: 0 16px; background: #fff; border-radius: 14px; padding-top: 8px; box-shadow: 0 8px 24px rgba(0,0,0,0.08); }
.tip { padding: 0 16px 20px; color: #999; font-size: 12px; text-align: center; }
</style>
