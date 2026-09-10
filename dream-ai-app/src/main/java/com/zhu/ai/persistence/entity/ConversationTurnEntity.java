package com.zhu.ai.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 短会话一行。表在 MySQL，由 {@code schema.sql} 建。
 */
@Data
@TableName("conversation_turn")
public class ConversationTurnEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String sessionId;

    private String role;

    private String content;
}
