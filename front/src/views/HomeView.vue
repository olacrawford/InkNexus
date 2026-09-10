<template>
  <section class="stack home-stack">
    <section class="hero-panel">
      <div>
        <p class="eyebrow">Platform Overview</p>
        <h3>墨枢微服务业务总览</h3>
        <p class="muted hero-copy">
          当前前端已经对接网关入口，围绕认证、图书、购物车、收货地址、订单等核心服务组织页面结构。
          这里更像一个已经上线的业务控制台，而不只是接口调试页。
        </p>
      </div>
      <div class="hero-actions">
        <RouterLink class="primary home-link" to="/books">进入图书中心</RouterLink>
        <RouterLink class="ghost home-link" to="/cart">查看购物车</RouterLink>
        <RouterLink class="ghost home-link" to="/orders">查看订单中心</RouterLink>
      </div>
    </section>

    <section class="grid stat-grid">
      <article class="card stat-card">
        <p class="eyebrow">Services</p>
        <strong>{{ probes.length }}</strong>
        <span class="muted">已接入服务数</span>
      </article>
      <article class="card stat-card">
        <p class="eyebrow">Healthy</p>
        <strong>{{ healthyCount }}</strong>
        <span class="muted">当前可访问服务</span>
      </article>
      <article class="card stat-card">
        <p class="eyebrow">Degraded</p>
        <strong>{{ degradedCount }}</strong>
        <span class="muted">需要排查服务</span>
      </article>
      <article class="card stat-card">
        <p class="eyebrow">Gateway</p>
        <strong>8080</strong>
        <span class="muted">统一流量入口</span>
      </article>
    </section>

    <section class="grid console-grid">
      <section class="card stack">
        <div class="section-head">
          <div>
            <p class="eyebrow">Service Mesh</p>
            <h3>服务探针</h3>
          </div>
          <button class="ghost" type="button" @click="checkAll">全部重试</button>
        </div>

        <div v-if="error" class="alert">{{ error }}</div>

        <div class="service-list">
          <article v-for="item in probes" :key="item.name" class="service-card">
            <div>
              <strong>{{ item.name }}</strong>
              <p class="muted">{{ item.url }}</p>
            </div>
            <div class="service-status">
              <span :class="['status-chip', statusClass(item.status)]">{{ item.status }}</span>
              <button class="ghost" type="button" @click="check(item)">检查</button>
            </div>
          </article>
        </div>
      </section>

      <section class="card stack">
        <div>
          <p class="eyebrow">Business Entry</p>
          <h3>业务入口</h3>
        </div>

        <div class="entry-list">
          <RouterLink class="entry-card" to="/books">
            <strong>图书中心</strong>
            <p class="muted">浏览图书、分页查询并直接下单</p>
          </RouterLink>
          <RouterLink class="entry-card" to="/cart">
            <strong>购物车</strong>
            <p class="muted">查看购物车、调整数量并管理商品</p>
          </RouterLink>
          <RouterLink class="entry-card" to="/addresses">
            <strong>收货地址</strong>
            <p class="muted">维护常用地址并设置默认地址</p>
          </RouterLink>
          <RouterLink class="entry-card" to="/orders">
            <strong>订单中心</strong>
            <p class="muted">查看订单状态、详情、支付和确认收货</p>
          </RouterLink>
        </div>
      </section>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import http from '../api/http'

const error = ref('')
const probes = reactive([
  { name: 'Auth Service', url: '/api/auth/hello', status: '未检查' },
  { name: 'Book Service', url: '/api/books/hello', status: '未检查' },
  { name: 'Cart Service', url: '/api/cart/hello', status: '未检查' },
  { name: 'Stock Service', url: '/api/stock/hello', status: '未检查' },
  { name: 'Order Service', url: '/api/orders/hello', status: '未检查' },
  { name: 'Payment Service', url: '/api/payment/hello', status: '未检查' }
])

const healthyCount = computed(() => probes.filter((item) => item.status !== '失败' && item.status !== '未检查').length)
const degradedCount = computed(() => probes.filter((item) => item.status === '失败').length)

function statusClass(status) {
  if (status === '失败') return 'is-down'
  if (status === '未检查') return 'is-idle'
  return 'is-up'
}

async function check(item) {
  try {
    const response = await http.get(item.url)
    item.status = response.data?.data || 'OK'
    error.value = ''
  } catch (e) {
    item.status = '失败'
    error.value = e.message || '接口检查失败'
  }
}

async function checkAll() {
  for (const item of probes) {
    await check(item)
  }
}

onMounted(checkAll)
</script>

<style scoped>
.home-stack {
  gap: 1.25rem;
}

.hero-panel {
  display: flex;
  justify-content: space-between;
  gap: 1.5rem;
  flex-wrap: wrap;
  padding: 1.75rem;
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(37, 31, 21, 0.96), rgba(88, 52, 14, 0.9));
  color: #f8f2e7;
}

.hero-panel .eyebrow,
.hero-panel .muted {
  color: rgba(248, 242, 231, 0.78);
}

.hero-panel h3 {
  margin: 0;
  font-size: 2rem;
}

.hero-copy {
  max-width: 54ch;
}

.hero-actions {
  display: flex;
  gap: 0.75rem;
  align-items: flex-start;
  flex-wrap: wrap;
}

.home-link {
  text-decoration: none;
}

.stat-grid {
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

.stat-card {
  display: grid;
  gap: 0.35rem;
}

.stat-card strong {
  font-size: 2rem;
  line-height: 1;
}

.console-grid {
  grid-template-columns: 1.4fr 1fr;
  align-items: start;
}

.service-list,
.entry-list {
  display: grid;
  gap: 0.85rem;
}

.service-card,
.entry-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  padding: 1rem;
  border-radius: 18px;
  border: 1px solid var(--line);
  background: rgba(255, 255, 255, 0.72);
}

.entry-card {
  display: grid;
  text-decoration: none;
  color: inherit;
}

.entry-card strong,
.service-card strong {
  font-size: 1rem;
}

.entry-card p,
.service-card p {
  margin: 0.2rem 0 0;
}

.service-status {
  display: flex;
  align-items: center;
  gap: 0.75rem;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 86px;
  padding: 0.45rem 0.75rem;
  border-radius: 999px;
  font-size: 0.85rem;
  border: 1px solid transparent;
}

.status-chip.is-up {
  background: rgba(21, 128, 61, 0.1);
  color: #166534;
  border-color: rgba(21, 128, 61, 0.18);
}

.status-chip.is-down {
  background: rgba(220, 38, 38, 0.1);
  color: #b91c1c;
  border-color: rgba(220, 38, 38, 0.2);
}

.status-chip.is-idle {
  background: rgba(140, 127, 104, 0.1);
  color: #6b5f4b;
  border-color: rgba(140, 127, 104, 0.18);
}

@media (max-width: 960px) {
  .console-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .service-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .service-status {
    width: 100%;
    justify-content: space-between;
  }

  .hero-panel h3 {
    font-size: 1.6rem;
  }
}
</style>
