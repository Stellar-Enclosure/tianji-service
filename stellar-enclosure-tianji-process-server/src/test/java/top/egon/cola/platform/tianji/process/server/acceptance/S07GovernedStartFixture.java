package top.egon.cola.platform.tianji.process.server.acceptance;

import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelResource;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessModelTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessReleaseDTO;
import top.egon.cola.platform.tianji.contract.orchestration.ProcessValueTypeEnum;
import top.egon.cola.platform.tianji.contract.orchestration.PublishProcessReleaseCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class S07GovernedStartFixture {
    static final String RELEASE = "s07-governed-release";
    static final String BPMN = "s07GovernedStart";
    static final String DMN = "s07Decision";
    static final String AGENT = "s07GovernedAgent";
    static final long BUSINESS_VERSION = 7;
    static final long AMOUNT = 10;
    private static final Map<String, ProcessValueTypeEnum> INPUTS = Map.of("amount", ProcessValueTypeEnum.INTEGER);
    private static final String PROCESS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="https://stellar-enclosure.example/test">
              <process id="s07GovernedStart" name="S07 governed START" isExecutable="true">
                <startEvent id="start"/>
                <sequenceFlow id="toReview" sourceRef="start" targetRef="review"/>
                <userTask id="review" name="S07 held review"/>
                <sequenceFlow id="toEnd" sourceRef="review" targetRef="end"/>
                <endEvent id="end"/>
              </process>
            </definitions>
            """;
    private static final String DECISION = """
            <?xml version="1.0" encoding="UTF-8"?>
            <definitions xmlns="http://www.omg.org/spec/DMN/20151101" id="s07Definitions" name="S07"
                namespace="https://stellar-enclosure.example/test">
              <decision id="s07Decision" name="S07 companion decision">
                <decisionTable id="s07Table" hitPolicy="FIRST">
                  <input><inputExpression id="amountExpression" typeRef="number"><text>amount</text></inputExpression></input>
                  <output id="routeOutput" name="route" typeRef="string"/>
                  <rule><inputEntry id="small"><text>&lt;= 10</text></inputEntry><outputEntry id="standard"><text>'standard'</text></outputEntry></rule>
                  <rule><inputEntry id="large"><text>&gt; 10</text></inputEntry><outputEntry id="review"><text>'review'</text></outputEntry></rule>
                </decisionTable>
              </decision>
            </definitions>
            """;
    static final String BPMN_SHA = sha(PROCESS);
    static final String DMN_SHA = sha(DECISION);

    private S07GovernedStartFixture() { }

    static PublishProcessReleaseCommand release() {
        return new PublishProcessReleaseCommand(RELEASE, List.of(
                new ProcessModelResource(ProcessModelTypeEnum.BPMN, BPMN, PROCESS, INPUTS),
                new ProcessModelResource(ProcessModelTypeEnum.DMN, DMN, DECISION, INPUTS)));
    }

    static void verifyRelease(ProcessReleaseDTO release) {
        var expected = Set.of(new ProcessModelDTO(ProcessModelTypeEnum.BPMN, BPMN, BPMN_SHA, INPUTS),
                new ProcessModelDTO(ProcessModelTypeEnum.DMN, DMN, DMN_SHA, INPUTS));
        if (release == null || !RELEASE.equals(release.releaseVersion())
                || release.models().size() != 2 || !Set.copyOf(release.models()).equals(expected)) {
            throw new IllegalStateException("S07 governed START release does not match the fixture");
        }
    }

    static String businessKey(long tenant) { return "s07-object-" + tenant; }

    private static String sha(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("S07 fixture model digest is unavailable");
        }
    }
}
