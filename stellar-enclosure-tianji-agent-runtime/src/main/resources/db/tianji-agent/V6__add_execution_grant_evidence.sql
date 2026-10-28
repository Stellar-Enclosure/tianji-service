CREATE TABLE tianji_agent.agent_run_execution_grant (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    run_id varchar(64) NOT NULL,
    binding_sha256 char(64) COLLATE "C" NOT NULL CHECK (binding_sha256 ~ '^[0-9a-f]{64}$'),
    issue_epoch bigint NOT NULL CHECK (issue_epoch > 0),
    grant_id varchar(63) COLLATE "C",
    ciphertext text,
    expires_at timestamptz,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, run_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES tianji_agent.agent_run (tenant_id, run_id) ON DELETE CASCADE,
    CHECK ((grant_id IS NULL AND ciphertext IS NULL AND expires_at IS NULL)
        OR (grant_id IS NOT NULL AND ciphertext IS NOT NULL AND grant_id ~ '^9[0-9]{62}$' AND ciphertext LIKE 'ENC:v1:%'
            AND octet_length(ciphertext) <= 32768 AND expires_at IS NOT NULL))
);
