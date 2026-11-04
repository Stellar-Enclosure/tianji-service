package top.egon.cola.platform.tianji.process.server.task;

import org.flowable.cmmn.engine.impl.behavior.impl.HumanTaskActivityBehavior;
import org.flowable.cmmn.engine.impl.parser.CmmnParserImpl;
import org.flowable.cmmn.engine.impl.parser.DefaultCmmnActivityBehaviorFactory;
import org.flowable.cmmn.model.HumanTask;
import org.flowable.cmmn.model.PlanItem;
import org.flowable.cmmn.spring.SpringCmmnEngineConfiguration;
import org.flowable.common.engine.impl.history.HistoryLevel;
import top.egon.cola.platform.tianji.process.server.execution.FlowableCaseService;

import java.util.Objects;
import java.util.function.Supplier;

/** Only marked CMMN tasks vary; all other native behavior and initialized dependencies are retained. */
public final class PinnedCaseActivityBehaviorFactory extends DefaultCmmnActivityBehaviorFactory {
    private final Supplier<FlowableCaseService> service;
    public PinnedCaseActivityBehaviorFactory(Supplier<FlowableCaseService> service) { this.service = Objects.requireNonNull(service, "service"); }
    @Override public HumanTaskActivityBehavior createHumanTaskActivityBehavior(PlanItem item, HumanTask task) {
        String namespace = "urn:stellar-enclosure:tianji:task:1";
        // CMMN's converter retains the namespaced taskLane extension but drops unknown attributes.
        boolean governed = task.getAttributeValue(namespace, "kind") != null || task.getExtensionElements().getOrDefault("taskLane", java.util.List.of()).stream()
                .anyMatch(element -> "taskLane".equals(element.getName()) && namespace.equals(element.getNamespace()));
        return governed ? new PinnedCaseHumanTaskActivityBehavior(task, service) : super.createHumanTaskActivityBehavior(item, task);
    }
    /** Called only after engine initialization; a configuration object alone is not execution readiness. */
    public static void requireConfigured(SpringCmmnEngineConfiguration configuration) {
        if (configuration == null || !(configuration.getActivityBehaviorFactory() instanceof PinnedCaseActivityBehaviorFactory factory)
                || !(configuration.getCmmnParser() instanceof CmmnParserImpl parser) || parser.getActivityBehaviorFactory() != factory
                || configuration.getExpressionManager() == null || configuration.getClassDelegateFactory() == null
                || factory.expressionManager != configuration.getExpressionManager() || factory.classDelegateFactory != configuration.getClassDelegateFactory()
                || configuration.getBeans() == null || !configuration.getBeans().isEmpty() || !"false".equals(configuration.getDatabaseSchemaUpdate())
                || configuration.getHistoryLevel() == null || !configuration.getHistoryLevel().isAtLeast(HistoryLevel.AUDIT) || configuration.isAsyncHistoryEnabled()
                || configuration.getCmmnRuntimeService() == null || configuration.getCmmnHistoryService() == null || configuration.getCmmnTaskService() == null
                || configuration.getCreateHumanTaskInterceptor() != null) throw new IllegalStateException("Governed CMMN execution is not configured");
    }
}
