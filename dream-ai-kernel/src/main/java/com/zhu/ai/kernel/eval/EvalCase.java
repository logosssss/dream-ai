package com.zhu.ai.kernel.eval;

/**
 * 一条回归用例：固定输入 + 期望。{@code sessionId} 为空时由 runner 用 {@code eval-{id}}。
 */
public record EvalCase(String id, String agentId, String sessionId, String input, EvalExpect expect) {

    public EvalCase {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("eval case id required");
        }
        if (input == null) {
            throw new IllegalArgumentException("eval case input required");
        }
        expect = expect == null ? EvalExpect.none() : expect;
    }

    public String resolvedSessionId() {
        return sessionId == null || sessionId.isBlank() ? "eval-" + id : sessionId;
    }
}
