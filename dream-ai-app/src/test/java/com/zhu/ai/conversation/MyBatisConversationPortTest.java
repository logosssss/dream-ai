package com.zhu.ai.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.zhu.ai.kernel.conversation.ConversationRole;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import com.zhu.ai.persistence.entity.ConversationTurnEntity;
import com.zhu.ai.service.ConversationTurnService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MyBatisConversationPortTest {

    @Test
    void blankSessionDoesNotWrite() {
        ConversationTurnService service = mock(ConversationTurnService.class);
        MyBatisConversationPort port = new MyBatisConversationPort(service);
        port.appendRound("  ", "u", "a");
        assertTrue(port.history(null).isEmpty());
        verify(service, never()).save(any(ConversationTurnEntity.class));
    }

    @Test
    void appendInsertsUserThenAssistant() {
        ConversationTurnService service = mock(ConversationTurnService.class);
        when(service.list(any(Wrapper.class))).thenReturn(List.of());
        MyBatisConversationPort port = new MyBatisConversationPort(service);
        port.appendRound("s1", "你好", "收到");
        ArgumentCaptor<ConversationTurnEntity> captor = ArgumentCaptor.forClass(ConversationTurnEntity.class);
        verify(service, times(2)).save(captor.capture());
        List<ConversationTurnEntity> rows = captor.getAllValues();
        assertEquals("s1", rows.get(0).getSessionId());
        assertEquals(ConversationRole.USER.name(), rows.get(0).getRole());
        assertEquals("你好", rows.get(0).getContent());
        assertEquals(ConversationRole.ASSISTANT.name(), rows.get(1).getRole());
        assertEquals("收到", rows.get(1).getContent());
    }

    @Test
    void historyMapsRows() {
        ConversationTurnService service = mock(ConversationTurnService.class);
        ConversationTurnEntity user = new ConversationTurnEntity();
        user.setRole(ConversationRole.USER.name());
        user.setContent("你好");
        ConversationTurnEntity assistant = new ConversationTurnEntity();
        assistant.setRole(ConversationRole.ASSISTANT.name());
        assistant.setContent("收到");
        when(service.list(any(Wrapper.class))).thenReturn(List.of(user, assistant));
        MyBatisConversationPort port = new MyBatisConversationPort(service);
        List<ConversationTurn> history = port.history("s1");
        assertEquals(2, history.size());
        assertEquals(ConversationRole.USER, history.get(0).role());
        assertEquals("你好", history.get(0).content());
    }
}
