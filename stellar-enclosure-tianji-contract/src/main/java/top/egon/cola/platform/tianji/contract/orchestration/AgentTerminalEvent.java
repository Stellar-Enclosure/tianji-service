package top.egon.cola.platform.tianji.contract.orchestration;

import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Result reference only. A receiver must authenticate the producer and match the entire stored dispatch intent. */
public record AgentTerminalEvent(int schemaVersion, String producerId, String eventId, long tenantId,
                                 String ownerIssuerSha256, String ownerSubjectId, String idempotencyKey,
                                 AgentProcessLink link, String definitionKey, long definitionVersion,
                                 String runId, long terminalRevision, AgentRunStatusEnum status, Instant finishedAt,
                                 String resultSha256, String errorCode, String sideEffectOutcome) {
    public AgentTerminalEvent {
        producerId = OrchestrationChecks.command(producerId);
        ownerIssuerSha256 = OrchestrationChecks.hash(ownerIssuerSha256);
        ownerSubjectId = OrchestrationChecks.id(ownerSubjectId, 256);
        idempotencyKey = OrchestrationChecks.command(idempotencyKey);
        link = Objects.requireNonNull(link, "link");
        definitionKey = OrchestrationChecks.id(definitionKey, 128);
        runId = OrchestrationChecks.id(runId, 128);
        if (schemaVersion != 1 || tenantId <= 0 || definitionVersion <= 0 || terminalRevision < 1
                || status == null || !(status == AgentRunStatusEnum.COMPLETED || status == AgentRunStatusEnum.FAILED
                || status == AgentRunStatusEnum.CANCELLED || status == AgentRunStatusEnum.TIMED_OUT)) throw OrchestrationChecks.invalid();
        if (finishedAt == null || finishedAt.isBefore(Instant.EPOCH) || finishedAt.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))
                || !finishedAt.equals(finishedAt.truncatedTo(ChronoUnit.MICROS))) throw OrchestrationChecks.invalid();
        resultSha256 = OrchestrationChecks.hash(resultSha256);
        errorCode = errorCode == null ? null : OrchestrationChecks.errorCode(errorCode);
        sideEffectOutcome = OrchestrationChecks.errorCode(sideEffectOutcome);
        if (!eventIdFor(producerId, tenantId, ownerIssuerSha256, ownerSubjectId, runId, terminalRevision).equals(eventId)) {
            throw OrchestrationChecks.invalid();
        }
    }

    /** The same terminal revision retains its event identity across delivery attempts and process restarts. */
    public static String eventIdFor(String producerId, long tenantId, String ownerIssuerSha256, String ownerSubjectId,
                                    String runId, long terminalRevision) {
        OrchestrationChecks.command(producerId);
        OrchestrationChecks.hash(ownerIssuerSha256);
        OrchestrationChecks.id(ownerSubjectId, 256);
        OrchestrationChecks.id(runId, 128);
        if (tenantId <= 0 || terminalRevision < 1) throw OrchestrationChecks.invalid();
        return hash(List.of("tianji-agent-terminal-id-v1", producerId, Long.toString(tenantId), ownerIssuerSha256,
                ownerSubjectId, runId, Long.toString(terminalRevision)));
    }

    /** Canonical envelope digest; wire JSON whitespace and property ordering do not affect deduplication. */
    public String fingerprint() {
        return hash(List.of("tianji-agent-terminal-event-v1", producerId, eventId, Long.toString(tenantId), ownerIssuerSha256,
                ownerSubjectId, idempotencyKey, link.sourceId(), link.scopeType().name(), link.scopeInstanceId(),
                link.activityOccurrenceId(), link.definitionContentSha256(), link.dispatchNotAfter().toString(), definitionKey,
                Long.toString(definitionVersion), runId, Long.toString(terminalRevision), status.name(), finishedAt.toString(),
                resultSha256, errorCode == null ? "" : errorCode, sideEffectOutcome));
    }

    private static String hash(List<String> fields) {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException("SHA-256 is unavailable"); }
        try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
            for (String field : fields) {
                byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
                output.writeInt(bytes.length);
                output.write(bytes);
            }
        } catch (IOException failure) { throw new IllegalStateException("Agent terminal event digest could not be calculated"); }
        return HexFormat.of().formatHex(digest.digest());
    }

    @Override public String toString() { return "AgentTerminalEvent[schemaVersion=" + schemaVersion + ", status=" + status + "]"; }
}
