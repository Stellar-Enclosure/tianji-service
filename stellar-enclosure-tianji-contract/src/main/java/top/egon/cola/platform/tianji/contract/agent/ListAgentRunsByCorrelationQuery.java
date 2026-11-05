package top.egon.cola.platform.tianji.contract.agent;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Exact correlation and bounded UTC creation window. The cursor only controls navigation. */
public record ListAgentRunsByCorrelationQuery(String correlationId, Instant fromInclusive,
                                              Instant toExclusive, String cursor) {
    private static final Duration MAX_WINDOW = Duration.ofDays(31);
    private static final Instant MIN_TIME = Instant.parse("0001-01-01T00:00:00Z");
    private static final Instant MAX_TIME = Instant.parse("9999-12-31T23:59:59.999999Z");

    public ListAgentRunsByCorrelationQuery {
        correlationId = ContractChecks.contextIdentifier(correlationId, "correlationId", 128);
        fromInclusive = creationTime(fromInclusive);
        toExclusive = creationTime(toExclusive);
        if (!fromInclusive.isBefore(toExclusive) || Duration.between(fromInclusive, toExclusive).compareTo(MAX_WINDOW) > 0) {
            throw invalid();
        }
        if (cursor != null && (cursor.length() > 256 || !cursor.matches("v1\\.[A-Za-z0-9_-]+"))) {
            throw invalid();
        }
    }

    public static Instant parseUtc(String value) {
        if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?Z")) {
            throw invalid();
        }
        try {
            return creationTime(LocalDateTime.parse(value.substring(0, value.length() - 1)).toInstant(ZoneOffset.UTC));
        } catch (DateTimeParseException malformed) {
            throw invalid();
        }
    }

    private static Instant creationTime(Instant value) {
        if (value == null || value.isBefore(MIN_TIME) || value.isAfter(MAX_TIME) || value.getNano() % 1_000 != 0) {
            throw invalid();
        }
        return value;
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Agent run correlation query is invalid");
    }
}
