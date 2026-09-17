package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 把 {@link RetrieveStage} 列表串成 {@link RetrievePort}：从空命中起步，按序 apply。
 * <p>
 * 每阶段记录 in/out 条数、输出字符数与耗时；失败时先打阶段名再抛出。
 * 截止时间（{@link RetrieveOptions#timeout()}）在阶段前检查，超时则停止后续阶段并返回已有命中。
 */
public final class StagedRetrievePort implements RetrievePort {

    private static final Logger log = LoggerFactory.getLogger(StagedRetrievePort.class);

    private final List<RetrieveStage> stages;

    private final RetrieveOptions options;

    public StagedRetrievePort(List<RetrieveStage> stages) {
        this(stages, RetrieveOptions.empty());
    }

    public StagedRetrievePort(List<RetrieveStage> stages, RetrieveOptions options) {
        this.stages = RetrieveStage.copyOf(stages);
        this.options = options == null ? RetrieveOptions.empty() : options;
        if (this.stages.isEmpty()) {
            throw new IllegalArgumentException("stages required");
        }
    }

    @Override
    public List<RetrieveHit> retrieve(String query, int topK) {
        if (topK <= 0) {
            return List.of();
        }
        RetrieveContext ctx = new RetrieveContext(query, topK, options);
        List<RetrieveHit> hits = List.of();
        for (RetrieveStage stage : stages) {
            if (ctx.expired()) {
                log.warn(
                        "retrieve deadline exceeded before stage={} outHits={}",
                        stage.name(),
                        hits.size());
                break;
            }
            int in = hits.size();
            long t0 = System.nanoTime();
            try {
                hits = stage.apply(ctx, hits == null ? List.of() : hits);
            } catch (RuntimeException ex) {
                log.warn("stage {} failed: {}", stage.name(), ex.toString());
                throw ex;
            }
            if (hits == null) {
                hits = List.of();
            }
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            ctx.record(stage.name(), in, hits.size(), sumChars(hits), ms);
        }
        if (log.isInfoEnabled()) {
            log.info(
                    "retrieve queryChars={} topK={} docType={} out={} outChars={} stages={}",
                    ctx.query().length(),
                    topK,
                    ctx.docType().isEmpty() ? "-" : ctx.docType(),
                    hits.size(),
                    sumChars(hits),
                    formatTraces(ctx.traces()));
        }
        return List.copyOf(hits);
    }

    static int sumChars(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return 0;
        }
        int n = 0;
        for (RetrieveHit hit : hits) {
            if (hit != null && hit.text() != null) {
                n += hit.text().length();
            }
        }
        return n;
    }

    static String formatTraces(List<RetrieveContext.StageTrace> traces) {
        if (traces == null || traces.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < traces.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            RetrieveContext.StageTrace t = traces.get(i);
            sb.append(t.stage())
                    .append(':')
                    .append(t.inHits())
                    .append("->")
                    .append(t.outHits())
                    .append('@')
                    .append(t.millis())
                    .append("ms")
                    .append('/')
                    .append(t.outChars())
                    .append("c");
        }
        return sb.append(']').toString();
    }
}
