package com.zhu.ai;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeVideoAutoConfiguration;
import org.springframework.ai.vectorstore.pgvector.autoconfigure.PgVectorStoreAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 唯一可启动模块。扫描 {@code com.zhu.ai}（含 agents 的 Handler）。
 * 排除 DashScope Agent/图像/音视频自动配置；pgvector 不用主数据源（MySQL），改为手工接 Postgres。
 */
@SpringBootApplication(
        scanBasePackages = "com.zhu.ai",
        exclude = {
            DashScopeAgentAutoConfiguration.class,
            DashScopeImageAutoConfiguration.class,
            DashScopeVideoAutoConfiguration.class,
            DashScopeAudioSpeechAutoConfiguration.class,
            DashScopeAudioTranscriptionAutoConfiguration.class,
            PgVectorStoreAutoConfiguration.class
        })
public class DreamAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DreamAiApplication.class, args);
    }
}
