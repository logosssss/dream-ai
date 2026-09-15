package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DashScopeChatAdapterTest {

    @Test
    void looksMultimodalDetectsQwen38Family() {
        assertTrue(DashScopeChatAdapter.looksMultimodal("qwen3.8-27b"));
        assertTrue(DashScopeChatAdapter.looksMultimodal("qwen3.5-plus"));
        assertTrue(DashScopeChatAdapter.looksMultimodal("qwen-vl-max"));
        assertFalse(DashScopeChatAdapter.looksMultimodal("qwen-plus"));
        assertFalse(DashScopeChatAdapter.looksMultimodal("qwen3-max"));
    }

    @Test
    void configuredMultiModelWinsOverHeuristic() {
        var adapter = new DashScopeChatAdapter(null, java.util.List.of(), (n, a) -> "", null, false);
        assertFalse(adapter.resolveMultiModel("qwen3.8-27b"));
        adapter = new DashScopeChatAdapter(null, java.util.List.of(), (n, a) -> "", null, true);
        assertTrue(adapter.resolveMultiModel("qwen-plus"));
        adapter = new DashScopeChatAdapter(null, java.util.List.of(), (n, a) -> "", null, null);
        assertTrue(adapter.resolveMultiModel("qwen3.8-27b"));
    }

    @Test
    void nextDeltaHandlesCumulativeAndIncremental() {
        StringBuilder acc = new StringBuilder();
        org.junit.jupiter.api.Assertions.assertEquals("A", DashScopeChatAdapter.nextDelta(acc, "A"));
        acc.append("A");
        org.junit.jupiter.api.Assertions.assertEquals("B", DashScopeChatAdapter.nextDelta(acc, "AB"));
        acc.append("B");
        org.junit.jupiter.api.Assertions.assertEquals("C", DashScopeChatAdapter.nextDelta(acc, "C"));
    }
}
