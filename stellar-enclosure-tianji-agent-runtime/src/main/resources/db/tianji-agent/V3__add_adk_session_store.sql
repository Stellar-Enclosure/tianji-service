CREATE TABLE tianji_agent.agent_session (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    subject_type varchar(16) NOT NULL CHECK (subject_type IN ('USER', 'SERVICE')),
    subject_id varchar(256) COLLATE "C" NOT NULL,
    app_name varchar(128) COLLATE "C" NOT NULL,
    session_id varchar(128) COLLATE "C" NOT NULL,
    session_state jsonb NOT NULL DEFAULT '{}'::jsonb,
    revision bigint NOT NULL DEFAULT 0 CHECK (revision >= 0),
    last_event_sequence bigint NOT NULL DEFAULT 0 CHECK (last_event_sequence >= 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, subject_type, subject_id, app_name, session_id)
);

CREATE TABLE tianji_agent.agent_session_event (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    app_name varchar(128) COLLATE "C" NOT NULL,
    session_id varchar(128) COLLATE "C" NOT NULL,
    sequence bigint NOT NULL CHECK (sequence > 0),
    native_event_id varchar(128) COLLATE "C" NOT NULL,
    event_timestamp_ms bigint NOT NULL,
    payload text NOT NULL CHECK (octet_length(payload) <= 1048576),
    PRIMARY KEY (tenant_id, subject_type, subject_id, app_name, session_id, sequence),
    UNIQUE (tenant_id, subject_type, subject_id, app_name, session_id, native_event_id),
    FOREIGN KEY (tenant_id, subject_type, subject_id, app_name, session_id)
        REFERENCES tianji_agent.agent_session (tenant_id, subject_type, subject_id, app_name, session_id)
        ON DELETE CASCADE
);

CREATE TABLE tianji_agent.agent_session_scope_state (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    app_name varchar(128) COLLATE "C" NOT NULL,
    scope_type varchar(8) NOT NULL CHECK (scope_type IN ('APP', 'USER')),
    subject_type varchar(16) NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    state_key varchar(256) COLLATE "C" NOT NULL,
    state_value jsonb NOT NULL,
    CHECK ((scope_type = 'APP' AND subject_type = '' AND subject_id = '')
        OR (scope_type = 'USER' AND subject_type IN ('USER', 'SERVICE') AND subject_id <> '')),
    PRIMARY KEY (tenant_id, app_name, scope_type, subject_type, subject_id, state_key)
);

CREATE TABLE tianji_agent.agent_session_lease (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    app_name varchar(128) COLLATE "C" NOT NULL,
    session_id varchar(128) COLLATE "C" NOT NULL,
    lease_epoch bigint NOT NULL DEFAULT 0 CHECK (lease_epoch >= 0),
    lease_owner varchar(64),
    lease_expires_at timestamptz,
    CHECK ((lease_owner IS NULL) = (lease_expires_at IS NULL)),
    PRIMARY KEY (tenant_id, subject_type, subject_id, app_name, session_id),
    FOREIGN KEY (tenant_id, subject_type, subject_id, app_name, session_id)
        REFERENCES tianji_agent.agent_session (tenant_id, subject_type, subject_id, app_name, session_id)
        ON DELETE CASCADE
);

CREATE INDEX agent_session_owner_updated_idx
    ON tianji_agent.agent_session (tenant_id, subject_type, subject_id, app_name, updated_at, session_id);

CREATE INDEX agent_run_active_session_idx
    ON tianji_agent.agent_run (tenant_id, subject_type, subject_id COLLATE "C", session_id COLLATE "C")
    WHERE session_id IS NOT NULL AND status IN ('CREATED', 'QUEUED', 'RUNNING', 'WAITING', 'SUSPENDED');
