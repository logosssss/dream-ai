package com.zhu.ai.kernel.eval;

import java.util.List;

/**
 * 单条用例的断言。字段为 null / 空则跳过该项。
 * 故意用「可检查的硬信号」（路由前缀、子串、调用次数），避免再调裁判模型。
 */
public record EvalExpect(
        Boolean success,
        String agentId,
        List<String> outputContains,
        List<String> outputNotContains,
        Integer minToolCalls,
        Integer maxToolCalls,
        Integer maxModelCalls) {

    public EvalExpect {
        outputContains = outputContains == null ? List.of() : List.copyOf(outputContains);
        outputNotContains = outputNotContains == null ? List.of() : List.copyOf(outputNotContains);
    }
}
