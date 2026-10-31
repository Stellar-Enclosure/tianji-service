package top.egon.cola.platform.tianji.process.server.execution;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProcessRequestDigestCompatibilityTest {
    @Test void existing_server_callers_keep_the_v1_utf8_primitive_command_hash_after_sharing_the_contract() {
        assertEquals("8d7cfd57d61d948aa9e68641caa29c8c3e443b16c903b129018e668aa84afe98",
                ProcessRequestDigest.hash(List.of("release", "workflow", "invoice-1", "correlation"), Map.of("amount", 20L, "approved", false, "note", "审阅")));
    }
}
