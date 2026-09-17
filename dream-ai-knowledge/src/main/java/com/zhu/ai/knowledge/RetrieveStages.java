package com.zhu.ai.knowledge;

import com.zhu.ai.kernel.knowledge.RetrievePort;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 装配辅助：按阶段名拼管线，避免 PortsConfig 硬编码 {@code new} 嵌套。
 */
public final class RetrieveStages {

    private RetrieveStages() {}

    /**
     * 兼容旧调用：docType + rerank 布尔 → 默认阶段名。
     */
    public static RetrievePort pipeline(
            List<MultiSourceRecallStage.WeightedSource> sources,
            ScoreFusion fusion,
            String docType,
            boolean rerank) {
        return pipeline(
                sources,
                fusion,
                RetrieveOptions.ofDocType(docType),
                defaultStageNames(rerank),
                List.of());
    }

    /**
     * 标准装配：召回源 + 融合 + 选项 + 阶段名列表 + 可选追加阶段。
     *
     * @param sources     至少一路
     * @param fusion      null → {@link LinearWeightedFusion}
     * @param options     写入 {@link RetrieveContext}（docType / userId / timeout）
     * @param stageNames  {@code recall|filter|rerank|truncate}；空则按 rerank 默认
     * @param extraStages 追加在具名阶段之后（自定义扩展）
     */
    public static RetrievePort pipeline(
            List<MultiSourceRecallStage.WeightedSource> sources,
            ScoreFusion fusion,
            RetrieveOptions options,
            List<String> stageNames,
            List<RetrieveStage> extraStages) {
        ScoreFusion resolved = fusion == null ? new LinearWeightedFusion() : fusion;
        List<String> names = stageNames == null || stageNames.isEmpty()
                ? defaultStageNames(true)
                : stageNames;
        List<RetrieveStage> stages = buildStages(sources, resolved, names);
        if (extraStages != null && !extraStages.isEmpty()) {
            stages = new ArrayList<>(stages);
            stages.addAll(extraStages);
        }
        return new StagedRetrievePort(stages, options == null ? RetrieveOptions.empty() : options);
    }

    /** {@code rerank=true} → recall/filter/rerank；否则末段 truncate。 */
    public static List<String> defaultStageNames(boolean rerank) {
        if (rerank) {
            return List.of("recall", "filter", "rerank");
        }
        return List.of("recall", "filter", "truncate");
    }

    /**
     * 按名字实例化阶段。{@code recall} 需要 sources；其它阶段无参。
     */
    public static List<RetrieveStage> buildStages(
            List<MultiSourceRecallStage.WeightedSource> sources,
            ScoreFusion fusion,
            List<String> stageNames) {
        if (stageNames == null || stageNames.isEmpty()) {
            throw new IllegalArgumentException("stageNames required");
        }
        List<RetrieveStage> stages = new ArrayList<>(stageNames.size());
        for (String raw : stageNames) {
            String name = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            switch (name) {
                case "recall" -> {
                    if (sources == null || sources.isEmpty()) {
                        throw new IllegalArgumentException("recall stage requires sources");
                    }
                    stages.add(new MultiSourceRecallStage(
                            sources, fusion == null ? new LinearWeightedFusion() : fusion));
                }
                case "filter" -> stages.add(new DocTypeFilterStage());
                case "rerank" -> stages.add(new RerankStage());
                case "truncate" -> stages.add(new TruncateStage());
                case "" -> throw new IllegalArgumentException("blank stage name");
                default -> throw new IllegalArgumentException(
                        "unknown stage: " + raw + " (use recall|filter|rerank|truncate)");
            }
        }
        return List.copyOf(stages);
    }
}
