package com.zhu.ai.web;

import com.zhu.ai.kernel.observe.InvokeObservation;
import com.zhu.ai.kernel.observe.ObservePort;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薄观测查询。学契约（trace / 耗时 / 次数 / route / blockedTools），不抄现仓 Admin UI。
 */
@RestController
@RequestMapping("/api/observe")
public class ObserveController {

    private final ObservePort observe;

    public ObserveController(ObservePort observe) {
        this.observe = observe;
    }

    @GetMapping
    public List<InvokeObservation> recent(@RequestParam(defaultValue = "20") int limit) {
        return observe.recent(Math.min(Math.max(limit, 1), 100));
    }
}
