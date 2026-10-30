CREATE TABLE tianji_agent.agent_memory_preference_intent (
    intent_id uuid NOT NULL,
    tenant_id bigint NOT NULL CONSTRAINT preference_intent_tenant CHECK (tenant_id > 0),
    actor_issuer char(64) COLLATE "C" NOT NULL CONSTRAINT preference_intent_issuer CHECK (actor_issuer ~ '^[0-9a-f]{64}$'),
    actor_type varchar(16) COLLATE "C" NOT NULL CONSTRAINT preference_intent_actor_type CHECK (actor_type = 'USER'),
    actor_id varchar(256) COLLATE "C" NOT NULL CONSTRAINT preference_intent_actor_id CHECK (length(actor_id) > 0),
    run_id varchar(64) NOT NULL,
    scope_id varchar(128) COLLATE "C" NOT NULL CONSTRAINT preference_intent_scope CHECK (length(scope_id) > 0),
    binding_id varchar(128) COLLATE "C" NOT NULL CONSTRAINT preference_intent_binding_id CHECK (length(binding_id) > 0),
    policy_ref varchar(256) COLLATE "C" NOT NULL CONSTRAINT preference_intent_policy CHECK (policy_ref ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}@[1-9][0-9]{0,18}#[0-9a-f]{64}$'),
    resource_uri varchar(2048) COLLATE "C" NOT NULL CONSTRAINT preference_intent_resource CHECK (length(resource_uri) > 0 AND resource_uri ~* '^https://'),
    definition_key varchar(128) NOT NULL CONSTRAINT preference_intent_definition_key CHECK (length(definition_key) > 0),
    definition_version bigint NOT NULL CONSTRAINT preference_intent_definition_version CHECK (definition_version > 0),
    definition_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT preference_intent_definition_hash CHECK (definition_sha256 ~ '^[0-9a-f]{64}$'),
    request_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT preference_intent_request_hash CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    snapshot_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT preference_intent_snapshot_hash CHECK (snapshot_sha256 ~ '^[0-9a-f]{64}$'),
    execution_policy_ref varchar(256) NOT NULL CONSTRAINT preference_intent_execution_policy CHECK (length(execution_policy_ref) > 0),
    session_required boolean NOT NULL,
    run_deadline_at timestamptz NOT NULL,
    deadline_at timestamptz NOT NULL,
    binding_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT preference_intent_fingerprint CHECK (binding_sha256 ~ '^[0-9a-f]{64}$'),
    issue_epoch bigint NOT NULL CONSTRAINT preference_intent_issue_epoch CHECK (issue_epoch > 0),
    grant_id varchar(63) COLLATE "C",
    ciphertext text,
    grant_expires_at timestamptz,
    state varchar(16) COLLATE "C" NOT NULL CONSTRAINT preference_intent_state CHECK (state = ANY (ARRAY['RESERVED', 'READY', 'CLAIMED', 'APPLIED', 'SKIPPED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DEAD'])),
    revision bigint NOT NULL CONSTRAINT preference_intent_revision CHECK (revision > 0),
    source_event_id varchar(64) COLLATE "C",
    lease_owner varchar(128) COLLATE "C",
    lease_epoch bigint NOT NULL DEFAULT 0 CONSTRAINT preference_intent_lease_epoch CHECK (lease_epoch >= 0),
    lease_expires_at timestamptz,
    next_index integer NOT NULL DEFAULT 0,
    total_count integer,
    commands_sha256 char(64) COLLATE "C",
    attempt_count integer NOT NULL DEFAULT 0,
    maximum_attempts integer NOT NULL CONSTRAINT preference_intent_attempt_limit CHECK (maximum_attempts >= 1 AND maximum_attempts <= 100),
    next_attempt_at timestamptz NOT NULL,
    last_error_code varchar(64) CONSTRAINT preference_intent_error CHECK (last_error_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    cancel_command_id varchar(128) COLLATE "C",
    cancel_request_sha256 char(64) COLLATE "C",
    cancelled_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    closed_at timestamptz,
    PRIMARY KEY (intent_id),
    CONSTRAINT preference_intent_source_fk FOREIGN KEY (tenant_id, run_id) REFERENCES tianji_agent.agent_run (tenant_id, run_id),
    CONSTRAINT preference_intent_natural_key UNIQUE (tenant_id, actor_issuer, actor_type, actor_id, run_id, scope_id),
    CONSTRAINT preference_intent_dates CHECK (deadline_at > created_at AND updated_at >= created_at AND (closed_at IS NULL OR closed_at >= created_at)),
    CONSTRAINT preference_intent_source_event CHECK ((source_event_id IS NULL OR length(source_event_id) > 0)
        AND (state <> ALL (ARRAY['CLAIMED', 'APPLIED']) OR source_event_id IS NOT NULL)
        AND (next_index = 0 OR source_event_id IS NOT NULL)),
    CONSTRAINT preference_intent_grant CHECK (
        (state = ANY (ARRAY['READY', 'CLAIMED']) AND grant_id IS NOT NULL AND grant_id ~ '^9[0-9]{62}$'
            AND ciphertext IS NOT NULL AND ciphertext ~ '^ENC:v1:[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$'
            AND octet_length(ciphertext) <= 32768 AND grant_expires_at IS NOT NULL AND grant_expires_at <= deadline_at)
        OR (state = ANY (ARRAY['RESERVED', 'APPLIED', 'SKIPPED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DEAD']) AND grant_id IS NULL AND ciphertext IS NULL AND grant_expires_at IS NULL)),
    CONSTRAINT preference_intent_lease CHECK (
        (state = 'CLAIMED' AND lease_owner IS NOT NULL AND length(lease_owner) > 0 AND lease_epoch > 0
            AND lease_expires_at IS NOT NULL AND lease_expires_at <= grant_expires_at)
        OR (state <> 'CLAIMED' AND lease_owner IS NULL AND lease_expires_at IS NULL)),
    CONSTRAINT preference_intent_progress CHECK (next_index >= 0 AND next_index <= 32
        AND ((total_count IS NULL AND commands_sha256 IS NULL AND next_index = 0)
            OR (total_count IS NOT NULL AND total_count >= 0 AND total_count <= 32 AND next_index <= total_count
                AND commands_sha256 IS NOT NULL AND commands_sha256 ~ '^[0-9a-f]{64}$'))),
    CONSTRAINT preference_intent_applied CHECK (state <> 'APPLIED' OR (total_count IS NOT NULL AND total_count > 0 AND next_index = total_count)),
    CONSTRAINT preference_intent_attempts CHECK (attempt_count >= 0 AND attempt_count <= maximum_attempts),
    CONSTRAINT preference_intent_closed CHECK ((state = ANY (ARRAY['APPLIED', 'SKIPPED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DEAD'])) = (closed_at IS NOT NULL)),
    CONSTRAINT preference_intent_cancel CHECK (
        (state = 'CANCELLED' AND cancel_command_id IS NOT NULL AND length(cancel_command_id) > 0 AND cancel_request_sha256 IS NOT NULL
            AND cancel_request_sha256 ~ '^[0-9a-f]{64}$' AND cancelled_at IS NOT NULL AND cancelled_at = closed_at)
        OR (state <> 'CANCELLED' AND cancel_command_id IS NULL AND cancel_request_sha256 IS NULL AND cancelled_at IS NULL))
);

CREATE INDEX agent_memory_preference_ready_idx ON tianji_agent.agent_memory_preference_intent
    (tenant_id, actor_issuer, state, next_attempt_at, intent_id) WHERE state = ANY (ARRAY['READY', 'CLAIMED']);
CREATE INDEX agent_memory_preference_deadline_idx ON tianji_agent.agent_memory_preference_intent
    (tenant_id, actor_issuer, deadline_at, intent_id) WHERE state = ANY (ARRAY['RESERVED', 'READY', 'CLAIMED']);
CREATE INDEX agent_memory_preference_grant_expiry_idx ON tianji_agent.agent_memory_preference_intent
    (tenant_id, actor_issuer, grant_expires_at, intent_id) WHERE state = ANY (ARRAY['READY', 'CLAIMED']);
