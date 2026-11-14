package top.egon.cola.platform.tianji.client;

import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ActivationPageDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.AutomationActivationDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.activation.ProcessActivationClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.MAX_STANDARD_RESPONSE_BYTES;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.V2;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.commandId;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.definitionKey;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.releaseVersion;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.require;
import static top.egon.cola.platform.tianji.client.AutomationHttpTransport.revision;

/** Explicit activation commands and independent current-version observations. */
public final class HttpProcessActivationClient implements ProcessActivationClient, AutoCloseable {
    private static final int COMMAND_REQUEST_LIMIT = 4096;
    private static final Set<String> ACTIVATION_CONFLICT = Set.of("TIANJI_PROCESS_ACTIVATION_CONFLICT");
    private final AutomationHttpTransport transport;

    public HttpProcessActivationClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout) {
        this(baseUri, http, tokens, timeout, null);
    }

    public HttpProcessActivationClient(URI baseUri, HttpClient http, Supplier<String> tokens, Duration timeout, Long tenantId) {
        this.transport = new AutomationHttpTransport(baseUri, http, tokens, timeout, tenantId);
    }

    @Override public AutomationActivationDTO activate(String definitionKey, ActivationCommand command) {
        require(command != null);
        var request = new ActivationCommand(command.getReleaseVersion(), command.getExpectedActivationRevision(), command.getCommandId());
        releaseVersion(request.getReleaseVersion());
        commandId(request.getCommandId());
        require(request.getExpectedActivationRevision() != null);
        revision(request.getExpectedActivationRevision(), 0);
        var response = transport.sendJson("POST", path(definitionKey), Map.of(), request,
                COMMAND_REQUEST_LIMIT, 200, MAX_STANDARD_RESPONSE_BYTES, ACTIVATION_CONFLICT);
        return AutomationResponseCodec.activation(response, value -> definitionKey.equals(value.getDefinitionKey())
                && request.getReleaseVersion().equals(value.getReleaseVersion()) && request.getCommandId().equals(value.getCommandId())
                && request.getExpectedActivationRevision().equals(value.getExpectedActivationRevision()));
    }

    @Override public ActivationPageDTO list(String definitionKey, String commandId, Integer limit, String cursor) {
        var filters = new LinkedHashMap<String, String>();
        if (commandId != null) {
            require(cursor == null);
            commandId(commandId);
            filters.put("commandId", commandId);
        }
        if (limit != null) {
            require(limit >= 1 && limit <= 100);
            filters.put("limit", limit.toString());
        }
        if (cursor != null) filters.put("cursor", cursor);
        var response = transport.send("GET", path(definitionKey), filters, null, 0, 200, MAX_STANDARD_RESPONSE_BYTES, Set.of());
        return AutomationResponseCodec.activations(response, value -> definitionKey.equals(value.getDefinitionKey())
                && value.getItems().size() <= (limit == null ? 20 : limit)
                && (commandId == null || value.getNextCursor() == null && value.getItems().size() <= 1
                        && value.getItems().stream().allMatch(item -> commandId.equals(item.getCommandId())))
                && consistentCurrentReceipt(value));
    }

    private static boolean consistentCurrentReceipt(ActivationPageDTO page) {
        var current = page.getCurrent();
        return current == null || page.getItems().stream().noneMatch(item -> item.getRevision() == current.revision()
                && (!item.getReleaseVersion().equals(current.releaseVersion()) || !item.getActivatedAt().equals(current.activatedAt())));
    }

    private static String path(String key) {
        definitionKey(key);
        return V2 + "process-definitions/" + AutomationHttpTransport.segment(key) + "/activations";
    }

    @Override public void close() { transport.close(); }
}
