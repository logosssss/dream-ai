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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 最小可演示的 Supervisor 图：主图只做意图路由，业务叶节点各自调模型。
 * <p>
 * 实现 {@link GraphPort}，让 {@link GraphAgentHandler} / Gateway 只依赖 Port，
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
 * 叶节点只依赖 {@link ChatPort}，不注入 Mapper / Conversation / Memory。
 * history / memory / retrieve 由 Gateway 装好后经 {@link GraphRunRequest} 传入，
 * 与单 Agent 路径一致，避免图里再查库。
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

    /** 叶节点共用的模型端口；本图不直接依赖 DashScope SDK。 */
    private final ChatPort chatPort;

    public SupervisorGraph(ChatPort chatPort) {
        this.chatPort = chatPort;
    }

    /**
     * {@link GraphPort} 标准入口：拆 {@link GraphRunRequest} 后走同步编排。
     * null 请求按空输入处理，避免 Handler 侧 NPE。
     */
    @Override
    public GraphRunResult run(GraphRunRequest request) {
        GraphRunRequest req = request == null
                ? new GraphRunRequest("", List.of(), "", "")
                : request;
        return run(req.input(), req.history(), req.memoryNotes(), req.retrievedContext());
    }

    /**
     * 编译并执行一整次 Supervisor 调用（包内 / 单测便捷入口）。
     *
     * @param input            用户输入（路由依据 + 叶节点 prompt）
     * @param history          Gateway 装好的短会话；不进 {@link OverAllState}，靠闭包传给叶节点
     *                         （避免把复杂对象塞进图状态序列化）
     * @param memoryNotes      长期记忆文本，写入初始 state，叶节点读出拼进 system
     * @param retrievedContext 检索片段，同上
     * @return 路由名 + 叶节点模型原文（不含 HTTP 层 {@code [route=…]} 前缀；前缀由 Handler 加）
     */
    public GraphRunResult run(
            String input, List<ConversationTurn> history, String memoryNotes, String retrievedContext) {
        try {
            // 每次 run 重新 compile：history 通过闭包绑到叶节点；课表演示优先清晰，不做图缓存
            CompiledGraph graph = compile(history == null ? List.of() : history);

            // 初始 state：只放图内需要流转的键；route / output 由节点写入
            Map<String, Object> seed = new HashMap<>();
            seed.put(SupervisorKeys.INPUT, input == null ? "" : input);
            seed.put(SupervisorKeys.MEMORY, memoryNotes == null ? "" : memoryNotes);
            seed.put(SupervisorKeys.RETRIEVED, retrievedContext == null ? "" : retrievedContext);

            // invoke：同步跑完整条路径直到 END，返回终态快照
            Optional<OverAllState> done = graph.invoke(seed);
            if (done.isEmpty()) {
                throw new IllegalStateException("supervisor graph returned empty state");
            }
            OverAllState state = done.get();
            String route = String.valueOf(state.value(SupervisorKeys.ROUTE, IntentRouter.CHAT));
            String output = String.valueOf(state.value(SupervisorKeys.OUTPUT, ""));
            log.info("supervisor done route={} outputChars={}", route, output.length());
            return new GraphRunResult(route, output);
        } catch (GraphStateException ex) {
            // Graph API 检查失败（重复节点名、边不合法等）→ 收成运行时异常，避免泄漏到 HTTP 栈细节
            throw new IllegalStateException("supervisor graph compile/run failed", ex);
        }
    }

    /**
     * 组装 {@link StateGraph} 并 compile。
     * <p>
     * 节点用 {@link AsyncNodeAction#node_async} 包装同步逻辑：框架统一按异步 Action 调度，
     * 本沙盘叶节点内部仍是同步 {@link ChatPort#complete}。
     * <p>
     * 条件边：{@link AsyncEdgeAction} 的返回值必须是 mappings 的 <b>key</b>
     *（{@link IntentRouter#CHAT} / {@link IntentRouter#KNOWLEDGE} / {@link IntentRouter#REVIEW}），
     * value 才是真正要跳转的节点 id（{@link #N_CHAT} 等）。
     */
    CompiledGraph compile(List<ConversationTurn> history) throws GraphStateException {
        StateGraph g = new StateGraph(keyFactory());

        // —— 节点 ——
        g.addNode(N_INTENT, AsyncNodeAction.node_async(this::intentRouter));
        // history 捕获进闭包：图状态里不存 List<ConversationTurn>
        g.addNode(N_CHAT, AsyncNodeAction.node_async(state -> chatLeaf(state, history)));
        g.addNode(N_KNOWLEDGE, AsyncNodeAction.node_async(state -> knowledgeLeaf(state, history)));
        g.addNode(N_REVIEW, AsyncNodeAction.node_async(state -> reviewLeaf(state, history)));

        // —— 边 ——
        g.addEdge(START, N_INTENT);
        g.addConditionalEdges(
                N_INTENT,
                // 边条件：读 intentRouter 写入的 route，决定下一跳
                AsyncEdgeAction.edge_async(state ->
                        String.valueOf(state.value(SupervisorKeys.ROUTE, IntentRouter.CHAT))),
                Map.of(
                        IntentRouter.CHAT, N_CHAT,
                        IntentRouter.KNOWLEDGE, N_KNOWLEDGE,
                        IntentRouter.REVIEW, N_REVIEW));
        // 三叶都直接结束；以后若要「叶后再汇总」，在这里改成汇聚节点而不是 END
        g.addEdge(N_CHAT, END);
        g.addEdge(N_KNOWLEDGE, END);
        g.addEdge(N_REVIEW, END);

        return g.compile();
    }

    /**
     * 路由节点：只写 {@link SupervisorKeys#ROUTE}，不调模型。
     * <p>
     * 决策委托 {@link IntentRouter#decide}（纯函数关键词规则），节点返回的 Map
     * 会按 {@link KeyStrategy}（此处为 Replace）合并进 {@link OverAllState}。
     */
    private Map<String, Object> intentRouter(OverAllState state) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String route = IntentRouter.decide(input);
        log.info("supervisor route={} inputChars={}", route, input.length());
        return Map.of(SupervisorKeys.ROUTE, route);
    }

    /**
     * 闲聊叶：system 复用 {@link ChatAgent#systemPrompt}，与 {@code agentId=chat} 行为对齐，
     * 便于对比「同模型入口、有无图路由」的差异。
     */
    private Map<String, Object> chatLeaf(OverAllState state, List<ConversationTurn> history) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String output = chatPort.complete(
                new ChatRequest(null, input, ChatAgent.systemPrompt(memory, retrieved), history));
        return Map.of(SupervisorKeys.OUTPUT, output);
    }

    /**
     * 知识叶：换一套更「死磕参考资料」的 system；retrieve 为空时也显式写进 prompt，
     * 避免模型假装有依据。
     */
    private Map<String, Object> knowledgeLeaf(OverAllState state, List<ConversationTurn> history) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String output = chatPort.complete(new ChatRequest(null, input, knowledgeSystem(memory, retrieved), history));
        return Map.of(SupervisorKeys.OUTPUT, output);
    }

    /**
     * 评审叶：独立人设，强调风险 / 边界 / 可维护性；与 knowledge 叶并列，证明「加叶不必改旧叶」。
     */
    private Map<String, Object> reviewLeaf(OverAllState state, List<ConversationTurn> history) {
        String input = String.valueOf(state.value(SupervisorKeys.INPUT, ""));
        String memory = String.valueOf(state.value(SupervisorKeys.MEMORY, ""));
        String retrieved = String.valueOf(state.value(SupervisorKeys.RETRIEVED, ""));
        String output = chatPort.complete(new ChatRequest(null, input, reviewSystem(memory, retrieved), history));
        return Map.of(SupervisorKeys.OUTPUT, output);
    }

    /**
     * 拼 knowledge 叶 system。单测可直接断言「空参考资料」分支是否出现。
     */
    static String knowledgeSystem(String memoryNotes, String retrievedContext) {
        StringBuilder system = new StringBuilder(KNOWLEDGE_SYSTEM);
        appendMemoryAndRetrieved(system, memoryNotes, retrievedContext);
        return system.toString();
    }

    /**
     * 拼 review 叶 system；记忆 / 检索拼接规则与 knowledge 共用，避免两套漂移。
     */
    static String reviewSystem(String memoryNotes, String retrievedContext) {
        StringBuilder system = new StringBuilder(REVIEW_SYSTEM);
        appendMemoryAndRetrieved(system, memoryNotes, retrievedContext);
        return system.toString();
    }

    /**
     * 把 Gateway 注入的长期记忆与检索片段追加进 system。
     * 检索为空时显式写「（空）」，逼模型承认资料不足，而不是默默编造。
     */
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

    /**
     * 每个 state 键的合并策略。本图全部用 {@link ReplaceStrategy}：后写覆盖前写。
     * <p>
     * 若以后要在多节点追加同一键（如 messages 列表），再对该键改用 Append / 自定义策略。
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
            return strategies;
        };
    }
}
