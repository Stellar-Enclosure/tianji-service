CREATE TABLE tianji_process_agent_dispatch (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    source_id varchar(128) COLLATE "C" NOT NULL,
    scope_type varchar(8) NOT NULL CHECK (scope_type = 'BPMN'),
    scope_id varchar(255) COLLATE "C" NOT NULL,
    occurrence_id varchar(255) COLLATE "C" NOT NULL,
    subject_issuer char(64) COLLATE "C" NOT NULL CHECK (subject_issuer ~ '^[0-9a-f]{64}$'),
    subject_id varchar(256) COLLATE "C" NOT NULL,
    idempotency_key varchar(128) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    process_definition_id varchar(255) COLLATE "C" NOT NULL,
    process_model_sha256 char(64) COLLATE "C" NOT NULL CHECK (process_model_sha256 ~ '^[0-9a-f]{64}$'),
    element_id varchar(128) COLLATE "C" NOT NULL,
    topic varchar(128) COLLATE "C" NOT NULL,
    schema_version varchar(128) COLLATE "C" NOT NULL,
    worker_input_sha256 char(64) COLLATE "C" NOT NULL CHECK (worker_input_sha256 ~ '^[0-9a-f]{64}$'),
    seed_sha256 char(64) COLLATE "C" NOT NULL CHECK (seed_sha256 ~ '^[0-9a-f]{64}$'),
    agent_definition_key varchar(128) COLLATE "C" NOT NULL,
    agent_definition_version bigint NOT NULL CHECK (agent_definition_version > 0),
    agent_definition_sha256 char(64) COLLATE "C" NOT NULL CHECK (agent_definition_sha256 ~ '^[0-9a-f]{64}$'),
    instruction text NOT NULL CHECK (octet_length(instruction) <= 65536),
    input_data jsonb NOT NULL CHECK (jsonb_typeof(input_data) = 'object' AND octet_length(input_data::text) <= 131072),
    business_key varchar(256) COLLATE "C" NOT NULL,
    correlation_id varchar(128) COLLATE "C" NOT NULL,
    submission_timeout_ms bigint NOT NULL CHECK (submission_timeout_ms BETWEEN 1000 AND 900000),
    dispatch_not_after timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    next_check_at timestamptz NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('PENDING', 'SUBMITTED', 'ACKNOWLEDGED', 'CLOSING', 'CLOSED')),
    run_id varchar(128) COLLATE "C",
    closed_at timestamptz,
    PRIMARY KEY (tenant_id, source_id, scope_type, scope_id, occurrence_id),
    UNIQUE (tenant_id, occurrence_id),
    UNIQUE (tenant_id, subject_issuer, subject_id, idempotency_key),
    UNIQUE (tenant_id, subject_issuer, subject_id, run_id),
    FOREIGN KEY (tenant_id, release_key) REFERENCES tianji_process_release (tenant_id, release_key),
    CONSTRAINT tianji_agent_dispatch_deadline_check CHECK (
        dispatch_not_after = created_at + submission_timeout_ms * interval '1 millisecond'),
    CONSTRAINT tianji_agent_dispatch_state_check CHECK (
        (state = 'PENDING' AND run_id IS NULL AND closed_at IS NULL)
        OR (state IN ('SUBMITTED', 'ACKNOWLEDGED') AND run_id IS NOT NULL AND closed_at IS NULL)
        OR (state = 'CLOSING' AND closed_at IS NULL)
        OR (state = 'CLOSED' AND closed_at IS NOT NULL)
    )
);

CREATE INDEX tianji_agent_dispatch_due_idx
    ON tianji_process_agent_dispatch (tenant_id, subject_issuer, subject_id, source_id, next_check_at, occurrence_id)
    WHERE state <> 'CLOSED';
