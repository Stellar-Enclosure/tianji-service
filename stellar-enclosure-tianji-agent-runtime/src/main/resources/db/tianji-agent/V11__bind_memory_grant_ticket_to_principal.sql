-- Keep existing USER evidence and accept SERVICE evidence only in its own IdP namespace.
-- Invalid old principal/ticket pairs stop migration; no evidence is rewritten or discarded.
ALTER TABLE tianji_agent.agent_memory_run_grant DROP CONSTRAINT memory_grant_ticket;

ALTER TABLE tianji_agent.agent_memory_run_grant
ADD CONSTRAINT memory_grant_ticket CHECK (
    (grant_id IS NULL AND ciphertext IS NULL AND expires_at IS NULL)
    OR (grant_id IS NOT NULL
        AND ((actor_type = 'USER' AND grant_id ~ '^9[0-9]{62}$')
            OR (actor_type = 'SERVICE' AND grant_id ~ '^8[0-9]{62}$'))
        AND ciphertext IS NOT NULL AND ciphertext LIKE 'ENC:v1:%'
        AND octet_length(ciphertext) <= 32768 AND expires_at IS NOT NULL)
);
