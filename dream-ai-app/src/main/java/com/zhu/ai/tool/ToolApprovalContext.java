package com.zhu.ai.tool;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本请求已批准的工具名（HITL）。由 HTTP 头写入，工具执行后清理。
 * <p>
 * 仅用于同步请求线程；SSE 工作线程须在执行前 {@link #open} 同样的审批集合。
 */
public final class ToolApprovalContext {

    private static final ThreadLocal<Set<String>> APPROVED = new ThreadLocal<>();

    private ToolApprovalContext() {}

    public static void open(Collection<String> toolNames) {
        APPROVED.set(normalize(toolNames));
    }

    public static void close() {
        APPROVED.remove();
    }

    public static boolean isApproved(String toolName) {
        Set<String> set = APPROVED.get();
        if (set == null || toolName == null) {
            return false;
        }
        return set.contains(toolName.trim());
    }

    /** 解析 {@code X-Dream-Tool-Approvals: a,b,c}。 */
    public static Set<String> parseHeader(String header) {
        if (header == null || header.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> normalize(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return Set.of();
        }
        return names.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
