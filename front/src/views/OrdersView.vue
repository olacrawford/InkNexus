<template>
  <section class="card stack">
    <div class="section-head">
      <div>
        <p class="eyebrow">Orders</p>
        <h3>订单</h3>
      </div>
      <button class="ghost" type="button" @click="loadOrders">刷新</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="loading" class="muted">正在同步订单数据...</div>

    <template v-else>
      <div v-if="orders.length" class="list-stack">
        <article v-for="order in orders" :key="order.id" class="list-card order-card">
          <div>
            <strong>{{ order.orderNo }}</strong>
            <p class="muted">状态：{{ orderStatusText(order.status) }} · 金额：￥{{ order.totalAmount }}</p>
          </div>
          <div class="row">
            <button class="ghost" type="button" @click="loadDetail(order.id)">详情</button>
            <button v-if="order.status === 0" class="primary" type="button" @click="payOrder(order.id)">立即支付</button>
            <button v-if="order.status === 0" class="ghost" type="button" @click="cancelOrder(order.id)">取消订单</button>
            <button v-if="order.status === 1" class="primary" type="button" @click="completeOrder(order.id)">确认收货</button>
          </div>
        </article>
      </div>
      <p v-else class="muted">还没有订单，先去图书页挑本书下单吧。</p>
    </template>

    <section v-if="detail" class="sub-card">
      <h4>订单详情</h4>
      <p class="muted">收货人：{{ detail.receiverName }} · 电话：{{ detail.receiverPhone }}</p>
      <p class="muted">地址：{{ detail.receiverAddress }}</p>
      <div class="list-stack">
        <div v-for="item in detail.items || []" :key="item.bookId" class="mini-row">
          <span>{{ item.bookTitle }}</span>
          <span>×{{ item.quantity }}</span>
          <strong>￥{{ item.subtotal }}</strong>
        </div>
      </div>
    </section>
  </section>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { orderApi, paymentApi } from '../api/inknexus'
import { getCurrentUser } from '../utils/session'

const orders = ref([])
const detail = ref(null)
const error = ref('')
const loading = ref(false)

function orderStatusText(status) {
  if (status === 0) return '待支付'
  if (status === 1) return '已支付'
  if (status === 2) return '已取消'
  if (status === 3) return '已完成'
  return status ?? '未知'
}

async function loadOrders() {
  const user = getCurrentUser()
  if (!user?.userId) {
    error.value = '请先登录后再查看订单'
    return
  }

  loading.value = true
  error.value = ''
  try {
    orders.value = await orderApi.list()
    detail.value = null
  } catch (e) {
    error.value = e.message || '订单列表加载失败'
  } finally {
    loading.value = false
  }
}

async function loadDetail(id) {
  try {
    detail.value = await orderApi.detail(id)
  } catch (e) {
    error.value = e.message || '订单详情加载失败'
  }
}

async function cancelOrder(id) {
  try {
    await orderApi.cancel(id)
    detail.value = null
    await loadOrders()
  } catch (e) {
    error.value = e.message || '取消订单失败'
  }
}

async function completeOrder(id) {
  try {
    await orderApi.complete(id)
    detail.value = null
    await loadOrders()
  } catch (e) {
    error.value = e.message || '确认收货失败'
  }
}

async function payOrder(id) {
  try {
    await paymentApi.pay(id)
    detail.value = null
    await loadOrders()
  } catch (e) {
    error.value = e.message || '支付失败'
  }
}

onMounted(loadOrders)
</script>

<style scoped>
.list-stack {
  display: grid;
  gap: 0.75rem;
}

.list-card {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  align-items: center;
  padding: 1rem;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.6);
}

.order-card {
  align-items: flex-start;
}

.sub-card {
  padding: 1rem;
  border-radius: 20px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.62);
}

.mini-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}
</style>
