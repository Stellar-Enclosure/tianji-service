package top.egon.cola.platform.tianji.client;

import tools.jackson.databind.JsonNode;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventClient;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.V2;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.decode;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.exact;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.exactInteger;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.nullable;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.require;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.string;
import static top.egon.cola.platform.tianji.client.AutomationResponseCodec.utc;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.encodeRequest;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.matching;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.opaque;
import static top.egon.cola.platform.tianji.client.HttpProcessAutomationClient.requestTree;

/** Sends and observes producer events using the current bearer authorization. */
public final class HttpProcessEventClient implements ProcessEventClient, AutoCloseable {
    private static final Pattern ID = Pattern.compile("[A-Za-z][A-Za-z0-9_.:-]{0,127}");
    private static final int EVENT_REQUEST_LIMIT = 68 * 1024;
    private static final int RECEIPT_RESPONSE_LIMIT = 4096;
    private static final Set<String> EVENT_CONFLICTS = Set.of("TIANJI_EVENT_CONFLICT", "TIANJI_EVENT_ROUTE_CHANGED",
            "TIANJI_EVENT_ROUTE_VERSION_CONFLICT", "TIANJI_PROCESS_NOT_ACTIVATED", "TIANJI_PROCESS_ACTIVATION_CONFLICT");
    private static final Set<String> DECISION_CODES = Set.of("APPLIED", "LATE", "RELEASE_CHANGED", "ROUTE_REVOKED", "INVALID_PAYLOAD", "EXPIRED");
    private final AutomationHttpTransport transport;

    public HttpProcessEventClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout) {
        this(baseUri, http, tokens, timeout, null);
    }

    public HttpProcessEventClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout, Long tenantId) {
        transport = new AutomationHttpTransport(baseUri, http, tokens, timeout, tenantId);
    }

    @Override public ProcessEventReceiptDTO receive(ProcessEventCommand command) {
        AutomationHttpTransport.require(command != null && command.isEventEnvelopeValid());
        byte[] body = encodeRequest(command);
        JsonNode wire = requestTree(body, EVENT_REQUEST_LIMIT);
        exact(wire, "eventId", "definitionKey", "routeKey", "routeVersion", "type", "occurredAt", "correlationKey", "waitToken", "payload");
        utc(wire.path("occurredAt"));
        ProcessEventCommand accepted;
        try {
            accepted = decode(new AutomationHttpTransport.ResponseVO(202, body), ProcessEventCommand.class,
                    node -> require(node.path("payload").isObject()), ProcessEventCommand::isEventEnvelopeValid);
        } catch (ProcessClientException invalid) {
            throw new IllegalArgumentException("Automation event request is invalid");
        }
        var response = transport.send("POST", V2 + "process-events", Map.of(), body, EVENT_REQUEST_LIMIT,
                202, RECEIPT_RESPONSE_LIMIT, EVENT_CONFLICTS);
        return receipt(response, result -> accepted.getEventId().equals(result.getEventId()));
    }

    @Override public ProcessEventReceiptDTO getReceipt(String receiptId) {
        AutomationHttpTransport.require(receiptId != null && ID.matcher(receiptId).matches());
        var response = transport.send("GET", V2 + "process-event-receipts/" + AutomationHttpTransport.segment(receiptId),
                Map.of(), null, 0, 200, RECEIPT_RESPONSE_LIMIT, Set.of(), HttpProcessAutomationClient.EXTENDED_ERRORS);
        return receipt(response, result -> receiptId.equals(result.getReceiptId()));
    }

    private static ProcessEventReceiptDTO receipt(AutomationHttpTransport.ResponseVO response, Predicate<ProcessEventReceiptDTO> scope) {
        return decode(response, ProcessEventReceiptDTO.class, HttpProcessEventClient::receiptShape,
                result -> result.isReceiptConsistent() && scope.test(result));
    }

    private static void receiptShape(JsonNode node) {
        exact(node, "receiptId", "eventId", "state", "releaseVersion", "activationRevision", "admittedAt", "instanceId",
                "nodeExecutionRef", "receivedAt", "decidedAt", "decisionCode");
        matching(node.path("receiptId"), ID);
        matching(node.path("eventId"), ID);
        string(node.path("state"));
        AutomationHttpTransport.releaseVersion(string(node.path("releaseVersion")));
        exactInteger(node.path("activationRevision"), 1);
        utc(node.path("admittedAt"));
        nullable(node, "instanceId", value -> opaque(value, 255));
        nullable(node, "nodeExecutionRef", value -> matching(value, ID));
        utc(node.path("receivedAt"));
        nullable(node, "decidedAt", AutomationResponseCodec::utc);
        nullable(node, "decisionCode", value -> require(DECISION_CODES.contains(string(value))));
    }

    @Override public void close() { transport.close(); }
}
