/** 与后端 JSON 契约对齐的前端类型（字段名保持 camelCase）。 */

export type AgentId = 'chat' | 'graph'

export interface RetrieveHitSummary {
  id: string
  score: number
  source: string
}

export interface AgentInvokeHttpResponse {
  agentId: string
  output: string
  traceId: string
  durationMs: number
  modelCalls: number
  toolCalls: number
  route: string
  model: string
  blockedTools: string[]
  executedTools: string[]
  failedTools: string[]
  retrieveHits: number
  retrieveHitSummaries: RetrieveHitSummary[]
}

export interface InvokeObservation {
  traceId: string
  sessionId: string
  agentId: string
  durationMs: number
  modelCalls: number
  toolCalls: number
  success: boolean
  errorMessage: string
  route: string
  model: string
  blockedTools: string[]
  executedTools: string[]
  failedTools: string[]
  retrieveHits: RetrieveHitSummary[]
}

export interface EvalExpect {
  success?: boolean | null
  agentId?: string | null
  outputContains?: string[] | null
  outputNotContains?: string[] | null
  minModelCalls?: number | null
  maxToolCalls?: number | null
  maxModelCalls?: number | null
  route?: string | null
  mustBlockTools?: string[] | null
  mustExecuteTools?: string[] | null
  minRetrieveHits?: number | null
  maxRetrieveHits?: number | null
}

export interface EvalCase {
  id: string
  agentId: string
  sessionId: string | null
  input: string
  expect: EvalExpect
}

export interface EvalCaseResult {
  caseId: string
  passed: boolean
  failures: string[]
  agentId: string
  output: string
  modelCalls: number | null
  toolCalls: number | null
  success: boolean | null
}

export interface EvalSuiteResult {
  total: number
  passed: number
  failed: number
  results: EvalCaseResult[]
}

export type SseEventName =
  | 'delta'
  | 'route'
  | 'model'
  | 'retrieve'
  | 'tool_start'
  | 'tool_blocked'
  | 'tool_executed'
  | 'tool_failed'
  | 'done'
  | 'error'

export interface StreamEvent {
  name: SseEventName | string
  data: unknown
  at: number
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant' | 'system'
  content: string
  meta?: Partial<AgentInvokeHttpResponse>
}
