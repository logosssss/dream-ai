package com.zhu.ai.knowledge;

import java.time.Duration;
import java.time.Instant;

/**
 * 装进 {@link StagedRetrievePort} 的检索选项：写入每轮 {@link RetrieveContext}。
 * {@link RetrievePort#retrieve} 只有 query/topK，个性化与 docType 先靠装配期选项传入。
 */
public record RetrieveOptions(String userId, String docType, Duration timeout) {

    public RetrieveOptions {
        userId = userId == null ? "" : userId.trim();
        docType = docType == null ? "" : docType.trim();
    }

    public static RetrieveOptions empty() {
        return new RetrieveOptions("", "", null);
    }

    public static RetrieveOptions ofDocType(String docType) {
        return new RetrieveOptions("", docType, null);
    }

    Instant deadlineFromNow() {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return null;
        }
        return Instant.now().plus(timeout);
    }
}
