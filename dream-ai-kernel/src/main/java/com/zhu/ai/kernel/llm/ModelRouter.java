package com.zhu.ai.kernel.llm;

/**
 * 按任务键解析模型 id（能力契约，不是 HTTP 端口）。
 * <p>
 * 返回空白表示使用 ChatPort / 厂商适配器的默认模型。
 * 实现放在 app（配置绑定）；Agent / Graph 只依赖本接口。
 */
public interface ModelRouter {

    /** 与 {@code agentId=chat}、Graph 闲聊叶对齐。 */
    String CHAT = "chat";

    /** Graph knowledge 叶。 */
    String KNOWLEDGE = "knowledge";

    /** Graph review 叶。 */
    String REVIEW = "review";

    /**
     * @param taskKey 任务键（如 {@link #CHAT}）；null / 未知键回落到 default
     * @return 模型 id；空白表示走装配默认模型
     */
    String resolve(String taskKey);
}
