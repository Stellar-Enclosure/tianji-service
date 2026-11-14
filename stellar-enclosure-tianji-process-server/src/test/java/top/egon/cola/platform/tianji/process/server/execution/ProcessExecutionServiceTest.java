package top.egon.cola.platform.tianji.process.server.execution;

import org.flowable.common.engine.impl.history.HistoryLevel;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.contract.orchestration.automation.AutomationStartCommand;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel.ValueType;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessExecutionServiceTest {
    @Test
    void rejects_disabled_or_asynchronous_history() {
        var source = mock(DataSource.class);
        var engine = mock(SpringProcessEngineConfiguration.class);
        var manager = new DataSourceTransactionManager(source);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(manager);
        when(engine.getRepositoryService()).thenReturn(mock(RepositoryService.class));
        when(engine.getRuntimeService()).thenReturn(mock(RuntimeService.class));
        when(engine.getHistoryService()).thenReturn(mock(HistoryService.class));
        when(engine.getDatabaseSchema()).thenReturn("tianji_process");
        when(engine.getBeans()).thenReturn(Map.of());
        when(engine.isDbHistoryUsed()).thenReturn(true);
        var dmn = mock(SpringDmnEngineConfiguration.class);
        when(dmn.getDataSource()).thenReturn(source);
        when(dmn.getTransactionManager()).thenReturn(manager);
        when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
        when(dmn.getBeans()).thenReturn(Map.of());
        when(engine.getHistoryLevel()).thenReturn(HistoryLevel.NONE);
        assertThrows(IllegalArgumentException.class, () -> new ProcessExecutionService(engine, dmn, new ProcessModelValidator()));
        when(engine.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT);
        when(engine.isAsyncHistoryEnabled()).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> new ProcessExecutionService(engine, dmn, new ProcessModelValidator()));
        verifyNoInteractions(source);
    }

    @Test
    void validates_command_identifiers_and_copies_the_variable_map() {
        Map<String, Object> variables = new java.util.HashMap<>(Map.of("amount", 10));
        var command = new ProcessExecutionService.StartCommand("v1", "invoice", "order-1", "command-1", "trace-1", variables);
        variables.put("amount", 20);
        assertEquals(10, command.variables().get("amount"));
        assertThrows(UnsupportedOperationException.class, () -> command.variables().put("amount", 30));
        assertThrows(IllegalArgumentException.class, () -> new ProcessExecutionService.StartCommand("../v1", "invoice", "order", "cmd", "trace", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessExecutionService.StartCommand("v1", "invoice", "order" + (char) 0, "cmd", "trace", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessExecutionService.StartCommand("v1", "invoice", "order", "cmd", "trace\n", Map.of()));
    }

    @Test
    void normalizes_bounded_primitive_values_and_rejects_precision_loss() {
        Map<String, ValueType> schema = Map.of("count", ValueType.INTEGER, "title", ValueType.STRING, "enabled", ValueType.BOOLEAN);
        var result = ProcessValues.inputs(schema, Map.of("count", new BigInteger("123"), "title", "中文😀", "enabled", true));
        assertEquals(123L, result.get("count"));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.inputs(schema,
                Map.of("count", 123.0, "title", "ok", "enabled", true)));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.inputs(schema,
                Map.of("count", 1_000_000_000_000_000L, "title", "ok", "enabled", true)));
        assertThrows(IllegalArgumentException.class, () -> ProcessValues.inputs(schema,
                Map.of("count", 1, "title", "bad" + (char) 0xd800, "enabled", true)));
        assertThrows(IllegalStateException.class, () -> ProcessValues.outputs(Map.of("n", ValueType.INTEGER), Map.of("n", 2.5)));
        assertEquals(2L, ProcessValues.outputs(Map.of("n", ValueType.INTEGER), Map.of("n", 2.0)).get("n"));
    }

    @Test
    void rejects_reserved_event_start_before_any_native_or_database_work() {
        var engine = mock(SpringProcessEngineConfiguration.class);
        var dmn = mock(SpringDmnEngineConfiguration.class);
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        when(engine.getDataSource()).thenReturn(source);
        when(engine.getTransactionManager()).thenReturn(manager);
        when(engine.getRepositoryService()).thenReturn(mock(RepositoryService.class));
        when(engine.getRuntimeService()).thenReturn(mock(RuntimeService.class));
        when(engine.getHistoryService()).thenReturn(mock(HistoryService.class));
        when(engine.getDatabaseSchema()).thenReturn("tianji_process");
        when(engine.getBeans()).thenReturn(Map.of());
        when(engine.isDbHistoryUsed()).thenReturn(true);
        when(engine.getHistoryLevel()).thenReturn(HistoryLevel.AUDIT);
        when(dmn.getDataSource()).thenReturn(source);
        when(dmn.getTransactionManager()).thenReturn(manager);
        when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
        when(dmn.getBeans()).thenReturn(Map.of());
        var service = new ProcessExecutionService(engine, dmn, new ProcessModelValidator());
        var actor = ProcessActor.verified(42, ProcessActor.SubjectType.USER, "owner", "https://registry.test/issuer");
        var command = new AutomationStartCommand("invoice", "invoice-1", "evt-v2-private", "trace", Map.of(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> service.startAutomation(actor, command));
        verifyNoInteractions(source, engine.getRuntimeService());
    }

    @Test
    void preserves_the_existing_start_command_hash_format() {
        assertEquals("fb8b42a11d2a032688b07cfc47fd2aa72ca8bf873eb1976879bfbaeb28a00d5a",
                ProcessRequestDigest.hash(java.util.List.of("release-one", "baseline", "order-1", "correlation-1"),
                        Map.of("amount", 10L)));
    }
}
