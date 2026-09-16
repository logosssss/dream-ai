package com.zhu.ai.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.zhu.ai.kernel.llm.ModelRouter;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MapModelRouterTest {

    @Test
    void taskSpecificWinsOverDefault() {
        ModelRouter router = new MapModelRouter(
                "qwen-plus",
                Map.of(
                        ModelRouter.CHAT, "qwen-turbo",
                        ModelRouter.REVIEW, "qwen-max"));
        assertEquals("qwen-turbo", router.resolve(ModelRouter.CHAT));
        assertEquals("qwen-max", router.resolve(ModelRouter.REVIEW));
        assertEquals("qwen-plus", router.resolve(ModelRouter.KNOWLEDGE));
        assertEquals("qwen-plus", router.resolve("unknown"));
    }

    @Test
    void blankMeansAdapterDefault() {
        ModelRouter router = new MapModelRouter("", Map.of(ModelRouter.CHAT, "  "));
        assertNull(router.resolve(ModelRouter.CHAT));
        assertNull(router.resolve(ModelRouter.REVIEW));
    }

    @Test
    void taskKeyIsCaseInsensitive() {
        ModelRouter router = new MapModelRouter(null, Map.of("Review", "qwen-max"));
        assertEquals("qwen-max", router.resolve("REVIEW"));
    }
}
