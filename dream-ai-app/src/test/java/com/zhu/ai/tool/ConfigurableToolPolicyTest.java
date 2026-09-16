package com.zhu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.tool.ToolPolicyDecision;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ConfigurableToolPolicyTest {

    @AfterEach
    void clearApprovals() {
        ToolApprovalContext.close();
    }

    @Test
    void emptyAllowlistAllowsAllWhenHitlOff() {
        var policy = new ConfigurableToolPolicy(List.of(), List.of("web_search"), ConfigurableToolPolicy.HitlMode.OFF);
        assertEquals(ToolPolicyDecision.ALLOW, policy.decide("web_search"));
        assertEquals(ToolPolicyDecision.ALLOW, policy.decide("current_date_time"));
        assertTrue(policy.visibleToModel("web_search"));
    }

    @Test
    void allowlistDeniesOutside() {
        var policy = new ConfigurableToolPolicy(
                List.of("current_date_time"), List.of(), ConfigurableToolPolicy.HitlMode.OFF);
        assertEquals(ToolPolicyDecision.ALLOW, policy.decide("current_date_time"));
        assertEquals(ToolPolicyDecision.DENY, policy.decide("web_search"));
        assertTrue(policy.visibleToModel("web_search"));
        assertTrue(policy.visibleToModel("current_date_time"));
    }

    @Test
    void enforceNeedsApprovalUntilHeader() {
        var policy = new ConfigurableToolPolicy(
                List.of(), List.of("web_search"), ConfigurableToolPolicy.HitlMode.ENFORCE);
        assertEquals(ToolPolicyDecision.NEED_APPROVAL, policy.decide("web_search"));
        ToolApprovalContext.open(Set.of("web_search"));
        assertEquals(ToolPolicyDecision.ALLOW, policy.decide("web_search"));
    }

    @Test
    void autoApprovesRequireList() {
        var policy = new ConfigurableToolPolicy(
                List.of(), List.of("web_search"), ConfigurableToolPolicy.HitlMode.AUTO);
        assertEquals(ToolPolicyDecision.ALLOW, policy.decide("web_search"));
    }
}
