package com.zhu.ai.observe;

import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.observe.ObservePort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.MDC;

/**
 * 进程内环缓冲观测：最近 {@link #CAPACITY} 条；同线程 begin→mark→complete。
 */
public final class InMemoryObservePort implements ObservePort {

    static final int CAPACITY = 100;

    private final ConcurrentLinkedDeque<InvokeObservation> ring = new ConcurrentLinkedDeque<>();

    private final ThreadLocal<Pending> current = new ThreadLocal<>();

    @Override
    public String begin(String sessionId, String agentId) {
        clearMdc();
        current.remove();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        current.set(new Pending(traceId, sessionId, agentId, System.nanoTime()));
        MDC.put("traceId", traceId);
        return traceId;
    }

    @Override
    public void markModelCall() {
        Pending pending = current.get();
        if (pending != null) {
            pending.modelCalls.incrementAndGet();
        }
    }

    @Override
    public void markToolCalls(int count) {
        if (count <= 0) {
            return;
        }
        Pending pending = current.get();
        if (pending != null) {
            pending.toolCalls.addAndGet(count);
        }
    }

    @Override
    public InvokeObservation complete(boolean success, String errorMessage) {
        Pending pending = current.get();
        current.remove();
        if (pending == null) {
            clearMdc();
            return new InvokeObservation("", "", "", 0L, 0, 0, success, errorMessage);
        }
        long durationMs = Math.max(0L, (System.nanoTime() - pending.startNanos) / 1_000_000L);
        InvokeObservation observation = new InvokeObservation(
                pending.traceId,
                pending.sessionId,
                pending.agentId,
                durationMs,
                pending.modelCalls.get(),
                pending.toolCalls.get(),
                success,
                errorMessage);
        ring.addFirst(observation);
        while (ring.size() > CAPACITY) {
            ring.pollLast();
        }
        clearMdc();
        return observation;
    }

    @Override
    public List<InvokeObservation> recent(int limit) {
        int n = Math.max(0, limit);
        List<InvokeObservation> out = new ArrayList<>(Math.min(n, ring.size()));
        for (InvokeObservation item : ring) {
            if (out.size() >= n) {
                break;
            }
            out.add(item);
        }
        return List.copyOf(out);
    }

    private static void clearMdc() {
        MDC.remove("traceId");
    }

    private static final class Pending {
        private final String traceId;
        private final String sessionId;
        private final String agentId;
        private final long startNanos;
        private final AtomicInteger modelCalls = new AtomicInteger();
        private final AtomicInteger toolCalls = new AtomicInteger();

        private Pending(String traceId, String sessionId, String agentId, long startNanos) {
            this.traceId = traceId;
            this.sessionId = sessionId;
            this.agentId = agentId;
            this.startNanos = startNanos;
        }
    }
}
