<template>
  <section class="card stack">
    <div class="section-head">
      <div>
        <p class="eyebrow">Address Book</p>
        <h3>收货地址</h3>
      </div>
      <button class="primary" type="button" @click="openCreate">新增地址</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="notice" class="notice">{{ notice }}</div>
    <div v-if="loading" class="muted">正在加载地址...</div>

    <div v-else-if="addresses.length" class="list-stack">
      <article v-for="address in addresses" :key="address.id" class="list-card address-card">
        <div class="address-main">
          <div class="address-title">
            <strong>{{ address.receiverName }}</strong>
            <span>{{ address.receiverPhone }}</span>
            <span v-if="address.isDefault === 1" class="default-tag">默认</span>
          </div>
          <p class="muted">{{ fullAddress(address) }}</p>
        </div>
        <div class="address-actions">
          <button class="ghost" type="button" :disabled="address.isDefault === 1" @click="setDefault(address)">
            设为默认
          </button>
          <button class="ghost" type="button" @click="openEdit(address)">编辑</button>
          <button class="ghost" type="button" @click="removeAddress(address)">删除</button>
        </div>
      </article>
    </div>
    <p v-else-if="!loading" class="muted">还没有保存收货地址。</p>

    <div v-if="formOpen" class="address-modal">
      <div class="address-card-form">
        <div class="section-head">
          <div>
            <p class="eyebrow">Address Form</p>
            <h4>{{ editingId ? '编辑地址' : '新增地址' }}</h4>
          </div>
          <button class="ghost" type="button" @click="closeForm">关闭</button>
        </div>

        <form class="form-grid address-form" @submit.prevent="submit">
          <label>
            <span>收货人</span>
            <input v-model="form.receiverName" placeholder="收货人姓名" />
          </label>
          <label>
            <span>收货电话</span>
            <input v-model="form.receiverPhone" placeholder="手机号" />
          </label>
          <label>
            <span>省份</span>
            <input v-model="form.province" placeholder="例如 广东省" />
          </label>
          <label>
            <span>城市</span>
            <input v-model="form.city" placeholder="例如 深圳市" />
          </label>
          <label>
            <span>区县</span>
            <input v-model="form.district" placeholder="例如 南山区" />
          </label>
          <label>
            <span>详细地址</span>
            <input v-model="form.detailAddress" placeholder="街道、小区、门牌号" />
          </label>
          <label class="default-check">
            <input v-model="form.isDefault" type="checkbox" />
            <span>设为默认地址</span>
          </label>
          <button class="primary full" type="submit" :disabled="submitting">
            {{ submitting ? '保存中...' : '保存地址' }}
          </button>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { addressApi } from '../api/inknexus'

const addresses = ref([])
const loading = ref(false)
const submitting = ref(false)
const formOpen = ref(false)
const editingId = ref(null)
const error = ref('')
const notice = ref('')
const form = reactive({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: false
})

function blankForm() {
  return {
    receiverName: '',
    receiverPhone: '',
    province: '',
    city: '',
    district: '',
    detailAddress: '',
    isDefault: false
  }
}

function fullAddress(address) {
  return [address.province, address.city, address.district, address.detailAddress].filter(Boolean).join(' ')
}

async function loadAddresses() {
  loading.value = true
  error.value = ''
  try {
    const data = await addressApi.list()
    addresses.value = Array.isArray(data) ? data : []
  } catch (e) {
    error.value = e.message || '地址加载失败'
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, blankForm())
  editingId.value = null
  formOpen.value = true
  error.value = ''
}

function openEdit(address) {
  Object.assign(form, {
    receiverName: address.receiverName || '',
    receiverPhone: address.receiverPhone || '',
    province: address.province || '',
    city: address.city || '',
    district: address.district || '',
    detailAddress: address.detailAddress || '',
    isDefault: address.isDefault === 1
  })
  editingId.value = address.id
  formOpen.value = true
  error.value = ''
}

function closeForm() {
  if (!submitting.value) {
    formOpen.value = false
  }
}

async function submit() {
  if (!form.receiverName.trim() || !form.receiverPhone.trim() || !form.detailAddress.trim()) {
    error.value = '请填写收货人、电话和详细地址'
    return
  }

  submitting.value = true
  error.value = ''
  notice.value = ''
  const payload = {
    receiverName: form.receiverName.trim(),
    receiverPhone: form.receiverPhone.trim(),
    province: form.province.trim(),
    city: form.city.trim(),
    district: form.district.trim(),
    detailAddress: form.detailAddress.trim(),
    isDefault: form.isDefault
  }

  try {
    if (editingId.value) {
      await addressApi.update(editingId.value, payload)
    } else {
      await addressApi.create(payload)
    }
    formOpen.value = false
    notice.value = editingId.value ? '地址已更新' : '地址已新增'
    await loadAddresses()
  } catch (e) {
    error.value = e.message || '保存地址失败'
  } finally {
    submitting.value = false
  }
}

async function setDefault(address) {
  try {
    await addressApi.setDefault(address.id)
    notice.value = '默认地址已更新'
    await loadAddresses()
  } catch (e) {
    error.value = e.message || '设置默认地址失败'
  }
}

async function removeAddress(address) {
  if (!window.confirm(`确认删除 ${address.receiverName} 的收货地址吗？`)) return
  try {
    await addressApi.remove(address.id)
    notice.value = '地址已删除'
    await loadAddresses()
  } catch (e) {
    error.value = e.message || '删除地址失败'
  }
}

onMounted(loadAddresses)
</script>

<style scoped>
.address-card {
  align-items: flex-start;
}

.address-main {
  min-width: 0;
}

.address-title {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
}

.address-title span {
  color: var(--muted);
  font-size: 0.9rem;
}

.default-tag {
  display: inline-flex;
  align-items: center;
  padding: 0.2rem 0.55rem;
  border-radius: 999px;
  background: var(--accent-soft);
  color: var(--accent) !important;
  font-weight: 700;
}

.address-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.address-modal {
  position: fixed;
  inset: 0;
  background: rgba(37, 31, 21, 0.45);
  display: grid;
  place-items: center;
  z-index: 100;
  padding: 1rem;
}

.address-card-form {
  width: 100%;
  max-width: 560px;
  background: var(--surface);
  border-radius: 22px;
  padding: 1.5rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  display: grid;
  gap: 1rem;
}

.address-card-form h4 {
  margin: 0.2rem 0 0;
}

.default-check {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.default-check input {
  width: 18px;
  height: 18px;
}

.default-check span {
  color: var(--ink);
}

@media (max-width: 720px) {
  .address-actions {
    width: 100%;
  }

  .address-actions button {
    flex: 1;
  }
}
</style>
