package com.zhu.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhu.ai.kernel.eval.EvalCase;
import com.zhu.ai.kernel.eval.EvalExpect;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 从 classpath {@code eval/cases/*.json} 加载 golden cases。
 * JSON 用宽松字段映射，缺省断言跳过。
 */
public final class ClasspathEvalCases {

    public static final String DEFAULT_PATTERN = "classpath*:eval/cases/*.json";

    private final ObjectMapper mapper;
    private final String pattern;

    public ClasspathEvalCases(ObjectMapper mapper) {
        this(mapper, DEFAULT_PATTERN);
    }

    public ClasspathEvalCases(ObjectMapper mapper, String pattern) {
        this.mapper = mapper;
        this.pattern = pattern;
    }

    public List<EvalCase> load() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(pattern);
            List<EvalCase> cases = new ArrayList<>();
            for (Resource resource : resources) {
                if (!resource.isReadable()) {
                    continue;
                }
                try (InputStream in = resource.getInputStream()) {
                    cases.add(parse(mapper.readTree(in), resource.getFilename()));
                }
            }
            cases.sort(Comparator.comparing(EvalCase::id));
            return List.copyOf(cases);
        } catch (IOException ex) {
            throw new UncheckedIOException("load eval cases failed: " + pattern, ex);
        }
    }

    static EvalCase parse(JsonNode root, String filename) {
        String id = text(root, "id");
        if (id == null || id.isBlank()) {
            id = filename == null ? "unknown" : filename.replace(".json", "");
        }
        String agentId = text(root, "agentId");
        String sessionId = text(root, "sessionId");
        String input = text(root, "input");
        if (input == null) {
            throw new IllegalArgumentException("eval case missing input: " + id);
        }
        JsonNode expectNode = root.get("expect");
        EvalExpect expect = expectNode == null || expectNode.isNull()
                ? EvalExpect.none()
                : new EvalExpect(
                        bool(expectNode, "success"),
                        text(expectNode, "agentId"),
                        strings(expectNode, "outputContains"),
                        strings(expectNode, "outputNotContains"),
                        integer(expectNode, "minToolCalls"),
                        integer(expectNode, "maxToolCalls"),
                        integer(expectNode, "maxModelCalls"),
                        text(expectNode, "route"),
                        strings(expectNode, "mustBlockTools"),
                        strings(expectNode, "mustExecuteTools"));
        return new EvalCase(id, agentId, sessionId, input, expect);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static Boolean bool(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asBoolean();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asInt();
    }

    private static List<String> strings(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull() || !v.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        v.forEach(n -> out.add(n.asText()));
        return out;
    }
}
