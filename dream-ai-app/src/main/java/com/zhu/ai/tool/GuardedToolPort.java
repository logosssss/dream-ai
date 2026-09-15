package com.zhu.ai.tool;

import com.zhu.ai.kernel.tool.ToolPolicyDecision;
import com.zhu.ai.kernel.tool.ToolPolicyPort;
import com.zhu.ai.kernel.tool.ToolPort;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 在真实 {@link ToolPort} 外包一层策略：白名单拒执、HITL 未批拒执，原因串回模型。
 */
public final class GuardedToolPort implements ToolPort {

    private static final Logger log = LoggerFactory.getLogger(GuardedToolPort.class);

    private final ToolPort delegate;
    private final ToolPolicyPort policy;

    public GuardedToolPort(ToolPort delegate, ToolPolicyPort policy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    @Override
    public String execute(String name, String argumentsJson) {
        ToolPolicyDecision decision = policy.decide(name);
        return switch (decision) {
            case ALLOW -> delegate.execute(name, argumentsJson);
            case DENY -> {
                log.info("tool denied by policy name={}", name);
                yield "tool denied by policy: " + name;
            }
            case NEED_APPROVAL -> {
                log.info("tool blocked pending approval name={}", name);
                yield "tool requires human approval: " + name
                        + " (pass X-Dream-Tool-Approvals or set dream.tools.hitl-mode=auto)";
            }
        };
    }
}
