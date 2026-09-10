package com.zhu.ai.web;

import java.util.Map;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 校验失败 → 400；模型不可重试错误（404 / Key）→ 502；超时等可重试 → 503。
 */
@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return error(ex);
    }

    @ExceptionHandler(NonTransientAiException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> aiUpstream(NonTransientAiException ex) {
        return error(ex);
    }

    @ExceptionHandler(TransientAiException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> aiUnavailable(TransientAiException ex) {
        return error(ex);
    }

    private static Map<String, String> error(Throwable ex) {
        String message = ex.getMessage();
        return Map.of("error", message != null && !message.isBlank() ? message : ex.getClass().getSimpleName());
    }
}
