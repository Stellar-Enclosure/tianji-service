CREATE TABLE tianji_agent.agent_checkpoint (
    tenant_id bigint NOT NULL,
    run_id varchar(64) COLLATE "C" NOT NULL,
    attempt integer NOT NULL,
    checkpoint_no integer NOT NULL,
    subject_type varchar(16) COLLATE "C" NOT NULL,
    subject_issuer varchar(64) COLLATE "C" NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    app_name varchar(128) COLLATE "C" NOT NULL,
    session_id varchar(128) COLLATE "C" NOT NULL,
    run_lease_epoch bigint NOT NULL,
    run_revision bigint NOT NULL,
    run_event_sequence bigint NOT NULL,
    session_lease_epoch bigint NOT NULL,
    prior_session_revision bigint NOT NULL,
    session_revision bigint NOT NULL,
    prior_session_event_sequence bigint NOT NULL,
    session_event_sequence bigint NOT NULL,
    native_invocation_id varchar(128) COLLATE "C" NOT NULL,
    native_event_id varchar(128) COLLATE "C" NOT NULL,
    definition_key varchar(128) COLLATE "C" NOT NULL,
    definition_version bigint NOT NULL,
    definition_sha256 char(64) COLLATE "C" NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    snapshot_sha256 char(64) COLLATE "C" NOT NULL,
    policy_version_ref varchar(256) COLLATE "C" NOT NULL,
    engine_version varchar(32) COLLATE "C" NOT NULL,
    safe_boundary varchar(64) COLLATE "C" NOT NULL,
    tool_receipts jsonb NOT NULL,
    captured_at timestamptz(6) NOT NULL,
    CONSTRAINT agent_checkpoint_pkey PRIMARY KEY (tenant_id, run_id, attempt, checkpoint_no),
    CONSTRAINT agent_checkpoint_run_fkey FOREIGN KEY (tenant_id, run_id)
        REFERENCES tianji_agent.agent_run (tenant_id, run_id) ON DELETE CASCADE,
    CONSTRAINT agent_checkpoint_session_event_fkey
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_event_sequence)
        REFERENCES tianji_agent.agent_session_event (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, sequence)
        ON DELETE CASCADE,
    CONSTRAINT agent_checkpoint_run_scope CHECK (tenant_id > 0 AND length(run_id) > 0 AND attempt > 0 AND checkpoint_no > 0
        AND run_lease_epoch > 0 AND run_revision > 0 AND run_event_sequence > 0),
    CONSTRAINT agent_checkpoint_owner CHECK (subject_type = 'USER' AND subject_issuer ~ '^[0-9a-f]{64}$'
        AND length(subject_id) > 0 AND app_name = 'tianji' AND length(session_id) > 0),
    CONSTRAINT agent_checkpoint_session_progress CHECK (session_lease_epoch > 0
        AND prior_session_revision >= 0 AND session_revision > prior_session_revision
        AND session_revision - prior_session_revision = 1
        AND prior_session_event_sequence >= 0 AND session_event_sequence > prior_session_event_sequence
        AND session_event_sequence - prior_session_event_sequence = 1),
    CONSTRAINT agent_checkpoint_native_ids CHECK (length(native_invocation_id) > 0 AND length(native_event_id) > 0),
    CONSTRAINT agent_checkpoint_definition CHECK (length(definition_key) > 0 AND definition_version > 0
        AND definition_sha256 ~ '^[0-9a-f]{64}$' AND request_sha256 ~ '^[0-9a-f]{64}$'
        AND snapshot_sha256 ~ '^[0-9a-f]{64}$' AND length(policy_version_ref) > 0),
    CONSTRAINT agent_checkpoint_engine_boundary CHECK (engine_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'
        AND safe_boundary ~ '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT agent_checkpoint_tool_receipts CHECK (CASE WHEN jsonb_typeof(tool_receipts) = 'array'
        THEN jsonb_array_length(tool_receipts) <= 64 AND octet_length(tool_receipts::text) <= 16384 ELSE false END),
    CONSTRAINT agent_checkpoint_first_input CHECK (CASE WHEN safe_boundary = 'FIRST_DURABLE_INPUT_V1'
        THEN attempt = 1 AND checkpoint_no = 1 AND prior_session_revision = 0
        AND prior_session_event_sequence = 0 AND tool_receipts = '[]'::jsonb ELSE true END),
    CONSTRAINT agent_checkpoint_capture_time CHECK (isfinite(captured_at))
);

CREATE INDEX agent_checkpoint_session_event_idx ON tianji_agent.agent_checkpoint
    (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, session_event_sequence);
