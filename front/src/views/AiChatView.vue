<template>
  <section class="card chat-card">
    <div class="section-head">
      <div>
        <p class="eyebrow">AI Assistant</p>
        <h3>书小助</h3>
      </div>
      <button class="ghost" type="button" @click="newConversation">新对话</button>
    </div>

    <p class="muted">可以帮你搜书、推荐图书、看我最近订单。只读咨询，不能帮你下单或支付。</p>

    <div v-if="error" class="alert">{{ error }}</div>

    <div class="chat-thread" role="log" aria-live="polite">
      <div v-if="messages.length === 0" class="empty-state">
        <p>你好，我是书小助。试试下面的问题，或直接输入你的疑问。</p>
        <div class="chip-row">
          <button v-for="s in suggestions" :key="s" class="chip" type="button" @click="quickAsk(s)">
            {{ s }}
          </button>
        </div>
      </div>

      <article
        v-for="(msg, index) in messages"
        :key="index"
        :class="['bubble', msg.role === 'user' ? 'bubble-user' : 'bubble-assistant']"
      >
        <p>{{ msg.text }}</p>
      </article>

      <div v-if="sending" class="bubble bubble-assistant bubble-typing">
        <p>正在思考…</p>
      </div>
    </div>

    <form class="chat-input" @submit.prevent="send">
      <textarea
        v-model="draft"
        rows="2"
        placeholder="例如：帮我推荐几本关于AI的书"
        :disabled="sending"
        @keydown.enter.exact.prevent="send"
      ></textarea>
      <button class="primary" type="submit" :disabled="sending || !draft.trim()">发送</button>
    </form>
  </section>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { aiApi, aiChatStream } from '../api/inknexus'

const CONVERSATION_KEY = 'inknexus_ai_conversation'

const suggestions = ['推荐几本关于AI的书', '查一下我的订单', '有哪些图书分类']
const messages = ref([])
const draft = ref('')
const sending = ref(false)
const error = ref('')
const conversationId = ref(localStorage.getItem(CONVERSATION_KEY) || '')

function rememberConversation(id) {
  if (id) {
    conversationId.value = id
    localStorage.setItem(CONVERSATION_KEY, id)
  }
}

async function send() {
  const text = draft.value.trim()
  if (!text || sending.value) return

  messages.value.push({ role: 'user', text })
  draft.value = ''
  sending.value = true
  error.value = ''

  // 先占一个空的助手气泡，流式增量逐字追加（打字机效果）
  const bubble = reactive({ role: 'assistant', text: '' })
  messages.value.push(bubble)

  try {
    await aiChatStream(
      { message: text, conversationId: conversationId.value || undefined },
      {
        onMeta: (id) => rememberConversation(id),
        onDelta: (token) => { bubble.text += token }
      }
    )
    if (!bubble.text) bubble.text = '没有拿到回复，请稍后再试。'
  } catch (e) {
    // 流式失败时降级为同步接口；若已收到部分增量则保留半截回答，不再重复请求
    if (!bubble.text) {
      try {
        const data = await aiApi.chat({ message: text, conversationId: conversationId.value || undefined })
        rememberConversation(data?.conversationId)
        bubble.text = data?.reply || '没有拿到回复，请稍后再试。'
      } catch (e2) {
        error.value = e2.message || 'AI 服务暂时不可用'
        bubble.text = '抱歉，我这边暂时无法回复，请稍后再试或检查 AI 服务是否已启动。'
      }
    }
  } finally {
    sending.value = false
  }
}

function quickAsk(text) {
  draft.value = text
  send()
}

function newConversation() {
  conversationId.value = ''
  localStorage.removeItem(CONVERSATION_KEY)
  messages.value = []
  error.value = ''
}
</script>

<style scoped>
.chat-card {
  display: flex;
  flex-direction: column;
  min-height: 70vh;
  max-width: 860px;
  margin: 0 auto;
}

.chat-thread {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  padding: 1rem 0;
  overflow-y: auto;
  max-height: 58vh;
}

.empty-state {
  margin: auto 0;
  text-align: center;
  color: var(--muted);
}
.empty-state .chip-row {
  justify-content: center;
  margin-top: 1rem;
}

.bubble {
  max-width: 78%;
  padding: 0.75rem 1rem;
  border-radius: 18px;
  font-size: 0.95rem;
  white-space: pre-wrap;
  word-break: break-word;
}
.bubble p {
  margin: 0;
}

.bubble-user {
  align-self: flex-end;
  background: var(--accent);
  color: #fff;
  border-bottom-right-radius: 6px;
}

.bubble-assistant {
  align-self: flex-start;
  background: var(--surface-2);
  border: 1px solid var(--line);
  border-bottom-left-radius: 6px;
}

.bubble-typing p {
  color: var(--muted);
}

.chat-input {
  display: flex;
  gap: 0.6rem;
  align-items: flex-end;
  margin-top: 0.75rem;
}
.chat-input textarea {
  resize: vertical;
  flex: 1;
}
.chat-input button {
  height: 100%;
}

@media (max-width: 640px) {
  .bubble {
    max-width: 92%;
  }
}
</style>
