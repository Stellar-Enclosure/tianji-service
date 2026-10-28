package top.egon.cola.platform.tianji.contract.agent;

import java.time.Duration;
import java.util.Objects;

final class PolicyChecks {
    private PolicyChecks() {
    }

    static void duration(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative() || value.compareTo(Duration.ofNanos(Long.MAX_VALUE)) > 0) {
            throw new IllegalArgumentException(name + " must be positive and fit nanoseconds");
        }
    }

    static void optionalDuration(Duration value, String name) {
        if (value != null) {
            duration(value, name);
        }
    }
}
