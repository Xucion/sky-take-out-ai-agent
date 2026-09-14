const AI_API_BASE = import.meta.env.VITE_AI_API_BASE || '/ai-api'

export interface AiConversation {
  conversationId: string
  title?: string | null
  status: string
  currentHandler: string
  lastMessageSequence: number
  lastMessageTime?: string | null
  createdAt: string
  updatedAt: string
}

export interface AiMessage {
  messageId: string
  sequenceNo: number
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  content: string
  status: 'PENDING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  errorCode?: string | null
  createdAt: string
  updatedAt: string
}

export interface ConversationPage {
  items: AiConversation[]
  limit: number
  offset: number
  hasMore: boolean
}

export interface MessagePage {
  items: AiMessage[]
  limit: number
  afterSequence: number
  nextAfterSequence: number
  hasMore: boolean
}

export interface AiChatResponse {
  answer: string
  intent: string
  toolUsed: string | null
  provider: string
  traceId: string
  userMessageId: string
  assistantMessageId: string
  replayed: boolean
}

export class AiApiError extends Error {
  constructor(
    message: string,
    public readonly code = 'AI_REQUEST_FAILED',
    public readonly status = 0,
  ) {
    super(message)
  }
}

function authentication(): string {
  return localStorage.getItem('sky_user_token') || ''
}

function redirectToLogin() {
  localStorage.removeItem('sky_user_token')
  localStorage.removeItem('sky_user_profile')
  const redirect = `${window.location.pathname}${window.location.search}${window.location.hash}`
  window.location.replace(`/login?redirect=${encodeURIComponent(redirect)}`)
}

async function parseError(response: Response): Promise<AiApiError> {
  let body: { code?: string; message?: string } = {}
  try {
    body = await response.json()
  } catch {
    // 非 JSON 网关错误使用统一文案，不能把 HTML 错误页展示给用户。
  }
  if (response.status === 401) redirectToLogin()
  return new AiApiError(
    body.message || (response.status === 401 ? '登录已过期，请重新登录' : '智能客服暂时不可用'),
    body.code,
    response.status,
  )
}

async function aiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${AI_API_BASE}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      authentication: authentication(),
      ...init.headers,
    },
  })
  if (!response.ok) throw await parseError(response)
  return response.json() as Promise<T>
}

export const createAiConversation = (title = '智能客服') =>
  aiRequest<AiConversation>('/ai/conversations', {
    method: 'POST',
    body: JSON.stringify({ title }),
  })

export const getAiConversations = (limit = 20, offset = 0) =>
  aiRequest<ConversationPage>(`/ai/conversations?limit=${limit}&offset=${offset}`)

export const getAiMessages = (conversationId: string, afterSequence = 0, limit = 100) =>
  aiRequest<MessagePage>(
    `/ai/conversations/${encodeURIComponent(conversationId)}/messages`
      + `?afterSequence=${afterSequence}&limit=${limit}`,
  )

export interface ChatMessageInput {
  conversationId: string
  message: string
  clientRequestId: string
  signal?: AbortSignal
}

export function sendAiMessage(input: ChatMessageInput): Promise<AiChatResponse> {
  return aiRequest<AiChatResponse>('/ai/poc/chat', {
    method: 'POST',
    body: JSON.stringify({
      conversationId: input.conversationId,
      message: input.message,
      clientRequestId: input.clientRequestId,
    }),
    signal: input.signal,
  })
}
