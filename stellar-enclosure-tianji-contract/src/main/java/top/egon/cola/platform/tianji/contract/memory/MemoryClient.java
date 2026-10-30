package top.egon.cola.platform.tianji.contract.memory;

/** Explicit Memory operations; tenant and issuer always come from the verified transport identity. */
public interface MemoryClient {
    String PATH = "/api/tianji/v1/memories";
    String READ_SCOPE = "tianji:memory:read";
    String WRITE_SCOPE = "tianji:memory:write";
    String FORGET_SCOPE = "tianji:memory:forget";

    MemoryEntryDTO getMemory(GetMemoryQuery query);
    MemoryRetrievalPageDTO retrieveMemory(MemoryRetrievalQuery query);
    default MemoryCandidatePageDTO memoryCandidates(MemoryCandidateQuery query) { throw new UnsupportedOperationException("Candidate review is unavailable in this client"); }
    default MemoryPreferenceExtractionResultDTO extractMemoryPreferences(ExtractMemoryPreferencesCommand command) { throw new UnsupportedOperationException("Preference extraction is unavailable in this client"); }
    default MemoryPreferencePreparationDTO prepareMemoryPreferences(PrepareMemoryPreferencesQuery query) { throw new UnsupportedOperationException("Preference preparation is unavailable in this client"); }
    MemoryMutationResultDTO upsertMemory(UpsertMemoryCommand command);
    MemoryMutationResultDTO correctMemory(CorrectMemoryCommand command);
    MemoryMutationResultDTO forgetMemory(ForgetMemoryCommand command);
}
