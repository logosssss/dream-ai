package com.zhu.ai.graph;

import java.util.Locale;

/**
 * Supervisor 意图路由（纯函数，可单测）。
 * <p>
 * 故意用关键词规则而不是再调一次模型：面试时好讲清「路由与业务叶节点解耦」。
 * 优先级：{@code review} → {@code knowledge} → {@code chat}（先匹配先赢）。
 */
public final class IntentRouter {

    public static final String CHAT = "chat";
    public static final String KNOWLEDGE = "knowledge";
    public static final String REVIEW = "review";

    private IntentRouter() {}

    public static String decide(String input) {
        if (input == null || input.isBlank()) {
            return CHAT;
        }
        String text = input.toLowerCase(Locale.ROOT);
        if (containsAny(
                text,
                "审查",
                "评审",
                "review",
                "帮我看看代码",
                "这段代码",
                "有没有问题",
                "风险点",
                "code review")) {
            return REVIEW;
        }
        if (containsAny(
                text,
                "检索",
                "知识库",
                "文档",
                "rag",
                "参考资料",
                "仓库知识",
                "agentgateway",
                "什么是 gateway",
                "gateway 是什么")) {
            return KNOWLEDGE;
        }
        return CHAT;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
