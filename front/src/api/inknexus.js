import http from './http'
import { unwrapResult } from './result'

// 下单幂等请求号：每次发起下单前生成一次，重试/超时重发时复用同一个值
export function newRequestId() {
  return typeof crypto !== 'undefined' && crypto.randomUUID
    ? crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

export const authApi = {
  login(payload) {
    return http.post('/api/auth/login', payload).then(unwrapResult)
  },
  register(payload) {
    return http.post('/api/auth/register', payload).then(unwrapResult)
  }
}

export const addressApi = {
  list() {
    return http.get('/api/auth/addresses').then(unwrapResult)
  },
  create(payload) {
    return http.post('/api/auth/addresses', payload).then(unwrapResult)
  },
  update(id, payload) {
    return http.put(`/api/auth/addresses/${id}`, payload).then(unwrapResult)
  },
  setDefault(id) {
    return http.put(`/api/auth/addresses/${id}/default`).then(unwrapResult)
  },
  remove(id) {
    return http.delete(`/api/auth/addresses/${id}`).then(unwrapResult)
  }
}

export const bookApi = {
  list() {
    return http.get('/api/books').then(unwrapResult)
  },
  detail(id) {
    return http.get(`/api/books/${id}`).then(unwrapResult)
  },
  page(params) {
    return http.get('/api/books/page', { params }).then(unwrapResult)
  },
  categories() {
    return http.get('/api/books/categories').then(unwrapResult)
  }
}

export const stockApi = {
  detail(bookId) {
    return http.get(`/api/stock/${bookId}`).then(unwrapResult)
  }
}

export const cartApi = {
  list() {
    return http.get('/api/cart').then(unwrapResult)
  },
  add(payload) {
    return http.post('/api/cart', payload).then(unwrapResult)
  },
  update(id, payload) {
    return http.put(`/api/cart/${id}`, payload).then(unwrapResult)
  },
  remove(id) {
    return http.delete(`/api/cart/${id}`).then(unwrapResult)
  },
  clear() {
    return http.delete('/api/cart').then(unwrapResult)
  }
}

export const orderApi = {
  list() {
    return http.get('/api/orders').then(unwrapResult)
  },
  detail(id) {
    return http.get(`/api/orders/${id}`).then(unwrapResult)
  },
  create(payload) {
    return http.post('/api/orders', payload).then(unwrapResult)
  },
  createFromCart(payload) {
    return http.post('/api/orders/from-cart', payload).then(unwrapResult)
  },
  cancel(id) {
    return http.put(`/api/orders/${id}/cancel`).then(unwrapResult)
  },
  complete(id) {
    return http.put(`/api/orders/${id}/complete`).then(unwrapResult)
  }
}

export const paymentApi = {
  pay(orderId) {
    return http.post('/api/payment/pay', { orderId }).then(unwrapResult)
  },
  detail(orderId) {
    return http.get(`/api/payment/order/${orderId}`).then(unwrapResult)
  }
}

export const aiApi = {
  chat(payload) {
    return http.post('/api/ai/chat', payload).then(unwrapResult)
  },
  hello() {
    return http.get('/api/ai/hello').then(unwrapResult)
  }
}

// SSE 流式对话：EventSource 不支持 POST，用 fetch + ReadableStream 解析
// 事件协议：meta=会话ID，delta=增量文本，done=结束。失败时调用方应降级到 aiApi.chat
export async function aiChatStream(payload, { onMeta, onDelta } = {}) {
  const token = localStorage.getItem('inknexus_token')
  const res = await fetch(`${import.meta.env.VITE_API_BASE_URL || ''}/api/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload)
  })
  if (!res.ok || !res.body) throw new Error(`HTTP ${res.status}`)

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { done, value } = await reader.read()
    if (done) return
    buffer += decoder.decode(value, { stream: true })
    let sep
    while ((sep = buffer.indexOf('\n\n')) !== -1) {
      const frame = buffer.slice(0, sep)
      buffer = buffer.slice(sep + 2)
      const fields = parseSseFrame(frame)
      if (!fields) continue
      if (fields.event === 'meta') onMeta?.(fields.data)
      else if (fields.event === 'delta') onDelta?.(fields.data)
      else if (fields.event === 'done') return
    }
  }
}

function parseSseFrame(frame) {
  let event = 'message'
  const dataLines = []
  for (const rawLine of frame.split('\n')) {
    const line = rawLine.replace(/\r$/, '')
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) {
      // SSE 规范只去掉冒号后的一个空格，保留 token 自身的空白
      dataLines.push(line.startsWith('data: ') ? line.slice(6) : line.slice(5))
    }
  }
  if (!dataLines.length) return null
  return { event, data: dataLines.join('\n') }
}
