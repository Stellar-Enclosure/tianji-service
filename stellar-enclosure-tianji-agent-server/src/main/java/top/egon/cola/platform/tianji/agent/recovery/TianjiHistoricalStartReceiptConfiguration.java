package top.egon.cola.platform.tianji.agent.recovery;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import top.egon.cola.platform.tianji.agent.application.tool.HistoricalToolReceiptAuthorization;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;
import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.AgentLedgerService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationService;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolStartReceiptReconciliationService.ReceiptVO;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartReceiptReader;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartToolBindings;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiProcessToolCredentialVerifier;
import top.egon.cola.platform.tianji.contract.agent.AgentErrorCodeEnum;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TenantIssuerAuthenticationManagerComponent;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedPrincipalConverter;

import javax.sql.DataSource;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Explicit trusted-host import for retained START bindings and independent current historical authority. */
@Configuration(proxyBeanMethods = false)
public class TianjiHistoricalStartReceiptConfiguration {
    @Bean(name = "tianjiHistoricalStartReceiptHost", destroyMethod = "close")
    HistoricalStartReceiptHost tianjiHistoricalStartReceiptHost(
            @Qualifier("tianjiHistoricalStartReceiptBinding") HistoricalStartReceiptBinding binding,
            DataSource source, AgentLedgerService ledger,
            @Qualifier("tenantIssuerAuthenticationManagerComponent") TenantIssuerAuthenticationManagerComponent authentication,
            @Qualifier("verifiedPrincipalConverter") VerifiedPrincipalConverter converter,
            @Qualifier("trustedIssuerRegistry") TrustedIssuerRegistry issuers, Environment environment) {
        if (!environment.getProperty("stellar.tianji.agent.storage.enabled", Boolean.class, false)
                || !ledger.usesDataSource(source)) throw invalid();
        var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(3)).build();
        TianjiFixedBpmnStartReceiptReader reader = null;
        try {
            var verifier = new TianjiProcessToolCredentialVerifier(binding.readOAuth(), authentication, converter, issuers, Clock.systemUTC());
            reader = new TianjiFixedBpmnStartReceiptReader(binding.bindings(), verifier, client, 2,
                    binding.readRegistrationByBackendSha256());
            var reconciliation = new ToolStartReceiptReconciliationService(source, ledger, binding.historicalAuthority(), reader);
            return new HistoricalStartReceiptHost(reconciliation, reader, client);
        } catch (RuntimeException privateConfiguration) {
            closeResources(reader, client);
            throw invalid();
        }
    }

    public record HistoricalStartReceiptBinding(TianjiFixedBpmnStartToolBindings bindings,
            Map<String, String> readRegistrationByBackendSha256, HistoricalToolReceiptAuthorization historicalAuthority,
            IdpServiceOAuth2Client readOAuth) {
        public HistoricalStartReceiptBinding {
            Objects.requireNonNull(bindings, "bindings");
            readRegistrationByBackendSha256 = Map.copyOf(readRegistrationByBackendSha256);
            Objects.requireNonNull(historicalAuthority, "historicalAuthority");
            Objects.requireNonNull(readOAuth, "readOAuth");
        }

        @Override public String toString() { return "HistoricalStartReceiptBinding[redacted]"; }
    }

    public static final class HistoricalStartReceiptHost implements AutoCloseable {
        private final ToolStartReceiptReconciliationService reconciliation;
        private final TianjiFixedBpmnStartReceiptReader reader;
        private final HttpClient client;
        private final AtomicBoolean closed = new AtomicBoolean();

        private HistoricalStartReceiptHost(ToolStartReceiptReconciliationService reconciliation,
                TianjiFixedBpmnStartReceiptReader reader, HttpClient client) {
            this.reconciliation = reconciliation;
            this.reader = reader;
            this.client = client;
        }

        public ReceiptVO observe(TrustedAgentActor owner, String dispatchRunId, UUID intentId) {
            if (closed.get()) {
                throw new AgentLedgerException(AgentErrorCodeEnum.TIANJI_DEPENDENCY_UNAVAILABLE,
                        "Historical START receipt host is unavailable");
            }
            return reconciliation.observe(owner, dispatchRunId, intentId);
        }

        @Override public void close() {
            if (closed.compareAndSet(false, true)) closeResources(reader, client);
        }
    }

    private static void closeResources(TianjiFixedBpmnStartReceiptReader reader, HttpClient client) {
        try {
            if (reader != null) reader.close();
        } finally {
            client.shutdownNow();
        }
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("TIANJI_HISTORICAL_START_RECEIPT_HOST_PREREQUISITES_INVALID");
    }
}
