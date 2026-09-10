package com.zhu.ai.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zhu.ai.persistence.entity.ConversationTurnEntity;

/**
 * 短会话 CRUD。Gateway 经 {@code ConversationPort} 用；直接查表也走本接口。
 */
public interface ConversationTurnService extends IService<ConversationTurnEntity> {}
