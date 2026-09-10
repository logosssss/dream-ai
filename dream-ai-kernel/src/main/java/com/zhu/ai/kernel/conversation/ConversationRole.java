package com.zhu.ai.kernel.conversation;

/** 一轮里的角色。Gateway 写入，ChatPort 适配器翻译成模型消息。 */
public enum ConversationRole {
    USER,
    ASSISTANT
}
