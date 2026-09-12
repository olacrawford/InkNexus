<template>
  <section class="card stack">
    <div class="section-head">
      <div>
        <p class="eyebrow">Books</p>
        <h3>图书列表</h3>
      </div>
      <div class="inline-form">
        <select v-model="selectedCategoryId" @change="search">
          <option :value="null">全部分类</option>
          <option v-for="c in categories" :key="c.id" :value="c.id">{{ c.name }}</option>
        </select>
        <input v-model="keyword" placeholder="搜索书名" @keyup.enter="search" />
        <button class="primary" type="button" @click="search">搜索</button>
      </div>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="notice" class="notice">{{ notice }}</div>

    <div class="meta-line" v-if="total > 0">共 {{ total }} 本 · 第 {{ pageNum }} / {{ pages }} 页</div>

    <div v-if="loading" class="muted">正在加载图书...</div>

    <div v-else-if="books.length" class="grid cards">
      <article v-for="book in books" :key="book.id" class="book-card">
        <div class="cover">{{ (book.title || 'BK').slice(0, 2) }}</div>
        <h4>{{ book.title }}</h4>
        <p>{{ book.author || '匿名作者' }}</p>
        <p v-if="stockOf(book)" class="stock-line" :class="{ 'stock-out': isSoldOut(book) }">
          {{ isSoldOut(book) ? '暂时缺货' : `库存 ${availableStock(book)}` }}
        </p>
        <div class="row book-row">
          <strong>￥{{ book.price }}</strong>
          <div class="book-actions">
            <button class="ghost" type="button" :disabled="isSoldOut(book)" @click="addToCart(book)">加入购物车</button>
            <button class="primary" type="button" :disabled="isSoldOut(book)" @click="openBuy(book)">立即购买</button>
          </div>
        </div>
      </article>
    </div>
    <p v-else class="muted">暂无图书。</p>

    <div v-if="pages > 1" class="pagination">
      <button class="ghost" type="button" :disabled="pageNum <= 1" @click="goPage(pageNum - 1)">上一页</button>
      <span class="muted">{{ pageNum }} / {{ pages }}</span>
      <button class="ghost" type="button" :disabled="pageNum >= pages" @click="goPage(pageNum + 1)">下一页</button>
    </div>

    <div v-if="buyingBook" class="buy-modal">
      <div class="buy-card">
        <div class="buy-head">
          <div>
            <p class="eyebrow">Checkout</p>
            <h4>{{ buyingBook.title }}</h4>
          </div>
          <button class="ghost" type="button" @click="closeBuy">关闭</button>
        </div>

        <div class="buy-meta">
          <span>单价 ￥{{ buyingBook.price }}</span>
          <span class="buy-total">合计 ￥{{ (Number(buyingBook.price) * orderForm.quantity).toFixed(2) }}</span>
        </div>

        <form class="form-grid" @submit.prevent="submitOrder">
          <label>
            <span>购买数量</span>
            <input v-model.number="orderForm.quantity" type="number" min="1" />
          </label>
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
            <input v-model="orderForm.receiverName" placeholder="收货人姓名" />
          </label>
          <label>
            <span>收货电话</span>
            <input v-model="orderForm.receiverPhone" placeholder="手机号" />
          </label>
          <label>
            <span>收货地址</span>
            <input v-model="orderForm.receiverAddress" placeholder="省市区 + 详细地址" />
          </label>
          <button class="primary full" type="submit">确认下单</button>
        </form>
      </div>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { addressApi, bookApi, cartApi, newRequestId, orderApi, stockApi } from '../api/inknexus'
import { getCurrentUser } from '../utils/session'

const books = ref([])
const keyword = ref('')
const pageNum = ref(1)
const pageSize = ref(8)
const total = ref(0)
const pages = ref(0)
const error = ref('')
const notice = ref('')
const loading = ref(false)
const categories = ref([])
const selectedCategoryId = ref(null)
const addresses = ref([])
const selectedAddressId = ref(null)
const buyingBook = ref(null)
// 幂等请求号：每次打开下单弹窗生成，同一弹窗内重试复用，防止重复提交产生多笔订单
const orderRequestId = ref('')
const orderForm = reactive({ quantity: 1, receiverName: '', receiverPhone: '', receiverAddress: '' })
const stocksById = ref({})

async function loadBooks() {
  loading.value = true
  error.value = ''
  try {
    const data = await bookApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value.trim() || undefined,
      categoryId: selectedCategoryId.value || undefined
    })
    books.value = data.records || []
    total.value = data.total || 0
    pages.value = data.pages || 0
    pageNum.value = data.current || 1
    loadStocks()
  } catch (e) {
    error.value = e.message || '加载图书失败'
  } finally {
    loading.value = false
  }
}

async function loadStocks() {
  const ids = books.value.map((book) => book.id)
  const results = await Promise.allSettled(ids.map((id) => stockApi.detail(id)))
  const next = {}
  results.forEach((result, index) => {
    if (result.status === 'fulfilled') {
      next[ids[index]] = result.value
    }
  })
  stocksById.value = next
}

function stockOf(book) {
  return stocksById.value[book.id]
}

function availableStock(book) {
  return Number(stockOf(book)?.availableStock ?? 0)
}

function isSoldOut(book) {
  return stockOf(book) != null && availableStock(book) <= 0
}

function search() {
  pageNum.value = 1
  loadBooks()
}

async function loadCategories() {
  try {
    categories.value = await bookApi.categories()
  } catch (e) {
    // 分类加载失败不阻塞图书列表
  }
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
    orderForm.receiverName = ''
    orderForm.receiverPhone = ''
    orderForm.receiverAddress = ''
    return
  }
  orderForm.receiverName = address.receiverName
  orderForm.receiverPhone = address.receiverPhone
  orderForm.receiverAddress = [address.province, address.city, address.district, address.detailAddress]
    .filter(Boolean)
    .join(' ')
}

function goPage(p) {
  if (p < 1 || p > pages.value) return
  pageNum.value = p
  loadBooks()
}

function openBuy(book) {
  if (!getCurrentUser()?.userId) {
    error.value = '请先登录'
    return
  }
  if (isSoldOut(book)) {
    error.value = '该图书库存不足'
    return
  }
  buyingBook.value = book
  orderForm.quantity = 1
  orderForm.receiverName = ''
  orderForm.receiverPhone = ''
  orderForm.receiverAddress = ''
  selectedAddressId.value = null
  orderRequestId.value = newRequestId()
  error.value = ''
  loadAddresses()
}

function closeBuy() {
  buyingBook.value = null
}

async function addToCart(book) {
  if (!getCurrentUser()?.userId) {
    error.value = '请先登录'
    return
  }
  if (isSoldOut(book)) {
    error.value = '该图书库存不足'
    return
  }
  try {
    await cartApi.add({ bookId: book.id, quantity: 1, selected: true })
    error.value = ''
    notice.value = `已加入购物车：${book.title}`
  } catch (e) {
    error.value = e.message || '加入购物车失败'
  }
}

async function submitOrder() {
  if (!buyingBook.value) return
  if (!getCurrentUser()?.userId) {
    error.value = '请先登录'
    return
  }
  if (!orderForm.receiverName.trim() || !orderForm.receiverPhone.trim() || !orderForm.receiverAddress.trim()) {
    error.value = '请填写完整的收货信息'
    return
  }

  try {
    await orderApi.create({
      bookId: buyingBook.value.id,
      quantity: orderForm.quantity,
      clientRequestId: orderRequestId.value,
      receiverName: orderForm.receiverName,
      receiverPhone: orderForm.receiverPhone,
      receiverAddress: orderForm.receiverAddress
    })
    error.value = ''
    notice.value = `下单成功：${buyingBook.value.title} × ${orderForm.quantity}`
    closeBuy()
  } catch (e) {
    error.value = e.message || '下单失败'
  }
}

onMounted(() => {
  loadCategories()
  loadBooks()
})
</script>

<style scoped>
.inline-form {
  display: flex;
  gap: 0.5rem;
}

.book-actions {
  display: flex;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.book-row {
  align-items: center;
}

.inline-form input {
  width: 240px;
}

.inline-form select {
  width: 160px;
}

.meta-line {
  color: var(--muted);
  font-size: 0.9rem;
}

.stock-line {
  color: var(--muted);
  font-size: 0.9rem;
}

.stock-out {
  color: #b5475a;
  font-weight: 700;
}

.pagination {
  display: flex;
  align-items: center;
  gap: 1rem;
  justify-content: center;
}

.buy-modal {
  position: fixed;
  inset: 0;
  background: rgba(37, 31, 21, 0.45);
  display: grid;
  place-items: center;
  z-index: 100;
  padding: 1rem;
}

.buy-card {
  width: 100%;
  max-width: 480px;
  background: var(--surface);
  border-radius: 22px;
  padding: 1.5rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.2);
  display: grid;
  gap: 1rem;
}

.buy-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
}

.buy-head h4 {
  margin: 0.2rem 0 0;
  font-size: 1.2rem;
}

.buy-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.6rem 0.9rem;
  border-radius: 12px;
  background: var(--accent-soft);
  color: var(--accent);
  font-size: 0.9rem;
}

.buy-total {
  font-weight: 700;
  font-size: 1rem;
}

@media (max-width: 720px) {
  .inline-form {
    flex-direction: column;
  }

  .inline-form input {
    width: 100%;
  }

  .book-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
