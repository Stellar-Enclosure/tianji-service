package top.egon.cola.platform.tianji.process.server.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.CreateProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DiscardProcessDraftCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftMutationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;
import top.egon.cola.platform.tianji.process.server.error.ProcessResourceNotFoundException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.draft.ProcessDraftService;
import top.egon.cola.platform.tianji.process.server.security.TianjiProcessAccess;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProcessDraftControllerTest {
    private static final ProcessActor OWNER = ProcessActor.verified(20, ProcessActor.SubjectType.USER, "editor", "https://idp.test/main");
    private static final Instant NOW = Instant.parse("2026-11-14T08:00:00Z");
    private static final String HASH = "a".repeat(64);
    private static final String CREATE = "/api/tianji/v2/process-definitions/review/drafts";
    private static final String LIST = "/api/tianji/v2/process-drafts";
    private static final String DRAFT = LIST + "/pd-one";
    private static final String SAVE = """
            {"commandId":"save-1","expectedRevision":1,"title":"Editing","content":{
              "models":null,"inputSchemaSource":" { ","outputSchemaSource":null,"nodeBindingsSource":null,"eventRoutesSource":null}}
            """;
    private final TianjiProcessAccess access = mock(TianjiProcessAccess.class);
    private final ProcessDraftService service = mock(ProcessDraftService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(access.require(anyString())).thenReturn(OWNER);
        mvc = MockMvcBuilders.standaloneSetup(new ProcessDraftController(access, service))
                .setControllerAdvice(new ProcessApiErrors(), new ProcessRequestBodyAdvice()).build();
    }

    @Test
    void all_approved_routes_delegate_owner_and_return_closed_no_store_projections() throws Exception {
        var summary = DraftSummaryDTO.builder().draftId("pd-one").definitionKey("review").title("Editing")
                .state(DraftSummaryDTO.StateEnum.EDITING).revision(1).contentSha256(HASH).createdAt(NOW).updatedAt(NOW)
                .availableActions(List.of("SAVE", "VALIDATE", "PUBLISH", "DISCARD")).build();
        when(service.create(eq(OWNER), eq("review"), any(CreateProcessDraftCommand.class))).thenReturn(summary);
        var detail = ProcessDraftDTO.builder().draftId("pd-one").definitionKey("review").title("Editing")
                .state(DraftSummaryDTO.StateEnum.EDITING).revision(1).contentSha256(HASH).createdAt(NOW).updatedAt(NOW)
                .availableActions(summary.getAvailableActions()).lastCommandId("create-1")
                .content(new DraftContentVO(null, " { ", null, null, null)).build();
        when(service.get(OWNER, "pd-one", "create-1")).thenReturn(detail);
        when(service.list(OWNER, null, "EDITING", 1, null, null)).thenReturn(new ProcessDraftPageDTO(NOW, List.of(summary), null));
        when(service.save(eq(OWNER), eq("pd-one"), any(SaveProcessDraftCommand.class)))
                .thenReturn(new DraftMutationResultDTO("pd-one", DraftMutationResultDTO.StateEnum.EDITING, 2, HASH, NOW, "save-1"));
        when(service.discard(eq(OWNER), eq("pd-one"), any(DiscardProcessDraftCommand.class)))
                .thenReturn(new DraftMutationResultDTO("pd-one", DraftMutationResultDTO.StateEnum.DISCARDED, 3, HASH, NOW, "discard-1"));

        mvc.perform(post(CREATE).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Editing\",\"commandId\":\"create-1\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.draftId").value("pd-one")).andExpect(jsonPath("$.ownerId").doesNotExist());
        mvc.perform(get(DRAFT).param("commandId", "create-1")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.content.inputSchemaSource").value(" { "));
        String unicode = SAVE.replace(" { ", "  未闭合 { 🚩 ");
        mvc.perform(put(DRAFT).contentType("application/json;charset=UTF-8").content(unicode.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.revision").value(2));
        mvc.perform(get(LIST).param("state", "EDITING").param("limit", "1")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.items[0].draftId").value("pd-one"));
        mvc.perform(post(DRAFT + "/discard-commands").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"commandId\":\"discard-1\",\"expectedRevision\":2}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("DISCARDED"));

        var input = org.mockito.ArgumentCaptor.forClass(SaveProcessDraftCommand.class);
        verify(service).save(eq(OWNER), eq("pd-one"), input.capture());
        assertEquals("  未闭合 { 🚩 ", input.getValue().getContent().inputSchemaSource());
        verify(access).require("tianji.api.process.draft.create.v2");
        verify(access).require("tianji.api.process.draft.get.v2");
        verify(access).require("tianji.api.process.draft.save.v2");
        verify(access).require("tianji.api.process.draft.list.v2");
        verify(access).require("tianji.api.process.draft.discard.v2");
    }

    @Test
    void rejects_closed_outer_shape_and_bounded_save_before_service() throws Exception {
        for (String invalid : List.of(
                SAVE.replace("\"title\":\"Editing\"", "\"title\":\"Editing\",\"ownerId\":\"other\""),
                SAVE.replace("\"expectedRevision\":1", "\"expectedRevision\":1.0"),
                SAVE.replace("\"models\":null", "\"models\":null,\"models\":null"),
                SAVE.replace("\"models\":null,", ""),
                SAVE.replace("\"inputSchemaSource\":\" { \"", "\"inputSchemaSource\":\"\\u0000\""),
                SAVE.replace("\"inputSchemaSource\":\" { \"", "\"inputSchemaSource\":\"\\ud800\""))) {
            mvc.perform(put(DRAFT).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("TIANJI_INVALID_COMMAND"));
        }
        String tooLong = SAVE.replace(" { ", "a".repeat(5 * 1024 * 1024));
        for (boolean chunked : List.of(false, true)) {
            var request = put(DRAFT).contentType(MediaType.APPLICATION_JSON).content(tooLong).with(value -> {
                if (chunked) {
                    value.removeHeader("Content-Length");
                    value.addHeader("Transfer-Encoding", "chunked");
                } else {
                    value.addHeader("Content-Length", Integer.toString(tooLong.length()));
                }
                return value;
            });
            mvc.perform(request).andExpect(status().isPayloadTooLarge()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        }
        String tooMuchCanonicalContent = SAVE.replace(" { ", "x".repeat(4 * 1024 * 1024));
        mvc.perform(put(DRAFT).contentType(MediaType.APPLICATION_JSON).content(tooMuchCanonicalContent))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("TIANJI_REQUEST_TOO_LARGE"));
        mvc.perform(put(DRAFT).contentType("application/json;charset=ISO-8859-1")
                        .content(SAVE.replace(" { ", "  未闭合 { 🚩 ").getBytes(StandardCharsets.UTF_8)))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(LIST).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get(DRAFT).param("commandId", "duplicate", "also"))
                .andExpect(status().isBadRequest()).andExpect(header().string("Cache-Control", "no-store"));
        verifyNoInteractions(service);
    }

    @Test
    void accepts_exact_outer_and_canonical_size_limits_in_the_same_save() throws Exception {
        String minimal = "{\"models\":null,\"inputSchemaSource\":\"\",\"outputSchemaSource\":null,"
                + "\"nodeBindingsSource\":null,\"eventRoutesSource\":null}";
        String inner = "x".repeat(4 * 1024 * 1024 - minimal.length());
        String save = SAVE.replace(" { ", inner);
        String exactWire = save + " ".repeat(5 * 1024 * 1024 - save.length());
        when(service.save(eq(OWNER), eq("pd-one"), any(SaveProcessDraftCommand.class)))
                .thenReturn(new DraftMutationResultDTO("pd-one", DraftMutationResultDTO.StateEnum.EDITING, 2, HASH, NOW, "save-1"));
        mvc.perform(put(DRAFT).contentType(MediaType.APPLICATION_JSON).content(exactWire))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        var captor = org.mockito.ArgumentCaptor.forClass(SaveProcessDraftCommand.class);
        verify(service).save(eq(OWNER), eq("pd-one"), captor.capture());
        assertEquals(inner.length(), captor.getValue().getContent().inputSchemaSource().length());
    }

    @Test
    void cross_owner_not_found_and_denied_scope_do_not_disclose_draft() throws Exception {
        when(service.get(OWNER, "pd-one", null)).thenThrow(new ProcessResourceNotFoundException());
        mvc.perform(get(DRAFT)).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_RESOURCE_NOT_FOUND"));
        doThrow(new AccessDeniedException("private reason")).when(access).require("tianji.api.process.draft.list.v2");
        mvc.perform(get(LIST)).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("TIANJI_PERMISSION_DENIED"));
        verify(service).get(OWNER, "pd-one", null);
        verifyNoMoreInteractions(service);
    }
}
