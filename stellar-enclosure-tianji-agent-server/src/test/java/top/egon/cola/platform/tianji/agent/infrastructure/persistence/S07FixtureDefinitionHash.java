package top.egon.cola.platform.tianji.agent.infrastructure.persistence;

import top.egon.cola.platform.tianji.contract.agent.AgentDefinitionSpecDTO;

/** Test-only bridge to the ledger's canonical definition digest. */
public final class S07FixtureDefinitionHash {
    private S07FixtureDefinitionHash() { }

    public static String of(AgentDefinitionSpecDTO definition) {
        return new AgentJsonCodec().definitionHash(definition);
    }
}
