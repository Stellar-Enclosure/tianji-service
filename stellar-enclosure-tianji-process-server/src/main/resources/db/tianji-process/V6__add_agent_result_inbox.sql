CREATE TABLE tianji_process_agent_result_inbox (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    event_id char(64) COLLATE "C" NOT NULL CHECK (event_id ~ '^[0-9a-f]{64}$'),
    producer_id varchar(128) COLLATE "C" NOT NULL,
    producer_issuer char(64) COLLATE "C" NOT NULL CHECK (producer_issuer ~ '^[0-9a-f]{64}$'),
    producer_subject varchar(256) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL CHECK (owner_issuer ~ '^[0-9a-f]{64}$'),
    owner_subject varchar(256) COLLATE "C" NOT NULL,
    source_id varchar(128) COLLATE "C" NOT NULL,
    scope_type varchar(8) NOT NULL CHECK (scope_type = 'BPMN'),
    scope_id varchar(255) COLLATE "C" NOT NULL,
    occurrence_id varchar(255) COLLATE "C" NOT NULL,
    run_id varchar(128) COLLATE "C" NOT NULL,
    terminal_revision bigint NOT NULL CHECK (terminal_revision > 0),
    event_sha256 char(64) COLLATE "C" NOT NULL CHECK (event_sha256 ~ '^[0-9a-f]{64}$'),
    event_payload jsonb NOT NULL CHECK (jsonb_typeof(event_payload) = 'object' AND octet_length(event_payload::text) <= 65536),
    state varchar(16) NOT NULL CHECK (state IN ('PENDING', 'APPLIED', 'IGNORED_LATE', 'DEAD')),
    received_at timestamptz NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    retry_not_after timestamptz NOT NULL,
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 1000),
    decided_at timestamptz,
    decision_code varchar(64) COLLATE "C",
    PRIMARY KEY (tenant_id, event_id),
    UNIQUE (tenant_id, owner_issuer, owner_subject, run_id),
    FOREIGN KEY (tenant_id, source_id, scope_type, scope_id, occurrence_id)
        REFERENCES tianji_process_agent_dispatch (tenant_id, source_id, scope_type, scope_id, occurrence_id),
    CONSTRAINT tianji_agent_inbox_retry_bounds_check CHECK (
        retry_not_after >= received_at + interval '1 minute' AND retry_not_after <= received_at + interval '720 hours'),
    CONSTRAINT tianji_agent_inbox_state_fields_check CHECK (
        (state = 'PENDING' AND decided_at IS NULL AND decision_code IS NULL)
        OR (state IN ('APPLIED', 'IGNORED_LATE', 'DEAD') AND decided_at IS NOT NULL AND decision_code IS NOT NULL)
    )
);

CREATE INDEX tianji_agent_result_inbox_due_idx
    ON tianji_process_agent_result_inbox (tenant_id, producer_issuer, producer_subject, producer_id, source_id, next_attempt_at, event_id)
    WHERE state = 'PENDING';
