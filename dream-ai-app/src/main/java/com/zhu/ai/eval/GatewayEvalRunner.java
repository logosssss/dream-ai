package com.zhu.ai.eval;

import com.zhu.ai.kernel.eval.EvalCase;
import com.zhu.ai.kernel.eval.EvalCaseResult;
import com.zhu.ai.kernel.eval.EvalScorer;
import com.zhu.ai.kernel.eval.EvalSuiteResult;
import com.zhu.ai.kernel.runtime.AgentGateway;
import com.zhu.ai.kernel.runtime.AgentInvokeRequest;
import com.zhu.ai.kernel.runtime.AgentInvokeResult;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 对着 {@link AgentGateway} 跑 golden cases。入口（HTTP / 单测）只调本类，不直连 Handler。
 */
public final class GatewayEvalRunner {

    private static final Logger log = LoggerFactory.getLogger(GatewayEvalRunner.class);

    private final AgentGateway gateway;
    private final ClasspathEvalCases cases;

    public GatewayEvalRunner(AgentGateway gateway, ClasspathEvalCases cases) {
        this.gateway = gateway;
        this.cases = cases;
    }

    public List<EvalCase> listCases() {
        return cases.load();
    }

    public EvalSuiteResult runAll() {
        return run(cases.load());
    }

    public EvalSuiteResult run(List<EvalCase> suite) {
        List<EvalCaseResult> results = new ArrayList<>();
        for (EvalCase evalCase : suite) {
            results.add(runOne(evalCase));
        }
        EvalSuiteResult suiteResult = EvalSuiteResult.of(results);
        log.info(
                "eval suite total={} passed={} failed={}",
                suiteResult.total(),
                suiteResult.passed(),
                suiteResult.failed());
        return suiteResult;
    }

    private EvalCaseResult runOne(EvalCase evalCase) {
        String agentId = evalCase.agentId() == null || evalCase.agentId().isBlank()
                ? "chat"
                : evalCase.agentId();
        try {
            AgentInvokeResult result = gateway.invoke(
                    new AgentInvokeRequest(agentId, evalCase.resolvedSessionId(), evalCase.input()));
            EvalCaseResult scored = EvalScorer.score(evalCase, result);
            if (!scored.passed()) {
                log.warn("eval case failed id={} failures={}", scored.caseId(), scored.failures());
            }
            return scored;
        } catch (RuntimeException ex) {
            log.warn("eval case error id={}: {}", evalCase.id(), ex.toString());
            return EvalScorer.error(evalCase, ex);
        }
    }
}
