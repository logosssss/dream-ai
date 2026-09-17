package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.observe.InMemoryObservePort;
import com.zhu.ai.tool.GuardedToolPort;
import com.zhu.ai.kernel.llm.TokenSink;
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
        assertTrue(obs.blockedTools().isEmpty());
        assertEquals(List.of("current_date_time"), obs.executedTools());
    }

    @Test
    void countsOnlyExecutedToolsNotPolicyBlocks() {
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
                                        "c1", "function", "web_search_prime", "{}")))
                                .build();
                    }
                    return new AssistantMessage("blocked");
                },
                (name, args) -> GuardedToolPort.DENIED_PREFIX + " " + name,
                observe);
        assertEquals("blocked", loop.run(List.of(new UserMessage("天气"))));
        var obs = observe.complete(true, null);
        assertEquals(2, obs.modelCalls());
        assertEquals(0, obs.toolCalls());
        assertEquals(List.of("web_search_prime"), obs.blockedTools());
        assertTrue(obs.executedTools().isEmpty());
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

    @Test
    void emitToolLifecycleToSink() {
        AtomicInteger steps = new AtomicInteger();
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s", "chat");
        List<String> starts = new java.util.ArrayList<>();
        List<String> executed = new java.util.ArrayList<>();
        TokenSink sink = new TokenSink() {
            @Override
            public void onDelta(String delta) {}

            @Override
            public void onToolStart(String toolName) {
                starts.add(toolName);
            }

            @Override
            public void onToolExecuted(String toolName) {
                executed.add(toolName);
            }
        };
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
                    return new AssistantMessage("ok");
                },
                (name, args) -> "tick",
                observe,
                sink);
        assertEquals("ok", loop.run(List.of(new UserMessage("几点"))));
        assertEquals(List.of("current_date_time"), starts);
        assertEquals(List.of("current_date_time"), executed);
    }

    @Test
    void retriesExecutionFailureThenSucceeds() {
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger toolRuns = new AtomicInteger();
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s", "chat");
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    if (steps.incrementAndGet() == 1) {
                        return AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "c1", "function", "flaky", "{}")))
                                .build();
                    }
                    return new AssistantMessage("recovered");
                },
                (name, args) -> {
                    if (toolRuns.incrementAndGet() < 3) {
                        return ToolCallbackPort.ERROR_PREFIX + " boom";
                    }
                    return "ok";
                },
                observe,
                null,
                2);
        assertEquals("recovered", loop.run(List.of(new UserMessage("x"))));
        assertEquals(3, toolRuns.get());
        var obs = observe.complete(true, null);
        assertEquals(1, obs.toolCalls());
        assertEquals(List.of("flaky"), obs.executedTools());
        assertTrue(obs.failedTools().isEmpty());
        assertTrue(obs.blockedTools().isEmpty());
    }

    @Test
    void marksFailedAfterRetriesExhausted() {
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger toolRuns = new AtomicInteger();
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s", "chat");
        List<String> failedEvents = new java.util.ArrayList<>();
        TokenSink sink = new TokenSink() {
            @Override
            public void onDelta(String delta) {}

            @Override
            public void onToolFailed(String toolName) {
                failedEvents.add(toolName);
            }
        };
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    if (steps.incrementAndGet() == 1) {
                        return AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "c1", "function", "broken", "{}")))
                                .build();
                    }
                    boolean sawError = messages.stream()
                            .filter(org.springframework.ai.chat.messages.ToolResponseMessage.class::isInstance)
                            .map(org.springframework.ai.chat.messages.ToolResponseMessage.class::cast)
                            .flatMap(m -> m.getResponses().stream())
                            .anyMatch(r -> r.responseData().startsWith(ToolCallbackPort.ERROR_PREFIX));
                    assertTrue(sawError);
                    return new AssistantMessage("sorry");
                },
                (name, args) -> {
                    toolRuns.incrementAndGet();
                    return ToolCallbackPort.ERROR_PREFIX + " down";
                },
                observe,
                sink,
                2);
        assertEquals("sorry", loop.run(List.of(new UserMessage("x"))));
        assertEquals(3, toolRuns.get());
        assertEquals(List.of("broken"), failedEvents);
        var obs = observe.complete(true, null);
        assertEquals(0, obs.toolCalls());
        assertEquals(List.of("broken"), obs.failedTools());
        assertTrue(obs.executedTools().isEmpty());
    }

    @Test
    void doesNotRetryPolicyBlockOrUnknownTool() {
        AtomicInteger steps = new AtomicInteger();
        AtomicInteger toolRuns = new AtomicInteger();
        InMemoryObservePort observe = new InMemoryObservePort();
        observe.begin("s", "chat");
        ToolCallingLoop loop = new ToolCallingLoop(
                messages -> {
                    int n = steps.incrementAndGet();
                    if (n == 1) {
                        return AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(
                                        new AssistantMessage.ToolCall(
                                                "c1", "function", "web_search_prime", "{}"),
                                        new AssistantMessage.ToolCall(
                                                "c2", "function", "nope", "{}")))
                                .build();
                    }
                    return new AssistantMessage("done");
                },
                (name, args) -> {
                    toolRuns.incrementAndGet();
                    if ("web_search_prime".equals(name)) {
                        return GuardedToolPort.DENIED_PREFIX + " " + name;
                    }
                    return ToolCallbackPort.UNKNOWN_PREFIX + " " + name;
                },
                observe,
                null,
                5);
        assertEquals("done", loop.run(List.of(new UserMessage("x"))));
        assertEquals(2, toolRuns.get());
        var obs = observe.complete(true, null);
        assertEquals(0, obs.toolCalls());
        assertEquals(List.of("web_search_prime"), obs.blockedTools());
        assertEquals(List.of("nope"), obs.failedTools());
        assertTrue(obs.executedTools().isEmpty());
    }
}
