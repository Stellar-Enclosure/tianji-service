package top.egon.cola.platform.tianji.client;

import java.util.regex.Pattern;

/** Closed diagnostic header carried only by the existing Agent-result HTTP transport. */
public final class AgentResultTraceLinkCarrier {
    public static final String PATH = "/api/tianji/v1/agent-result-inbox";
    public static final String HEADER = "X-Egon-Tianji-Agent-Result-Link";
    private static final Pattern SAMPLED_V00 =
            Pattern.compile("00-(?!0{32}-)[0-9a-f]{32}-(?!0{16}-)[0-9a-f]{16}-01");

    private AgentResultTraceLinkCarrier() { }

    public static boolean isValid(String value) {
        return value != null && value.length() == 55 && SAMPLED_V00.matcher(value).matches();
    }
}
