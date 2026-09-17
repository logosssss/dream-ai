import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  fetchEvalCases,
  fetchHealth,
  fetchObserve,
  invokeAgent,
  invokeAgentStream,
  runEvalSuite,
} from './api/client'
import type {
  AgentId,
  AgentInvokeHttpResponse,
  ChatMessage,
  EvalCase,
  EvalSuiteResult,
  InvokeObservation,
  StreamEvent,
} from './api/types'
import './index.css'

type SideTab = 'events' | 'observe' | 'eval'

function uid() {
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`
}

function chipList(label: string, values: string[] | undefined, kind?: 'ok' | 'warn' | 'bad') {
  if (!values || values.length === 0) {
    return null
  }
  return (
    <span className={`chip ${kind ?? ''}`}>
      {label}:{values.join(',')}
    </span>
  )
}

function MetaChips({ meta }: { meta?: Partial<AgentInvokeHttpResponse> }) {
  if (!meta) {
    return null
  }
  return (
    <div className="meta-chips">
      {meta.route ? <span className="chip">route:{meta.route}</span> : null}
      {meta.model ? <span className="chip">model:{meta.model}</span> : null}
      {meta.traceId ? <span className="chip">trace:{meta.traceId.slice(0, 8)}</span> : null}
      {typeof meta.durationMs === 'number' ? (
        <span className="chip">{meta.durationMs}ms</span>
      ) : null}
      {typeof meta.toolCalls === 'number' ? (
        <span className="chip">tools:{meta.toolCalls}</span>
      ) : null}
      {chipList('blocked', meta.blockedTools, 'warn')}
      {chipList('executed', meta.executedTools, 'ok')}
      {chipList('failed', meta.failedTools, 'bad')}
      {typeof meta.retrieveHits === 'number' && meta.retrieveHits > 0 ? (
        <span className="chip">rag:{meta.retrieveHits}</span>
      ) : null}
    </div>
  )
}

export default function App() {
  const [agentId, setAgentId] = useState<AgentId>('graph')
  const [sessionId, setSessionId] = useState(() => `web-${uid()}`)
  const [approvals, setApprovals] = useState('web_search_prime')
  const [streamMode, setStreamMode] = useState(true)
  const [input, setInput] = useState('从知识库检索 AgentGateway')
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [events, setEvents] = useState<StreamEvent[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [health, setHealth] = useState<'unknown' | 'ok' | 'bad'>('unknown')
  const [tab, setTab] = useState<SideTab>('events')
  const [observe, setObserve] = useState<InvokeObservation[]>([])
  const [cases, setCases] = useState<EvalCase[]>([])
  const [suite, setSuite] = useState<EvalSuiteResult | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const bottomRef = useRef<HTMLDivElement | null>(null)

  const healthClass = useMemo(() => {
    if (health === 'ok') return 'health ok'
    if (health === 'bad') return 'health bad'
    return 'health'
  }, [health])

  const refreshHealth = useCallback(async () => {
    try {
      const h = await fetchHealth()
      setHealth(h.status === 'UP' ? 'ok' : 'bad')
    } catch {
      setHealth('bad')
    }
  }, [])

  const refreshObserve = useCallback(async () => {
    try {
      setObserve(await fetchObserve(20))
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  const refreshCases = useCallback(async () => {
    try {
      setCases(await fetchEvalCases())
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    }
  }, [])

  useEffect(() => {
    void refreshHealth()
    void refreshObserve()
    void refreshCases()
  }, [refreshHealth, refreshObserve, refreshCases])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages, busy])

  const appendAssistant = (content: string, meta?: Partial<AgentInvokeHttpResponse>) => {
    setMessages((prev) => [...prev, { id: uid(), role: 'assistant', content, meta }])
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    const text = input.trim()
    if (!text || busy) {
      return
    }
    setError('')
    setBusy(true)
    setEvents([])
    setMessages((prev) => [...prev, { id: uid(), role: 'user', content: text }])
    setInput('')

    try {
      if (streamMode) {
        const ac = new AbortController()
        abortRef.current = ac
        let streamed = ''
        const assistantId = uid()
        setMessages((prev) => [
          ...prev,
          { id: assistantId, role: 'assistant', content: '' },
        ])
        const done = await invokeAgentStream(
          { agentId, sessionId, input: text, approvals },
          {
            signal: ac.signal,
            onEvent: (evt) => {
              setEvents((prev) => [...prev, evt])
              if (evt.name === 'delta') {
                const delta =
                  typeof evt.data === 'string'
                    ? evt.data
                    : typeof evt.data === 'object' && evt.data && 'delta' in evt.data
                      ? String((evt.data as { delta: unknown }).delta)
                      : String(evt.data ?? '')
                streamed += delta
                setMessages((prev) =>
                  prev.map((m) => (m.id === assistantId ? { ...m, content: streamed } : m)),
                )
              }
              if (evt.name === 'error') {
                const msg =
                  typeof evt.data === 'object' && evt.data && 'error' in evt.data
                    ? String((evt.data as { error: unknown }).error)
                    : String(evt.data)
                setError(msg)
              }
            },
          },
        )
        if (done) {
          setMessages((prev) =>
            prev.map((m) =>
              m.id === assistantId
                ? { ...m, content: done.output || streamed, meta: done }
                : m,
            ),
          )
        }
      } else {
        const res = await invokeAgent({ agentId, sessionId, input: text, approvals })
        appendAssistant(res.output, res)
      }
      await refreshObserve()
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        setError('已取消流式请求')
      } else {
        setError(err instanceof Error ? err.message : String(err))
      }
    } finally {
      abortRef.current = null
      setBusy(false)
    }
  }

  const onRunEval = async () => {
    setBusy(true)
    setError('')
    try {
      setSuite(await runEvalSuite())
      setTab('eval')
      await refreshObserve()
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="app">
      <header className="topbar">
        <div className="brand">
          <h1>Dream-AI</h1>
          <p>开源配套控制台：对话 / SSE / HITL / 观测 / 评测，对接本地 8090。</p>
        </div>
        <div className={healthClass} title="GET /actuator/health">
          <span className="dot" />
          {health === 'ok' ? '8090 UP' : health === 'bad' ? '8090 不可达' : '检测中…'}
        </div>
      </header>

      <div className="layout">
        <section className="panel">
          <div className="panel-head">
            <h2>对话</h2>
            <div className="row-actions">
              <button
                type="button"
                className="btn ghost"
                onClick={() => {
                  setMessages([])
                  setEvents([])
                  setSessionId(`web-${uid()}`)
                }}
              >
                新会话
              </button>
              {busy && streamMode ? (
                <button
                  type="button"
                  className="btn"
                  onClick={() => abortRef.current?.abort()}
                >
                  取消流式
                </button>
              ) : null}
            </div>
          </div>

          <div className="controls">
            <div className="field">
              <label htmlFor="agentId">agentId</label>
              <select
                id="agentId"
                value={agentId}
                onChange={(e) => setAgentId(e.target.value as AgentId)}
              >
                <option value="chat">chat</option>
                <option value="graph">graph</option>
              </select>
            </div>
            <div className="field">
              <label htmlFor="sessionId">sessionId</label>
              <input
                id="sessionId"
                value={sessionId}
                onChange={(e) => setSessionId(e.target.value)}
              />
            </div>
            <div className="field span-2">
              <label htmlFor="approvals">X-Dream-Tool-Approvals（HITL，逗号分隔）</label>
              <input
                id="approvals"
                value={approvals}
                onChange={(e) => setApprovals(e.target.value)}
                placeholder="web_search_prime,datetime_offset"
              />
            </div>
            <div className="field span-2">
              <label>
                <input
                  type="checkbox"
                  checked={streamMode}
                  onChange={(e) => setStreamMode(e.target.checked)}
                />{' '}
                使用 /invoke/stream（SSE）
              </label>
            </div>
          </div>

          {error ? <div className="error-banner">{error}</div> : null}

          <div className="messages">
            {messages.length === 0 ? (
              <div className="empty">
                试试：闲聊「现在几点了」、知识「从知识库检索 AgentGateway」、审查「请审查这段代码有没有风险点」。
              </div>
            ) : null}
            {messages.map((m) => (
              <div key={m.id} className={`bubble ${m.role}`}>
                <div className="role">{m.role}</div>
                <div>{m.content || (busy ? '…' : '')}</div>
                <MetaChips meta={m.meta} />
              </div>
            ))}
            <div ref={bottomRef} />
          </div>

          <form className="composer" onSubmit={onSubmit}>
            <textarea
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) {
                  e.preventDefault()
                  e.currentTarget.form?.requestSubmit()
                }
              }}
              disabled={busy}
            />
            <button className="btn primary" type="submit" disabled={busy || !input.trim()}>
              {busy ? '发送中' : '发送'}
            </button>
          </form>
        </section>

        <aside className="panel">
          <div className="panel-head">
            <div className="tabs">
              <button
                type="button"
                className={`tab ${tab === 'events' ? 'active' : ''}`}
                onClick={() => setTab('events')}
              >
                SSE
              </button>
              <button
                type="button"
                className={`tab ${tab === 'observe' ? 'active' : ''}`}
                onClick={() => {
                  setTab('observe')
                  void refreshObserve()
                }}
              >
                Observe
              </button>
              <button
                type="button"
                className={`tab ${tab === 'eval' ? 'active' : ''}`}
                onClick={() => {
                  setTab('eval')
                  void refreshCases()
                }}
              >
                Eval
              </button>
            </div>
            <button
              type="button"
              className="btn ghost"
              onClick={() => {
                if (tab === 'observe') void refreshObserve()
                if (tab === 'eval') void refreshCases()
                void refreshHealth()
              }}
            >
              刷新
            </button>
          </div>

          <div className="side-body">
            {tab === 'events' ? (
              events.length === 0 ? (
                <div className="empty">流式调用时这里会出现 route / model / tool_* / delta / done。</div>
              ) : (
                events.map((evt, i) => (
                  <div key={`${evt.at}-${i}`} className="log-line">
                    <strong style={{ color: 'var(--brand-dim)' }}>{evt.name}</strong>{' '}
                    {typeof evt.data === 'string'
                      ? evt.data
                      : JSON.stringify(evt.data)}
                  </div>
                ))
              )
            ) : null}

            {tab === 'observe' ? (
              observe.length === 0 ? (
                <div className="empty">暂无观测，先发一轮对话。</div>
              ) : (
                observe.map((o) => (
                  <article key={o.traceId} className="card">
                    <h3>
                      {o.agentId} · {o.traceId.slice(0, 10)}
                    </h3>
                    <pre>
                      {`session=${o.sessionId}
route=${o.route || '-'} model=${o.model || '-'}
ms=${o.durationMs} modelCalls=${o.modelCalls} toolCalls=${o.toolCalls} ok=${o.success}
blocked=[${o.blockedTools?.join(',') ?? ''}]
executed=[${o.executedTools?.join(',') ?? ''}]
failed=[${o.failedTools?.join(',') ?? ''}]
retrieve=${o.retrieveHits?.length ?? 0}`}
                    </pre>
                  </article>
                ))
              )
            ) : null}

            {tab === 'eval' ? (
              <>
                <div className="row-actions">
                  <button type="button" className="btn primary" disabled={busy} onClick={onRunEval}>
                    POST /api/eval/run
                  </button>
                  <button type="button" className="btn" onClick={() => void refreshCases()}>
                    刷新 cases
                  </button>
                </div>
                {suite ? (
                  <div className="card">
                    <h3>
                      suite {suite.passed}/{suite.total} passed
                      {suite.failed ? ` · failed ${suite.failed}` : ''}
                    </h3>
                    {suite.results.map((r) => (
                      <pre key={r.caseId} style={{ marginTop: '0.5rem' }}>
                        {`${r.passed ? '✓' : '✗'} ${r.caseId}
${r.failures?.length ? r.failures.join('\n') : r.output?.slice(0, 120) ?? ''}`}
                      </pre>
                    ))}
                  </div>
                ) : null}
                {cases.map((c) => (
                  <article key={c.id} className="card">
                    <h3>{c.id}</h3>
                    <pre>
                      {`agent=${c.agentId}
input=${c.input}
expect.route=${c.expect?.route ?? '-'} maxToolCalls=${c.expect?.maxToolCalls ?? '-'}`}
                    </pre>
                  </article>
                ))}
              </>
            ) : null}
          </div>
        </aside>
      </div>
    </div>
  )
}
