package com.zhu.ai.kernel.eval;

import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.ArrayList;
import java.util.List;

/**
 * 纯函数打分：把 Gateway 结果对照 {@link EvalExpect}。
 * 不调模型、不碰 IO；面试可讲「回归断言与运行时解耦」。
 */
public final class EvalScorer {

    private EvalScorer() {}

    public static EvalCaseResult score(EvalCase evalCase, AgentInvokeResult result) {
        List<String> failures = new ArrayList<>();
        EvalExpect expect = evalCase.expect();
        String output = result == null || result.output() == null ? "" : result.output();
        String agentId = result == null ? null : result.agentId();
        InvokeObservation obs = result == null ? null : result.observe();
        Integer modelCalls = obs == null ? null : obs.modelCalls();
        Integer toolCalls = obs == null ? null : obs.toolCalls();
        Boolean success = obs == null ? null : obs.success();

        if (expect.success() != null) {
            if (success == null) {
                failures.add("success expected " + expect.success() + " but observe missing");
            } else if (success != expect.success()) {
                failures.add("success expected " + expect.success() + " got " + success);
            }
        }
        if (expect.agentId() != null && !expect.agentId().isBlank()) {
            if (agentId == null || !expect.agentId().equals(agentId)) {
                failures.add("agentId expected " + expect.agentId() + " got " + agentId);
            }
        }
        for (String needle : expect.outputContains()) {
            if (!output.contains(needle)) {
                failures.add("output missing: " + needle);
            }
        }
        for (String needle : expect.outputNotContains()) {
            if (output.contains(needle)) {
                failures.add("output must not contain: " + needle);
            }
        }
        if (expect.minToolCalls() != null) {
            if (toolCalls == null) {
                failures.add("minToolCalls=" + expect.minToolCalls() + " but observe missing");
            } else if (toolCalls < expect.minToolCalls()) {
                failures.add("toolCalls " + toolCalls + " < min " + expect.minToolCalls());
            }
        }
        if (expect.maxToolCalls() != null) {
            if (toolCalls == null) {
                failures.add("maxToolCalls=" + expect.maxToolCalls() + " but observe missing");
            } else if (toolCalls > expect.maxToolCalls()) {
                failures.add("toolCalls " + toolCalls + " > max " + expect.maxToolCalls());
            }
        }
        if (expect.maxModelCalls() != null) {
            if (modelCalls == null) {
                failures.add("maxModelCalls=" + expect.maxModelCalls() + " but observe missing");
            } else if (modelCalls > expect.maxModelCalls()) {
                failures.add("modelCalls " + modelCalls + " > max " + expect.maxModelCalls());
            }
        }

        return new EvalCaseResult(
                evalCase.id(), failures.isEmpty(), failures, agentId, output, modelCalls, toolCalls, success);
    }

    /** Gateway 抛错时记为失败用例。 */
    public static EvalCaseResult error(EvalCase evalCase, Throwable error) {
        String msg = error == null ? "unknown error" : error.getMessage();
        if (msg == null || msg.isBlank()) {
            msg = error.getClass().getSimpleName();
        }
        return new EvalCaseResult(
                evalCase.id(), false, List.of("invoke error: " + msg), null, null, null, null, false);
    }
}
