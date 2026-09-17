package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrieveHit;
import java.util.ArrayList;
import java.util.List;

/**
 * 检索管线中的一截：输入命中列表 → 输出命中列表。
 * <p>
 * 用列表组合代替 {@code Pipeline(Hybrid(...))} 嵌套：召回 / 过滤 / 精排 / 截断各管一段，
 * 调试看 {@link RetrieveContext#traces()} 即可知道卡在哪一截。
 */
@FunctionalInterface
public interface RetrieveStage {

    /**
     * @param ctx   本轮查询上下文（query、目标 topK、阶段埋点）
     * @param input 上一阶段产出；召回阶段通常忽略（从空列表起步）
     */
    List<RetrieveHit> apply(RetrieveContext ctx, List<RetrieveHit> input);

    /** 阶段名，写入埋点与日志；默认取简单类名。 */
    default String name() {
        String simple = getClass().getSimpleName();
        return simple.isEmpty() ? "stage" : simple;
    }

    /** 包装一截并覆盖显示名（便于日志里写 recall / filter）。 */
    static RetrieveStage named(String name, RetrieveStage stage) {
        return new RetrieveStage() {
            @Override
            public List<RetrieveHit> apply(RetrieveContext ctx, List<RetrieveHit> input) {
                return stage.apply(ctx, input);
            }

            @Override
            public String name() {
                return name;
            }
        };
    }

    /** 不可变副本，避免装配后被改 list。 */
    static List<RetrieveStage> copyOf(List<RetrieveStage> stages) {
        if (stages == null || stages.isEmpty()) {
            return List.of();
        }
        return List.copyOf(new ArrayList<>(stages));
    }
}
