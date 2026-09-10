<template>
  <div :class="['app-shell', { 'auth-mode': isAuthPage }]">
    <aside v-if="!isAuthPage" class="sidebar">
      <div class="brand">
        <span class="brand-mark">墨</span>
        <div>
          <h1>墨枢 InkNexus</h1>
          <p>微服务电商中台</p>
        </div>
      </div>

      <div v-if="currentUser" class="side-user">
        <strong>{{ currentUser.nickname || currentUser.username }}</strong>
        <span>ID {{ currentUser.userId }}</span>
      </div>

      <nav>
        <RouterLink to="/home">平台总览</RouterLink>
        <RouterLink to="/books">图书中心</RouterLink>
        <RouterLink to="/cart">购物车</RouterLink>
        <RouterLink to="/addresses">收货地址</RouterLink>
        <RouterLink to="/orders">订单中心</RouterLink>
        <RouterLink to="/ai">AI 助手</RouterLink>
      </nav>
    </aside>

    <main :class="['main-panel', { 'auth-main': isAuthPage }]">
      <header v-if="!isAuthPage" class="topbar">
        <div>
          <p class="eyebrow">Gateway Console</p>
          <h2>墨枢微服务前端控制台</h2>
        </div>
        <div class="topbar-actions">
          <span v-if="currentUser" class="session-pill">
            {{ currentUser.nickname || currentUser.username }}
          </span>
          <span class="service-pill">Gateway 在线</span>
          <button v-if="currentUser" class="ghost" type="button" @click="goHome">回总览</button>
          <button v-if="currentUser" class="primary" type="button" @click="logout">退出登录</button>
        </div>
      </header>

      <div :class="['content', { 'auth-content': isAuthPage }]">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { clearSession, getCurrentUser } from './utils/session'

const router = useRouter()
const route = useRoute()
const currentUser = computed(() => getCurrentUser())
const isAuthPage = computed(() => route.path === '/login')

function goHome() {
  router.push('/home')
}

function logout() {
  clearSession()
  router.push('/login')
}
</script>
