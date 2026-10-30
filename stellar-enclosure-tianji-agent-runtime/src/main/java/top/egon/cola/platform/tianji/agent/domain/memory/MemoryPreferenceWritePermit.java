package top.egon.cola.platform.tianji.agent.domain.memory;

import top.egon.cola.platform.tianji.agent.domain.TrustedAgentActor;
import top.egon.cola.platform.tianji.contract.memory.UpsertMemoryCommand;

import javax.sql.DataSource;
import java.time.Instant;

/** A narrow operation capability, not a caller identity or a stored permission snapshot. Its issuing authority must verify ownership. */
public interface MemoryPreferenceWritePermit {
    TrustedAgentActor actor();
    UpsertMemoryCommand command();
    Instant expiresAt();
    boolean usesDataSource(DataSource candidate);

    /** Checks the current epoch/lease on the same primary transaction as the fact. No model or remote authority call is allowed here. */
    void verifyTransaction();
}
