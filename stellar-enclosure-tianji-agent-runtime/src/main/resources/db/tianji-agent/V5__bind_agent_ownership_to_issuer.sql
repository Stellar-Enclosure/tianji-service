ALTER TABLE tianji_agent.agent_run
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');
ALTER TABLE tianji_agent.agent_run ALTER COLUMN subject_id TYPE varchar(256) COLLATE "C";
ALTER TABLE tianji_agent.agent_run
    DROP CONSTRAINT agent_run_tenant_id_subject_type_subject_id_idempotency_key_key;
ALTER TABLE tianji_agent.agent_run
    ADD CONSTRAINT agent_run_issuer_idempotency_key UNIQUE (tenant_id, subject_type, subject_issuer, subject_id, idempotency_key);
CREATE INDEX agent_run_issuer_owner_created_idx
    ON tianji_agent.agent_run (tenant_id, subject_type, subject_issuer, subject_id, created_at, run_id);
CREATE INDEX agent_run_issuer_active_session_idx
    ON tianji_agent.agent_run (tenant_id, subject_type, subject_issuer, subject_id, session_id COLLATE "C")
    WHERE session_id IS NOT NULL AND status IN ('CREATED', 'QUEUED', 'RUNNING', 'WAITING', 'SUSPENDED');

ALTER TABLE tianji_agent.agent_run_command_receipt
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');
ALTER TABLE tianji_agent.agent_run_command_receipt ALTER COLUMN subject_id TYPE varchar(256) COLLATE "C";
ALTER TABLE tianji_agent.agent_run_command_receipt DROP CONSTRAINT agent_run_command_receipt_pkey;
ALTER TABLE tianji_agent.agent_run_command_receipt
    ADD CONSTRAINT agent_run_command_receipt_pkey
    PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, operation, command_id);

ALTER TABLE tianji_agent.agent_session
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');
ALTER TABLE tianji_agent.agent_session_event
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');
ALTER TABLE tianji_agent.agent_session_lease
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');
ALTER TABLE tianji_agent.agent_session_scope_state
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');

ALTER TABLE tianji_agent.agent_session_event DROP CONSTRAINT agent_session_event_tenant_id_subject_type_subject_id_app__fkey;
ALTER TABLE tianji_agent.agent_session_lease DROP CONSTRAINT agent_session_lease_tenant_id_subject_type_subject_id_app__fkey;
ALTER TABLE tianji_agent.agent_session DROP CONSTRAINT agent_session_pkey;
ALTER TABLE tianji_agent.agent_session
    ADD CONSTRAINT agent_session_pkey PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id);
CREATE INDEX agent_session_issuer_owner_updated_idx
    ON tianji_agent.agent_session (tenant_id, subject_type, subject_issuer, subject_id, app_name, updated_at, session_id);

ALTER TABLE tianji_agent.agent_session_event DROP CONSTRAINT agent_session_event_pkey;
ALTER TABLE tianji_agent.agent_session_event DROP CONSTRAINT agent_session_event_tenant_id_subject_type_subject_id_app_n_key;
ALTER TABLE tianji_agent.agent_session_event
    ADD CONSTRAINT agent_session_event_pkey
        PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, sequence),
    ADD CONSTRAINT agent_session_event_issuer_native_key
        UNIQUE (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id, native_event_id),
    ADD CONSTRAINT agent_session_event_issuer_session_fkey
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id)
        REFERENCES tianji_agent.agent_session (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id)
        ON DELETE CASCADE;

ALTER TABLE tianji_agent.agent_session_lease DROP CONSTRAINT agent_session_lease_pkey;
ALTER TABLE tianji_agent.agent_session_lease
    ADD CONSTRAINT agent_session_lease_pkey PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id),
    ADD CONSTRAINT agent_session_lease_issuer_session_fkey
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id)
        REFERENCES tianji_agent.agent_session (tenant_id, subject_type, subject_issuer, subject_id, app_name, session_id)
        ON DELETE CASCADE;

ALTER TABLE tianji_agent.agent_session_scope_state DROP CONSTRAINT agent_session_scope_state_pkey;
ALTER TABLE tianji_agent.agent_session_scope_state
    ADD CONSTRAINT agent_session_scope_state_pkey
    PRIMARY KEY (tenant_id, app_name, scope_type, subject_type, subject_issuer, subject_id, state_key),
    ADD CONSTRAINT agent_session_scope_app_issuer_check CHECK (scope_type <> 'APP' OR subject_issuer = 'LEGACY');
