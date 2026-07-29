<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { showConfirmDialog, showToast } from 'vant'
import { areaList } from '@vant/area-data'
import { api } from '../api'

const regions = Object.entries(areaList.province_list).map(([provinceCode, provinceName]) => ({
  code: provinceCode,
  name: provinceName,
  cities: Object.entries(areaList.city_list)
    .filter(([cityCode]) => cityCode.startsWith(provinceCode.slice(0, 2)))
    .map(([cityCode, cityName]) => ({
      code: cityCode,
      name: cityName,
      districts: Object.entries(areaList.county_list)
        .filter(([districtCode]) => districtCode.startsWith(cityCode.slice(0, 4)))
        .map(([districtCode, districtName]) => ({ code: districtCode, name: districtName }))
    }))
}))

const router = useRouter()
const addresses = ref([])
const loading = ref(false)
const saving = ref(false)
const editorOpen = ref(false)
const editingId = ref(null)
const form = reactive({
  consignee: '',
  phone: '',
  sex: '0',
  provinceCode: '',
  provinceName: '',
  cityCode: '',
  cityName: '',
  districtCode: '',
  districtName: '',
  detail: '',
  label: '家'
})

const availableCities = computed(() =>
  regions.find((province) => province.code === form.provinceCode)?.cities || [])
const availableDistricts = computed(() =>
  availableCities.value.find((city) => city.code === form.cityCode)?.districts || [])

async function load() {
  loading.value = true
  try {
    addresses.value = await api.addressList()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  editingId.value = null
  Object.assign(form, {
    consignee: '',
    phone: '',
    sex: '0',
    provinceCode: '',
    provinceName: '',
    cityCode: '',
    cityName: '',
    districtCode: '',
    districtName: '',
    detail: '',
    label: '家'
  })
}

function openCreate() {
  resetForm()
  editorOpen.value = true
}

function openEdit(address) {
  const matchedProvince = regions.find((province) =>
    province.code === address.provinceCode
    || province.name === address.provinceName
    || province.cities.some((city) => city.name === address.cityName))
  const matchedCity = matchedProvince?.cities.find((city) =>
    city.code === address.cityCode || city.name === address.cityName)
  const matchedDistrict = matchedCity?.districts.find((district) =>
    district.code === address.districtCode || district.name === address.districtName)
  editingId.value = address.id
  Object.assign(form, {
    consignee: address.consignee || '',
    phone: address.phone || '',
    sex: address.sex || '0',
    provinceCode: matchedProvince?.code || '',
    provinceName: matchedProvince?.name || address.provinceName || '',
    cityCode: matchedCity?.code || '',
    cityName: matchedCity?.name || address.cityName || '',
    districtCode: matchedDistrict?.code || '',
    districtName: matchedDistrict?.name || address.districtName || '',
    detail: address.detail || '',
    label: address.label || '家'
  })
  editorOpen.value = true
}

function payload() {
  return {
    ...(editingId.value ? { id: editingId.value } : {}),
    ...form
  }
}

function changeProvince() {
  const province = regions.find((item) => item.code === form.provinceCode)
  form.provinceName = province?.name || ''
  form.cityCode = ''
  form.cityName = ''
  form.districtCode = ''
  form.districtName = ''
}

function changeCity() {
  const city = availableCities.value.find((item) => item.code === form.cityCode)
  form.cityName = city?.name || ''
  form.districtCode = ''
  form.districtName = ''
}

function changeDistrict() {
  form.districtName = availableDistricts.value
    .find((item) => item.code === form.districtCode)?.name || ''
}

async function save() {
  if (!form.consignee.trim()) return showToast('请填写联系人')
  if (!/^1\d{10}$/.test(form.phone)) return showToast('请填写正确的手机号')
  if (!form.provinceCode || !form.cityCode || !form.districtCode || !form.detail.trim()) {
    return showToast('请选择省、市、区并填写详细地址')
  }

  saving.value = true
  try {
    if (editingId.value) await api.updateAddress(payload())
    else await api.saveAddress(payload())
    showToast({ type: 'success', message: editingId.value ? '地址已更新' : '地址已添加' })
    editorOpen.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function makeDefault(address) {
  if (address.isDefault === 1) return
  await api.setDefaultAddress(address.id)
  showToast({ type: 'success', message: '已设为默认地址' })
  await load()
}

async function remove(address) {
  await showConfirmDialog({ title: '删除地址', message: `确定删除“${address.consignee}”的地址吗？` })
  await api.deleteAddress(address.id)
  showToast('地址已删除')
  await load()
}

function addressText(address) {
  return [address.provinceName, address.cityName, address.districtName, address.detail]
    .filter(Boolean)
    .join(' ')
}

onMounted(load)
</script>

<template>
  <div class="page addresses-page">
    <header class="page-head address-head">
      <button class="back-button" aria-label="返回" @click="router.back()">‹</button>
      <div>
        <div class="eyebrow">外送信息</div>
        <h1 class="page-title">收货地址</h1>
        <p class="page-subtitle">外送订单使用；堂食扫码点餐不需要填写。</p>
      </div>
    </header>

    <van-loading v-if="loading" class="page-loading" vertical>正在读取地址</van-loading>
    <van-empty v-else-if="!addresses.length" description="还没有收货地址" />
    <main v-else class="address-list">
      <article v-for="address in addresses" :key="address.id" class="address-card">
        <div class="address-main" @click="makeDefault(address)">
          <div class="address-meta">
            <span v-if="address.label" class="address-label">{{ address.label }}</span>
            <strong>{{ address.consignee }}</strong>
            <span>{{ address.phone }}</span>
          </div>
          <p>{{ addressText(address) }}</p>
          <small v-if="address.isDefault === 1">默认地址</small>
          <small v-else>点此设为默认</small>
        </div>
        <div class="address-actions">
          <button @click="openEdit(address)">编辑</button>
          <button @click="remove(address)">删除</button>
        </div>
      </article>
    </main>

    <div class="add-address-bar">
      <van-button block round type="danger" icon="plus" @click="openCreate">新增收货地址</van-button>
    </div>

    <van-popup v-model:show="editorOpen" position="bottom" round safe-area-inset-bottom>
      <section class="address-editor">
        <div class="editor-head">
          <div>
            <div class="eyebrow">收货信息</div>
            <h2>{{ editingId ? '编辑地址' : '新增地址' }}</h2>
          </div>
          <button aria-label="关闭" @click="editorOpen = false">×</button>
        </div>
        <van-field v-model="form.consignee" label="联系人" maxlength="20" placeholder="怎么称呼" />
        <van-field v-model="form.phone" label="手机号" type="tel" maxlength="11" placeholder="用于联系配送" />
        <div class="region-fields">
          <label><span>省份</span><select v-model="form.provinceCode" @change="changeProvince">
            <option value="">请选择</option>
            <option v-for="province in regions" :key="province.code" :value="province.code">{{ province.name }}</option>
          </select></label>
          <label><span>城市</span><select v-model="form.cityCode" :disabled="!availableCities.length" @change="changeCity">
            <option value="">请选择</option>
            <option v-for="city in availableCities" :key="city.code" :value="city.code">{{ city.name }}</option>
          </select></label>
          <label><span>区县</span><select v-model="form.districtCode" :disabled="!availableDistricts.length" @change="changeDistrict">
            <option value="">请选择</option>
            <option v-for="district in availableDistricts" :key="district.code" :value="district.code">{{ district.name }}</option>
          </select></label>
        </div>
        <van-field
          v-model="form.detail"
          label="详细地址"
          type="textarea"
          maxlength="100"
          rows="2"
          autosize
          placeholder="街道、门牌号、楼层等"
        />
        <van-field v-model="form.label" label="标签" maxlength="8" placeholder="家 / 公司 / 学校" />
        <van-button block round type="danger" :loading="saving" @click="save">保存地址</van-button>
      </section>
    </van-popup>
  </div>
</template>

<style scoped>
.address-head { display: flex; align-items: flex-start; gap: 12px; }
.back-button {
  width: 34px; height: 34px; padding: 0; border: 1px solid var(--line); border-radius: 50%;
  background: transparent; color: var(--ink); font-size: 28px; line-height: 28px;
}
.page-loading { padding-top: 80px; }
.address-list { padding: 12px; }
.address-card {
  display: grid; grid-template-columns: 1fr auto; gap: 12px; margin-bottom: 11px; padding: 16px;
  border: 1px solid rgba(68,54,43,.08); border-radius: 14px; background: var(--card);
}
.address-meta { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.address-meta > span:not(.address-label) { color: var(--muted); }
.address-label { padding: 2px 6px; border-radius: 4px; background: var(--brand-soft); color: var(--brand-dark); font-size: 9px; }
.address-main p { margin: 10px 0 7px; font-size: 13px; line-height: 1.6; }
.address-main small { color: var(--forest); font-size: 10px; }
.address-actions { display: flex; flex-direction: column; justify-content: center; gap: 9px; border-left: 1px solid var(--line); padding-left: 12px; }
.address-actions button { padding: 0; border: 0; background: transparent; color: var(--muted); font-size: 11px; }
.add-address-bar {
  position: fixed; z-index: 4; left: 50%; bottom: 50px; width: min(520px, 100%); padding: 10px 14px;
  transform: translateX(-50%); border-top: 1px solid var(--line); background: var(--card);
}
.address-editor { padding: 22px 18px 28px; background: var(--card); }
.editor-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.editor-head h2 { margin: 5px 0 0; font-family: "STSong", "Songti SC", serif; font-size: 23px; }
.editor-head button { width: 32px; height: 32px; border: 1px solid var(--line); border-radius: 50%; background: transparent; font-size: 20px; }
.address-editor > .van-button { margin-top: 18px; }
.region-fields { display: grid; grid-template-columns: repeat(3, 1fr); gap: 7px; padding: 10px 16px; border-bottom: 1px solid #ebedf0; }
.region-fields label span { display: block; margin-bottom: 6px; color: #646566; font-size: 12px; }
.region-fields select { width: 100%; min-width: 0; padding: 9px 7px; border: 1px solid var(--line); border-radius: 7px; background: white; color: var(--ink); font-size: 12px; }
.region-fields select:disabled { background: #f4f2ef; color: #aaa; }
</style>
