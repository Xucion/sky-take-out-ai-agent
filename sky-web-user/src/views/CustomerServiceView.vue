<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import BottomNav from '../components/BottomNav.vue'
import {
  AiApiError,
  createAiConversation,
  getAiConversations,
  getAiMessages,
  streamAiMessage,
  type AiConversation,
  type AiMessage,
  type AiStreamEvent,
} from '../api/ai'
import { useToastStore } from '../stores/toast'

interface SendAttempt {
  message: string
  orderId?: number
  clientRequestId: string
  lastEventId?: string
  serverFailed: boolean
}

interface UiMessage {
  key: string
  messageId?: string
  role: 'USER' | 'ASSISTANT'
  content: string
  status: 'PENDING' | 'COMPLETED' | 'FAILED'
  error?: string
  attempt?: SendAttempt
}

const toast = useToastStore()
const conversations = ref<AiConversation[]>([])
const activeConversationId = ref('')
const messages = ref<UiMessage[]>([])
const input = ref('')
const orderIdInput = ref('')
const loading = ref(true)
const sending = ref(false)
const historyEl = ref<HTMLElement | null>(null)
let activeAbort: AbortController | null = null
let historyLoadVersion = 0

const canSend = computed(() => Boolean(input.value.trim()) && !sending.value && Boolean(activeConversationId.value))
const quickQuestions = ['门店现在营业吗？', '我想查询订单进度', '你能提供哪些帮助？']

function requestId() {
  const random = globalThis.crypto?.randomUUID?.() || Math.random().toString(36).slice(2)
  return `web-${Date.now()}-${random}`.slice(0, 64)
}

function mapHistory(message: AiMessage): UiMessage | null {
  if (message.role !== 'USER' && message.role !== 'ASSISTANT') return null
  return {
    key: message.messageId,
    messageId: message.messageId,
    role: message.role,
    content: message.content,
    status: message.status === 'COMPLETED' ? 'COMPLETED'
      : message.status === 'FAILED' ? 'FAILED' : 'PENDING',
    error: message.errorCode || undefined,
  }
}

async function initialize() {
  loading.value = true
  try {
    const page = await getAiConversations()
    conversations.value = page.items || []
    if (!conversations.value.length) {
      const created = await createAiConversation()
      conversations.value = [created]
    }
    await selectConversation(conversations.value[0].conversationId)
  } catch (error) {
    toast.show((error as Error).message)
  } finally {
    loading.value = false
  }
}

async function selectConversation(conversationId: string) {
  if (!conversationId) return
  const loadVersion = ++historyLoadVersion
  activeAbort?.abort()
  sending.value = false
  activeConversationId.value = conversationId
  messages.value = []
  try {
    let cursor = 0
    do {
      const page = await getAiMessages(conversationId, cursor, 100)
      if (loadVersion !== historyLoadVersion) return
      messages.value.push(...page.items.map(mapHistory).filter((item): item is UiMessage => Boolean(item)))
      cursor = page.nextAfterSequence
      if (!page.hasMore) break
    } while (true)
    await scrollToBottom()
  } catch (error) {
    if (loadVersion === historyLoadVersion) toast.show((error as Error).message)
  }
}

async function createConversation() {
  if (sending.value) return
  try {
    const created = await createAiConversation()
    conversations.value.unshift(created)
    await selectConversation(created.conversationId)
  } catch (error) {
    toast.show((error as Error).message)
  }
}

function useQuickQuestion(question: string) {
  input.value = question
}

async function send() {
  const text = input.value.trim()
  if (!text || !canSend.value) return
  const parsedOrderId = orderIdInput.value.trim() ? Number(orderIdInput.value) : undefined
  if (parsedOrderId !== undefined && (!Number.isSafeInteger(parsedOrderId) || parsedOrderId <= 0)) {
    toast.show('请输入正确的订单 ID')
    return
  }
  const attempt: SendAttempt = {
    message: text,
    orderId: parsedOrderId,
    clientRequestId: requestId(),
    serverFailed: false,
  }
  const localId = attempt.clientRequestId
  messages.value.push(
    { key: `${localId}-user`, role: 'USER', content: text, status: 'COMPLETED' },
    { key: `${localId}-assistant`, role: 'ASSISTANT', content: '', status: 'PENDING', attempt },
  )
  input.value = ''
  orderIdInput.value = ''
  await scrollToBottom()
  await runAttempt(attempt, `${localId}-assistant`)
}

async function retry(message: UiMessage) {
  if (!message.attempt || sending.value) return
  const attempt = message.attempt
  if (attempt.serverFailed) {
    attempt.clientRequestId = requestId()
    attempt.lastEventId = undefined
    attempt.serverFailed = false
  }
  message.content = ''
  message.error = undefined
  message.status = 'PENDING'
  await runAttempt(attempt, message.key)
}

async function runAttempt(attempt: SendAttempt, assistantKey: string) {
  sending.value = true
  activeAbort = new AbortController()
  let receivedError: string | undefined
  try {
    await streamAiMessage({
      conversationId: activeConversationId.value,
      message: attempt.message,
      orderId: attempt.orderId,
      clientRequestId: attempt.clientRequestId,
      lastEventId: attempt.lastEventId,
      signal: activeAbort.signal,
      onEvent: (event) => {
        const assistant = messages.value.find((item) => item.key === assistantKey)
        if (!assistant) return
        attempt.lastEventId = event.id
        applyEvent(assistant, attempt, event)
        if (event.type === 'error') receivedError = event.data.message
        void scrollToBottom()
      },
    })
    const assistant = messages.value.find((item) => item.key === assistantKey)
    if (assistant && receivedError) {
      assistant.status = 'FAILED'
      assistant.error = receivedError
    }
  } catch (error) {
    if ((error as Error).name === 'AbortError') return
    const assistant = messages.value.find((item) => item.key === assistantKey)
    if (assistant) {
      assistant.status = 'FAILED'
      assistant.error = error instanceof AiApiError ? error.message : '连接中断，请重试'
    }
  } finally {
    sending.value = false
    activeAbort = null
  }
}

function applyEvent(assistant: UiMessage, attempt: SendAttempt, event: AiStreamEvent) {
  if (event.type === 'message.delta') {
    assistant.messageId = event.data.messageId
    assistant.content += event.data.delta
  } else if (event.type === 'message.completed') {
    assistant.messageId = event.data.messageId
    assistant.content = event.data.answer
    assistant.status = 'COMPLETED'
  } else {
    attempt.serverFailed = true
    assistant.status = 'FAILED'
    assistant.error = event.data.message
  }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
    event.preventDefault()
    void send()
  }
}

async function scrollToBottom() {
  await nextTick()
  historyEl.value?.scrollTo({ top: historyEl.value.scrollHeight, behavior: 'smooth' })
}

onMounted(initialize)
onBeforeUnmount(() => {
  historyLoadVersion++
  activeAbort?.abort()
})
</script>

<template>
  <main class="page narrow support-page">
    <header class="support-header">
      <div class="agent-mark">AI</div>
      <div><h1>智能客服</h1><p><i></i>在线 · 查询结果以业务系统为准</p></div>
      <button class="new-chat" :disabled="sending" @click="createConversation">＋ 新对话</button>
    </header>

    <section class="conversation-bar">
      <label for="conversation-select">当前会话</label>
      <select id="conversation-select" v-model="activeConversationId" :disabled="sending"
              @change="selectConversation(activeConversationId)">
        <option v-for="conversation in conversations" :key="conversation.conversationId"
                :value="conversation.conversationId">
          {{ conversation.title || '智能客服' }} · {{ conversation.conversationId.slice(0, 8) }}
        </option>
      </select>
    </section>

    <section ref="historyEl" class="chat-history" aria-live="polite">
      <div v-if="loading" class="chat-loading">正在连接智能客服…</div>
      <template v-else>
        <article class="welcome-card">
          <span>✦</span>
          <div><strong>你好，我是苍穹智能客服</strong><p>目前可以查询门店营业状态和当前账号下的指定订单进度。</p></div>
        </article>
        <div v-if="!messages.length" class="quick-list">
          <button v-for="question in quickQuestions" :key="question" @click="useQuickQuestion(question)">{{ question }}</button>
        </div>
        <article v-for="message in messages" :key="message.key" class="message-row" :class="message.role.toLowerCase()">
          <div v-if="message.role === 'ASSISTANT'" class="message-avatar">AI</div>
          <div class="bubble" :class="{ failed: message.status === 'FAILED' }">
            <p v-if="message.content">{{ message.content }}</p>
            <span v-else-if="message.status === 'PENDING'" class="typing"><i></i><i></i><i></i></span>
            <p v-else>本次回复未完成</p>
            <footer v-if="message.status === 'FAILED'">
              <span>{{ message.error || '连接失败' }}</span>
              <button v-if="message.attempt" @click="retry(message)">重试</button>
            </footer>
          </div>
        </article>
      </template>
    </section>

    <section class="composer">
      <div class="order-field">
        <label for="order-id">订单 ID（查询订单时填写）</label>
        <input id="order-id" v-model="orderIdInput" inputmode="numeric" maxlength="19" placeholder="例如 88" :disabled="sending" />
      </div>
      <div class="compose-row">
        <textarea v-model="input" maxlength="2000" rows="1" :disabled="sending"
                  placeholder="请输入你的问题…" @keydown="handleComposerKeydown"></textarea>
        <button class="send-button" :disabled="!canSend" @click="send">{{ sending ? '…' : '↑' }}</button>
      </div>
      <p>AI 可能出错；订单与营业状态以系统实时查询结果为准。</p>
    </section>
    <BottomNav active="support" />
  </main>
</template>

<style scoped>
.support-page{height:100vh;display:grid;grid-template-rows:auto auto 1fr auto;background:linear-gradient(180deg,#fff8f1 0,#f7f5f2 30%);overflow:hidden;padding-bottom:calc(68px + env(safe-area-inset-bottom))}.support-header{display:grid;grid-template-columns:44px 1fr auto;align-items:center;gap:11px;padding:14px 16px;background:rgba(255,255,255,.94);border-bottom:1px solid var(--line);backdrop-filter:blur(14px)}.agent-mark,.message-avatar{display:grid;place-items:center;background:linear-gradient(145deg,#ff7a43,#e94c1d);color:#fff;font-weight:900;box-shadow:0 7px 18px rgba(232,73,27,.2)}.agent-mark{width:44px;height:44px;border-radius:15px;font-size:14px}.support-header h1{font-size:18px;margin:0 0 4px}.support-header p{font-size:10px;color:var(--muted);margin:0}.support-header p i{display:inline-block;width:6px;height:6px;border-radius:50%;background:var(--green);margin-right:5px}.new-chat{border:1px solid #ffd5c4;background:#fff7f1;color:var(--orange-dark);border-radius:999px;padding:8px 11px;font-size:11px;font-weight:800}.new-chat:disabled{opacity:.45}.conversation-bar{display:flex;align-items:center;gap:8px;padding:8px 16px;background:#fff;border-bottom:1px solid var(--line)}.conversation-bar label{font-size:11px;color:var(--muted);white-space:nowrap}.conversation-bar select{min-width:0;flex:1;border:0;background:#f8f5f2;border-radius:9px;padding:8px 10px;font-size:11px;color:var(--ink);outline:none}.chat-history{overflow-y:auto;padding:18px 16px 24px;scroll-behavior:smooth}.chat-loading{text-align:center;color:var(--muted);padding:60px 0}.welcome-card{display:grid;grid-template-columns:34px 1fr;gap:10px;padding:15px;background:#fff;border:1px solid #f1e6dc;border-radius:17px;box-shadow:0 8px 24px rgba(70,45,25,.06);margin-bottom:14px}.welcome-card>span{display:grid;place-items:center;width:34px;height:34px;border-radius:11px;background:#fff0e6;color:var(--orange-dark);font-size:18px}.welcome-card strong{font-size:14px}.welcome-card p{margin:5px 0 0;color:var(--muted);font-size:12px;line-height:1.6}.quick-list{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:18px}.quick-list button{border:1px solid #eadfd6;background:#fff;color:#685f59;border-radius:999px;padding:9px 12px;font-size:12px}.message-row{display:flex;align-items:flex-end;gap:8px;margin:13px 0}.message-row.user{justify-content:flex-end}.message-avatar{width:28px;height:28px;border-radius:10px;font-size:9px;flex:0 0 auto}.bubble{max-width:78%;background:#fff;border-radius:6px 17px 17px 17px;padding:11px 13px;box-shadow:0 6px 18px rgba(60,40,25,.06);font-size:14px;line-height:1.65;overflow-wrap:anywhere}.user .bubble{background:linear-gradient(145deg,#ff7840,#e95022);color:#fff;border-radius:17px 6px 17px 17px;box-shadow:0 7px 18px rgba(232,73,27,.16)}.bubble p{white-space:pre-wrap;margin:0}.bubble.failed{border:1px solid #f2bbb3;background:#fff7f5}.bubble footer{display:flex;align-items:center;gap:10px;margin-top:8px;padding-top:8px;border-top:1px solid #f3d8d3;color:#bb4b3e;font-size:10px}.bubble footer span{flex:1}.bubble footer button{border:0;border-radius:999px;background:#ffe5df;color:#bd3f31;padding:5px 10px;font-weight:800}.typing{display:flex;gap:4px;padding:5px}.typing i{width:6px;height:6px;border-radius:50%;background:#c4bbb4;animation:pulse 1.2s infinite}.typing i:nth-child(2){animation-delay:.15s}.typing i:nth-child(3){animation-delay:.3s}@keyframes pulse{0%,70%,100%{opacity:.3;transform:translateY(0)}35%{opacity:1;transform:translateY(-3px)}}.composer{background:rgba(255,255,255,.97);border-top:1px solid var(--line);padding:9px 13px 10px;box-shadow:0 -8px 24px rgba(50,35,25,.05)}.order-field{display:flex;align-items:center;gap:8px;margin-bottom:7px}.order-field label{font-size:10px;color:var(--muted);white-space:nowrap}.order-field input{min-width:0;flex:1;border:0;background:#f7f4f1;border-radius:8px;padding:6px 9px;font-size:11px;outline:none}.compose-row{display:grid;grid-template-columns:1fr 42px;gap:8px;align-items:end}.compose-row textarea{width:100%;max-height:96px;resize:none;border:1px solid var(--line);border-radius:16px;background:#faf9f8;padding:11px 13px;outline:none;line-height:1.4}.compose-row textarea:focus{border-color:var(--orange)}.send-button{width:42px;height:42px;border:0;border-radius:14px;background:linear-gradient(145deg,#ff7840,#e94b1d);color:#fff;font-size:23px;font-weight:900;box-shadow:0 7px 16px rgba(232,73,27,.22)}.send-button:disabled{opacity:.38;box-shadow:none}.composer>p{text-align:center;margin:6px 0 0;color:#aaa;font-size:9px}
</style>
