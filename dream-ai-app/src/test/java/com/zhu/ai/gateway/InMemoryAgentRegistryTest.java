package com.zhu.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zhu.ai.kernel.agent.AgentHandler;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryAgentRegistryTest {

    @Test
    void findsById() {
        AgentHandler chat = new StubHandler("chat");
        InMemoryAgentRegistry registry = new InMemoryAgentRegistry(List.of(chat));
        assertTrue(registry.find("chat").isPresent());
        assertEquals(chat, registry.find("chat").orElseThrow());
        assertTrue(registry.find("other").isEmpty());
    }

    @Test
    void duplicateIdFailsFast() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> new InMemoryAgentRegistry(List.of(new StubHandler("chat"), new StubHandler("chat"))));
        assertTrue(ex.getMessage().contains("duplicate agent id 'chat'"));
    }

    private static final class StubHandler implements AgentHandler {

        private final String id;

        private StubHandler(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String name() {
            return id;
        }

        @Override
        public AgentInvokeResult handle(AgentInvokeRequest request) {
            return new AgentInvokeResult(id, request.input());
        }
    }
}
