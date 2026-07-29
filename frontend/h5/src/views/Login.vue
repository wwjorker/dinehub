<script setup>
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { api } from '../api'
import { demoDeviceId, saveLogin, saveTable, store } from '../store'

const route = useRoute()
const router = useRouter()
const nickname = ref('')
const loading = ref(false)

function safeDestination(value) {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//')
    ? value
    : '/menu'
}

async function login() {
  const value = nickname.value.trim()
  if (!value) {
    showToast('先给自己取一个昵称吧')
    return
  }
  loading.value = true
  try {
    const data = await api.demoLogin(value, demoDeviceId())
    saveLogin(data.token, value)
    if (store.tableToken) {
      try {
        const table = await api.tableInfo(store.tableToken)
        saveTable(store.tableToken, table.name)
      } catch {
        saveTable('', '')
      }
    }
    router.replace(safeDestination(route.query.redirect))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-block">
      <div class="seal">食</div>
      <div>
        <div class="brand-name">食汇</div>
        <div class="brand-en">DINEHUB · TABLE SERVICE</div>
      </div>
    </section>

    <section class="intro">
      <p class="eyebrow">LOCAL DINING</p>
      <h1>从落座到结账，<br />让一顿饭更顺畅。</h1>
      <p>扫码绑定桌台，选菜、抢券、下单与订单跟进都在这里完成。</p>
    </section>

    <section class="login-card">
      <label for="nickname">演示昵称</label>
      <input id="nickname" v-model="nickname" maxlength="12" autocomplete="nickname"
        placeholder="例如：小王" @keyup.enter="login" />
      <button :disabled="loading" @click="login">
        {{ loading ? '正在进入…' : '进入食汇' }}
      </button>
      <p>昵称只用于展示；本机拥有独立的演示身份，不会与同名用户串号。正式环境接入微信授权。</p>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  min-height: 100vh;
  padding: 28px 22px 44px;
  background:
    linear-gradient(rgba(184, 58, 47, 0.07) 1px, transparent 1px),
    var(--paper);
  background-size: 100% 28px;
}
.brand-block { display: flex; align-items: center; gap: 12px; }
.seal {
  width: 45px; height: 45px; display: grid; place-items: center;
  border: 1px solid var(--brand); border-radius: 50%; color: var(--brand);
  font-family: "STSong", serif; font-size: 24px;
}
.brand-name { font-family: "STSong", serif; font-size: 21px; font-weight: 700; letter-spacing: .18em; }
.brand-en { margin-top: 2px; color: var(--muted); font-size: 9px; letter-spacing: .13em; }
.intro { margin-top: 74px; }
.intro h1 { margin: 10px 0 14px; font-family: "STSong", serif; font-size: 35px; line-height: 1.35; font-weight: 600; }
.intro > p:last-child { max-width: 330px; color: var(--muted); font-size: 14px; line-height: 1.8; }
.login-card { margin-top: 46px; padding: 19px; border: 1px solid var(--line); background: var(--card); border-radius: 4px 18px 18px; }
.login-card label { display: block; margin-bottom: 8px; font-size: 12px; color: var(--muted); }
.login-card input {
  width: 100%; padding: 12px 2px; border: 0; border-bottom: 1px solid var(--line);
  outline: none; background: transparent; font-size: 17px;
}
.login-card input:focus { border-color: var(--brand); }
.login-card button {
  width: 100%; margin-top: 18px; padding: 12px; border: 0; border-radius: 9px;
  background: var(--brand); color: #fff; font-weight: 700; cursor: pointer;
}
.login-card button:disabled { opacity: .65; }
.login-card p { margin: 11px 0 0; color: #9c958d; font-size: 11px; line-height: 1.5; text-align: center; }
</style>
