package com.zhu.ai.kernel.knowledge;

import java.util.List;

/**
 * 把命中编成带编号的参考资料。只在 Gateway 调用，Agent / Graph 叶只读字符串。
 */
public final class RetrieveCitations {

    private RetrieveCitations() {}

    /**
     * @return 空命中返回空串；否则 {@code [1] text} 多段用 {@code ---} 分隔
     */
    public static String format(List<RetrieveHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            if (i > 0) {
                sb.append("\n---\n");
            }
            RetrieveHit hit = hits.get(i);
            sb.append('[').append(i + 1).append("] ").append(hit.text());
        }
        return sb.toString();
    }
}
