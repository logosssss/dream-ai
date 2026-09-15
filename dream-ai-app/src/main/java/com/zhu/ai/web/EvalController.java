package com.zhu.ai.web;

import com.zhu.ai.eval.GatewayEvalRunner;
import com.zhu.ai.kernel.eval.EvalCase;
import com.zhu.ai.kernel.eval.EvalSuiteResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 薄评测入口：列 cases / 跑回归。学「golden + 硬断言」契约，不抄现仓 Admin / legacy 控制面。
 */
@RestController
@RequestMapping("/api/eval")
public class EvalController {

    private final GatewayEvalRunner runner;

    public EvalController(GatewayEvalRunner runner) {
        this.runner = runner;
    }

    @GetMapping("/cases")
    public List<EvalCase> cases() {
        return runner.listCases();
    }

    @PostMapping("/run")
    public EvalSuiteResult run() {
        return runner.runAll();
    }
}
