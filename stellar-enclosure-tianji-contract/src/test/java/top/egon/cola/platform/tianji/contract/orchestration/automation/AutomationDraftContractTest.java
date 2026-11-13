package top.egon.cola.platform.tianji.contract.orchestration.automation;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftCommandReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftContentVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftModelSourceVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftPublicationVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftSummaryDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.DraftValidationResultDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.ProcessDraftDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.draft.SaveProcessDraftCommand;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutomationDraftContractTest {

    private static final String HASH = "a".repeat(64);
    private static final Instant SAVED_AT = Instant.parse("2026-11-11T08:09:10.123456Z");
    private static final GraphVO GRAPH = new GraphVO("autoFlow", List.of(), List.of());

    @Test
    void retains_incomplete_editor_text_and_freezes_the_model_list() {
        var model = new DraftModelSourceVO("BPMN", "", "  <bpmn");
        var models = new ArrayList<>(List.of(model));
        var content = new DraftContentVO(models, " { ", "", "\r\n[", "  ");
        var command = new SaveProcessDraftCommand().setContent(content);
        models.clear();

        assertEquals(List.of(model), command.getContent().models());
        assertThrows(UnsupportedOperationException.class, () -> content.models().clear());
        assertEquals("", command.getContent().models().getFirst().definitionKey());
        assertEquals("  <bpmn", command.getContent().models().getFirst().xml());
        assertEquals(" { ", content.inputSchemaSource());
        assertEquals("", content.outputSchemaSource());
        assertEquals("\r\n[", content.nodeBindingsSource());
        assertEquals("  ", content.eventRoutesSource());
    }

    @Test
    void rejects_unsupported_model_type_and_forbidden_text_bytes() {
        assertThrows(IllegalArgumentException.class, () -> new DraftModelSourceVO("OTHER", "", ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftModelSourceVO("BPMN", null, ""));
        assertThrows(IllegalArgumentException.class, () -> new DraftModelSourceVO("BPMN", "", null));
        assertThrows(IllegalArgumentException.class, () -> new DraftModelSourceVO("BPMN", "", "\0"));
        assertThrows(IllegalArgumentException.class, () -> new DraftModelSourceVO("BPMN", "", "\uD800"));
        assertThrows(IllegalArgumentException.class, () -> new DraftContentVO(null, null, null, null, "\0"));
        assertThrows(IllegalArgumentException.class, () -> new DraftContentVO(
                Collections.nCopies(65, new DraftModelSourceVO("DMN", "", "")), null, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DraftContentVO(
                Collections.singletonList(null), null, null, null, null));
    }

    @Test
    void counts_the_entire_draft_as_escaped_utf8_at_the_four_mib_limit() {
        var wire = new LinkedHashMap<String, Object>();
        wire.put("models", null);
        wire.put("inputSchemaSource", "漢\n");
        wire.put("outputSchemaSource", null);
        wire.put("nodeBindingsSource", null);
        wire.put("eventRoutesSource", null);
        int overhead = ProcessTypedJson.canonicalBytes(wire).length;
        String source = "漢\n" + " ".repeat(DraftContentVO.MAX_CONTENT_BYTES - overhead);

        assertDoesNotThrow(() -> new DraftContentVO(null, source, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new DraftContentVO(null, source + " ", null, null, null));
    }

    @Test
    void retains_explicit_null_for_every_required_nullable_draft_content_member() {
        var content = new DraftContentVO(null, null, null, null, null);
        assertNull(content.models());
        assertNull(content.inputSchemaSource());
        assertNull(content.outputSchemaSource());
        assertNull(content.nodeBindingsSource());
        assertNull(content.eventRoutesSource());
        assertEquals(List.of(), new DraftContentVO(List.of(), null, null, null, null).models());
        assertEquals(JsonInclude.Include.ALWAYS, DraftContentVO.class.getAnnotation(JsonInclude.class).value());

        for (var component : DraftContentVO.class.getRecordComponents()) {
            var property = component.getAccessor().getAnnotation(JsonProperty.class);
            assertNotNull(property, component.getName());
            assertTrue(property.required(), component.getName());
        }
    }

    @Test
    void preserves_current_draft_and_original_command_receipt_as_independent_observations() throws Exception {
        var receipt = new DraftCommandReceiptDTO().setCommandId("save-3").setOperation(DraftCommandReceiptDTO.OperationEnum.SAVE)
                .setRequestSha256(HASH).setResultRevision(3).setResultContentSha256("b".repeat(64)).setCompletedAt(SAVED_AT);
        var draft = new ProcessDraftDTO().setDraftId("pd-101").setDefinitionKey("autoFlow").setTitle("Workflow")
                .setState(DraftSummaryDTO.StateEnum.EDITING).setRevision(7).setContentSha256(HASH)
                .setCreatedAt(SAVED_AT.minusSeconds(60)).setUpdatedAt(SAVED_AT)
                .setAvailableActions(List.of("SAVE", "VALIDATE", "PUBLISH", "DISCARD"))
                .setContent(new DraftContentVO(List.of(), null, null, null, null))
                .setLastCommandId("save-7").setCommandReceipt(receipt);

        assertTrue(receipt.isReceiptConsistent());
        assertTrue(draft.isDraftProjectionConsistent());
        assertEquals(3, draft.getCommandReceipt().getResultRevision());
        assertNotEquals(draft.getRevision(), draft.getCommandReceipt().getResultRevision());
        assertNotEquals(draft.getLastCommandId(), draft.getCommandReceipt().getCommandId());
        assertNotEquals(draft.getContentSha256(), draft.getCommandReceipt().getResultContentSha256());
        for (String field : List.of("baseReleaseVersion", "publishedReleaseVersion", "baseSourceManifestSha256",
                "publication", "commandReceipt")) {
            assertEquals(JsonInclude.Include.ALWAYS, ProcessDraftDTO.class.getDeclaredField(field)
                    .getAnnotation(JsonInclude.class).value());
        }
        assertFalse(draft.setBaseReleaseVersion("v1").isDraftProjectionConsistent());
        assertTrue(draft.setBaseSourceManifestSha256(HASH).isDraftProjectionConsistent());
        assertTrue(draft.setCommandReceipt(null).isDraftProjectionConsistent());
        assertNull(draft.getCommandReceipt());
        assertFalse(draft.setCommandReceipt(receipt.setResultRevision(8)).isDraftProjectionConsistent());
    }

    @Test
    void requires_preflight_projection_only_for_a_valid_revision() throws Exception {
        var issue = new DraftValidationResultDTO.ValidationIssueVO("/models/0/xml", "INVALID_MODEL", "Invalid model", null);
        var result = new DraftValidationResultDTO().setDraftId("pd-101").setRevision(7).setContentSha256(HASH)
                .setCompilerVersion("automation-bpmn-2").setIssues(List.of(issue));
        assertTrue(result.isValidationOutcomeConsistent());
        assertFalse(result.isValid());
        assertNull(result.getGraph());
        assertNull(result.getSourceManifestSha256());
        assertNull(result.getIssues().getFirst().nodeId());
        for (String field : List.of("graph", "sourceManifestSha256")) {
            assertEquals(JsonInclude.Include.ALWAYS, DraftValidationResultDTO.class.getDeclaredField(field)
                    .getAnnotation(JsonInclude.class).value());
        }
        assertFalse(result.setGraph(GRAPH).isValidationOutcomeConsistent());
        assertFalse(result.setValid(true).setSourceManifestSha256(HASH).isValidationOutcomeConsistent());
        assertTrue(result.setIssues(List.of()).isValidationOutcomeConsistent());
    }

    @Test
    void publication_uses_the_previous_revision_and_seals_edit_actions() {
        var publication = new DraftPublicationVO("pd-101", 7, 8, "v2", HASH, HASH, HASH,
                "automation-bpmn-2", SAVED_AT, "publish-1");
        assertEquals(7, publication.publishedRevision());
        assertEquals(8, publication.draftRevision());
        assertThrows(IllegalArgumentException.class, () -> new DraftPublicationVO("pd-101", 7, 7, "v2", HASH, HASH, HASH,
                "automation-bpmn-2", SAVED_AT, "publish-1"));
        assertThrows(IllegalArgumentException.class, () -> new DraftPublicationVO("pd-101", 7, 8, "v2", HASH, HASH, HASH,
                "automation-bpmn-2", SAVED_AT.plusNanos(1), "publish-1"));

        var draft = new ProcessDraftDTO().setDraftId("pd-101").setState(DraftSummaryDTO.StateEnum.PUBLISHED)
                .setRevision(8).setPublishedReleaseVersion("v2").setPublication(publication).setLastCommandId("publish-1")
                .setCreatedAt(SAVED_AT.minusSeconds(60)).setUpdatedAt(SAVED_AT).setAvailableActions(List.of());
        assertTrue(draft.isDraftProjectionConsistent());
        assertFalse(draft.setAvailableActions(List.of("PUBLISH")).isDraftProjectionConsistent());
        assertFalse(draft.setAvailableActions(List.of()).setPublishedReleaseVersion("v3").isDraftProjectionConsistent());
        var discarded = new DraftSummaryDTO().setState(DraftSummaryDTO.StateEnum.DISCARDED)
                .setCreatedAt(SAVED_AT.minusSeconds(60)).setUpdatedAt(SAVED_AT);
        assertFalse(discarded.setAvailableActions(List.of("SAVE")).isSummaryConsistent());
        assertFalse(discarded.setAvailableActions(List.of("DISCARD")).isSummaryConsistent());
        assertTrue(discarded.setAvailableActions(List.of()).isSummaryConsistent());
    }

    @Test
    void activation_preserves_explicit_zero_and_separates_historical_receipts_from_current() throws Exception {
        var command = new ActivationCommand().setCommandId("activate-1").setReleaseVersion("v1");
        assertNull(command.getExpectedActivationRevision());
        assertNotNull(ActivationCommand.class.getDeclaredField("expectedActivationRevision").getAnnotation(NotNull.class));
        assertEquals(0L, command.setExpectedActivationRevision(0L).getExpectedActivationRevision());

        var first = new AutomationActivationDTO().setDefinitionKey("autoFlow").setCommandId("activate-1").setReleaseVersion("v1")
                .setRevision(1).setExpectedActivationRevision(0L).setActivatedAt(SAVED_AT);
        assertNull(first.getPreviousRevision());
        assertEquals(0L, first.getExpectedActivationRevision());
        assertTrue(first.isActivationConsistent());
        assertFalse(first.setPreviousRevision(0L).isActivationConsistent());
        first.setPreviousRevision(null);
        var second = new AutomationActivationDTO().setDefinitionKey("autoFlow").setCommandId("activate-2").setReleaseVersion("v2")
                .setRevision(2).setExpectedActivationRevision(1L).setPreviousRevision(1L).setActivatedAt(SAVED_AT.plusSeconds(1));
        assertTrue(second.isActivationConsistent());
        assertFalse(second.setPreviousRevision(null).isActivationConsistent());
        second.setPreviousRevision(1L);

        var page = new ActivationPageDTO().setDefinitionKey("autoFlow").setObservedAt(SAVED_AT.plusSeconds(2)).setItems(List.of());
        assertTrue(page.isActivationObservationConsistent());
        assertFalse(page.setItems(List.of(first)).isActivationObservationConsistent());
        page.setCurrent(new ActivationPageDTO.CurrentActivationVO(2, "v2", second.getActivatedAt()));
        assertTrue(page.isActivationObservationConsistent());
        assertNotEquals(page.getCurrent().revision(), first.getRevision());
        assertTrue(page.setItems(List.of(second, first)).isActivationObservationConsistent());
        assertFalse(page.setItems(List.of(first, second)).isActivationObservationConsistent());
        assertFalse(page.setItems(List.of(first.setDefinitionKey("anotherFlow"))).isActivationObservationConsistent());
    }

    @Test
    void source_and_draft_representations_do_not_render_payloads_as_log_text() {
        var model = new DraftModelSourceVO("BPMN", "", "private-xml");
        var content = new DraftContentVO(List.of(model), "private-json", null, null, null);
        assertFalse(model.toString().contains("private-xml"));
        assertFalse(content.toString().contains("private-json"));
        assertFalse(new SaveProcessDraftCommand().setContent(content).toString().contains("content="));
        assertFalse(new ProcessDraftDTO().setContent(content).toString().contains("content="));

        var schema = ProcessSchemaVO.fromWire(Map.of("type", "object", "properties", Map.of(),
                "required", List.of(), "additionalProperties", false));
        var source = new AutomationModelSourceVO("AUTOMATION_V2",
                List.of(new AutomationModelSourceVO.ModelSourceVO("BPMN", "autoFlow", "private-xml")),
                schema, schema, List.of(), List.of());
        var release = new AutomationReleaseDTO().setSourceDraftId("pd-101").setSourceDraftRevision(7)
                .setArtifact(source).setGraph(GRAPH);
        assertEquals(7, release.getSourceDraftRevision());
        assertTrue(release.isReleaseProjectionConsistent());
        assertFalse(release.toString().contains("private-xml"));
        assertFalse(release.setGraph(new GraphVO("anotherFlow", List.of(), List.of())).isReleaseProjectionConsistent());
    }
}
