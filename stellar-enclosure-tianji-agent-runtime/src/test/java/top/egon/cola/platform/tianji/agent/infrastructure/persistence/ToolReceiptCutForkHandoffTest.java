package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import org.junit.jupiter.api.Test;
import top.egon.cola.platform.tianji.agent.infrastructure.persistence.ToolReceiptCutForkHandoff.Cut;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ToolReceiptCutForkHandoffTest {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55432/tianji_it_tool_cut_fixture";
    private static final String OWNER = "tool_cut_owner";
    private static final String VECTOR = "0.8.3";
    private static final String RUN = "51e5716a-68f6-4818-a91a-c27813be1a35";
    private static final UUID INVOCATION = UUID.fromString("d2fcd30f-1bc1-4f28-b0b4-e39322386860");
    private static final UUID INTENT = UUID.fromString("35d941ca-942b-4a2b-9578-c5cf053169c1");

    @Test
    void requires_a_dedicated_disposable_database_and_exact_private_frame() throws IOException {
        var request = new ToolReceiptCutForkHandoff(URL, OWNER, VECTOR);
        var pipe = new ByteArrayOutputStream();
        request.send(pipe);
        byte[] bytes = pipe.toByteArray();
        assertEquals(request, receive(bytes));
        assertFalse(request.toString().contains(OWNER));
        for (String url : List.of(URL.replace("tool_cut_", ""), URL.replace("127.0.0.1", "localhost"),
                URL.replace("55432", "65536"), URL + "?user=owner")) {
            assertThrows(IllegalArgumentException.class, () -> new ToolReceiptCutForkHandoff(url, OWNER, VECTOR));
        }
        assertThrows(IllegalArgumentException.class, () -> new ToolReceiptCutForkHandoff(URL, OWNER, "0.8.3rc"));
        byte[] version = bytes.clone();
        version[0]++;
        for (var malformed : List.of(version, Arrays.copyOf(bytes, bytes.length - 1),
                Arrays.copyOf(bytes, bytes.length + 1), new byte[225])) {
            assertThrows(IllegalArgumentException.class, () -> receive(malformed));
        }
    }

    @Test
    void rejects_noncanonical_or_ambiguous_receipt_marker_fields() {
        var cut = new Cut(RUN, INVOCATION, INTENT);
        assertEquals(cut, Cut.decode(cut.encode()));
        assertFalse(cut.toString().contains(RUN));
        for (String malformed : List.of(cut.encode().strip(), cut.encode() + "x", cut.encode() + "\n",
                cut.encode().replace(" ", "  "), cut.encode().replace(RUN, "1-1-1-1-1"),
                cut.encode().replace(INVOCATION.toString(), "1-1-1-1-1"), cut.encode().replace(INTENT.toString(), ""))) {
            assertThrows(IllegalArgumentException.class, () -> Cut.decode(malformed));
        }
    }

    private static ToolReceiptCutForkHandoff receive(byte[] bytes) throws IOException {
        return ToolReceiptCutForkHandoff.receive(new ByteArrayInputStream(bytes));
    }
}
