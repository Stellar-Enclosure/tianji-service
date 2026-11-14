package top.egon.cola.platform.tianji.worker;

import jakarta.validation.Valid;

@FunctionalInterface
public interface WorkerEffectOutcomeResolver {
    WorkerEffectEvidenceVO resolve(@Valid WorkerEffectReconcileQuery query);
}
