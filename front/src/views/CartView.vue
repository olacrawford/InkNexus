<template>
  <section class="card stack cart-panel">
    <div class="section-head">
      <div>
        <p class="eyebrow">Cart</p>
        <h3>购物车</h3>
      </div>
      <div class="toolbar">
        <button class="ghost" type="button" @click="loadCart">刷新</button>
        <button class="ghost" type="button" :disabled="!cartItems.length" @click="clearCart">清空</button>
      </div>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="notice" class="notice">{{ notice }}</div>

    <div v-if="loading" class="muted">正在加载购物车...</div>

    <template v-else>
      <div v-if="cartItems.length" class="cart-summary">
        <span>共 {{ cartItems.length }} 种商品</span>
        <span>已选 {{ selectedCount }} 件</span>
        <strong>合计 ￥{{ selectedTotal.toFixed(2) }}</strong>
        <button class="primary" type="button" :disabled="!selectedItems.length || hasStockIssue" @click="openCheckout">去结算</button>
      </div>

      <div v-if="cartItems.length" class="list-stack">
        <article v-for="item in cartItems" :key="item.id" class="list-card cart-item">
          <label class="select-cell">
            <input type="checkbox" :checked="item.selected === 1" @change="toggleSelected(item)" />
          </label>

          <div class="cover cart-cover">{{ coverText(item) }}</div>

          <div class="cart-info">
            <strong>{{ bookOf(item)?.title || ('图书 #' + item.bookId) }}</strong>
            <p class="muted">{{ bookOf(item)?.author || '图书商品' }}</p>
            <p v-if="stockWarning(item)" class="stock-warning">库存不足，可售 {{ availableStock(item) }}</p>
          </div>

          <span class="cart-price">￥{{ bookOf(item)?.price ?? 0 }}</span>

          <div class="qty-control">
            <button class="ghost" type="button" :disabled="item.quantity <= 1" @click="changeQuantity(item, -1)">-</button>
            <span>{{ item.quantity }}</span>
            <button class="ghost" type="button" @click="changeQuantity(item, 1)">+</button>
          </div>

          <strong class="cart-subtotal">￥{{ subtotal(item).toFixed(2) }}</strong>
          <button class="ghost" type="button" @click="removeItem(item)">删除</button>
        </article>
      </div>

      <div v-else class="empty-state">
        <p class="muted">购物车是空的。</p>
        <RouterLink class="primary" to="/books">去图书中心</RouterLink>
      </div>
    </template>

    <div v-if="checkoutOpen" class="checkout-modal">
      <div class="checkout-card">
        <div class="checkout-head">
          <div>
            <p class="eyebrow">Checkout</p>
            <h4>购物车结算</h4>
          </div>
          <button class="ghost" type="button" @click="closeCheckout">关闭</button>
        </div>

        <p class="muted">已选 {{ selectedCount }} 件 · 合计 ￥{{ selectedTotal.toFixed(2) }}</p>

        <form class="form-grid" @submit.prevent="submitCheckout">
          <label>
            <span>收货地址</span>
            <select v-model="selectedAddressId" @change="applyAddress">
              <option :value="null">手填收货信息</option>
              <option v-for="address in addresses" :key="address.id" :value="address.id">
                {{ addressLabel(address) }}
              </option>
            </select>
          </label>
          <label>
            <span>收货人</span>
            <input v-model="checkoutForm.receiverName" placeholder="收货人姓名" />
          </label>
          <label>
            <span>收货电话</span>
            <input v-model="checkoutForm.receiverPhone" placeholder="手机号" />
          </label>
          <label>
            <span>收货地址</span>
            <input v-model="checkoutForm.receiverAddress" placeholder="省市区 + 详细地址" />
          </label>
          <button class="primary full" type="submit" :disabled="checkoutSubmitting">
            {{ checkoutSubmitting ? '正在下单' : '确认下单' }}
          </button>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { addressApi, bookApi, cartApi, newRequestId, orderApi, stockApi } from '../api/bookmall'
import { getCurrentUser } from '../utils/session'

const cartItems = ref([])
const booksById = ref({})
const stocksById = ref({})
const error = ref('')
const notice = ref('')
const loading = ref(false)
const checkoutOpen = ref(false)
const checkoutSubmitting = ref(false)
// 幂等请求号：每次打开结算弹窗生成，同一弹窗内重试复用，防止重复提交产生多笔订单
const checkoutRequestId = ref('')
const addresses = ref([])
const selectedAddressId = ref(null)
const checkoutForm = reactive({ receiverName: '', receiverPhone: '', receiverAddress: '' })

const selectedItems = computed(() => cartItems.value.filter((item) => item.selected === 1))
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0))
const selectedTotal = computed(() => {
  return selectedItems.value.reduce((sum, item) => {
    const price = Number(bookOf(item)?.price || 0)
    return sum + price * item.quantity
  }, 0)
})
const hasStockIssue = computed(() => selectedItems.value.some((item) => stockWarning(item)))

function bookOf(item) {
  return booksById.value[item.bookId]
}

function coverText(item) {
  return (bookOf(item)?.title || `BK${item.bookId}`).slice(0, 2)
}

function subtotal(item) {
  const price = Number(bookOf(item)?.price || 0)
  return price * item.quantity
}

async function loadAddresses() {
  try {
    const data = await addressApi.list()
    addresses.value = Array.isArray(data) ? data : []
  } catch {
    addresses.value = []
  }
}

function addressLabel(address) {
  const full = [address.province, address.city, address.district, address.detailAddress].filter(Boolean).join(' ')
  return `${address.receiverName} · ${full}`
}

function applyAddress() {
  const address = addresses.value.find((item) => item.id === selectedAddressId.value)
  if (!address) {
    checkoutForm.receiverName = ''
    checkoutForm.receiverPhone = ''
    checkoutForm.receiverAddress = ''
    return
  }
  checkoutForm.receiverName = address.receiverName
  checkoutForm.receiverPhone = address.receiverPhone
  checkoutForm.receiverAddress = [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join(' ')
}

async function loadCart() {
  if (!getCurrentUser()?.userId) {
    error.value = '请先登录后再查看购物车'
    return
  }

  loading.value = true
  error.value = ''
  notice.value = ''
  try {
    const data = await cartApi.list()
    cartItems.value = Array.isArray(data) ? data : []
    await loadBooks()
    await loadStocks()
  } catch (e) {
    error.value = e.message || '购物车加载失败'
  } finally {
    loading.value = false
  }
}

async function loadBooks() {
  await Promise.all(cartItems.value.map(async (item) => {
    if (booksById.value[item.bookId]) return
    try {
      booksById.value[item.bookId] = await bookApi.detail(item.bookId)
    } catch {
      booksById.value[item.bookId] = {
        id: item.bookId,
        title: `图书 #${item.bookId}`,
        author: '',
        price: 0
      }
    }
  }))
}

async function loadStocks() {
  const ids = [...new Set(cartItems.value.map((item) => item.bookId))]
  const results = await Promise.allSettled(ids.map((id) => stockApi.detail(id)))
  const next = {}
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      next[ids[index]] = result.value
    }
  })
  stocksById.value = next
}

function stockOf(item) {
  return stocksById.value[item.bookId]
}

function availableStock(item) {
  return Number(stockOf(item)?.availableStock ?? 0)
}

function stockWarning(item) {
  const stock = stockOf(item)
  return stock != null && item.quantity > availableStock(item)
}

async function toggleSelected(item) {
  const next = item.selected === 1 ? 0 : 1
  try {
    const updated = await cartApi.update(item.id, {
      quantity: item.quantity,
      selected: next === 1
    })
    item.selected = updated.selected
    error.value = ''
  } catch (e) {
    error.value = e.message || '更新勾选状态失败'
  }
}

async function changeQuantity(item, delta) {
  const next = item.quantity + delta
  if (next < 1) return
  if (stockOf(item) && next > availableStock(item)) {
    error.value = '库存不足，无法继续增加数量'
    return
  }
  try {
    const updated = await cartApi.update(item.id, {
      quantity: next,
      selected: item.selected === 1
    })
    item.quantity = updated.quantity
    item.selected = updated.selected
    error.value = ''
  } catch (e) {
    error.value = e.message || '更新数量失败'
  }
}

async function removeItem(item) {
  try {
    await cartApi.remove(item.id)
    cartItems.value = cartItems.value.filter((it) => it.id !== item.id)
    error.value = ''
    notice.value = '已删除购物车商品'
  } catch (e) {
    error.value = e.message || '删除失败'
  }
}

async function clearCart() {
  if (!cartItems.value.length) return
  if (!window.confirm('确认清空购物车吗？')) return
  try {
    await cartApi.clear()
    cartItems.value = []
    error.value = ''
    notice.value = '购物车已清空'
  } catch (e) {
    error.value = e.message || '清空购物车失败'
  }
}

function openCheckout() {
  if (!selectedItems.value.length) return
  if (!getCurrentUser()?.userId) {
    error.value = '请先登录'
    return
  }
  error.value = ''
  selectedAddressId.value = null
  checkoutForm.receiverName = ''
  checkoutForm.receiverPhone = ''
  checkoutForm.receiverAddress = ''
  checkoutRequestId.value = newRequestId()
  checkoutOpen.value = true
  loadAddresses()
}

function closeCheckout() {
  if (!checkoutSubmitting.value) {
    checkoutOpen.value = false
  }
}

async function submitCheckout() {
  if (!getCurrentUser()?.userId) {
    error.value = '请先登录'
    return
  }
  if (!checkoutForm.receiverName.trim() || !checkoutForm.receiverPhone.trim() || !checkoutForm.receiverAddress.trim()) {
    error.value = '请填写完整的收货信息'
    return
  }
  if (hasStockIssue.value) {
    error.value = '所选商品库存不足，请先调整数量'
    return
  }

  checkoutSubmitting.value = true
  error.value = ''
  notice.value = ''
  try {
    const selectedIds = selectedItems.value.map((item) => item.id)
    await orderApi.createFromCart({
      clientRequestId: checkoutRequestId.value,
      receiverName: checkoutForm.receiverName,
      receiverPhone: checkoutForm.receiverPhone,
      receiverAddress: checkoutForm.receiverAddress
    })

    const failed = []
    for (const item of selectedItems.value) {
      try {
        await cartApi.remove(item.id)
      } catch {
        failed.push(item.id)
      }
    }

    cartItems.value = cartItems.value.filter((item) => !selectedIds.includes(item.id) || failed.includes(item.id))
    checkoutOpen.value = false
    notice.value = failed.length ? '下单成功，部分购物车商品需要手动清理' : '下单成功'
  } catch (e) {
    error.value = e.message || '下单失败'
  } finally {
    checkoutSubmitting.value = false
  }
}

onMounted(loadCart)
</script>

<style scoped>
.cart-panel {
  gap: 1rem;
}

.cart-summary {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
  padding: 0.9rem 1rem;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--surface-2);
}

.cart-item {
  display: grid;
  grid-template-columns: auto 64px minmax(0, 1fr) auto auto auto auto;
  align-items: center;
  gap: 1rem;
}

.select-cell {
  display: flex;
  align-items: center;
}

.select-cell input {
  width: 18px;
  height: 18px;
}

.cart-cover {
  width: 64px;
  min-height: 64px;
  height: 64px;
  font-size: 1.1rem;
}

.cart-info {
  min-width: 0;
}

.cart-info strong,
.cart-info p {
  overflow-wrap: anywhere;
}

.stock-warning {
  margin-top: 0.25rem;
  color: #b5475a;
  font-size: 0.9rem;
  font-weight: 700;
}

.cart-price,
.cart-subtotal {
  white-space: nowrap;
}

.qty-control {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.qty-control button {
  min-width: 38px;
  padding: 0.45rem 0.6rem;
}

.qty-control span {
  min-width: 2.2rem;
  text-align: center;
}

.empty-state {
  display: grid;
  justify-items: start;
  gap: 0.75rem;
  padding: 1.25rem;
  border-radius: 16px;
  border: 1px solid var(--line);
  background: var(--surface-2);
}

.empty-state a {
  text-decoration: none;
}

.checkout-modal {
  position: fixed;
  inset: 0;
  background: rgba(37, 31, 21, 0.45);
  display: grid;
  place-items: center;
  z-index: 100;
  padding: 1rem;
}

.checkout-card {
  width: 100%;
  max-width: 480px;
  background: var(--surface);
  border-radius: 22px;
  padding: 1.5rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  display: grid;
  gap: 1rem;
}

.checkout-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.checkout-head h4 {
  margin: 0.2rem 0 0;
  font-size: 1.2rem;
}

@media (max-width: 880px) {
  .cart-item {
    grid-template-columns: auto 64px minmax(0, 1fr) auto auto;
  }

  .cart-subtotal {
    grid-column: 4 / 6;
    justify-self: end;
  }
}

@media (max-width: 640px) {
  .cart-item {
    grid-template-columns: auto 64px minmax(0, 1fr);
    align-items: start;
  }

  .cart-price,
  .qty-control,
  .cart-subtotal {
    grid-column: 2 / 4;
  }

  .cart-item > button {
    grid-column: 3;
    justify-self: end;
  }
}
</style>
