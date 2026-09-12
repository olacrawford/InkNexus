import { ref } from 'vue'

const SESSION_KEY = 'inknexus_session'
const TOKEN_KEY = 'inknexus_token'

// 兼容历史 session 结构，统一归一化成 { token, user: { userId, username, nickname } }
function normalize(data) {
  if (!data) return { token: '', user: null }

  const rawUser = data.user || data
  const user = rawUser && typeof rawUser === 'object'
    ? {
        userId: rawUser.userId ?? rawUser.id ?? data.userId ?? data.id ?? null,
        username: rawUser.username ?? data.username ?? '',
        nickname: rawUser.nickname ?? data.nickname ?? rawUser.username ?? data.username ?? ''
      }
    : null

  return {
    token: data.token || '',
    user
  }
}

function loadSession() {
  try {
    const raw = localStorage.getItem(SESSION_KEY)
    if (!raw) return null
    return normalize(JSON.parse(raw))
  } catch {
    return null
  }
}

export const session = ref(loadSession())

export function saveSession(data) {
  const normalized = normalize(data)
  session.value = normalized
  localStorage.setItem(SESSION_KEY, JSON.stringify(normalized))
  if (normalized.token) {
    localStorage.setItem(TOKEN_KEY, normalized.token)
  }
}

export function clearSession() {
  session.value = null
  localStorage.removeItem(SESSION_KEY)
  localStorage.removeItem(TOKEN_KEY)
}

export function getCurrentUser() {
  return session.value ? normalize(session.value).user : null
}

export function getCurrentToken() {
  return session.value?.token || localStorage.getItem(TOKEN_KEY) || ''
}

export function isLoggedIn() {
  return Boolean(getCurrentToken())
}
