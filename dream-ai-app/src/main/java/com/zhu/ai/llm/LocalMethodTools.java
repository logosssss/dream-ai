package com.zhu.ai.llm;

import java.util.Locale;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * {@code MethodToolCallback} 示例：业务写 {@link Tool} 方法，由
 * {@code MethodToolCallbackProvider} 扫成 {@link org.springframework.ai.tool.ToolCallback}。
 * Bean 装配在 {@code ChatConfig}。
 */
public class LocalMethodTools {

    public record TextStats(int charCount, int codePointCount, boolean hasCjk, String preview) {}

    @Tool(
            name = "text_stats",
            description = "统计文本的字符数、码点数、是否含中日韩字符，并返回截断预览。"
                    + "用户问「这段有多长」「几个字」「有没有中文」时必须调用，不要心算。")
    public TextStats textStats(
            @ToolParam(description = "待统计的文本，必填") String text) {
        if (text == null) {
            text = "";
        }
        boolean hasCjk = false;
        int codePoints = text.codePointCount(0, text.length());
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            if (isCjk(cp)) {
                hasCjk = true;
                break;
            }
            i += Character.charCount(cp);
        }
        String preview = text.length() <= 40 ? text : text.substring(0, 40) + "…";
        return new TextStats(text.length(), codePoints, hasCjk, preview);
    }

    static boolean isCjk(int codePoint) {
        Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
        return script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL
                || Character.UnicodeBlock.of(codePoint) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }

    /** 便于单测断言，与 Locale 无关。 */
    static String normalizePreview(String preview) {
        return preview == null ? "" : preview.toLowerCase(Locale.ROOT);
    }
}
