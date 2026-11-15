package top.egon.cola.platform.tianji.agent.infrastructure.memory;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import top.egon.cola.platform.tianji.contract.memory.MemoryEntryDTO;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Accessors(chain = true)
@ToString(onlyExplicitlyIncluded = true)
@JsonIgnoreType
public class LegacyMemoryFactBO {
    private long tenantId;
    private String ownerIssuer;
    private MemoryEntryDTO entry;
    private String indexModelRef;
    private String sourceEvidenceSha256;
    private String sourceActorSha256;
    private String reasonCode;
    private Instant retiredAt;
    private Long sourceRevision;
    private Instant sourceObservedAt;

    static LegacyMemoryFactBO from(JdbcMemoryRepository repository, JdbcMemoryRepository.StoredMemory stored,
                                   JdbcMemoryRepository.SourceVersionVO source) {
        return new LegacyMemoryFactBO(repository.tenantId(), repository.issuer(), stored.entry(), stored.indexModelRef(),
                stored.sourceEvidenceSha256(), stored.sourceActorSha256(), stored.reasonCode(), stored.retiredAt(),
                source == null ? null : source.sequence(), source == null ? null : source.occurredAt());
    }
}
