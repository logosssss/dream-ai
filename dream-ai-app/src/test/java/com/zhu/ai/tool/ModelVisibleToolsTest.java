package com.zhu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.tool.ToolPolicyDecision;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

class ModelVisibleToolsTest {

    @Test
    void allowlistDoesNotHideToolsFromModel() {
        List<ToolCallback> all = List.of(
                FunctionToolCallback.builder("a", () -> "a").description("a").build(),
                FunctionToolCallback.builder("b", () -> "b").description("b").build());
        var policy = new ConfigurableToolPolicy(List.of("b"), List.of(), ConfigurableToolPolicy.HitlMode.OFF);
        List<ToolCallback> visible = ModelVisibleTools.filter(all, policy);
        assertEquals(2, visible.size());
        assertTrue(policy.visibleToModel("a"));
        assertTrue(policy.visibleToModel("b"));
        assertFalse(policy.visibleToModel(""));
        assertEquals(ToolPolicyDecision.DENY, policy.decide("a"));
        assertEquals(ToolPolicyDecision.ALLOW, policy.decide("b"));
    }
}
