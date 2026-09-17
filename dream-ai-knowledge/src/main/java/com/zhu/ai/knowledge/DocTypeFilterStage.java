package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.List;

/**
 * 按 {@link RetrieveContext#docType()} 过滤；空白则原样放行。
 * docType 来自装配选项写入的 Context，本阶段不持有配置副本。
 */
public final class DocTypeFilterStage implements RetrieveStage {

    @Override
    public String name() {
        return "filter";
    }

    @Override
    public List<RetrieveHit> apply(RetrieveContext ctx, List<RetrieveHit> input) {
        return RetrieveFilters.byDocType(input, ctx == null ? "" : ctx.docType());
    }
}
