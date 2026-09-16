package com.zhu.ai.kernel.memory;

/**
 * 长期记忆能力契约（hexagonal Port，不是 HTTP/TCP 端口）。
 * <p>
 * <b>存什么</b>：按 {@code memoryKey} 保存跨多轮可压缩的「笔记」文本（事实、偏好、约定等），
 * 不是完整对话原文。当前 Gateway 用 {@code sessionId} 当 key；以后可换成 userId / tenant 等，接口不变。
 * <p>
 * <b>谁调用</b>：只由 {@link com.zhu.ai.kernel.runtime.AgentGateway} 编排——
 * {@code invoke} 前 {@link #recall} 填进请求的 {@code memoryNotes}，成功落库后
 * {@link #rememberRound}。业务 {@code AgentHandler} <em>禁止</em>注入本契约，避免 Agent 直连存储。
 * <p>
 * <b>与短会话的边界</b>（对照 {@link com.zhu.ai.kernel.conversation.ConversationPort}）：
 * <ul>
 *   <li>Conversation：近期 USER/ASSISTANT 原文窗口，供模型续写上下文</li>
 *   <li>Memory：可摘要、可截断的长期笔记，进 system 侧「长期记忆」段，不替代 history</li>
 * </ul>
 * 也与 {@link com.zhu.ai.kernel.knowledge.RetrievePort} 分开：RAG 是语料库检索，Memory 是本会话（或本用户）累积笔记。
 * <p>
 * <b>实现落点</b>：kernel 只定义契约；app 提供 Redis / 进程内存储。
 * 滑动摘要不套在本契约上：Gateway {@link #rememberRound} 后发事件，异步监听器超软上限时
 * 再经 {@link #replaceNotes} 写回；底层仍可对总长度做硬截断，摘要失败不阻断主路径。
 * <p>
 * <b>空 key</b>：{@code memoryKey == null} 或空白时，各方法应为无操作 / recall 返回空串，与 Conversation 约定一致。
 */
public interface MemoryPort {

    /**
     * 读出当前笔记全文，供 Gateway 注入本轮请求。
     *
     * @param memoryKey 记忆键；空则返回 {@code ""}
     * @return 已存笔记；无记录时返回空串（不要返回 {@code null}）
     */
    String recall(String memoryKey);

    /**
     * 追加本轮用户输入与助手输出。典型实现为拼接 {@code U:… / A:…} 行，并在超硬上限时丢弃最旧字符。
     * <p>
     * 本方法只负责持久化；是否摘要由 app 侧事件 + 异步任务决定，实现类不要在这里调模型。
     *
     * @param memoryKey        记忆键；空则忽略
     * @param userInput        本轮用户原文（可为 null，实现宜当空串）
     * @param assistantOutput  本轮助手原文（可为 null，实现宜当空串）
     */
    void rememberRound(String memoryKey, String userInput, String assistantOutput);

    /**
     * 用整段文本覆盖已有笔记（摘要压缩后的写回点）。
     * <p>
     * 与 {@link #rememberRound} 的「追加」相对：调用方应传入可独立理解的压缩结果（例如带
     * {@code Summary:} 前缀的要点），而不是再拼一轮 U/A。空 key 忽略；实现侧仍可做硬长度上限。
     *
     * @param memoryKey 记忆键；空则忽略
     * @param notes     覆盖内容；{@code null} 宜视为空串
     */
    void replaceNotes(String memoryKey, String notes);
}
