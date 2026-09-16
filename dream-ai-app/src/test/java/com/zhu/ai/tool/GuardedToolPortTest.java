package com.zhu.ai.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.tool.ToolPort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GuardedToolPortTest {

    @AfterEach
    void clearApprovals() {
        ToolApprovalContext.close();
    }

    @Test
    void allowDelegates() {
        AtomicInteger calls = new AtomicInteger();
        ToolPort raw = (name, args) -> {
            calls.incrementAndGet();
            return "ok:" + name;
        };
        var policy = new ConfigurableToolPolicy(List.of(), List.of(), ConfigurableToolPolicy.HitlMode.OFF);
        GuardedToolPort port = new GuardedToolPort(raw, policy);
        assertEquals("ok:ping", port.execute("ping", "{}"));
        assertEquals(1, calls.get());
    }

    @Test
    void denyDoesNotDelegate() {
        AtomicInteger calls = new AtomicInteger();
        ToolPort raw = (name, args) -> {
            calls.incrementAndGet();
            return "should-not-run";
        };
        var policy = new ConfigurableToolPolicy(
                List.of("current_date_time"), List.of(), ConfigurableToolPolicy.HitlMode.OFF);
        GuardedToolPort port = new GuardedToolPort(raw, policy);
        String out = port.execute("web_search", "{}");
        assertTrue(out.startsWith("tool denied by policy:"));
        assertTrue(GuardedToolPort.isPolicyBlock(out));
        assertEquals(0, calls.get());
    }

    @Test
    void needApprovalBlocksUntilContext() {
        AtomicInteger calls = new AtomicInteger();
        ToolPort raw = (name, args) -> {
            calls.incrementAndGet();
            return "ran";
        };
        var policy = new ConfigurableToolPolicy(
                List.of(), List.of("web_search"), ConfigurableToolPolicy.HitlMode.ENFORCE);
        GuardedToolPort port = new GuardedToolPort(raw, policy);
        assertTrue(port.execute("web_search", "{}").contains("requires human approval"));
        assertEquals(0, calls.get());
        ToolApprovalContext.open(List.of("web_search"));
        assertEquals("ran", port.execute("web_search", "{}"));
        assertEquals(1, calls.get());
    }
}
