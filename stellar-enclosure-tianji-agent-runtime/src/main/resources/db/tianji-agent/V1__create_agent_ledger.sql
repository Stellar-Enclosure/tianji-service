CREATE SCHEMA IF NOT EXISTS tianji_agent;

CREATE TABLE tianji_agent.agent_definition (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    definition_key varchar(128) NOT NULL,
    active_version bigint,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, definition_key)
);

CREATE TABLE tianji_agent.agent_definition_version (
    tenant_id bigint NOT NULL,
    definition_key varchar(128) NOT NULL,
    version bigint NOT NULL CHECK (version > 0),
    content_sha256 char(64) NOT NULL,
    specification jsonb NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, definition_key, version),
    FOREIGN KEY (tenant_id, definition_key)
        REFERENCES tianji_agent.agent_definition (tenant_id, definition_key)
);

CREATE TABLE tianji_agent.agent_run (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    run_id varchar(64) NOT NULL,
    definition_key varchar(128) NOT NULL,
    definition_version bigint NOT NULL,
    subject_type varchar(16) NOT NULL CHECK (subject_type IN ('USER', 'SERVICE')),
    subject_id varchar(256) NOT NULL,
    idempotency_key varchar(128) NOT NULL,
    request_hash char(64) NOT NULL,
    session_id varchar(128),
    correlation_id varchar(128),
    status varchar(24) NOT NULL CHECK (status IN ('CREATED', 'QUEUED', 'RUNNING', 'WAITING',
        'SUSPENDED', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    wait_reason varchar(128),
    cancel_requested_at timestamptz,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    last_event_sequence bigint NOT NULL DEFAULT 0 CHECK (last_event_sequence >= 0),
    attempt integer NOT NULL DEFAULT 0 CHECK (attempt >= 0),
    lease_epoch bigint NOT NULL DEFAULT 0 CHECK (lease_epoch >= 0),
    lease_owner varchar(128),
    lease_expires_at timestamptz,
    deadline_at timestamptz NOT NULL,
    execution_snapshot jsonb NOT NULL,
    result jsonb,
    error_code varchar(80),
    created_at timestamptz NOT NULL,
    finished_at timestamptz,
    PRIMARY KEY (tenant_id, run_id),
    UNIQUE (tenant_id, subject_type, subject_id, idempotency_key),
    FOREIGN KEY (tenant_id, definition_key, definition_version)
        REFERENCES tianji_agent.agent_definition_version (tenant_id, definition_key, version)
);

CREATE INDEX agent_run_owner_created_idx
    ON tianji_agent.agent_run (tenant_id, subject_type, subject_id, created_at, run_id);
CREATE INDEX agent_run_dispatch_idx
    ON tianji_agent.agent_run (status, deadline_at, created_at)
    WHERE status = 'QUEUED';

CREATE TABLE tianji_agent.agent_event (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    sequence bigint NOT NULL CHECK (sequence > 0),
    event_id varchar(64) NOT NULL,
    event_type varchar(128) NOT NULL,
    schema_version varchar(64) NOT NULL,
    author varchar(128),
    branch varchar(128),
    tool_call_id varchar(128),
    correlation_id varchar(128),
    native_event_id varchar(128),
    payload jsonb NOT NULL,
    occurred_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, run_id, sequence),
    UNIQUE (tenant_id, event_id),
    FOREIGN KEY (tenant_id, run_id)
        REFERENCES tianji_agent.agent_run (tenant_id, run_id)
);

CREATE UNIQUE INDEX agent_event_native_id_idx
    ON tianji_agent.agent_event (tenant_id, run_id, native_event_id)
    WHERE native_event_id IS NOT NULL;
