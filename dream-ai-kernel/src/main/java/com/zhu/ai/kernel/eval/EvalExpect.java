package com.zhu.ai.kernel.eval;

import java.util.List;

/**
 * 单条用例的断言。字段为 null / 空则跳过该项。
 * 故意用「可检查的硬信号」（路由、工具执行/拦截、子串、调用次数、检索条数），避免再调裁判模型。
 */
public record EvalExpect(
        Boolean success,
        String agentId,
        List<String> outputContains,
        List<String> outputNotContains,
        Integer minToolCalls,
        Integer maxToolCalls,
        Integer maxModelCalls,
        String route,
        List<String> mustBlockTools,
        List<String> mustExecuteTools,
        Integer minRetrieveHits,
        Integer maxRetrieveHits) {

    public EvalExpect {
        outputContains = outputContains == null ? List.of() : List.copyOf(outputContains);
        outputNotContains = outputNotContains == null ? List.of() : List.copyOf(outputNotContains);
        mustBlockTools = mustBlockTools == null ? List.of() : List.copyOf(mustBlockTools);
        mustExecuteTools = mustExecuteTools == null ? List.of() : List.copyOf(mustExecuteTools);
        route = route == null ? null : route;
    }

    /** 无检索断言时的便捷构造。 */
    public EvalExpect(
            Boolean success,
            String agentId,
            List<String> outputContains,
            List<String> outputNotContains,
            Integer minToolCalls,
            Integer maxToolCalls,
            Integer maxModelCalls,
            String route,
            List<String> mustBlockTools,
            List<String> mustExecuteTools) {
        this(
                success,
                agentId,
                outputContains,
                outputNotContains,
                minToolCalls,
                maxToolCalls,
                maxModelCalls,
                route,
                mustBlockTools,
                mustExecuteTools,
                null,
                null);
    }

    /** 全字段跳过的空期望。 */
    public static EvalExpect none() {
        return new EvalExpect(null, null, null, null, null, null, null, null, null, null);
    }
}
