package com.zhu.ai.kernel.eval;

import java.util.List;

/** 单条用例打分结果；{@code failures} 非空则 {@code passed=false}。 */
public record EvalCaseResult(
        String caseId,
        boolean passed,
        List<String> failures,
        String agentId,
        String output,
        Integer modelCalls,
        Integer toolCalls,
        Boolean success) {

    public EvalCaseResult {
        failures = failures == null ? List.of() : List.copyOf(failures);
        passed = failures.isEmpty();
    }
}
