package top.egon.cola.platform.tianji.process.server.decision;

import org.flowable.common.engine.impl.interceptor.CommandExecutor;
import org.flowable.dmn.api.DmnHistoryService;
import org.flowable.dmn.api.DmnRepositoryService;
import org.flowable.dmn.spring.SpringDmnEngineConfiguration;
import org.flowable.engine.RepositoryService;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import top.egon.cola.platform.tianji.process.server.release.ProcessModelValidator;

import javax.sql.DataSource;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProcessDecisionServiceTest {
    @Test
    void requires_decision_history_and_shared_storage() {
        var source = mock(DataSource.class);
        var manager = new DataSourceTransactionManager(source);
        var process = mock(SpringProcessEngineConfiguration.class);
        when(process.getDataSource()).thenReturn(source);
        when(process.getTransactionManager()).thenReturn(manager);
        when(process.getRepositoryService()).thenReturn(mock(RepositoryService.class));
        when(process.getDatabaseSchema()).thenReturn("tianji_process");
        when(process.getBeans()).thenReturn(Map.of());
        var dmn = mock(SpringDmnEngineConfiguration.class);
        when(dmn.getDataSource()).thenReturn(source);
        when(dmn.getTransactionManager()).thenReturn(manager);
        when(dmn.getDmnRepositoryService()).thenReturn(mock(DmnRepositoryService.class));
        when(dmn.getDmnHistoryService()).thenReturn(mock(DmnHistoryService.class));
        when(dmn.getCommandExecutor()).thenReturn(mock(CommandExecutor.class));
        when(dmn.getDatabaseSchema()).thenReturn("tianji_process");
        when(dmn.getBeans()).thenReturn(Map.of());
        when(dmn.isHistoryEnabled()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService(process, dmn, new ProcessModelValidator()));
        verifyNoInteractions(source);
    }

    @Test
    void validates_request_identifiers_and_copies_inputs() {
        Map<String, Object> source = new java.util.HashMap<>(Map.of("amount", 10));
        var command = new ProcessDecisionService.EvaluateCommand("v1", "decision", "request-1", "trace-1", source);
        source.put("amount", 20);
        assertEquals(10, command.inputs().get("amount"));
        assertThrows(UnsupportedOperationException.class, () -> command.inputs().put("amount", 30));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService.EvaluateCommand("../v1", "decision", "r", "c", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService.EvaluateCommand("v1", "decision", "r\n", "c", Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ProcessDecisionService.EvaluateCommand("v1", "decision", "r", "c", null));
    }
}
