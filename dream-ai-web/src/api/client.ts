import type {
  AgentId,
  AgentInvokeHttpResponse,
  EvalCase,
  EvalSuiteResult,
  InvokeObservation,
  StreamEvent,
} from './types'

const APPROVALS_HEADER = 'X-Dream-Tool-Approvals'

function approvalsHeaders(approvals: string): HeadersInit {
  const trimmed = approvals.trim()
  if (!trimmed) {
    return { 'Content-Type': 'application/json' }
  }
  return {
    'Content-Type': 'application/json',
    [APPROVALS_HEADER]: trimmed,
  }
}

export async function invokeAgent(input: {
  agentId: AgentId
  sessionId: string
  input: string
  approvals: string
}): Promise<AgentInvokeHttpResponse> {
  const res = await fetch('/api/agent/invoke', {
    method: 'POST',
    headers: approvalsHeaders(input.approvals),
    body: JSON.stringify({
      agentId: input.agentId,
      sessionId: input.sessionId,
      input: input.input,
    }),
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  return (await res.json()) as AgentInvokeHttpResponse
}

/**
 * POST SSE：浏览器 EventSource 不支持 body，故用 fetch + 流解析。
 * 事件名与后端 {@code AgentInvokeController} 注释一致。
 */
export async function invokeAgentStream(
  input: {
    agentId: AgentId
    sessionId: string
    input: string
    approvals: string
  },
  handlers: {
    onEvent: (event: StreamEvent) => void
    signal?: AbortSignal
  },
): Promise<AgentInvokeHttpResponse | null> {
  const res = await fetch('/api/agent/invoke/stream', {
    method: 'POST',
    headers: approvalsHeaders(input.approvals),
    body: JSON.stringify({
      agentId: input.agentId,
      sessionId: input.sessionId,
      input: input.input,
    }),
    signal: handlers.signal,
  })
  if (!res.ok || !res.body) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []
  let donePayload: AgentInvokeHttpResponse | null = null

  const flush = () => {
    if (dataLines.length === 0) {
      eventName = 'message'
      return
    }
    const raw = dataLines.join('\n')
    dataLines = []
    let data: unknown = raw
    try {
      data = JSON.parse(raw)
    } catch {
      // delta 可能是纯文本
    }
    const evt: StreamEvent = { name: eventName, data, at: Date.now() }
    handlers.onEvent(evt)
    if (eventName === 'done' && data && typeof data === 'object') {
      donePayload = data as AgentInvokeHttpResponse
    }
    eventName = 'message'
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split(/\r?\n/)
    buffer = parts.pop() ?? ''
    for (const line of parts) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim()
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart())
      } else if (line === '') {
        flush()
      }
    }
  }
  if (buffer.trim()) {
    const line = buffer
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  flush()
  return donePayload
}

export async function fetchObserve(limit = 20): Promise<InvokeObservation[]> {
  const res = await fetch(`/api/observe?limit=${limit}`)
  if (!res.ok) {
    throw new Error(`observe HTTP ${res.status}`)
  }
  return (await res.json()) as InvokeObservation[]
}

export async function fetchEvalCases(): Promise<EvalCase[]> {
  const res = await fetch('/api/eval/cases')
  if (!res.ok) {
    throw new Error(`eval cases HTTP ${res.status}`)
  }
  return (await res.json()) as EvalCase[]
}

export async function runEvalSuite(): Promise<EvalSuiteResult> {
  const res = await fetch('/api/eval/run', { method: 'POST' })
  if (!res.ok) {
    throw new Error(`eval run HTTP ${res.status}`)
  }
  return (await res.json()) as EvalSuiteResult
}

export async function fetchHealth(): Promise<{ status?: string }> {
  const res = await fetch('/actuator/health')
  if (!res.ok) {
    throw new Error(`health HTTP ${res.status}`)
  }
  return (await res.json()) as { status?: string }
}
