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

export interface MessageDeltaEvent {
  eventId: string
  conversationId: string
  messageId: string
  index: number
  delta: string
  createdAt: string
}

export interface MessageCompletedEvent {
  eventId: string
  conversationId: string
  messageId: string
  answer: string
  traceId: string
  replayed: boolean
  createdAt: string
}

export interface StreamErrorEvent {
  eventId: string
  conversationId: string
  code: string
  message: string
  traceId: string
  createdAt: string
}

export type AiStreamEvent =
  | { type: 'message.delta'; id: string; data: MessageDeltaEvent }
  | { type: 'message.completed'; id: string; data: MessageCompletedEvent }
  | { type: 'error'; id: string; data: StreamErrorEvent }

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

export interface StreamMessageInput {
  conversationId: string
  message: string
  orderId?: number
  clientRequestId: string
  lastEventId?: string
  signal?: AbortSignal
  onEvent: (event: AiStreamEvent) => void
}

export async function streamAiMessage(input: StreamMessageInput): Promise<void> {
  const response = await fetch(
    `${AI_API_BASE}/ai/conversations/${encodeURIComponent(input.conversationId)}/messages/stream`,
    {
      method: 'POST',
      headers: {
        Accept: 'text/event-stream',
        'Content-Type': 'application/json',
        authentication: authentication(),
        ...(input.lastEventId ? { 'Last-Event-ID': input.lastEventId } : {}),
      },
      body: JSON.stringify({
        message: input.message,
        orderId: input.orderId,
        clientRequestId: input.clientRequestId,
      }),
      signal: input.signal,
    },
  )
  if (!response.ok) throw await parseError(response)
  if (!response.body) throw new AiApiError('浏览器不支持流式响应', 'STREAM_UNSUPPORTED')

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    buffer += decoder.decode(value, { stream: !done })
    buffer = consumeSseBlocks(buffer, input.onEvent)
    if (done) break
  }
  if (buffer.trim()) parseSseBlock(buffer, input.onEvent)
}

function consumeSseBlocks(buffer: string, onEvent: (event: AiStreamEvent) => void): string {
  while (true) {
    const lfBoundary = buffer.indexOf('\n\n')
    const crlfBoundary = buffer.indexOf('\r\n\r\n')
    let boundary = -1
    let length = 2
    if (lfBoundary >= 0 && (crlfBoundary < 0 || lfBoundary < crlfBoundary)) {
      boundary = lfBoundary
    } else if (crlfBoundary >= 0) {
      boundary = crlfBoundary
      length = 4
    }
    if (boundary < 0) return buffer
    parseSseBlock(buffer.slice(0, boundary), onEvent)
    buffer = buffer.slice(boundary + length)
  }
}

function parseSseBlock(block: string, onEvent: (event: AiStreamEvent) => void) {
  let type = ''
  let id = ''
  const data: string[] = []
  for (const line of block.split(/\r?\n/)) {
    if (line.startsWith('event:')) type = line.slice(6).trim()
    else if (line.startsWith('id:')) id = line.slice(3).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (!type || !data.length) return
  const payload = JSON.parse(data.join('\n'))
  if (type === 'message.delta' || type === 'message.completed' || type === 'error') {
    onEvent({ type, id, data: payload } as AiStreamEvent)
  }
}
