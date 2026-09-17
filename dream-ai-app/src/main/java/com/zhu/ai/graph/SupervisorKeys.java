package com.zhu.ai.graph;

/**
 * Supervisor / Knowledge 子图共用的 {@code OverAllState} 键名常量。
 * <p>
 * Spring AI Alibaba Graph 用 {@code Map<String, Object>} 传状态；键名一旦写错，
 * 节点读到的是默认空串，排障很难。本类把键收拢，主图 {@link SupervisorGraph} 与
 * 子图 {@link KnowledgeSubGraph} 共用同一套名字，子图嵌入后状态才能正确合并回主图。
 * <p>
 * <b>谁写入</b>
 * <ul>
 *   <li>运行入口 {@code SupervisorGraph#run}：种子写入 {@link #INPUT} / {@link #MEMORY} / {@link #RETRIEVED}</li>
 *   <li>{@code intentRouter} 节点：写 {@link #ROUTE}</li>
 *   <li>业务叶 / knowledge 子图：写 {@link #OUTPUT}、{@link #MODEL}；子图另写 {@link #KNOWLEDGE_GATE}</li>
 * </ul>
 * <b>谁读出</b>：条件边（按 {@link #ROUTE} / {@link #KNOWLEDGE_GATE} 分发）、叶节点拼 prompt、
 * {@code run} 结束时组装 {@link com.zhu.ai.kernel.graph.GraphRunResult}。
 * <p>
 * <b>合并策略</b>：主图与子图的 {@code KeyStrategyFactory} 均对这些键使用 {@code ReplaceStrategy}
 * （后写覆盖先写），不做列表追加。因此同一次 invoke 内后一个节点的 OUTPUT 会盖掉前一个。
 * <p>
 * 字符串字面量（如 {@code "input"}）勿在业务代码里散落硬编码；新增键时同步改本类与两处
 * {@code keyFactory()}。
 */
public final class SupervisorKeys {

    /**
     * 本轮用户输入原文。
     * <p>
     * 由 {@link SupervisorGraph#run} 从 {@code GraphRunRequest#input()} 写入种子；
     * intent / chat / review / knowledge 各节点只读，不改写。
     */
    public static final String INPUT = "input";

    /**
     * 长期记忆笔记文本（可空）。
     * <p>
     * Gateway 经 {@code MemoryPort} 召回后塞进 {@code GraphRunRequest#memoryNotes()}，
     * 再由 {@link SupervisorGraph#run} 写入种子。叶节点拼 system prompt 时读取；
     * 图内不负责读写 Redis / 摘要压缩。
     */
    public static final String MEMORY = "memoryNotes";

    /**
     * 本轮检索上下文（可空；有命中时通常含 {@code [1]}、{@code [2]} 编号块）。
     * <p>
     * Gateway 经 {@code RetrievePort} 装好后写入种子。Knowledge 子图的 gate 看本键是否空白
     * 决定 {@link #KNOWLEDGE_GATE}；generate / cite 用它拼「参考资料」并校验引用。
     * chat / review 叶也会附带进 system，但不是主路径。
     */
    public static final String RETRIEVED = "retrievedContext";

    /**
     * Supervisor 意图路由结果：{@code chat} / {@code knowledge} / {@code review}。
     * <p>
     * 由 {@code intentRouter} 节点经 {@link IntentRouter#decide} 写入；主图条件边按本键
     * 分发到对应叶或 knowledge 子图。结束时进入 {@code GraphRunResult#route()}，
     * 再上报观测 / SSE {@code onRoute}。子图不应改写本键。
     */
    public static final String ROUTE = "route";

    /**
     * 当前业务线产出的最终（或中间）回答文本。
     * <p>
     * chat / review 叶一次写完；knowledge 子图在 generate → cite（或 refuse）路径上可能写两次，
     * 因 Replace 策略，cite / refuse 的值覆盖 generate。{@link SupervisorGraph#run} 读本键作为
     * {@code GraphRunResult#output()}。
     */
    public static final String OUTPUT = "output";

    /**
     * 本轮叶节点实际选用的聊天模型 id（可空串表示未解析到 / 未调模型）。
     * <p>
     * 由叶内 {@code ModelRouter#resolve} 写入；knowledge 无命中拒答时写空串。
     * 结束时进入 {@code GraphRunResult#model()}，并可经 {@code ObservePort#markModel} /
     * SSE {@code onModel} 对外可见。
     */
    public static final String MODEL = "model";

    /**
     * Knowledge 子图门禁结论，供子图内条件边使用。
     * <p>
     * 取值见 {@link KnowledgeSubGraph#GATE_OK}（有检索上下文）与
     * {@link KnowledgeSubGraph#GATE_EMPTY}（资料为空 → refuse，不调模型）。
     * 仅 knowledge 子图的 gate 节点写入；主图路由不读本键。主图 {@code keyFactory} 仍注册本键，
     * 以便子图状态合并回主图时策略一致。
     */
    public static final String KNOWLEDGE_GATE = "knowledgeGate";

    private SupervisorKeys() {}
}
