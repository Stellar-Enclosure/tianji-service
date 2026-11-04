package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.*;
import top.egon.cola.platform.tianji.process.server.identity.CaseContext;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.ValidatedCaseModel;
import top.egon.cola.platform.tianji.process.server.release.ValidatedProcessModel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CaseSnapshotTest {
    static final Instant NOW = Instant.parse("2026-11-04T08:00:00Z");
    static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "owner", "https://idp.example/20");
    static final ProcessActor OPERATOR = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "operator", "https://idp.example/20");
    static final byte[] XML = "fixed-case-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    static final String HASH = hash(XML);
    // Independent length-delimited golden for the fixed enabled runtime fixture (24 fields + empty value map).
    static final String ENABLED_VERSION = "3f3515ee8dc9633744bb2204b5c091035dbbc295bc3ed6f0ae8c2709771f7150";

    @Test void three_native_case_states_and_six_plan_states_are_closed_and_terminal_projection_has_no_actions() {
        for (var state : List.of("available", "enabled", "disabled", "active", "completed", "terminated")) {
            var snapshot = assertDoesNotThrow(()->new CaseSnapshot(origin(), model(), observation("active", null, List.of(item("primary", state, 1, false)))));
            var dto = assertDoesNotThrow(() -> snapshot.projection(context(OPERATOR, Set.of("7"))));
            assertNotNull(dto); assertEquals(CasePlanItemStateEnum.valueOf(state.toUpperCase(java.util.Locale.ROOT)), dto.planItems().getFirst().state());
        }
        for (var state : List.of("completed", "terminated")) {
            var snapshot = assertDoesNotThrow(()->new CaseSnapshot(origin(), model(), observation(state, NOW, List.of(item("primary", "enabled", 1, true)))));
            var dto = assertDoesNotThrow(() -> snapshot.projection(context(OWNER, Set.of())));
            assertNotNull(dto); assertEquals(CaseInstanceStatusEnum.valueOf(state.toUpperCase(java.util.Locale.ROOT)), dto.status());
            assertEquals(NOW, dto.finishedAt()); assertEquals(List.of(), dto.planItems().getFirst().availableActions());
        }
    }

    @Test void revision_prevents_same_state_aba_and_history_has_an_independent_read_only_domain() {
        var one=item("primary","enabled",1,false); var three=item("primary","enabled",3,false); var history=item("primary","enabled",1,true);
        var snapshot=assertDoesNotThrow(()->new CaseSnapshot(origin(),model(),observation("active",null,List.of(one))));
        String first=snapshot.version(one);
        assertNotNull(first); assertEquals(ENABLED_VERSION, first); assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(first,snapshot.version(three)); assertNotEquals(first,snapshot.version(history));
        assertFalse(snapshot.version(one).contains("owner"));
    }

    @Test void action_advertisement_requires_current_released_role_and_supplemental_assignee_protection() {
        var primary=item("primary","enabled",1,false); var supplemental=item("supplemental","active",1,false);
        var snapshot=assertDoesNotThrow(()->new CaseSnapshot(origin(),model(),observation("active",null,List.of(primary,supplemental))));
        var dto=assertDoesNotThrow(()->snapshot.projection(context(OPERATOR,Set.of("8"))));
        assertNotNull(dto); assertEquals(List.of(CasePlanItemActionEnum.DISABLE,CasePlanItemActionEnum.START),dto.planItems().getFirst().availableActions());
        assertEquals(List.of(CasePlanItemActionEnum.TERMINATE),dto.planItems().get(1).availableActions());
        var claimed=new CaseNativeAccess.PlanItem(supplemental.id(),supplemental.elementId(),supplemental.definitionId(),supplemental.kind(),supplemental.state(),false,1,NOW,null,null,null,NOW,null,null,null,null,"another-user",2);
        var protectedSnapshot=assertDoesNotThrow(()->new CaseSnapshot(origin(),model(),observation("active",null,List.of(claimed))));
        assertEquals(List.of(),assertDoesNotThrow(()->protectedSnapshot.projection(context(OPERATOR,Set.of("8")))).planItems().getFirst().availableActions());
    }

    @Test void exact_owner_or_same_realm_current_operator_can_read_but_service_cannot_borrow_a_role() {
        var snapshot=assertDoesNotThrow(()->new CaseSnapshot(origin(),model(),observation("active",null,List.of())));
        assertNotNull(assertDoesNotThrow(()->snapshot.projection(context(OWNER,Set.of()))));
        assertNotNull(assertDoesNotThrow(()->snapshot.projection(context(OPERATOR,Set.of("9")))));
        assertThrows(top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException.class,()->snapshot.projection(context(OPERATOR,Set.of())));
        var foreign=ProcessActor.verified(20,ProcessActor.SubjectType.USER,"operator","https://foreign.example/20");
        assertThrows(top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException.class,()->snapshot.projection(context(foreign,Set.of("7"))));
        var service=ProcessActor.verified(20,ProcessActor.SubjectType.SERVICE,"worker","https://idp.example/20");
        assertThrows(top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException.class,()->snapshot.projection(context(service,Set.of())));
    }

    @Test void milestone_uses_occurrence_time_and_never_advertises_actions() {
        var item=new CaseNativeAccess.PlanItem("pi-milestone","milestone","completionMilestone","milestone","completed",true,0,NOW,null,null,null,null,null,NOW,null,NOW,null,0);
        var snapshot=assertDoesNotThrow(()->new CaseSnapshot(origin(),model(),observation("completed",NOW,List.of(item))));
        var dto=assertDoesNotThrow(()->snapshot.projection(context(OWNER,Set.of()))); assertNotNull(dto);
        assertEquals(CasePlanItemKindEnum.MILESTONE,dto.planItems().getFirst().kind()); assertEquals(List.of(),dto.planItems().getFirst().availableActions());
    }

    @Test void unsupported_native_states_duplicate_elements_and_wrong_definition_affinity_fail_closed() {
        for (var state:List.of("suspended","waitingForRepetition","exit","unknown"))
            assertThrows(IllegalStateException.class,()->new CaseSnapshot(origin(),model(),observation("active",null,List.of(item("primary",state,1,false)))));
        assertThrows(IllegalStateException.class,()->new CaseSnapshot(origin(),model(),observation("suspended",null,List.of())));
        var primary=item("primary","enabled",1,false);
        assertThrows(IllegalStateException.class,()->new CaseSnapshot(origin(),model(),observation("active",null,List.of(primary,primary))));
        assertThrows(IllegalStateException.class,()->new CaseSnapshot(origin(),model(),new CaseNativeAccess.Observation("case-1","foreign","20","business","active",null,List.of())));
    }

    static CaseContext context(ProcessActor actor,Set<String> roles){return new CaseContext(actor,"tianji.api.process.case.get",roles,NOW.plusSeconds(30),2,3);}
    static CaseStartProvenanceStore.Entry origin(){return new CaseStartProvenanceStore.Entry(new CaseStartProvenanceStore.Seed("case-1","DIRECT","business","correlation","release","governedCase","case-def",HASH,OWNER,"CASE_START","start","a".repeat(64),OWNER,"start","a".repeat(64),null,null,null,null,null,null,null),NOW);}
    static ValidatedCaseModel model(){
        var primary=new ValidatedProcessModel.HumanTask(ValidatedProcessModel.HumanTaskKind.ACTION,Map.of("operator",Set.of("7","8")),"operator",null,null,List.of());
        var supplemental=new ValidatedProcessModel.HumanTask(ValidatedProcessModel.HumanTaskKind.ACTION,Map.of("operator",Set.of("8","9")),"operator",null,null,List.of());
        return new ValidatedCaseModel("governedCase",XML,Map.of("primaryTask",primary,"supplementalTask",supplemental),Map.of("primary",new ValidatedCaseModel.PlanItemPolicy("primaryTask",Set.of(ValidatedCaseModel.Action.DISABLE,ValidatedCaseModel.Action.ENABLE,ValidatedCaseModel.Action.START),Set.of("7","8")),"supplemental",new ValidatedCaseModel.PlanItemPolicy("supplementalTask",Set.of(ValidatedCaseModel.Action.START,ValidatedCaseModel.Action.TERMINATE),Set.of("8","9")),"milestone",new ValidatedCaseModel.PlanItemPolicy("completionMilestone",Set.of(),Set.of())));
    }
    static CaseNativeAccess.Observation observation(String state,Instant end,List<CaseNativeAccess.PlanItem> items){return new CaseNativeAccess.Observation("case-1","case-def","20","business",state,end,items);}
    static CaseNativeAccess.PlanItem item(String element,String state,int revision,boolean history){return new CaseNativeAccess.PlanItem("pi-"+element,element,element.equals("primary")?"primaryTask":"supplementalTask",org.flowable.cmmn.api.runtime.PlanItemDefinitionType.HUMAN_TASK,state,history,revision,NOW,null,"enabled".equals(state)?NOW:null,null,"active".equals(state)?NOW:null,"completed".equals(state)?NOW:null,null,"terminated".equals(state)?NOW:null,Set.of("completed","terminated").contains(state)?NOW:null,null,0);}
    private static String hash(byte[] bytes){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception impossible){throw new AssertionError(impossible);}}
}
