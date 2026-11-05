package top.egon.cola.platform.tianji.contract.orchestration;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Exact correlation and bounded UTC receipt window. The cursor only controls navigation. */
public record ListProcessInstancesByCorrelationQuery(String correlationId, Instant fromInclusive,
                                                     Instant toExclusive, String cursor) {
    private static final Duration MAX_WINDOW = Duration.ofDays(31);
    private static final Instant MIN_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("9999-12-31T23:59:59.999999Z");

    public ListProcessInstancesByCorrelationQuery {
        correlationId = OrchestrationChecks.command(correlationId);
        fromInclusive = receiptTime(fromInclusive);
        toExclusive = receiptTime(toExclusive);
        if (!fromInclusive.isBefore(toExclusive) || Duration.between(fromInclusive, toExclusive).compareTo(MAX_WINDOW) > 0) {
            throw OrchestrationChecks.invalid();
        }
        cursor = optionalCursor(cursor);
    }

    public static Instant parseUtc(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z")) {
            throw OrchestrationChecks.invalid();
        }
        try {
            return receiptTime(LocalDateTime.parse(value.substring(0, value.length() - 1)).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException malformed) {
            throw OrchestrationChecks.invalid();
        }
    }

    static Instant receiptTime(Instant value) {
        if (value == null || value.isBefore(MIN_TIME) || value.isAfter(MAX_TIME) || value.getNano() % 1_000 != 0) {
            throw OrchestrationChecks.invalid();
        }
        return value;
    }

    static String optionalCursor(String value) {
        if (value != null && (value.length() > 1_200 || !value.matches("v1\\.[A-Za-z0-9_-]+"))) {
            throw OrchestrationChecks.invalid();
        }
        return value;
    }
}
