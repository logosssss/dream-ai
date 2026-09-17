package com.zhu.ai.knowledge;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 一次 {@code retrieve(query, topK)} 的可变上下文：目标条数、过滤条件、截止时间与阶段埋点。
 * 由 {@link StagedRetrievePort} 创建，沿阶段列表向下传。
 */
public final class RetrieveContext {

    private final String query;

    private final int topK;

    private final String userId;

    private final String docType;

    private final Instant deadline;

    private final List<StageTrace> traces = new ArrayList<>();

    public RetrieveContext(String query, int topK) {
        this(query, topK, RetrieveOptions.empty());
    }

    public RetrieveContext(String query, int topK, RetrieveOptions options) {
        RetrieveOptions opts = options == null ? RetrieveOptions.empty() : options;
        this.query = query == null ? "" : query;
        this.topK = topK;
        this.userId = opts.userId();
        this.docType = opts.docType();
        this.deadline = opts.deadlineFromNow();
    }

    public String query() {
        return query;
    }

    /** Gateway / 调用方期望的最终条数（精排或截断的目标）。 */
    public int topK() {
        return topK;
    }

    /** 预留个性化；当前阶段可不使用。 */
    public String userId() {
        return userId;
    }

    /** 供 {@link DocTypeFilterStage} 读取，避免阶段自己持有配置副本。 */
    public String docType() {
        return docType;
    }

    public Instant deadline() {
        return deadline;
    }

    /** 已过截止时间则 true；无 deadline 时恒 false。 */
    public boolean expired() {
        return deadline != null && Instant.now().isAfter(deadline);
    }

    void record(String stage, int inHits, int outHits, int outChars, long millis) {
        traces.add(new StageTrace(stage, inHits, outHits, Math.max(0L, millis), Math.max(0, outChars)));
    }

    public List<StageTrace> traces() {
        return List.copyOf(traces);
    }

    /**
     * 单阶段埋点快照。
     *
     * @param stage    阶段名
     * @param inHits   进入条数
     * @param outHits  离开条数
     * @param millis   耗时
     * @param outChars 离开命中正文总字符数（估 token 预算）
     */
    public record StageTrace(String stage, int inHits, int outHits, long millis, int outChars) {}
}
