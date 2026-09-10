package com.zhu.ai.conversation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.conversation.ConversationRole;
import com.zhu.ai.kernel.conversation.ConversationTurn;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryConversationPortTest {

    @Test
    void blankSessionIsNoop() {
        InMemoryConversationPort port = new InMemoryConversationPort();
        port.appendRound("  ", "u", "a");
        assertTrue(port.history(null).isEmpty());
        assertTrue(port.history("").isEmpty());
        assertTrue(port.history("  ").isEmpty());
    }

    @Test
    void appendAndRead() {
        InMemoryConversationPort port = new InMemoryConversationPort();
        port.appendRound("s1", "你好", "收到");
        List<ConversationTurn> history = port.history("s1");
        assertEquals(2, history.size());
        assertEquals(ConversationRole.USER, history.get(0).role());
        assertEquals("你好", history.get(0).content());
        assertEquals(ConversationRole.ASSISTANT, history.get(1).role());
        assertEquals("收到", history.get(1).content());
        assertTrue(port.history("s2").isEmpty());
    }

    @Test
    void trimsOldestTurns() {
        InMemoryConversationPort port = new InMemoryConversationPort();
        for (int i = 0; i < 12; i++) {
            port.appendRound("s1", "u" + i, "a" + i);
        }
        List<ConversationTurn> history = port.history("s1");
        assertEquals(InMemoryConversationPort.MAX_TURNS, history.size());
        assertEquals("u2", history.get(0).content());
        assertEquals("a11", history.get(history.size() - 1).content());
    }
}
