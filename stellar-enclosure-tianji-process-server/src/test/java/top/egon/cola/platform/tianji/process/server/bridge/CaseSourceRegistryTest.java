package top.egon.cola.platform.tianji.process.server.bridge;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianquan.shoubing.contract.ServiceTokenContext;
import top.egon.cola.platform.tianquan.shoubing.starter.autoconfigure.IdpStarterProperties;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceOAuth2Client;
import top.egon.cola.platform.tianquan.shoubing.starter.client.IdpServiceTokenRequest;
import top.egon.cola.platform.tianquan.shoubing.starter.client.ServiceAuthorizationKey;
import top.egon.cola.platform.tianquan.shoubing.starter.security.TrustedIssuerRegistry;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CaseSourceRegistryTest {
    static final URI ISSUER=URI.create("https://idp.example/20"), RESOURCE=URI.create("https://process.example/api/tianji");
    static final String PARENT_SHA="b".repeat(64), CASE_SHA="a".repeat(64);

    @Test void properties_default_off_with_bounded_maintenance_and_strict_unknown_field_binding() {
        var properties=new CaseSourceProperties(null,null,null,null,null,null,null);
        assertEquals(Boolean.FALSE,properties.enabled());assertEquals(Boolean.FALSE,properties.newStartsEnabled());
        assertEquals(List.of(),properties.sources());assertEquals(Duration.ofSeconds(1),properties.pollInterval());assertEquals(Duration.ofSeconds(1),properties.retryDelay());assertEquals(32,properties.batchSize());assertEquals(10,properties.maxFailures());
        assertFalse(CaseSourceProperties.class.getAnnotation(ConfigurationProperties.class).ignoreUnknownFields());
    }
    @Test void source_list_is_copied_and_invalid_flags_bounds_or_identifiers_are_not_silently_normalized() {
        var sources=new ArrayList<>(List.of(source("source")));var properties=new CaseSourceProperties(true,true,sources,null,null,32,10);sources.clear();
        assertEquals(1,properties.sources().size());assertThrows(UnsupportedOperationException.class,()->properties.sources().add(source("other")));
        assertThrows(IllegalArgumentException.class,()->new CaseSourceProperties(false,true,List.of(),null,null,32,10));
        assertThrows(IllegalArgumentException.class,()->new CaseSourceProperties(true,false,List.of(),Duration.ofMillis(99),null,32,10));
        assertThrows(IllegalArgumentException.class,()->new CaseSourceProperties(true,false,List.of(),null,null,101,10));
        assertThrows(IllegalArgumentException.class,()->new CaseSourceProperties(true,false,List.of(),null,null,0,10));
        assertThrows(IllegalArgumentException.class,()->new CaseSourceRegistry.BindingKey(20,"release","process","BAD","caseSource","governedCase",CASE_SHA));
    }
    @Test void an_empty_direct_mode_registry_never_resolves_any_identity_or_oauth_dependency() {
        var registry=new CaseSourceRegistry(new CaseSourceProperties(true,false,List.of(),null,null,32,10),null,null,()->{throw new AssertionError("OAuth lookup forbidden in direct mode");});
        assertEquals(List.of(),registry.registrations());assertEquals(Set.of(),registry.activeDueKeys());
    }
    @Test void exact_binding_uses_real_registered_local_service_metadata_and_not_a_model_selected_actor() {
        try(var f=new Fixture()){
            var registration=assertDoesNotThrow(()->f.registry.require(binding()));assertNotNull(registration);
            assertEquals(ProcessActor.verified(20,ProcessActor.SubjectType.SERVICE,"case-service",ISSUER.toASCIIString()),registration.actor());
            assertEquals(binding(),registration.binding());assertEquals("source",registration.sourceId());assertTrue(f.registry.current(registration));
            assertEquals(Set.of(registration.inboxKey()),f.registry.activeDueKeys());assertEquals(Optional.of(registration),f.registry.find(registration.inboxKey()));
            verify(f.oauth,atLeastOnce()).authorizationKey(argThat(request->request.context()==ServiceTokenContext.TENANT&&request.tenantId().equals("20")&&request.appId().equals("process-app")&&request.audience().equals(RESOURCE)&&request.scopes().equals(Set.of("tianji:case:start"))));
        }
    }
    @Test void duplicate_exact_binding_or_duplicate_tenant_source_audit_id_fails_startup() {
        try(var f=new Fixture()){
            assertThrows(IllegalArgumentException.class,()->new CaseSourceRegistry(properties(List.of(source("source"),source("rename"))),f.starter,f.issuers,()->f.oauth));
            var other=new CaseSourceProperties.Source(20,"source","release","process",PARENT_SHA,"otherSource","governedCase",CASE_SHA,ISSUER,"local","case-service","process-app",RESOURCE);
            assertThrows(IllegalArgumentException.class,()->new CaseSourceRegistry(properties(List.of(source("source"),other)),f.starter,f.issuers,()->f.oauth));
        }
    }
    @Test void withdrawal_or_changed_client_identity_is_explicitly_unavailable_without_borrowing_a_new_service() {
        try(var f=new Fixture()){
            var registration=canonical();
            assertTrue(f.registry.current(registration));when(f.issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.empty());assertFalse(f.registry.current(registration));
            when(f.issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trusted()));
            when(f.oauth.authorizationKey(any())).thenReturn(new ServiceAuthorizationKey(ISSUER.toASCIIString(),"local","other-client",1,"process-app",RESOURCE,ServiceTokenContext.TENANT,"20",Set.of("tianji:case:start")));
            assertFalse(f.registry.current(registration));assertEquals(Set.of(),f.registry.activeDueKeys());
        }
    }
    @Test void unknown_local_metadata_failure_is_not_converted_into_a_false_revocation() {
        try(var f=new Fixture()){
            when(f.oauth.authorizationKey(any())).thenThrow(new IllegalStateException("local metadata temporarily unavailable"));
            assertThrows(IllegalStateException.class,()->f.registry.current(canonical()));
        }
    }
    @Test void current_validation_is_local_inside_the_native_transaction_and_never_requests_a_token() {
        try(var f=new Fixture()){
            clearInvocations(f.oauth);TransactionSynchronizationManager.setActualTransactionActive(true);
            try{assertTrue(f.registry.current(canonical()));}finally{TransactionSynchronizationManager.setActualTransactionActive(false);}
            verify(f.oauth).authorizationKey(any(IdpServiceTokenRequest.class));verifyNoMoreInteractions(f.oauth);
        }
    }
    @Test void a_constructed_registration_is_not_authority_and_renaming_does_not_match_the_old_inbox_tuple() {
        try(var f=new Fixture()){
            var forged=new CaseSourceRegistry.Registration(binding(),"unregistered",canonical().actor(),key());
            assertFalse(f.registry.current(forged));assertEquals(Optional.empty(),f.registry.find(forged.inboxKey()));
            assertThrows(IllegalStateException.class,()->f.registry.require(new CaseSourceRegistry.BindingKey(20,"other","process",PARENT_SHA,"caseSource","governedCase",CASE_SHA)));
        }
    }
    static CaseSourceProperties.Source source(String id){return new CaseSourceProperties.Source(20,id,"release","process",PARENT_SHA,"caseSource","governedCase",CASE_SHA,ISSUER,"local","case-service","process-app",RESOURCE);}
    static CaseSourceProperties properties(List<CaseSourceProperties.Source> sources){return new CaseSourceProperties(true,true,sources,Duration.ofSeconds(1),Duration.ofSeconds(1),32,10);}
    static CaseSourceRegistry.BindingKey binding(){return new CaseSourceRegistry.BindingKey(20,"release","process",PARENT_SHA,"caseSource","governedCase",CASE_SHA);}
    static ServiceAuthorizationKey key(){return new ServiceAuthorizationKey(ISSUER.toASCIIString(),"local","case-service",1,"process-app",RESOURCE,ServiceTokenContext.TENANT,"20",Set.of("tianji:case:start"));}
    static CaseSourceRegistry.Registration canonical(){return new CaseSourceRegistry.Registration(binding(),"source",ProcessActor.verified(20,ProcessActor.SubjectType.SERVICE,"case-service",ISSUER.toASCIIString()),key());}
    static TrustedIssuerRegistry.TrustedIssuerVO trusted(){return new TrustedIssuerRegistry.TrustedIssuerVO(ISSUER,URI.create("https://idp.example/20/jwks"),"realm-20",TrustedIssuerRegistry.RealmType.TENANT,"20",TrustedIssuerRegistry.Status.ACTIVE,1);}
    private static final class Fixture implements AutoCloseable{
        final IdpStarterProperties starter=new IdpStarterProperties();final TrustedIssuerRegistry issuers=mock(TrustedIssuerRegistry.class);final IdpServiceOAuth2Client oauth=mock(IdpServiceOAuth2Client.class);final CaseSourceRegistry registry;
        Fixture(){starter.getServiceClient().setAppId("process-app");when(issuers.findByIssuer(ISSUER.toASCIIString())).thenReturn(Optional.of(trusted()));when(oauth.authorizationKey(any())).thenReturn(key());registry=new CaseSourceRegistry(properties(List.of(source("source"))),starter,issuers,()->oauth);}
        @Override public void close(){}
    }
}
