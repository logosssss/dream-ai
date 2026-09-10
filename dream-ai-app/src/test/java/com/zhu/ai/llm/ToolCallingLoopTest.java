package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.observe.InMemoryObservePort;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

class ToolCallingLoopTest {

    @Test
    void returnsTextWhenNoToolCalls() {
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> new AssistantMessage("直接回答"),
                (name, args) -> {
                    throw new AssertionError("tool should not run");
                });
        assertEquals("直接回答", loop.run(List.of(new UserMessage("hi"))));
    }

    @Test
    void executesToolThenReturnsModelText() {
        AtomicInteger steps = new AtomicInteger();
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    int n = steps.incrementAndGet();
                    if (n == 1) {
                        return AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "c1", "function", "current_date_time", "{}")))
                                .build();
                    }
                    return new AssistantMessage("现在是工具给的时间");
                },
                (name, args) -> {
                    assertEquals("current_date_time", name);
                    return "2026-09-09 16:00:00 CST";
                });
        assertEquals("现在是工具给的时间", loop.run(List.of(new UserMessage("几点了"))));
        assertEquals(2, steps.get());
    }

    @Test
    void stopsAtMaxSteps() {
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger toolRuns = new AtomicInteger();
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    steps.incrementAndGet();
                    return AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "c" + steps.get(), "function", "current_date_time", "{}")))
                            .build();
                },
                (name, args) -> {
                    toolRuns.incrementAndGet();
                    return "tick";
                });
        assertEquals(ToolCallingLoop.MAX_STEPS_MESSAGE, loop.run(List.of(new UserMessage("一直调工具"))));
        assertEquals(ToolCallingLoop.MAX_STEPS, steps.get());
        assertEquals(ToolCallingLoop.MAX_STEPS - 1, toolRuns.get());
    }

    @Test
    void countsModelAndToolCalls() {
        AtomicInteger steps = new AtomicInteger();
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s", "chat");
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    int n = steps.incrementAndGet();
                    if (n == 1) {
                        return AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "c1", "function", "current_date_time", "{}")))
                                .build();
                    }
                    return new AssistantMessage("done");
                },
                (name, args) -> "tick",
                observe);
        assertEquals("done", loop.run(List.of(new UserMessage("hi"))));
        var obs = observe.complete(true, null);
        assertEquals(2, obs.modelCalls());
        assertEquals(1, obs.toolCalls());
    }

    @Test
    void unknownToolFeedsErrorBack() {
        AtomicInteger steps = new AtomicInteger();
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    if (steps.incrementAndGet() == 1) {
                        return AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "c1", "function", "nope", "{}")))
                                .build();
                    }
                    boolean sawUnknown = messages.stream()
                            .filter(org.springframework.ai.chat.messages.ToolResponseMessage.class::isInstance)
                            .map(org.springframework.ai.chat.messages.ToolResponseMessage.class::cast)
                            .flatMap(m -> m.getResponses().stream())
                            .anyMatch(r -> r.responseData().contains("unknown tool"));
                    assertTrue(sawUnknown);
                    return new AssistantMessage("工具不可用");
                },
                (name, args) -> "unknown tool: " + name);
        assertEquals("工具不可用", loop.run(List.of(new UserMessage("x"))));
    }
}
