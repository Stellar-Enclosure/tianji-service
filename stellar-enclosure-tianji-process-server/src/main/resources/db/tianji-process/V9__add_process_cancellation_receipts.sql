-- Immutable Process cancellation audit. Apply externally in the same migration transaction.
-- Native BPMN state stays owned by Flowable; linked Agent closure reuses the existing dispatch table.
CREATE TABLE tianji_process_cancel_receipt (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) COLLATE "C" NOT NULL,
    subject_issuer varchar(64) COLLATE "C" NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    command_type varchar(32) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    process_instance_id varchar(255) COLLATE "C" NOT NULL,
    start_command_type varchar(32) COLLATE "C" NOT NULL,
    start_command_id varchar(128) COLLATE "C" NOT NULL,
    start_request_sha256 char(64) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    model_type varchar(8) COLLATE "C" NOT NULL,
    definition_key varchar(128) COLLATE "C" NOT NULL,
    engine_definition_id varchar(255) COLLATE "C" NOT NULL,
    definition_sha256 char(64) COLLATE "C" NOT NULL,
    business_key varchar(255) COLLATE "C" NOT NULL,
    correlation_id varchar(128) COLLATE "C" NOT NULL,
    expected_version char(64) COLLATE "C" NOT NULL,
    reason varchar(512) COLLATE "C" NOT NULL,
    accepted boolean NOT NULL,
    result_status varchar(16) COLLATE "C" NOT NULL,
    result_version char(64) COLLATE "C" NOT NULL,
    result_finished_at timestamptz(6) NOT NULL,
    decided_at timestamptz(6) NOT NULL,
    agent_closure_scheduled boolean NOT NULL,
    CONSTRAINT tianji_process_cancel_receipt_pkey
        PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_cancel_command_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_cancel_start_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, start_command_type, start_command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_cancel_definition_fk
        FOREIGN KEY (tenant_id, release_key, model_type, definition_key)
        REFERENCES tianji_process_release_definition (tenant_id, release_key, model_type, definition_key),
    CONSTRAINT tianji_process_cancel_tenant
        CHECK (tenant_id > 0),
    CONSTRAINT tianji_process_cancel_owner
        CHECK (subject_type = ANY(ARRAY['USER', 'SERVICE'])
            AND subject_issuer ~ '^[0-9a-f]{64}$'
            AND subject_id <> '' AND subject_id = btrim(subject_id) AND subject_id !~ '[[:cntrl:]]'),
    CONSTRAINT tianji_process_cancel_kind
        CHECK (command_type = 'PROCESS_CANCEL' AND start_command_type = 'START' AND model_type = 'BPMN'),
    CONSTRAINT tianji_process_cancel_command
        CHECK (command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
            AND start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT tianji_process_cancel_hashes
        CHECK (request_sha256 ~ '^[0-9a-f]{64}$' AND start_request_sha256 ~ '^[0-9a-f]{64}$'
            AND definition_sha256 ~ '^[0-9a-f]{64}$' AND expected_version ~ '^[0-9a-f]{64}$'
            AND result_version ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tianji_process_cancel_instance
        CHECK (process_instance_id <> '' AND process_instance_id = btrim(process_instance_id)
            AND process_instance_id !~ '[[:cntrl:]]'),
    CONSTRAINT tianji_process_cancel_release
        CHECK (release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'
            AND definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT tianji_process_cancel_engine
        CHECK (engine_definition_id <> '' AND engine_definition_id = btrim(engine_definition_id)
            AND engine_definition_id !~ '[[:cntrl:]]'),
    CONSTRAINT tianji_process_cancel_business
        CHECK (business_key <> '' AND business_key = btrim(business_key) AND business_key !~ '[[:cntrl:]]'
            AND correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT tianji_process_cancel_reason
        CHECK (reason <> '' AND reason = btrim(reason) AND reason !~ '[[:cntrl:]]' AND octet_length(reason) <= 2048),
    CONSTRAINT tianji_process_cancel_result
        CHECK (result_status = ANY(ARRAY['COMPLETED', 'CANCELLED'])
            AND (NOT accepted OR result_status = 'CANCELLED') AND (NOT agent_closure_scheduled OR accepted))
);

CREATE UNIQUE INDEX tianji_process_cancel_accepted_idx
    ON tianji_process_cancel_receipt (tenant_id, process_instance_id)
    WHERE accepted;

CREATE INDEX tianji_agent_dispatch_scope_open_idx
    ON tianji_process_agent_dispatch (tenant_id, scope_type, scope_id, occurrence_id)
    WHERE state <> 'CLOSED';
