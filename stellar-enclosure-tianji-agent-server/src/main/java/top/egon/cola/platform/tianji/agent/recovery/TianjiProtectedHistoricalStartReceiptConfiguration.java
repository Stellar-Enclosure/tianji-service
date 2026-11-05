package top.egon.cola.platform.tianji.agent.recovery;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import top.egon.cola.platform.tianji.agent.recovery.TianjiHistoricalStartReceiptConfiguration.HistoricalStartReceiptBinding;
import top.egon.cola.platform.tianji.agent.server.integration.process.TianjiFixedBpmnStartToolBindings;
import top.egon.cola.platform.tianji.agent.server.security.TianjiAgentAccess;
import top.egon.cola.platform.tianji.agent.server.security.TianjiServletHistoricalStartReceiptAuthority;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;

import static top.egon.cola.platform.tianji.agent.server.security.TianjiHistoricalStartReceiptPermissions.ENABLED;

/** Explicitly imported protected adapter. Its current authority is the same instance used by the receipt host. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = {"stellar.tianji.agent.api.enabled", "stellar.tianji.agent.storage.enabled", ENABLED}, havingValue = "true")
@Import(TianjiHistoricalStartReceiptConfiguration.class)
public class TianjiProtectedHistoricalStartReceiptConfiguration {
    @Bean("tianjiServletHistoricalStartReceiptAuthority")
    TianjiServletHistoricalStartReceiptAuthority tianjiServletHistoricalStartReceiptAuthority(
            @Qualifier("tianjiAgentAccess") TianjiAgentAccess access) {
        return new TianjiServletHistoricalStartReceiptAuthority(access, Clock.systemUTC());
    }

    @Bean("tianjiHistoricalStartReceiptBinding")
    HistoricalStartReceiptBinding tianjiHistoricalStartReceiptBinding(
            @Qualifier("tianjiProtectedHistoricalStartReceiptBindings") ProtectedHistoricalStartReceiptBindings retained,
            @Qualifier("tianjiServletHistoricalStartReceiptAuthority") TianjiServletHistoricalStartReceiptAuthority authority) {
        return new HistoricalStartReceiptBinding(retained.bindings(), retained.readRegistrationByBackendSha256(), authority, retained.readOAuth());
    }

    public record ProtectedHistoricalStartReceiptBindings(TianjiFixedBpmnStartToolBindings bindings,
            Map<String, String> readRegistrationByBackendSha256, IdpServiceOAuth2Client readOAuth) {
        public ProtectedHistoricalStartReceiptBindings {
            Objects.requireNonNull(bindings, "bindings");
            readRegistrationByBackendSha256 = Map.copyOf(readRegistrationByBackendSha256);
            Objects.requireNonNull(readOAuth, "readOAuth");
        }

        @Override public String toString() { return "ProtectedHistoricalStartReceiptBindings[redacted]"; }
    }
}
