package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;

/**
 * 精排阶段：委托 {@link RetrieveRerank}，输出截到 {@link RetrieveContext#topK()}。
 */
public final class RerankStage implements RetrieveStage {

    @Override
    public String name() {
        return "rerank";
    }

    @Override
    public List<RetrieveHit> apply(RetrieveContext ctx, List<RetrieveHit> input) {
        return RetrieveRerank.rerank(ctx.query(), input, ctx.topK());
    }
}
