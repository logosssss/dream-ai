package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;

/**
 * 截断到 {@link RetrieveContext#topK()}；关闭精排时作为管线末段。
 */
public final class TruncateStage implements RetrieveStage {

    @Override
    public String name() {
        return "truncate";
    }

    @Override
    public List<RetrieveHit> apply(RetrieveContext ctx, List<RetrieveHit> input) {
        if (input == null || input.isEmpty() || ctx.topK() <= 0) {
            return List.of();
        }
        if (input.size() <= ctx.topK()) {
            return List.copyOf(input);
        }
        return List.copyOf(input.subList(0, ctx.topK()));
    }
}
