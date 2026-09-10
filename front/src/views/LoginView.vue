<template>
  <section class="card login-card auth-card">
    <div class="auth-hero">
      <p class="eyebrow">墨枢 InkNexus</p>
      <h3>{{ mode === 'login' ? '欢迎回来' : '创建账号' }}</h3>
      <p class="muted auth-hint">先登录或注册，再浏览图书、下单和管理订单。</p>
    </div>

    <div class="tab-row auth-tabs">
      <button class="ghost" type="button" :class="{ active: mode === 'login' }" @click="switchMode('login')">登录</button>
      <button class="ghost" type="button" :class="{ active: mode === 'register' }" @click="switchMode('register')">注册</button>
    </div>

    <div v-if="error" class="alert">{{ error }}</div>
    <div v-if="notice" class="notice">{{ notice }}</div>

    <form class="form-grid" @submit.prevent="submit">
      <label>
        <span>用户名</span>
        <input v-model="form.username" placeholder="请输入用户名" autocomplete="username" autocapitalize="off" spellcheck="false" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="form.password" type="password" placeholder="请输入密码" autocomplete="current-password" />
      </label>

      <template v-if="mode === 'register'">
        <label>
          <span>昵称</span>
          <input v-model="form.nickname" placeholder="昵称（可选）" />
        </label>
        <label>
          <span>手机号</span>
          <input v-model="form.phone" placeholder="手机号（可选）" />
        </label>
        <label>
          <span>邮箱</span>
          <input v-model="form.email" placeholder="邮箱（可选）" />
        </label>
      </template>

      <button class="primary full" type="submit" :disabled="submitting">
        {{ submitting ? '提交中...' : mode === 'login' ? '登录' : '注册' }}
      </button>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api/bookmall'
import { saveSession } from '../utils/session'

const router = useRouter()
const mode = ref('login')
const submitting = ref(false)
const error = ref('')
const notice = ref('')
const form = reactive({ username: '', password: '', nickname: '', phone: '', email: '' })

function switchMode(next) {
  mode.value = next
  error.value = ''
  notice.value = ''
}

async function submit() {
  error.value = ''
  notice.value = ''
  if (!form.username.trim() || !form.password) {
    error.value = '请输入用户名和密码'
    return
  }

  submitting.value = true
  try {
    if (mode.value === 'login') {
      const data = await authApi.login({ username: form.username, password: form.password })
      saveSession(data)
      router.push('/home')
    } else {
      await authApi.register({
        username: form.username,
        password: form.password,
        nickname: form.nickname,
        phone: form.phone,
        email: form.email
      })
      notice.value = '注册成功，请登录'
      switchMode('login')
      form.password = ''
    }
  } catch (e) {
    error.value = e.message || '操作失败'
  } finally {
    submitting.value = false
  }
}
</script>
