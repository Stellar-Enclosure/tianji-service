package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Parameter/namespace markers only; actual SQL remains in the explicitly provisioned PostgreSQL integration suite. */
class ProcessTaskOriginTest {
    private static final ProcessActor REVIEWER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "reviewer", "https://idp.example/20");

    @Test void an_approver_can_resolve_a_different_starter_only_in_the_same_verified_namespace() throws Exception {
        var jdbc = mock(JdbcTemplate.class); var row = mock(ResultSet.class);
        when(row.getString("subject_type")).thenReturn("USER"); when(row.getString("subject_id")).thenReturn("applicant"); when(row.getString("command_id")).thenReturn("start-1");
        when(row.getString("request_sha256")).thenReturn("a".repeat(64)); when(row.getString("release_key")).thenReturn("release"); when(row.getString("correlation_id")).thenReturn("correlation");
        when(row.getTimestamp("created_at")).thenReturn(Timestamp.from(Instant.parse("2026-10-31T00:00:00Z")));
        doAnswer(call -> {
            RowMapper<?> mapper = call.getArgument(1); return List.of(mapper.mapRow(row, 0));
        }).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(20L), eq(REVIEWER.issuerFingerprint()), eq("instance"));
        var store = new ProcessCommandReceiptStore(jdbc, "tianji_process"); var origin = store.taskOrigin(REVIEWER, "instance");
        assertEquals("applicant", origin.requester().subjectId()); assertNotEquals(REVIEWER, origin.requester()); assertEquals(REVIEWER.issuerFingerprint(), origin.requester().issuerFingerprint());
        assertEquals("start-1", origin.commandId()); assertEquals("instance", origin.instanceId()); assertEquals("a".repeat(64), origin.requestHash());
        verify(jdbc).query(argThat(sql -> sql.contains("subject_issuer=?") && sql.contains("command_type='START'") && sql.contains("state='APPLIED'") && !sql.contains("subject_id=?")),
                org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(20L), eq(REVIEWER.issuerFingerprint()), eq("instance"));
    }

    @Test void service_legacy_and_unknown_or_ambiguous_origins_never_become_a_source_reference() {
        var jdbc = mock(JdbcTemplate.class); var store = new ProcessCommandReceiptStore(jdbc, "tianji_process");
        assertThrows(IllegalArgumentException.class, () -> store.taskOrigin(new ProcessActor(20, ProcessActor.SubjectType.USER, "reviewer"), "instance"));
        assertThrows(IllegalArgumentException.class, () -> store.taskOrigin(ProcessActor.verified(20, ProcessActor.SubjectType.SERVICE, "worker", "https://idp.example/20"), "instance"));
        verifyNoInteractions(jdbc);
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class))).thenReturn(List.of());
        assertThrows(ProcessResourceNotFoundException.class, () -> store.taskOrigin(REVIEWER, "instance"));
        var reference = new ProcessCommandReceiptStore.TaskOrigin(REVIEWER, "start", "a".repeat(64), "release", "correlation", "instance", Instant.EPOCH);
        doReturn(List.of(reference, reference)).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        assertThrows(ProcessResourceNotFoundException.class, () -> store.taskOrigin(REVIEWER, "instance"));
    }

    @Test void optional_command_lookup_preserves_the_required_lookup_and_exact_actor_fence() {
        var jdbc = mock(JdbcTemplate.class); var store = new ProcessCommandReceiptStore(jdbc, "tianji_process");
        when(jdbc.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class))).thenReturn(List.of());
        assertTrue(store.lockIfPresent(REVIEWER, ProcessCommandReceiptStore.Kind.TASK_COMPLETE, "complete").isEmpty());
        assertThrows(ProcessResourceNotFoundException.class, () -> store.lock(REVIEWER, ProcessCommandReceiptStore.Kind.TASK_COMPLETE, "complete"));
        verify(jdbc, times(2)).query(argThat(sql -> sql.contains("subject_type=? AND subject_issuer=? AND subject_id=? AND command_type=? AND command_id=? FOR UPDATE")
                        && sql.contains("NULL::text AS decision_result")), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), eq(20L), eq("USER"), eq(REVIEWER.issuerFingerprint()), eq("reviewer"), eq("TASK_COMPLETE"), eq("complete"));
        var receipt = new ProcessCommandReceiptStore.Receipt("a".repeat(64), "release", "correlation", "APPLIED", "task", Instant.EPOCH, null);
        doReturn(List.of(receipt)).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        assertEquals(receipt, store.lock(REVIEWER, ProcessCommandReceiptStore.Kind.TASK_COMPLETE, "complete"));
        doReturn(List.of(receipt, receipt)).when(jdbc).query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<Object>>any(), any(Object[].class));
        assertThrows(IllegalStateException.class, () -> store.lockIfPresent(REVIEWER, ProcessCommandReceiptStore.Kind.TASK_COMPLETE, "complete"));
    }
}
