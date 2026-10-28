package top.egon.cola.platform.tianji.agent.infrastructure.adk.session;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AdkSessionStateCodecTest {
    private final AdkSessionStateCodec legacy = new AdkSessionStateCodec(false);
    private final AdkSessionStateCodec typed = new AdkSessionStateCodec(true);

    @Test
    void preserves_numeric_types_nested_null_and_wrapper_shaped_user_values() {
        List<Object> nested = new ArrayList<>();
        nested.add(null);
        nested.add(List.of("long", "1"));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("byte", (byte) 1);
        value.put("short", (short) 2);
        value.put("int", 3);
        value.put("long", 4L);
        value.put("float", -0.0f);
        value.put("double", -0.0d);
        value.put("integer", new BigInteger("123456789012345678901234567890"));
        value.put("decimal", new BigDecimal("12345678901234567890.00100"));
        value.put("nested", nested);
        value.put("wrapper", Map.of("map", Map.of("long", "1")));
        assertEquals(value, typed.decodeMap(typed.encode(value), 2));
        assertEquals(4L, typed.decodeValue(typed.encode(4L), 2));
        assertEquals(Map.of("count", 1), typed.decodeMap(legacy.encode(Map.of("count", 1)), 1));
        assertThrows(IllegalArgumentException.class, () -> legacy.encode(4L));
    }

    @Test
    void rejects_unknown_versions_malformed_tags_and_non_json_values() {
        assertThrows(IllegalArgumentException.class, () -> typed.decodeValue("[\"long\",\"01\"]", 2));
        assertThrows(IllegalArgumentException.class, () -> typed.decodeValue("[\"double\",\"NaN\"]", 2));
        assertThrows(IllegalArgumentException.class, () -> typed.decodeValue("[\"unknown\",\"1\"]", 2));
        assertThrows(IllegalArgumentException.class, () -> typed.decodeValue("[\"null\",null]", 2));
        assertThrows(IllegalArgumentException.class, () -> typed.decodeValue("[\"map\",{\"x\":[\"integer\",\"1\"],\"x\":[\"integer\",\"2\"]}]", 2));
        assertThrows(IllegalArgumentException.class, () -> typed.decodeMap("[\"string\",\"x\"]", 2));
        assertThrows(IllegalArgumentException.class, () -> typed.decodeValue("{}", 3));
        assertThrows(IllegalArgumentException.class, () -> typed.encode(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> typed.encode(new Object()));
        assertThrows(IllegalArgumentException.class, () -> typed.encode("invalid" + (char) 0 + "value"));
        assertThrows(IllegalArgumentException.class, () -> typed.encode(Map.of("invalid" + (char) 0xd800, 1)));
        Object nested = 1;
        for (int i = 0; i < 65; i++) nested = List.of(nested);
        Object excessive = nested;
        assertThrows(IllegalArgumentException.class, () -> typed.encode(excessive));
    }
}
