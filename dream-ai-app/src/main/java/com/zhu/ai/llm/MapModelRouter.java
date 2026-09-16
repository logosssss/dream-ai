package com.zhu.ai.llm;

import com.zhu.ai.kernel.llm.ModelRouter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 基于配置表的 {@link ModelRouter}：先查任务键，再回落 {@code default}。
 */
public final class MapModelRouter implements ModelRouter {

    private final String defaultModel;
    private final Map<String, String> byTask;

    public MapModelRouter(String defaultModel, Map<String, String> byTask) {
        this.defaultModel = blankToNull(defaultModel);
        Map<String, String> copy = new LinkedHashMap<>();
        if (byTask != null) {
            for (Map.Entry<String, String> e : byTask.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                String model = blankToNull(e.getValue());
                if (model != null) {
                    copy.put(e.getKey().trim().toLowerCase(Locale.ROOT), model);
                }
            }
        }
        this.byTask = Map.copyOf(copy);
    }

    @Override
    public String resolve(String taskKey) {
        if (taskKey != null && !taskKey.isBlank()) {
            String specific = byTask.get(taskKey.trim().toLowerCase(Locale.ROOT));
            if (specific != null) {
                return specific;
            }
        }
        return defaultModel;
    }

    private static String blankToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.trim();
    }
}
