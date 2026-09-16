package com.zhu.ai.memory;

/**
 * 把超长长期笔记压成较短文本。失败应抛异常或返回空，由 {@link MemorySummarizeService} 保留原笔记。
 */
@FunctionalInterface
public interface MemorySummarizer {

    /**
     * @param notes           当前笔记全文
     * @param targetMaxChars  期望摘要上限（提示用，实现可不严格）
     * @return 压缩后文本；空白表示跳过写回
     */
    String summarize(String notes, int targetMaxChars);
}
