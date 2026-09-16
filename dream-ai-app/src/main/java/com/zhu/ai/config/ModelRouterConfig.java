package com.zhu.ai.config;

import com.zhu.ai.kernel.llm.ModelRouter;
import com.zhu.ai.llm.MapModelRouter;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按任务选模型：{@code dream.models.*}。未配置则全部回落适配器默认模型。
 */
@Configuration
public class ModelRouterConfig {

    private static final Logger log = LoggerFactory.getLogger(ModelRouterConfig.class);

    @Bean
    @ConfigurationProperties(prefix = "dream.models")
    DreamModelsProperties dreamModelsProperties() {
        return new DreamModelsProperties();
    }

    @Bean
    ModelRouter modelRouter(DreamModelsProperties props) {
        Map<String, String> byTask = new LinkedHashMap<>();
        byTask.put(ModelRouter.CHAT, props.getChat());
        byTask.put(ModelRouter.KNOWLEDGE, props.getKnowledge());
        byTask.put(ModelRouter.REVIEW, props.getReview());
        ModelRouter router = new MapModelRouter(props.getDefaultModel(), byTask);
        log.info(
                "ModelRouter default={} chat={} knowledge={} review={}",
                nullToDash(router.resolve("__missing__")),
                nullToDash(router.resolve(ModelRouter.CHAT)),
                nullToDash(router.resolve(ModelRouter.KNOWLEDGE)),
                nullToDash(router.resolve(ModelRouter.REVIEW)));
        return router;
    }

    private static String nullToDash(String model) {
        return model == null || model.isBlank() ? "(adapter-default)" : model;
    }

    /** 绑定 {@code dream.models.*}。yaml 键 {@code default} 对应 {@link #setDefault}。 */
    public static final class DreamModelsProperties {

        private String defaultModel = "";
        private String chat = "";
        private String knowledge = "";
        private String review = "";

        public String getDefaultModel() {
            return defaultModel;
        }

        /** Spring 绑定 {@code dream.models.default}。 */
        public void setDefault(String defaultModel) {
            this.defaultModel = defaultModel == null ? "" : defaultModel;
        }

        public String getChat() {
            return chat;
        }

        public void setChat(String chat) {
            this.chat = chat == null ? "" : chat;
        }

        public String getKnowledge() {
            return knowledge;
        }

        public void setKnowledge(String knowledge) {
            this.knowledge = knowledge == null ? "" : knowledge;
        }

        public String getReview() {
            return review;
        }

        public void setReview(String review) {
            this.review = review == null ? "" : review;
        }
    }
}
