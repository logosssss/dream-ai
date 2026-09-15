package com.zhu.ai.kernel.eval;

import java.util.List;

/** 一整套 golden cases 的汇总。 */
public record EvalSuiteResult(int total, int passed, int failed, List<EvalCaseResult> results) {

    public EvalSuiteResult {
        results = results == null ? List.of() : List.copyOf(results);
    }

    public boolean allPassed() {
        return failed == 0;
    }

    public static EvalSuiteResult of(List<EvalCaseResult> results) {
        List<EvalCaseResult> copy = results == null ? List.of() : List.copyOf(results);
        int ok = 0;
        for (EvalCaseResult r : copy) {
            if (r.passed()) {
                ok++;
            }
        }
        return new EvalSuiteResult(copy.size(), ok, copy.size() - ok, copy);
    }
}
