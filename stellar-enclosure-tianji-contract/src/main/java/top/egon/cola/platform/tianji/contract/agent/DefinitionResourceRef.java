package top.egon.cola.platform.tianji.contract.agent;

/** Immutable reference to a published prompt, skill, or tool schema. */
public record DefinitionResourceRef(String key, long version, String contentSha256) {
    public DefinitionResourceRef {
        key = ContractChecks.required(key, "key", 128);
        if (version <= 0 || contentSha256 == null || !contentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("resource version or SHA-256 is invalid");
        }
    }
}
