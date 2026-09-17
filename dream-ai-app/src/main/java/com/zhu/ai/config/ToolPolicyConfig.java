package com.zhu.ai.config;

import com.zhu.ai.kernel.tool.ToolPolicyPort;
import com.zhu.ai.tool.ConfigurableToolPolicy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工具控制面：白名单 + HITL 模式 + 执行失败重试。
 */
@Configuration
public class ToolPolicyConfig {

    private static final Logger log = LoggerFactory.getLogger(ToolPolicyConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "dream.tools")
    ToolPolicyProperties toolPolicyProperties() {
        return new ToolPolicyProperties();
    }

    @Bean
    ConfigurableToolPolicy configurableToolPolicy(ToolPolicyProperties props) {
        ConfigurableToolPolicy.HitlMode mode = ConfigurableToolPolicy.HitlMode.from(props.getHitlMode());
        ConfigurableToolPolicy policy =
                new ConfigurableToolPolicy(props.getAllowlist(), props.getRequireApproval(), mode);
        log.info(
                "ToolPolicy hitlMode={} allowlist={} requireApproval={} maxRetries={}",
                policy.hitlMode(),
                policy.allowlist(),
                policy.requireApproval(),
                props.getMaxRetries());
        return policy;
    }

    @Bean
    ToolPolicyPort toolPolicyPort(ConfigurableToolPolicy policy) {
        return policy;
    }

    /** 绑定 {@code dream.tools.*}。 */
    public static final class ToolPolicyProperties {

        /** 非空则仅名单内工具可声明给模型并执行。 */
        private List<String> allowlist = List.of();

        /** 需人工审批的工具名；配合 hitl-mode=enforce。 */
        private List<String> requireApproval = List.of();

        /** off | enforce | auto */
        private String hitlMode = "off";

        /**
         * 工具执行失败后的额外重试次数（总尝试 = 1 + maxRetries）。
         * 策略拒执与未知工具不重试。默认 2。
         */
        private int maxRetries = 2;

        public List<String> getAllowlist() {
            return allowlist;
        }

        public void setAllowlist(List<String> allowlist) {
            this.allowlist = allowlist == null ? List.of() : allowlist;
        }

        public List<String> getRequireApproval() {
            return requireApproval;
        }

        public void setRequireApproval(List<String> requireApproval) {
            this.requireApproval = requireApproval == null ? List.of() : requireApproval;
        }

        public String getHitlMode() {
            return hitlMode;
        }

        public void setHitlMode(String hitlMode) {
            this.hitlMode = hitlMode;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = Math.max(0, maxRetries);
        }
    }
}
