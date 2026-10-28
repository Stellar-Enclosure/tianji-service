package top.egon.cola.platform.tianji.process.server.decision;

import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FlowablePinnedDecisionExecutorTest {
    @Test
    void rejects_unisolated_beans_or_a_different_transaction_datasource() {
        DataSource source = mock(DataSource.class);
        SpringDmnEngineConfiguration engine = engine(source);
        when(engine.getBeans()).thenReturn(Map.of("unsafe", new Object()));
        assertThrows(IllegalArgumentException.class, () -> new FlowablePinnedDecisionExecutor(engine));
        when(engine.getBeans()).thenReturn(Map.of());
        when(engine.getDataSource()).thenReturn(mock(DataSource.class));
        assertThrows(IllegalArgumentException.class, () -> new FlowablePinnedDecisionExecutor(engine));
    }

    @Test
    void rejects_inputs_outside_the_model_schema_before_entering_the_engine() throws Exception {
        DataSource source = mock(DataSource.class);
        SpringDmnEngineConfiguration engine = engine(source);
        var adapter = new FlowablePinnedDecisionExecutor(engine);
        byte[] xml;
        try (var input = getClass().getResourceAsStream("/fixtures/baseline.dmn")) {
            assertNotNull(input);
            xml = input.readAllBytes();
        }
        var model = new ProcessModelValidator().validateDmn(xml, "baselineDecision");
        var binding = new FlowablePinnedDecisionExecutor.Binding(20, "definition", "deployment", 1, model);
        java.util.List<Map<String, Object>> cases = java.util.List.of(Map.of(), Map.of("amount", "10"),
                Map.of("amount", new BigDecimal("10")), Map.of("amount", new BigInteger("1000000000000000")),
                Map.of("amount", 10, "extra", true));
        for (Map<String, Object> input : cases) {
            assertThrows(IllegalArgumentException.class, () -> adapter.evaluate(binding, input));
        }
        verifyNoInteractions(engine.getDmnRepositoryService(), engine.getCommandExecutor(), source);
    }

    private static SpringDmnEngineConfiguration engine(DataSource source) {
        SpringDmnEngineConfiguration engine = mock(SpringDmnEngineConfiguration.class);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(new DataSourceTransactionManager(source));
        when(engine.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(engine.getCommandExecutor()).thenReturn(mock(CommandExecutor.class));
        when(engine.getBeans()).thenReturn(Map.of());
        return engine;
    }
}
