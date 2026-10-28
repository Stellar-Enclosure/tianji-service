package top.egon.cola.platform.tianji.contract.agent;

import java.time.Duration;
import java.util.Objects;

final class PolicyChecks {
    private PolicyChecks() {
    }

    static void duration(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    static void optionalDuration(Duration value, String name) {
        if (value != null) {
            duration(value, name);
        }
    }
}
