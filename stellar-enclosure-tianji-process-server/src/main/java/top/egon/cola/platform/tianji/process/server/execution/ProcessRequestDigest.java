package top.egon.cola.platform.tianji.process.server.execution;

import top.egon.cola.platform.tianji.contract.orchestration.OrchestrationRequestDigests;

import java.util.List;
import java.util.Map;

/** Length-delimited hashing of normalized primitive command values. */
public final class ProcessRequestDigest {
    private ProcessRequestDigest() { }

    public static String hash(List<String> fields, Map<String, Object> values) {
        return OrchestrationRequestDigests.hash(fields, values);
    }
}
