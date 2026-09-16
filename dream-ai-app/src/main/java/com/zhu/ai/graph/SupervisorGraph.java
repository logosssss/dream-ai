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
import com.zhu.ai.agent.chat.ChatAgent;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.kernel.graph.GraphPort;
import com.zhu.ai.kernel.graph.GraphRunRequest;
import com.zhu.ai.kernel.graph.GraphRunResult;
import com.zhu.ai.kernel.llm.ChatPort;
import com.zhu.ai.kernel.llm.ChatRequest;
import com.zhu.ai.kernel.llm.ModelRouter;
import com.zhu.ai.kernel.llm.TokenSink;
import com.zhu.ai.kernel.observe.ObservePort;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 最小可演示的 Supervisor 图：主图只做意图路由，业务叶节点各自调模型。
 * <p>
 * 实现 {@link GraphPort}，让 agents 的 GraphAgent / Gateway 只依赖 Port，
 * Spring AI Alibaba Graph SDK（{@code StateGraph} / {@code CompiledGraph}）留在本类，不泄漏到 kernel。
 *
 * <h2>拓扑（面试时建议默画）</h2>
 * <pre>
 *   START
 *     │
 *     ▼
 *  intentRouter     —— 写入 state.route = chat | knowledge | review
 *     │
 *     ├─(chat)──────► chat 叶 ──────────► END
 *     │
 *     ├─(knowledge)─► knowledge 叶 ─────► END
 *     │
 *     └─(review)────► review 叶 ────────► END
 * </pre>
 *
 * <h2>为什么用 Supervisor，而不是一个大图塞满逻辑</h2>
 * <ul>
 *   <li>主图职责单一：只决定「走哪条业务线」</li>
 *   <li>叶节点可独立替换 / 单测 / 以后拆成子 {@code CompiledGraph}</li>
 *   <li>加第四条路由时：加节点 + 改 {@link IntentRouter} + 改条件边 mappings，不必重写已有叶</li>
 * </ul>
 *
 * <h2>和 {@code agentId=chat} 的差别</h2>
 * <ul>
 *   <li>{@code chat}：HTTP 直达一个 Agent，内部是工具循环，没有图级路由</li>
 *   <li>{@code graph}：先经本 Supervisor 选叶，再进对应 system + {@link ChatPort}</li>
 * </ul>
 *
 * <h2>边界</h2>
 * 叶节点只依赖 {@link ChatPort} + {@link ModelRouter}，不注入 Mapper / Conversation / Memory。
 * history / memory / retrieve 由 Gateway 装好后经 {@link GraphRunRequest} 传入，
 * 与单 Agent 路径一致，避免图里再查库。不同叶可配不同模型（chat 小 / review 大）。
 * SSE 时 {@link GraphRunRequest#sink()} 经闭包进节点：路由节点先 {@code onRoute}，叶内 {@code stream}。
 */
public final class SupervisorGraph implements GraphPort {

    private static final Logger log = LoggerFactory.getLogger(SupervisorGraph.class);

    /** 意图路由节点 id，须与 {@link #compile} 里 addNode / 边的名字一致。 */
    public static final String N_INTENT = "intentRouter";

    /** 闲聊叶节点 id；条件边 mappings 的 value 指向这里。 */
    public static final String N_CHAT = "chat";

    /** 知识问答叶节点 id；命中检索 / RAG 类关键词时走这里。 */
    public static final String N_KNOWLEDGE = "knowledge";

    /** 评审叶节点 id；命中审查 / review 类关键词时走这里（优先级高于 knowledge）。 */
    public static final String N_REVIEW = "review";

    /**
     * knowledge 叶专用 system：比 ChatAgent 更强调「只信参考资料」。
     * 路由到此叶时，即使 Gateway 也塞了 retrieve，模型仍被明确约束「没有就说不足」。
     */
    private static final String KNOWLEDGE_SYSTEM =
            "你是知识问答助手，用简洁中文回答。\n"
                    + "优先依据「参考资料」作答；参考资料为空或不够就明确说资料不足，不要编造。\n"
                    + "不要把时效新闻、天气、股价当成仓库知识。\n";

    /**
     * review 叶专用 system：偏风险与改进点，和闲聊 / 知识问答语气区分开，
     * 方便演示「同模型入口、不同叶 = 不同人设」。
     */
    private static final String REVIEW_SYSTEM =
            "你是代码与方案评审助手，用简洁中文指出风险与改进点。\n"
                    + "优先谈正确性、边界、安全与可维护性；没有足够上下文就明确说缺什么，不要编造实现细节。\n"
                    + "输出短条目，避免长篇空话。\n";

    /** 叶节点共用的 ChatPort；本图不直接依赖 DashScope SDK。 */
    private final ChatPort chatPort;

    /** 按叶任务键解析模型；未配置时返回空，走适配器默认模型。 */
    private final ModelRouter models;

    /** 可选：把选用模型写入本轮观测。 */
    private final ObservePort observe;

    public SupervisorGraph(ChatPort chatPort) {
        this(chatPort, task -> null, null);
    }

    public SupervisorGraph(ChatPort chatPort, ModelRouter models) {
        this(chatPort, models, null);
    }

    public SupervisorGraph(ChatPort chatPort, ModelRouter models, ObservePort observe) {
        this.chatPort = chatPort;
        this.models = models == null ? task -> null : models;
        this.observe = observe;
    }

    /**
     * {@link GraphPort} 标准入口：拆 {@link GraphRunRequest} 后走编排。
     * null 请求按空输入处理，避免 Handler 侧 NPE。
     * {@link GraphRunRequest#sink()} 非空时叶节点走 {@link ChatPort#stream}。
     */
    @Override
    public GraphRunResult run(GraphRunRequest request) {
        GraphRunRequest req = request == null
                ? new GraphRunRequest("", List.of(), "", "")
                : request;
        return run(req.input(), req.history(), req.memoryNotes(), req.retrievedContext(), req.sink());
    }

    /**
     * 同步便捷入口（无 SSE）。
     */
    public GraphRunResult run(
            String input, List<ConversationTurn> history, String memoryNotes, String retrievedContext) {
        return run(input, history, memoryNotes, retrievedContext, null);
    }

    /**
     * 编译并执行一整次 Supervisor 调用。
     *
     * @param sink 非空时：intent 上报 route，叶节点 stream 推 delta；空则 complete
     */
    public GraphRunResult run(
            String input,
            List<ConversationTurn> history,
            String memoryNotes,
            String retrievedContext,
            TokenSink sink) {
        try {
            // history / sink 闭包进节点：图状态不序列化 TokenSink（异步线程也安全）
            CompiledGraph graph = compile(history == null ? List.of() : history, sink);

            Map<String, Object> seed = new HashMap<>();
            seed.put(SupervisorKeys.INPUT, input == null ? "" : input);
            seed.put(SupervisorKeys.MEMORY, memoryNotes == null ? "" : memoryNotes);
            seed.put(SupervisorKeys.RETRIEVED, retrievedContext == null ? "" : retrievedContext);

            Optional<OverAllState> done = graph.invoke(seed);
            if (done.isEmpty()) {
                throw new IllegalStateException("supervisor graph returned empty state");
            }
            OverAllState state = done.get();
            String route = String.valueOf(state.value(SupervisorKeys.ROUTE, IntentRouter.CHAT));
            String output = String.valueOf(state.value(SupervisorKeys.OUTPUT, ""));
            String model = String.valueOf(state.value(SupervisorKeys.MODEL, ""));
            log.info("supervisor done route={} model={} outputChars={}", route, model, output.length());
            return new GraphRunResult(route, output, model);
        } catch (GraphStateException ex) {
            throw new IllegalStateException("supervisor graph compile/run failed", ex);
        }
    }

    /**
     * 组装 {@link StateGraph} 并 compile。
     * <p>
     * 无 sink：叶内 {@link ChatPort#complete}；有 sink：叶内 {@link ChatPort#stream}。
     */
    CompiledGraph compile(List<ConversationTurn> history, TokenSink sink) throws GraphStateException {
        StateGraph g = new StateGraph(keyFactory());

        g.addNode(N_INTENT, AsyncNodeAction.node_async(state -> intentRouter(state, sink)));
        g.addNode(N_CHAT, AsyncNodeAction.node_async(state -> chatLeaf(state, history, sink)));
        g.addNode(N_KNOWLEDGE, AsyncNodeAction.node_async(state -> knowledgeLeaf(state, history, sink)));
        g.addNode(N_REVIEW, AsyncNodeAction.node_async(state -> reviewLeaf(state, history, sink)));

        g.addEdge(START, N_INTENT);
        g.addConditionalEdges(
                N_INTENT,
                AsyncEdgeAction.edge_async(state ->
                        String.valueOf(state.value(SupervisorKeys.ROUTE, IntentRouter.CHAT))),
                Map.of(
                        IntentRouter.CHAT, N_CHAT,
                        IntentRouter.KNOWLEDGE, N_KNOWLEDGE,
                        IntentRouter.REVIEW, N_REVIEW));
        g.addEdge(N_CHAT, END);
        g.addEdge(N_KNOWLEDGE, END);
        g.addEdge(N_REVIEW, END);

        return g.compile();
    }

    private Map<String, Object> intentRouter(OverAllState state, TokenSink sink) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String route = IntentRouter.decide(input);
        log.info("supervisor route={} inputChars={}", route, input.length());
        if (sink != null && !sink.cancelled()) {
            sink.onRoute(route);
        }
        return Map.of(SupervisorKeys.ROUTE, route);
    }

    private Map<String, Object> chatLeaf(
            OverAllState state, List<ConversationTurn> history, TokenSink sink) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String model = resolveAndMark(ModelRouter.CHAT);
        String output =
                invokeLeaf(model, input, ChatAgent.systemPrompt(memory, retrieved), history, sink);
        return leafResult(model, output);
    }

    private Map<String, Object> knowledgeLeaf(
            OverAllState state, List<ConversationTurn> history, TokenSink sink) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String model = resolveAndMark(ModelRouter.KNOWLEDGE);
        String output = invokeLeaf(model, input, knowledgeSystem(memory, retrieved), history, sink);
        return leafResult(model, output);
    }

    private Map<String, Object> reviewLeaf(
            OverAllState state, List<ConversationTurn> history, TokenSink sink) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String model = resolveAndMark(ModelRouter.REVIEW);
        String output = invokeLeaf(model, input, reviewSystem(memory, retrieved), history, sink);
        return leafResult(model, output);
    }

    private String invokeLeaf(
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

    private static Map<String, Object> leafResult(String model, String output) {
        Map<String, Object> result = new HashMap<>();
        result.put(SupervisorKeys.OUTPUT, output == null ? "" : output);
        result.put(SupervisorKeys.MODEL, model == null ? "" : model);
        return result;
    }

    private String resolveAndMark(String taskKey) {
        String model = models.resolve(taskKey);
        if (observe != null && model != null && !model.isBlank()) {
            observe.markModel(model);
        }
        return model;
    }

    static String knowledgeSystem(String memoryNotes, String retrievedContext) {
        StringBuilder system = new StringBuilder(KNOWLEDGE_SYSTEM);
        appendMemoryAndRetrieved(system, memoryNotes, retrievedContext);
        return system.toString();
    }

    static String reviewSystem(String memoryNotes, String retrievedContext) {
        StringBuilder system = new StringBuilder(REVIEW_SYSTEM);
        appendMemoryAndRetrieved(system, memoryNotes, retrievedContext);
        return system.toString();
    }

    private static void appendMemoryAndRetrieved(
            StringBuilder system, String memoryNotes, String retrievedContext) {
        if (memoryNotes != null && !memoryNotes.isBlank()) {
            system.append("\n长期记忆：\n").append(memoryNotes);
        }
        if (retrievedContext != null && !retrievedContext.isBlank()) {
            system.append("\n参考资料：\n").append(retrievedContext);
        } else {
            system.append("\n参考资料：（空）\n");
        }
    }

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
            return strategies;
        };
    }
}
