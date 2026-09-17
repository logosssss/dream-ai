package com.zhu.ai.graph;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.llm.ModelRouter;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.observe.ObservePort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Knowledge 业务子图：主图 {@link SupervisorGraph} 只路由到本子图，叶内再拆门禁 / 生成 / 引用。
 * <p>
 * 以 {@link CompiledGraph} 形式经 {@code StateGraph#addNode(String, CompiledGraph)} 嵌入主图
 * 的 {@link SupervisorGraph#N_KNOWLEDGE} 节点。与「把 gate+generate 全塞进一个 AsyncNode」相比，
 * 子图让步骤可单测、可口述，后续要加「再检索 / 重写」也只需改本类。
 *
 * <h2>拓扑（面试可默画）</h2>
 * <pre>
 *   START
 *     │
 *     ▼
 *   gate ─── 读 {@link SupervisorKeys#RETRIEVED}，写 {@link SupervisorKeys#KNOWLEDGE_GATE}
 *     │
 *     ├─({@link #GATE_OK})────► generate ──► cite ──► END
 *     │                         调模型写 OUTPUT     补 [1] 引用
 *     │
 *     └─({@link #GATE_EMPTY})─► refuse ────────────► END
 *                               固定拒答，不调模型
 * </pre>
 *
 * <h2>边界（刻意不做）</h2>
 * <ul>
 *   <li>不注入 {@code RetrievePort}：检索由 Gateway 装进 {@link SupervisorKeys#RETRIEVED}，
 *       本图只消费结果，避免图里再查库、也避免与主路径 RAG 策略分叉</li>
 *   <li>不改 {@link SupervisorKeys#ROUTE}：路由是主图 intent 的职责</li>
 *   <li>不做 checkpoint / 可恢复：教学切片以「子图拆步」为主，恢复叙事留给口述</li>
 * </ul>
 *
 * <h2>状态键</h2>
 * 与主图共用 {@link SupervisorKeys}；合并策略均为 {@code ReplaceStrategy}。
 * 子图内 {@code cite} / {@code refuse} 会覆盖 {@code generate} 写下的 {@link SupervisorKeys#OUTPUT}。
 *
 * <h2>SSE</h2>
 * {@link TokenSink} 经闭包传入节点，不进图状态（避免序列化 / 跨线程问题）。
 * refuse 与 cite 补丁会 {@code onDelta}；generate 走 {@link ChatPort#stream}。
 *
 * @see SupervisorGraph
 * @see SupervisorKeys
 */
public final class KnowledgeSubGraph {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSubGraph.class);

    /**
     * 门禁节点 id：判断检索上下文是否为空。
     * 须与 {@link #compile} 里 {@code addNode} / 条件边起点一致。
     */
    public static final String N_GATE = "knowledgeGate";

    /**
     * 生成节点 id：有命中时调 {@link ChatPort} 产出回答。
     * 仅在门禁为 {@link #GATE_OK} 时进入。
     */
    public static final String N_GENERATE = "knowledgeGenerate";

    /**
     * 引用补全节点 id：模型漏写 {@code [1]} 时追加 {@link #CITATION_FALLBACK}。
     * 接在 generate 之后；无补丁时 OUTPUT 原样通过。
     */
    public static final String N_CITE = "knowledgeCite";

    /**
     * 拒答节点 id：无检索命中时写固定文案 {@link #NO_HIT_REPLY}，不调模型。
     * 仅在门禁为 {@link #GATE_EMPTY} 时进入。
     */
    public static final String N_REFUSE = "knowledgeRefuse";

    /**
     * 条件边取值：{@link SupervisorKeys#RETRIEVED} 非空白 → 走 generate。
     * 由 {@link #gate} 写入 {@link SupervisorKeys#KNOWLEDGE_GATE}。
     */
    public static final String GATE_OK = "ok";

    /**
     * 条件边取值：检索上下文空白 → 走 refuse。
     * 缺省回落也用本值，避免 gate 未写时误进 generate。
     */
    public static final String GATE_EMPTY = "empty";

    /**
     * 无命中时的固定拒答（同步结果与 SSE delta 共用）。
     * 刻意不调模型，避免「资料不足」还被模型编造；与观测里 model 空串对齐。
     */
    public static final String NO_HIT_REPLY = "资料不足，无法根据知识库作答。";

    /**
     * 模型回答未含 {@code [1]}、且检索上下文含 {@code [1]} 时追加的后缀。
     * 与 prompt「必须点名引用编号」及前端/评测对 {@code [1]} 的断言对齐；
     * SSE 下只推「新增后缀」那段 delta，避免整段重推。
     */
    public static final String CITATION_FALLBACK = "\n依据：[1]";

    /**
     * knowledge 叶专用 system 人设：强调依据参考资料、必须引用编号、禁止把时效信息当库内知识。
     * 记忆与检索正文由 {@link #knowledgeSystem} 追加，不写进本常量。
     */
    private static final String KNOWLEDGE_SYSTEM =
            "你是知识问答助手，用简洁中文回答。\n"
                    + "优先依据「参考资料」作答；参考资料为空或不够就明确说资料不足，不要编造。\n"
                    + "回答中必须点名引用编号，例如 [1] 或 [2]。\n"
                    + "不要把时效新闻、天气、股价当成仓库知识。\n";

    private KnowledgeSubGraph() {}

    /**
     * 编译 knowledge 子图，供主图 {@code addNode(N_KNOWLEDGE, …)} 嵌入。
     * <p>
     * {@code history} / {@code sink} 以闭包形式进入节点，不写入 {@link OverAllState}：
     * 图状态只保留可替换的字符串键（见 {@link SupervisorKeys}），避免 TokenSink 序列化问题。
     * 每次 Supervisor {@code compile} 都会重新编译本子图，从而带上当次请求的 history/sink。
     *
     * @param chatPort 叶内模型调用；不可为 null
     * @param models   按任务键解析模型；null 视为一律回落适配器默认
     * @param observe  可选；解析到模型 id 时 {@code markModel}
     * @param history  短会话轮次，原样进 {@link ChatRequest}；null 当空列表
     * @param sink     可选 SSE；null 则 generate 走 {@link ChatPort#complete}
     * @return 已 compile 的子图，可直接作为主图节点
     */
    static CompiledGraph compile(
            ChatPort chatPort,
            ModelRouter models,
            ObservePort observe,
            List<ConversationTurn> history,
            TokenSink sink)
            throws GraphStateException {
        List<ConversationTurn> hist = history == null ? List.of() : history;
        ModelRouter router = models == null ? task -> null : models;

        StateGraph g = new StateGraph(keyFactory());
        g.addNode(N_GATE, AsyncNodeAction.node_async(KnowledgeSubGraph::gate));
        g.addNode(
                N_GENERATE,
                AsyncNodeAction.node_async(state -> generate(state, chatPort, router, observe, hist, sink)));
        g.addNode(N_CITE, AsyncNodeAction.node_async(state -> cite(state, sink)));
        g.addNode(N_REFUSE, AsyncNodeAction.node_async(state -> refuse(sink)));

        g.addEdge(START, N_GATE);
        // 条件边 mappings 的 key 必须与 gate 写入的 KNOWLEDGE_GATE 取值一致
        g.addConditionalEdges(
                N_GATE,
                AsyncEdgeAction.edge_async(state ->
                        String.valueOf(state.value(SupervisorKeys.KNOWLEDGE_GATE, GATE_EMPTY))),
                Map.of(GATE_OK, N_GENERATE, GATE_EMPTY, N_REFUSE));
        g.addEdge(N_GENERATE, N_CITE);
        g.addEdge(N_CITE, END);
        g.addEdge(N_REFUSE, END);

        return g.compile();
    }

    /**
     * 门禁：只看 {@link SupervisorKeys#RETRIEVED} 是否空白，写入 {@link SupervisorKeys#KNOWLEDGE_GATE}。
     * 不调模型、不改 OUTPUT——把「有没有料」和「怎么答」拆开，便于单测与日志对照。
     */
    private static Map<String, Object> gate(OverAllState state) {
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String decision = retrieved.isBlank() ? GATE_EMPTY : GATE_OK;
        log.info("knowledge gate={} retrievedChars={}", decision, retrieved.length());
        return Map.of(SupervisorKeys.KNOWLEDGE_GATE, decision);
    }

    /**
     * 生成：拼 knowledge system（含记忆与参考资料），按 {@link ModelRouter#KNOWLEDGE} 选模，
     * 经 {@link #invokeLeaf} 得到回答，写入 {@link SupervisorKeys#OUTPUT} / {@link SupervisorKeys#MODEL}。
     * 引用是否齐全交给下一跳 {@link #cite}，本节点不补后缀。
     */
    private static Map<String, Object> generate(
            OverAllState state,
            ChatPort chatPort,
            ModelRouter models,
            ObservePort observe,
            List<ConversationTurn> history,
            TokenSink sink) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String model = resolveAndMark(models, observe, ModelRouter.KNOWLEDGE);
        String output = invokeLeaf(
                chatPort, model, input, knowledgeSystem(memory, retrieved), history, sink);
        Map<String, Object> result = new HashMap<>();
        result.put(SupervisorKeys.OUTPUT, output == null ? "" : output);
        result.put(SupervisorKeys.MODEL, model == null ? "" : model);
        return result;
    }

    /**
     * 引用补全：在 generate 之后运行。
     * 若检索侧已有 {@code [1]} 而模型未点名，追加 {@link #CITATION_FALLBACK} 并覆盖 OUTPUT；
     * SSE 仅推新增后缀，与同步最终串一致。
     */
    private static Map<String, Object> cite(OverAllState state, TokenSink sink) {
        String output = String.valueOf(state.value(SupervisorKeys.OUTPUT, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String cited = ensureCitations(output, retrieved);
        if (sink != null
                && !sink.cancelled()
                && cited.length() > output.length()) {
            sink.onDelta(cited.substring(output.length()));
        }
        return Map.of(SupervisorKeys.OUTPUT, cited);
    }

    /**
     * 拒答：无检索命中的短路路径。写固定 {@link #NO_HIT_REPLY}，MODEL 置空；
     * 有 sink 时推同一段 delta，保证流式与 JSON 出参文案一致。
     */
    private static Map<String, Object> refuse(TokenSink sink) {
        log.info("knowledge leaf refuse: no retrieve hits");
        if (sink != null && !sink.cancelled()) {
            sink.onDelta(NO_HIT_REPLY);
        }
        Map<String, Object> result = new HashMap<>();
        result.put(SupervisorKeys.OUTPUT, NO_HIT_REPLY);
        result.put(SupervisorKeys.MODEL, "");
        return result;
    }

    /**
     * 统一叶内模型调用：有未取消的 sink → {@link ChatPort#stream}（并 {@code onModel}）；
     * 否则 → {@link ChatPort#complete}。与主图 chat / review 叶语义对齐。
     */
    private static String invokeLeaf(
            ChatPort chatPort,
            String model,
            String input,
            String system,
            List<ConversationTurn> history,
            TokenSink sink) {
        ChatRequest request = new ChatRequest(model, input, system, history);
        if (sink != null && !sink.cancelled()) {
            if (model != null && !model.isBlank()) {
                sink.onModel(model);
            }
            return chatPort.stream(request, sink);
        }
        return chatPort.complete(request);
    }

    /**
     * 解析任务模型并可选写入观测；未配置时返回空/null，由适配器走默认模型。
     */
    private static String resolveAndMark(ModelRouter models, ObservePort observe, String taskKey) {
        String model = models.resolve(taskKey);
        if (observe != null && model != null && !model.isBlank()) {
            observe.markModel(model);
        }
        return model;
    }

    /**
     * 引用兜底：检索上下文含 {@code [1]} 且回答未含时追加 {@link #CITATION_FALLBACK}；
     * 检索无编号标记或回答已含 {@code [1]} 则原样返回。包内可见便于单测。
     */
    static String ensureCitations(String output, String retrievedContext) {
        String out = output == null ? "" : output;
        if (retrievedContext == null || !retrievedContext.contains("[1]")) {
            return out;
        }
        if (out.contains("[1]")) {
            return out;
        }
        return out + CITATION_FALLBACK;
    }

    /**
     * 组装 knowledge system：人设常量 + 可选长期记忆 + 参考资料块（空则显式写「（空）」）。
     * 包内可见，供单测断言 prompt 是否带上检索正文。
     */
    static String knowledgeSystem(String memoryNotes, String retrievedContext) {
        StringBuilder system = new StringBuilder(KNOWLEDGE_SYSTEM);
        if (memoryNotes != null && !memoryNotes.isBlank()) {
            system.append("\n长期记忆：\n").append(memoryNotes);
        }
        if (retrievedContext != null && !retrievedContext.isBlank()) {
            system.append("\n参考资料：\n").append(retrievedContext);
        } else {
            system.append("\n参考资料：（空）\n");
        }
        return system.toString();
    }

    /**
     * 子图状态键策略：与主图一致，全部 {@link ReplaceStrategy}。
     * 必须覆盖主图已有键 + {@link SupervisorKeys#KNOWLEDGE_GATE}，否则嵌入后合并行为不一致。
     */
    private static KeyStrategyFactory keyFactory() {
        return () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            ReplaceStrategy replace = new ReplaceStrategy();
            strategies.put(SupervisorKeys.INPUT, replace);
            strategies.put(SupervisorKeys.MEMORY, replace);
            strategies.put(SupervisorKeys.RETRIEVED, replace);
            strategies.put(SupervisorKeys.ROUTE, replace);
            strategies.put(SupervisorKeys.OUTPUT, replace);
            strategies.put(SupervisorKeys.MODEL, replace);
            strategies.put(SupervisorKeys.KNOWLEDGE_GATE, replace);
            return strategies;
        };
    }
}
