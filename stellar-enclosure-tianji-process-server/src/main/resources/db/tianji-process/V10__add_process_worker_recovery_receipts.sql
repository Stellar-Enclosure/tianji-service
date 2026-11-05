-- Immutable Process Worker recovery audit. Apply externally in the owned Process schema.
-- A row records one accepted native retry scheduling decision and its admitted evidence.
CREATE TABLE tianji_process_worker_recovery_receipt (
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
    job_id varchar(255) COLLATE "C" NOT NULL,
    occurrence_id varchar(255) COLLATE "C" NOT NULL,
    execution_id varchar(255) COLLATE "C" NOT NULL,
    element_id varchar(128) COLLATE "C" NOT NULL,
    deadletter_revision integer NOT NULL,
    expected_version char(64) COLLATE "C" NOT NULL,
    evidence_sha256 char(64) COLLATE "C" NOT NULL,
    admission_policy varchar(32) COLLATE "C" NOT NULL,
    failure_code varchar(64) COLLATE "C" NOT NULL,
    failure_decided_at timestamptz(6) NOT NULL,
    reason varchar(512) COLLATE "C" NOT NULL,
    result_status varchar(24) COLLATE "C" NOT NULL,
    retries integer NOT NULL,
    decided_at timestamptz(6) NOT NULL,
    CONSTRAINT tianji_process_worker_recovery_receipt_pkey
        PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_worker_recovery_command_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_worker_recovery_start_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, start_command_type, start_command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_process_worker_recovery_definition_fk
        FOREIGN KEY (tenant_id, release_key, model_type, definition_key)
        REFERENCES tianji_process_release_definition (tenant_id, release_key, model_type, definition_key),
    CONSTRAINT tianji_process_worker_recovery_tenant
        CHECK (tenant_id > 0),
    CONSTRAINT tianji_process_worker_recovery_owner
        CHECK (subject_type = ANY(ARRAY['USER', 'SERVICE'])
            AND subject_issuer ~ '^[0-9a-f]{64}$'
            AND subject_id <> '' AND subject_id = btrim(subject_id) AND subject_id !~ '[[:cntrl:]]'),
    CONSTRAINT tianji_process_worker_recovery_kind
        CHECK (command_type = 'PROCESS_WORKER_RECOVER' AND start_command_type = 'START' AND model_type = 'BPMN'),
    CONSTRAINT tianji_process_worker_recovery_command
        CHECK (command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
            AND start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
            AND correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT tianji_process_worker_recovery_hashes
        CHECK (request_sha256 ~ '^[0-9a-f]{64}$' AND start_request_sha256 ~ '^[0-9a-f]{64}$'
            AND definition_sha256 ~ '^[0-9a-f]{64}$' AND expected_version ~ '^[0-9a-f]{64}$'
            AND evidence_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tianji_process_worker_recovery_release
        CHECK (release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'
            AND definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT tianji_process_worker_recovery_instance
        CHECK (process_instance_id <> '' AND process_instance_id = btrim(process_instance_id)
            AND process_instance_id !~ '[[:cntrl:]]' AND process_instance_id <> '.' AND process_instance_id <> '..'),
    CONSTRAINT tianji_process_worker_recovery_affinity
        CHECK (engine_definition_id <> '' AND engine_definition_id = btrim(engine_definition_id)
            AND engine_definition_id !~ '[[:cntrl:]]'
            AND business_key <> '' AND business_key = btrim(business_key) AND business_key !~ '[[:cntrl:]]'
            AND job_id <> '' AND job_id = btrim(job_id) AND job_id !~ '[[:cntrl:]]' AND char_length(job_id) <= 64
            AND occurrence_id = job_id
            AND execution_id <> '' AND execution_id = btrim(execution_id) AND execution_id !~ '[[:cntrl:]]'
            AND element_id <> '' AND element_id = btrim(element_id) AND element_id !~ '[[:cntrl:]]'
            AND deadletter_revision > 0),
    CONSTRAINT tianji_process_worker_recovery_evidence
        CHECK (admission_policy = 'READ_ONLY_EXHAUSTED_RETRY_V1' AND failure_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT tianji_process_worker_recovery_reason
        CHECK (reason <> '' AND reason = btrim(reason) AND octet_length(reason) <= 2048
            AND translate(reason,
                chr(1) || chr(2) || chr(3) || chr(4) || chr(5) || chr(6) || chr(7) || chr(8)
                || chr(9) || chr(10) || chr(11) || chr(12) || chr(13) || chr(14) || chr(15) || chr(16)
                || chr(17) || chr(18) || chr(19) || chr(20) || chr(21) || chr(22) || chr(23) || chr(24)
                || chr(25) || chr(26) || chr(27) || chr(28) || chr(29) || chr(30) || chr(31) || chr(127), '') = reason),
    CONSTRAINT tianji_process_worker_recovery_result
        CHECK (result_status = 'RETRY_SCHEDULED' AND retries = 1)
);

CREATE UNIQUE INDEX tianji_process_worker_recovery_job_uk
    ON tianji_process_worker_recovery_receipt (job_id);
