package top.egon.cola.platform.tianji.agent.server.security;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.contract.state.IdentityAuthorityScopes;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;
import top.egon.cola.platform.tianquan.shoubing.starter.state.IdentityAuthorityProperties;

/** Selects the same finite source registration for issuance and subsequent authorization. */
final class TianjiExecutionAuthorityBindings {
    private TianjiExecutionAuthorityBindings() { }

    static Map<URI, IdpServiceTokenRequest> source(IdpStarterProperties starter, IdentityAuthorityProperties authority,
                                                   TrustedIssuerRegistry issuers) {
        if (starter == null || authority == null || authority.getBindings() == null || authority.getBindings().size() > 4096) {
            throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDINGS_INVALID");
        }
        URI source = resource(starter.getResourceUri());
        Map<URI, IdpServiceTokenRequest> result = new LinkedHashMap<>();
        authority.getBindings().forEach((name, value) -> {
            if (value == null || value.getScopes() == null || !value.getScopes().contains(IdentityAuthorityScopes.EXECUTION_GRANT_ISSUE)) return;
            if (name == null || !name.matches("[A-Za-z0-9._-]{1,128}") || value.getIssuer() == null) {
                throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDING_INVALID");
            }
            URI boundResource = value.getSourceResource() == null ? source : resource(value.getSourceResource());
            if (!source.toASCIIString().equals(boundResource.toASCIIString())) return;
            var trusted = issuers.findByIssuer(value.getIssuer().toASCIIString())
                    .orElseThrow(() -> new IllegalStateException("TIANJI_EXECUTION_GRANT_ISSUER_UNTRUSTED"));
            if (trusted.realmType() != TrustedIssuerRegistry.RealmType.TENANT || value.getContext() != ServiceTokenContext.TENANT
                    || !Objects.equals(value.getTenantId(), trusted.tenantId())) {
                throw new IllegalStateException("TIANJI_EXECUTION_GRANT_TENANT_INVALID");
            }
            var context = new IdpServiceTokenRequest(value.getRegistrationId(), starter.getServiceClient().getAppId(), value.getAudience(),
                    value.getContext(), value.getTenantId(), Set.copyOf(value.getScopes()));
            if (result.putIfAbsent(value.getIssuer(), context) != null) throw new IllegalStateException("TIANJI_EXECUTION_GRANT_BINDING_DUPLICATE");
        });
        return Map.copyOf(result);
    }

    static URI resource(URI value) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme()) || value.getHost() == null
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !value.normalize().equals(value) || value.toASCIIString().length() > 2048) {
            throw new IllegalArgumentException("TIANJI_EXECUTION_GRANT_RESOURCE_INVALID");
        }
        return value;
    }
}
