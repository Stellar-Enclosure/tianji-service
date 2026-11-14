package top.egon.cola.platform.tianji.process.server.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.EventRouteVO.ScheduleVO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventCommand;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessEventReceiptDTO;
import top.egon.cola.platform.tianji.contract.orchestration.automation.ProcessTypedJson;
import top.egon.cola.platform.tianji.process.server.error.AutomationOperationException;
import top.egon.cola.platform.tianji.process.server.identity.ProcessActor;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationBO;
import top.egon.cola.platform.tianji.process.server.release.AutomationActivationStore;
import top.egon.cola.platform.tianji.process.server.release.AutomationReleaseStore;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_EVENT_ROUTE_VERSION_CONFLICT;
import static top.egon.cola.platform.tianji.process.server.error.AutomationOperationException.Code.TIANJI_PROCESS_ACTIVATION_CONFLICT;

/** Plans deterministic UTC fires from the active immutable release, then uses normal durable event admission. */
@Validated
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class ProcessEventSchedule {
    private static final int MAX_OCCURRENCES = 10_000;
    private static final int MAX_REGISTERED_SOURCES = 128;
    private static final String FIRE_KEY_PREFIX = "schedule-";

    @Qualifier("automationActivationStore") private final AutomationActivationStore activations;
    @Qualifier("automationReleaseStore") private final AutomationReleaseStore releases;
    @Qualifier("processEventApplicationService") private final ProcessEventApplicationService events;
    private final Map<ScheduleScopeVO, PendingFireVO> pendingFires = new HashMap<>();

    public Optional<FireVO> next(@Min(1) long tenantId, @NotNull String definitionKey, @NotNull String routeKey,
                                 @Min(1) @Max(ProcessTypedJson.V2_MAX_WIRE_INTEGER) long routeVersion) {
        requireScope(tenantId, definitionKey, routeKey, routeVersion);
        var observed = activeSchedule(tenantId, definitionKey, routeKey, routeVersion);
        if (observed.isEmpty()) return Optional.empty();
        return plannedFire(observed.get(), activations.now());
    }

    public ProcessEventReceiptDTO admit(@NotNull @Valid FireVO fire, @NotNull ProcessActor producer,
                                       @NotNull Map<String, Object> payload) {
        return admitFire(fire, producer, payload, false);
    }

    private ProcessEventReceiptDTO admitFire(FireVO fire, ProcessActor producer, Map<String, Object> payload, boolean heldByScheduler) {
        if (fire == null || producer == null || producer.tenantId() != fire.tenantId()
                || producer.subjectType() != ProcessActor.SubjectType.SERVICE
                || !digest(producer.issuerFingerprint())) throw new AccessDeniedException("Trusted schedule producer is required");
        var observed = activeSchedule(fire.tenantId(), fire.definitionKey(), fire.routeKey(), fire.routeVersion())
                .orElseThrow(() -> new AutomationOperationException(TIANJI_EVENT_ROUTE_VERSION_CONFLICT));
        var activation = observed.activation();
        if (!matchesActivation(fire, observed)) {
            throw new AutomationOperationException(TIANJI_PROCESS_ACTIVATION_CONFLICT);
        }
        var route = observed.route();
        Instant now = activations.now();
        if (!currentFireWindow(fire.scheduledAt(), activation.getActivatedAt(), route, now, heldByScheduler)) {
            throw new AutomationOperationException(TIANJI_EVENT_ROUTE_VERSION_CONFLICT);
        }
        var command = new ProcessEventCommand(fire.fireKey(), fire.definitionKey(), fire.routeKey(), fire.routeVersion(),
                route.type(), fire.scheduledAt(), fire.fireKey(), null, payload);
        command.validateForRoute(route);
        // The event owner repeats the tuple comparison under the tenant fence before writing phase 1.
        return events.receiveScheduled(producer, command, fire.releaseVersion(), fire.activationRevision(), fire.routeSha256());
    }

    /** Holds only future fires in memory. Every durable fire goes through the owning event admission. */
    public synchronized void advanceRegistered(@NotNull ScheduleRegistration registration) {
        var sources = registeredSources(registration.sources());
        pendingFires.keySet().retainAll(sources.keySet());
        if (sources.isEmpty()) return;
        for (var entry : sources.entrySet()) {
            try {
                advanceSource(entry.getKey(), entry.getValue());
            } catch (RuntimeException failure) {
                log.warn("Process schedule advance failed: type={}", failure.getClass().getName());
            }
        }
    }

    private void advanceSource(ScheduleScopeVO scope, ScheduledSourceVO source) {
        var observed = activeSchedule(scope.tenantId(), scope.definitionKey(), scope.routeKey(), scope.routeVersion());
        if (observed.isEmpty()) {
            pendingFires.remove(scope);
            return;
        }
        var active = observed.get();
        Instant now = activations.now();
        var waiting = pendingFires.get(scope);
        if (waiting == null || !source.producer().equals(waiting.producer()) || !matchesActivation(waiting.fire(), active)) {
            rememberNext(scope, source.producer(), active, now);
            return;
        }
        var fire = waiting.fire();
        if (fire.scheduledAt().isAfter(now)) return;
        if (currentFireWindow(fire.scheduledAt(), active.activation().getActivatedAt(), active.route(), now, true)) {
            admitFire(fire, source.producer(), source.payload().apply(fire), true);
        }
        rememberNext(scope, source.producer(), active, activations.now());
    }

    private void rememberNext(ScheduleScopeVO scope, ProcessActor producer, ActiveScheduleVO schedule, Instant now) {
        var next = plannedFire(schedule, now);
        pendingFires.remove(scope);
        next.ifPresent(fire -> pendingFires.put(scope, new PendingFireVO(producer, fire)));
    }

    private static Optional<FireVO> plannedFire(ActiveScheduleVO fixed, Instant now) {
        var activation = fixed.activation();
        var route = fixed.route();
        return nextFire(activation.getActivatedAt(), route.schedule(), now)
                .map(at -> new FireVO(activation.getTenantId(), activation.getDefinitionKey(), activation.getReleaseVersion(),
                        activation.getRevision(), route.routeKey(), route.version(), fixed.routeSha256(), at,
                        fireKey(activation.getTenantId(), activation.getDefinitionKey(), activation.getReleaseVersion(),
                                fixed.routeSha256(), at)));
    }

    private static boolean matchesActivation(FireVO fire, ActiveScheduleVO active) {
        var activation = active.activation();
        return fire.activationRevision() == activation.getRevision() && fire.releaseVersion().equals(activation.getReleaseVersion())
                && fire.routeSha256().equals(active.routeSha256());
    }

    private static Map<ScheduleScopeVO, ScheduledSourceVO> registeredSources(List<ScheduledSourceVO> registrations) {
        if (registrations == null || registrations.size() > MAX_REGISTERED_SOURCES) throw invalid();
        var result = new LinkedHashMap<ScheduleScopeVO, ScheduledSourceVO>();
        var ambiguous = new HashSet<ScheduleScopeVO>();
        for (var source : registrations) {
            if (source == null) continue;
            var key = new ScheduleScopeVO(source.producer().tenantId(), source.definitionKey(), source.routeKey(), source.routeVersion());
            if (result.putIfAbsent(key, source) != null) ambiguous.add(key);
        }
        ambiguous.forEach(result::remove);
        return result;
    }

    /** Returns the first aligned occurrence strictly after both activation and the database observation. */
    public static Optional<Instant> nextFire(Instant activatedAt, ScheduleVO schedule, Instant databaseNow) {
        requireTime(activatedAt);
        requireTime(databaseNow);
        requirePrecision(schedule);
        Instant boundary = activatedAt.isAfter(databaseNow) ? activatedAt : databaseNow;
        var end = schedule.endAt();
        if (end != null && !boundary.isBefore(end)) return Optional.empty();
        var start = schedule.startAt();
        if (boundary.isBefore(start)) return Optional.of(start);
        try {
            long elapsedSlots = Duration.between(start, boundary).dividedBy(schedule.interval());
            if (elapsedSlots >= occurrenceLimit(schedule) - 1) return Optional.empty();
            Instant candidate = start.plus(schedule.interval().multipliedBy(elapsedSlots + 1));
            return end == null || !candidate.isAfter(end) ? Optional.of(candidate) : Optional.empty();
        } catch (ArithmeticException | DateTimeException pastRange) {
            return Optional.empty();
        }
    }

    public static String fireKey(long tenantId, String definitionKey, String releaseVersion, String routeSha256, Instant scheduledAt) {
        if (tenantId < 1 || !key(definitionKey) || !release(releaseVersion) || !digest(routeSha256)) throw invalid();
        requireTime(scheduledAt);
        return FIRE_KEY_PREFIX + ProcessTypedJson.sha256("AUTOMATION_EVENT_SCHEDULE_FIRE_V2", ProcessTypedJson.canonicalBytes(
                List.of(tenantId, definitionKey, releaseVersion, routeSha256, scheduledAt.toString())));
    }

    private Optional<ActiveScheduleVO> activeSchedule(long tenantId, String definitionKey, String routeKey, long routeVersion) {
        var activation = activations.current(tenantId, definitionKey);
        if (activation.isEmpty()) return Optional.empty();
        var current = activation.get();
        var release = releases.find(tenantId, current.getReleaseVersion()).orElseThrow(ProcessEventSchedule::unavailable);
        if (!definitionKey.equals(release.rootDefinitionKey()) || !current.getSourceManifestSha256().equals(release.sourceSha256())
                || !current.getCompiledManifestSha256().equals(release.compiledSha256())
                || !current.getDependencyManifestSha256().equals(release.dependencySha256())) throw unavailable();
        var matches = release.source().eventRoutes().stream().filter(route -> routeKey.equals(route.routeKey())
                && routeVersion == route.version() && "START".equals(route.mode()) && definitionKey.equals(route.targetDefinitionKey())
                && route.schedule() != null).limit(2).toList();
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() != 1 || !(release.dependencies().get("eventRoutes") instanceof Map<?, ?> registration)
                || !(registration.get(routeKey + "/" + routeVersion) instanceof String routeSha) || !digest(routeSha)) throw unavailable();
        requirePrecision(matches.getFirst().schedule());
        return Optional.of(new ActiveScheduleVO(current, matches.getFirst(), routeSha));
    }

    private static boolean currentFireWindow(Instant instant, Instant activatedAt, EventRouteVO route, Instant now,
                                             boolean heldByScheduler) {
        var schedule = route.schedule();
        Duration maximumDelay = heldByScheduler ? route.ttl() : schedule.interval();
        if (!instant.isAfter(activatedAt) || instant.isAfter(now) || instant.isBefore(schedule.startAt())
                || schedule.endAt() != null && instant.isAfter(schedule.endAt())
                || Duration.between(instant, now).compareTo(maximumDelay) >= 0) return false;
        try {
            var elapsed = Duration.between(schedule.startAt(), instant);
            long index = elapsed.dividedBy(schedule.interval());
            return index < occurrenceLimit(schedule) && elapsed.equals(schedule.interval().multipliedBy(index));
        } catch (ArithmeticException pastRange) {
            return false;
        }
    }

    private static int occurrenceLimit(ScheduleVO schedule) {
        return schedule.maxOccurrences() == null ? MAX_OCCURRENCES : schedule.maxOccurrences();
    }

    private static void requirePrecision(ScheduleVO schedule) {
        if (schedule == null || schedule.interval().getNano() % 1_000 != 0) throw invalid();
    }

    private static void requireScope(long tenantId, String definitionKey, String routeKey, long routeVersion) {
        if (tenantId < 1 || !key(definitionKey) || !key(routeKey) || routeVersion < 1
                || routeVersion > ProcessTypedJson.V2_MAX_WIRE_INTEGER) throw invalid();
    }

    private static void requireTime(Instant instant) {
        if (instant == null || instant.getNano() % 1_000 != 0) throw invalid();
    }

    private static boolean key(String value) {
        return value != null && value.matches("[A-Za-z_][A-Za-z0-9_.:-]{0,127}");
    }

    private static boolean release(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}");
    }

    private static boolean digest(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Invalid process schedule");
    }

    private static IllegalStateException unavailable() {
        return new IllegalStateException("Process schedule evidence is unavailable");
    }

    private record ActiveScheduleVO(AutomationActivationBO activation, EventRouteVO route, String routeSha256) { }

    private record ScheduleScopeVO(long tenantId, String definitionKey, String routeKey, long routeVersion) { }

    private record PendingFireVO(ProcessActor producer, FireVO fire) { }

    /** A trusted host supplies bounded operator registrations and stable business payloads for each fire key. */
    @FunctionalInterface
    public interface ScheduleRegistration {
        List<ScheduledSourceVO> sources();
    }

    public record ScheduledSourceVO(ProcessActor producer, String definitionKey, String routeKey, long routeVersion,
                                     Function<FireVO, Map<String, Object>> payload) {
        public ScheduledSourceVO {
            if (producer == null || producer.subjectType() != ProcessActor.SubjectType.SERVICE
                    || !digest(producer.issuerFingerprint()) || payload == null) throw invalid();
            requireScope(producer.tenantId(), definitionKey, routeKey, routeVersion);
        }
    }

    public record FireVO(long tenantId, String definitionKey, String releaseVersion, long activationRevision, String routeKey,
                         long routeVersion, String routeSha256, Instant scheduledAt, String fireKey) {
        public FireVO {
            requireScope(tenantId, definitionKey, routeKey, routeVersion);
            if (activationRevision < 1 || activationRevision > ProcessTypedJson.V2_MAX_WIRE_INTEGER
                    || !ProcessEventSchedule.fireKey(tenantId, definitionKey, releaseVersion, routeSha256, scheduledAt).equals(fireKey)) {
                throw invalid();
            }
        }
    }
}
