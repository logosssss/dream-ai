package com.zhu.ai.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.zhu.ai.persistence.entity.ConversationTurnEntity;
import com.zhu.ai.persistence.mapper.ConversationTurnMapper;
import com.zhu.ai.service.ConversationTurnService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
public class ConversationTurnServiceImpl extends ServiceImpl<ConversationTurnMapper, ConversationTurnEntity>
        implements ConversationTurnService {}
