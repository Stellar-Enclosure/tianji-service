package top.egon.cola.platform.tianji.agent.domain.memory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import top.egon.cola.platform.tianji.contract.agent.AgentRunStatusEnum;
import top.egon.cola.platform.tianji.contract.memory.MemoryKindEnum;
import top.egon.cola.platform.tianji.contract.memory.MemorySensitivityEnum;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MemoryPreferenceExtractionTest {
    private static final String RETENTION = "retention@1#" + "a".repeat(64);
    private final MemoryPreferenceExtraction extraction = new MemoryPreferenceExtraction();

    @TestFactory Stream<DynamicTest> offline_preference_quality_cases() throws Exception {
        var mapper = new JsonMapper();
        JsonNode cases;
        try (var input = getClass().getResourceAsStream("/evaluation/memory-preferences-v1.json")) {
            assertNotNull(input); cases = mapper.readTree(input);
        }
        assertTrue(cases.isArray()); assertTrue(cases.size() >= 30);
        var ids = new HashSet<String>(); var tests = new ArrayList<DynamicTest>();
        for (var testCase : cases) {
            String id = testCase.required("id").textValue(); assertTrue(ids.add(id));
            tests.add(DynamicTest.dynamicTest(id, () -> {
                var source = new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.valueOf(testCase.required("status").textValue()),
                        MemoryPreferenceExtraction.SourceKind.valueOf(testCase.required("kind").textValue()), testCase.required("approved").booleanValue(), testCase.required("text").textValue());
                var result = extraction.evaluate(source, policy(3));
                assertEquals(testCase.required("outcome").textValue(), result.outcome().name());
                var actual = new HashMap<String, String>();
                for (var proposal : result.proposals()) {
                    assertFalse(proposal.confirmed()); assertEquals(MemoryKindEnum.PREFERENCE, proposal.kind());
                    assertEquals(MemorySensitivityEnum.CONFIDENTIAL, proposal.sensitivity()); assertEquals(RETENTION, proposal.retentionPolicyRef());
                    assertTrue(proposal.confidence().compareTo(BigDecimal.ONE) < 0); assertNull(proposal.content().text());
                    assertEquals(1, proposal.content().facts().size()); actual.putAll(proposal.content().facts());
                    var fact = proposal.content().facts().entrySet().iterator().next();
                    String line = source.text().substring(proposal.sourceStart(), proposal.sourceEnd()).strip();
                    assertEquals("memory.preference." + fact.getKey() + '=' + fact.getValue(), line);
                }
                Map<String, String> expected = new HashMap<>();
                testCase.required("facts").properties().forEach(entry -> expected.put(entry.getKey(), entry.getValue().textValue()));
                assertEquals(expected, actual); assertEquals(policy(3).reference(), result.policyRef());
                assertEquals(actual.isEmpty(), result.sourceSha256() == null);
                if (!source.text().isEmpty()) assertFalse(result.toString().contains(source.text()));
            }));
        }
        return tests.stream();
    }

    @Test void resource_bounds_and_no_partial_candidates_survive_overflow_conflict_or_unsupported_fields() {
        var tooLarge = source("文".repeat(22_000));
        assertEquals(MemoryPreferenceExtraction.Outcome.SOURCE_LIMIT, extraction.evaluate(tooLarge, policy(3)).outcome());
        for (var text : List.of("memory.preference.language=zh-CN\nmemory.preference.timezone=Asia/Shanghai",
                "memory.preference.language=zh-CN\nmemory.preference.language=en-US",
                "memory.preference.language=zh-CN\nmemory.preference.password=private-value")) {
            var result = extraction.evaluate(source(text), policy(1));
            assertNotEquals(MemoryPreferenceExtraction.Outcome.ELIGIBLE, result.outcome()); assertTrue(result.proposals().isEmpty()); assertNull(result.sourceSha256());
        }
        assertThrows(IllegalArgumentException.class, () -> source("\ud800"));
    }

    @Test void policy_fingerprint_covers_the_allowlist_confidence_retention_classification_and_capacity() {
        var original = policy(3);
        var reordered = new LinkedHashMap<String, Set<String>>();
        reordered.put("response_format", Set.of("bullets", "paragraphs")); reordered.put("timezone", Set.of("UTC", "Asia/Shanghai")); reordered.put("language", Set.of("en-US", "zh-CN"));
        var copy = new MemoryExtractionPolicy("preferences", 1, reordered, new BigDecimal("0.500000"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3);
        assertEquals(original.reference(), copy.reference()); assertNotEquals(original.reference(), policy(2).reference());
        for (var changed : List.of(new MemoryExtractionPolicy("preferences", 2, reordered, new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3),
                new MemoryExtractionPolicy("preferences", 1, reordered, new BigDecimal("0.6"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3),
                new MemoryExtractionPolicy("preferences", 1, reordered, new BigDecimal("0.5"), MemorySensitivityEnum.RESTRICTED, RETENTION, 3),
                new MemoryExtractionPolicy("preferences", 1, reordered, new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, "retention@2#" + "b".repeat(64), 3),
                new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3))) assertNotEquals(original.reference(), changed.reference());
        reordered.clear(); assertEquals(3, copy.preferenceValues().size());
        assertThrows(UnsupportedOperationException.class, () -> copy.preferenceValues().get("language").add("unreviewed"));
        assertThrows(UnsupportedOperationException.class, () -> copy.preferenceValues().put("unreviewed", Set.of("value")));
    }

    @Test void unreviewed_policy_cannot_assert_certainty_lower_classification_or_open_arbitrary_value_fields() {
        for (var confidence : List.of(BigDecimal.ONE, new BigDecimal("-0.1"), new BigDecimal("0.0000001")))
            assertThrows(IllegalArgumentException.class, () -> new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN")), confidence, MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3));
        for (var classification : List.of(MemorySensitivityEnum.PUBLIC, MemorySensitivityEnum.INTERNAL))
            assertThrows(IllegalArgumentException.class, () -> new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN")), new BigDecimal("0.5"), classification, RETENTION, 3));
        for (var value : List.of("", "any\nvalue", "Cafe\u0301", " value", "\ud800"))
            assertThrows(IllegalArgumentException.class, () -> new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of(value)), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3));
        assertThrows(IllegalArgumentException.class, () -> new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of()), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, 3));
        assertThrows(IllegalArgumentException.class, () -> new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, "unversioned", 3));
    }

    private static MemoryPreferenceExtraction.Source source(String text) {
        return new MemoryPreferenceExtraction.Source(AgentRunStatusEnum.COMPLETED, MemoryPreferenceExtraction.SourceKind.USER_INPUT, true, text);
    }
    private static MemoryExtractionPolicy policy(int maximum) {
        return new MemoryExtractionPolicy("preferences", 1, Map.of("language", Set.of("zh-CN", "en-US"), "timezone", Set.of("Asia/Shanghai", "UTC"),
                "response_format", Set.of("paragraphs", "bullets")), new BigDecimal("0.5"), MemorySensitivityEnum.CONFIDENTIAL, RETENTION, maximum);
    }
}
