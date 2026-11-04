package top.egon.cola.platform.tianji.process.server;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.parallel.Isolated;

/** Explicit native acceptance only: select this class alone against a fresh loopback database and temporary runtime role. */
@EnabledIfSystemProperty(named = "tianji.process.postgres.test.url", matches = ".+")
@Isolated("Owns one explicitly supplied fresh PostgreSQL schema")
@Timeout(180)
class FormalCasePostgresTest {
    @Test
    void native_case_lifecycle_and_exact_bpmn_continuation_are_atomic_and_recoverable() throws Exception {
        var database = ProcessServerPostgresTest.formalCaseDatabase();
        try (var app = ProcessServerPostgresTest.context(new Class<?>[]{TianjiProcessServerApplication.class,
                        ProcessServerPostgresTest.TestDelivery.class, CaseFlowableScenarios.NativeCaseWiring.class},
                database.scopedUrl(), database.runtimeUser(), "--flowable.history-level=full")) {
            CaseFlowableScenarios.verify(app, database.bootstrap());
        }
    }
}
