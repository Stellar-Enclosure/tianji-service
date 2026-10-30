package top.egon.cola.platform.tianji.agent.infrastructure.adk;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.domain.AgentLedgerException;

import static org.junit.jupiter.api.Assertions.*;

class AdkSemanticJsonDigestTest {
    @Test
    void object_order_is_canonical_but_exact_decimal_digits_and_negative_zero_remain_distinct() {
        assertEquals(AdkSemanticJsonDigest.json("{\"b\":2,\"a\":1}", false), AdkSemanticJsonDigest.json("{ \"a\":1, \"b\":2 }", false));
        assertNotEquals(AdkSemanticJsonDigest.json("{\"n\":123456789.123456789}", false).sha256(),
                AdkSemanticJsonDigest.json("{\"n\":123456789.123456788}", false).sha256());
        assertNotEquals(AdkSemanticJsonDigest.json("{\"n\":-0.0}", false).sha256(), AdkSemanticJsonDigest.json("{\"n\":0.0}", false).sha256());
        assertNotEquals(AdkSemanticJsonDigest.json("{\"n\":9223372036854775808}", false).sha256(),
                AdkSemanticJsonDigest.json("{\"n\":9223372036854775809}", false).sha256());
    }

    @Test
    void only_root_transport_options_are_omitted_and_duplicate_or_trailing_values_are_rejected() {
        assertEquals(AdkSemanticJsonDigest.json("{\"temperature\":0.5,\"httpOptions\":{\"headers\":{\"Authorization\":\"first\"}}}", true),
                AdkSemanticJsonDigest.json("{\"httpOptions\":{\"headers\":{\"Authorization\":\"second\"}},\"temperature\":0.5}", true));
        assertNotEquals(AdkSemanticJsonDigest.json("{\"args\":{\"httpOptions\":1}}", true).sha256(),
                AdkSemanticJsonDigest.json("{\"args\":{\"httpOptions\":2}}", true).sha256());
        assertThrows(AgentLedgerException.class, () -> AdkSemanticJsonDigest.json("{\"a\":1,\"a\":2}", false));
        assertThrows(AgentLedgerException.class, () -> AdkSemanticJsonDigest.json("{} {}", false));
    }
}
