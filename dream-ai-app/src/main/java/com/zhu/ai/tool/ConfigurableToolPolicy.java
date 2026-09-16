package com.zhu.ai.tool;

import com.zhu.ai.kernel.tool.ToolPolicyDecision;
import com.zhu.ai.kernel.tool.ToolPolicyPort;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 可配置工具策略：
 * <ul>
 *   <li>{@code allowlist} 非空 → 仅名单内可<strong>执行</strong>（仍声明给模型，拦截时走 DENY 回传）</li>
 *   <li>{@code requireApproval} + {@code hitlMode=enforce} → 需本轮审批（请求头 / 上下文）</li>
 *   <li>{@code hitlMode=auto} → 审批类工具自动放行（演示 / 测试）</li>
 *   <li>{@code hitlMode=off} → 忽略审批名单</li>
 * </ul>
 */
public final class ConfigurableToolPolicy implements ToolPolicyPort {

    public enum HitlMode {
        OFF,
        ENFORCE,
        AUTO;

        public static HitlMode from(String raw) {
            if (raw == null || raw.isBlank()) {
                return OFF;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "off" -> OFF;
                case "enforce" -> ENFORCE;
                case "auto" -> AUTO;
                default -> throw new IllegalArgumentException(
                        "dream.tools.hitl-mode must be off|enforce|auto, got: " + raw);
            };
        }
    }

    private final Set<String> allowlist;
    private final Set<String> requireApproval;
    private final HitlMode hitlMode;

    public ConfigurableToolPolicy(
            Collection<String> allowlist, Collection<String> requireApproval, HitlMode hitlMode) {
        this.allowlist = normalize(allowlist);
        this.requireApproval = normalize(requireApproval);
        this.hitlMode = Objects.requireNonNullElse(hitlMode, HitlMode.OFF);
    }

    @Override
    public ToolPolicyDecision decide(String toolName) {
        String name = toolName == null ? "" : toolName.trim();
        if (name.isEmpty()) {
            return ToolPolicyDecision.DENY;
        }
        if (!allowlist.isEmpty() && !allowlist.contains(name)) {
            return ToolPolicyDecision.DENY;
        }
        if (!requireApproval.contains(name) || hitlMode == HitlMode.OFF) {
            return ToolPolicyDecision.ALLOW;
        }
        if (hitlMode == HitlMode.AUTO) {
            return ToolPolicyDecision.ALLOW;
        }
        // ENFORCE
        if (ToolApprovalContext.isApproved(name)) {
            return ToolPolicyDecision.ALLOW;
        }
        return ToolPolicyDecision.NEED_APPROVAL;
    }

    /**
     * 是否把该工具 schema 声明给模型。
     * 默认全量声明：白名单只拦执行，这样模型仍会发起 tool call，日志能看到 {@code tool blocked}。
     * 若要从模型侧藏起白名单外工具，另开 advertise 过滤（当前不做）。
     */
    public boolean visibleToModel(String toolName) {
        String name = toolName == null ? "" : toolName.trim();
        return !name.isEmpty();
    }

    public Set<String> allowlist() {
        return allowlist;
    }

    public Set<String> requireApproval() {
        return requireApproval;
    }

    public HitlMode hitlMode() {
        return hitlMode;
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
