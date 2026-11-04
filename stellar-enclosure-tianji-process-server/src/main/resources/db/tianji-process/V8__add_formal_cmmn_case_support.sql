-- Add widened checks under temporary names. NOT VALID checks are enforced for
-- new rows immediately; existing rows are validated explicitly before the old
-- narrow checks are removed.
ALTER TABLE tianji_process_release_definition
    ADD CONSTRAINT tianji_process_release_definition_model_type_v8
    CHECK (model_type = ANY(ARRAY['BPMN', 'CMMN', 'DMN'])) NOT VALID;

ALTER TABLE tianji_human_task_receipt
    ADD CONSTRAINT human_task_model_type_v8
    CHECK (model_type = ANY(ARRAY['BPMN', 'CMMN'])) NOT VALID;

ALTER TABLE tianji_human_task_receipt
    ADD CONSTRAINT human_task_start_command_type_v8
    CHECK (
        (model_type = 'BPMN' AND start_command_type = 'START')
        OR
        (model_type = 'CMMN'
            AND start_command_type = ANY(ARRAY['START', 'CASE_START']))
    ) NOT VALID;

ALTER TABLE tianji_human_task_receipt
    ADD CONSTRAINT human_task_case_kind
    CHECK (model_type <> 'CMMN' OR task_kind = 'ACTION') NOT VALID;

CREATE TABLE tianji_case_start_provenance (
    tenant_id bigint NOT NULL,
    case_instance_id varchar(255) COLLATE "C" NOT NULL,
    start_kind varchar(16) COLLATE "C" NOT NULL,
    business_key varchar(255) COLLATE "C" NOT NULL,
    correlation_id varchar(128) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    case_model_type varchar(8) COLLATE "C" NOT NULL,
    case_definition_key varchar(128) COLLATE "C" NOT NULL,
    case_engine_definition_id varchar(255) COLLATE "C" NOT NULL,
    case_model_sha256 char(64) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer varchar(64) COLLATE "C" NOT NULL,
    owner_subject varchar(256) COLLATE "C" NOT NULL,
    owner_start_command_type varchar(32) COLLATE "C" NOT NULL,
    owner_start_command_id varchar(128) COLLATE "C" NOT NULL,
    owner_start_request_sha256 char(64) COLLATE "C" NOT NULL,
    executor_type varchar(16) COLLATE "C" NOT NULL,
    executor_issuer varchar(64) COLLATE "C" NOT NULL,
    executor_subject varchar(256) COLLATE "C" NOT NULL,
    case_start_command_type varchar(32) COLLATE "C" NOT NULL,
    case_start_command_id varchar(128) COLLATE "C" NOT NULL,
    case_start_request_sha256 char(64) COLLATE "C" NOT NULL,
    source_id varchar(128) COLLATE "C",
    parent_process_instance_id varchar(255) COLLATE "C",
    source_occurrence_id varchar(255) COLLATE "C",
    source_element_id varchar(128) COLLATE "C",
    parent_model_type varchar(8) COLLATE "C",
    parent_definition_key varchar(128) COLLATE "C",
    parent_engine_definition_id varchar(255) COLLATE "C",
    parent_model_sha256 char(64) COLLATE "C",
    created_at timestamptz(6) NOT NULL,

    CONSTRAINT provenance_pkey
        PRIMARY KEY (tenant_id, case_instance_id),

    CONSTRAINT case_provenance_tenant_id
        CHECK (tenant_id > 0),
    CONSTRAINT case_provenance_case_instance_id
        CHECK (case_instance_id <> '' AND case_instance_id = btrim(case_instance_id)
            AND case_instance_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_provenance_start_kind
        CHECK (start_kind = ANY(ARRAY['DIRECT', 'LINKED_BPMN'])),
    CONSTRAINT case_provenance_business_key
        CHECK (business_key <> '' AND business_key = btrim(business_key)
            AND business_key !~ '[[:cntrl:]]'),
    CONSTRAINT case_provenance_correlation_id
        CHECK (correlation_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT case_provenance_release_key
        CHECK (release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_provenance_case_model_type
        CHECK (case_model_type = 'CMMN'),
    CONSTRAINT case_provenance_case_definition_key
        CHECK (case_definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_provenance_case_engine_definition_id
        CHECK (case_engine_definition_id <> ''
            AND case_engine_definition_id = btrim(case_engine_definition_id)
            AND case_engine_definition_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_provenance_case_model_sha256
        CHECK (case_model_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_provenance_owner_type
        CHECK (owner_type = ANY(ARRAY['USER', 'SERVICE'])),
    CONSTRAINT case_provenance_owner_issuer
        CHECK (owner_issuer ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_provenance_owner_subject
        CHECK (owner_subject <> '' AND owner_subject = btrim(owner_subject)
            AND owner_subject !~ '[[:cntrl:]]'),
    CONSTRAINT case_provenance_owner_start_command_type
        CHECK (owner_start_command_type = ANY(ARRAY['START', 'CASE_START'])),
    CONSTRAINT case_provenance_owner_start_command_id
        CHECK (owner_start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT case_provenance_owner_start_request_sha256
        CHECK (owner_start_request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_provenance_executor_type
        CHECK (executor_type = ANY(ARRAY['USER', 'SERVICE'])),
    CONSTRAINT case_provenance_executor_issuer
        CHECK (executor_issuer ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_provenance_executor_subject
        CHECK (executor_subject <> '' AND executor_subject = btrim(executor_subject)
            AND executor_subject !~ '[[:cntrl:]]'),
    CONSTRAINT case_provenance_case_start_command_type
        CHECK (case_start_command_type = 'CASE_START'),
    CONSTRAINT case_provenance_case_start_command_id
        CHECK (case_start_command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT case_provenance_case_start_request_sha256
        CHECK (case_start_request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_provenance_source_id
        CHECK (source_id IS NULL
            OR source_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT case_provenance_parent_process_instance_id
        CHECK (parent_process_instance_id IS NULL
            OR (parent_process_instance_id <> ''
                AND parent_process_instance_id = btrim(parent_process_instance_id)
                AND parent_process_instance_id !~ '[[:cntrl:]]')),
    CONSTRAINT case_provenance_source_occurrence_id
        CHECK (source_occurrence_id IS NULL
            OR (source_occurrence_id <> ''
                AND source_occurrence_id = btrim(source_occurrence_id)
                AND source_occurrence_id !~ '[[:cntrl:]]')),
    CONSTRAINT case_provenance_source_element_id
        CHECK (source_element_id IS NULL
            OR source_element_id ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_provenance_parent_model_type
        CHECK (parent_model_type IS NULL OR parent_model_type = 'BPMN'),
    CONSTRAINT case_provenance_parent_definition_key
        CHECK (parent_definition_key IS NULL
            OR parent_definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_provenance_parent_engine_definition_id
        CHECK (parent_engine_definition_id IS NULL
            OR (parent_engine_definition_id <> ''
                AND parent_engine_definition_id = btrim(parent_engine_definition_id)
                AND parent_engine_definition_id !~ '[[:cntrl:]]')),
    CONSTRAINT case_provenance_parent_model_sha256
        CHECK (parent_model_sha256 IS NULL
            OR parent_model_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_provenance_linked_fields
        CHECK (
            (start_kind = 'DIRECT'
                AND source_id IS NULL
                AND parent_process_instance_id IS NULL
                AND source_occurrence_id IS NULL
                AND source_element_id IS NULL
                AND parent_model_type IS NULL
                AND parent_definition_key IS NULL
                AND parent_engine_definition_id IS NULL
                AND parent_model_sha256 IS NULL)
            OR
            (start_kind = 'LINKED_BPMN'
                AND source_id IS NOT NULL
                AND parent_process_instance_id IS NOT NULL
                AND source_occurrence_id IS NOT NULL
                AND source_element_id IS NOT NULL
                AND parent_model_type IS NOT NULL
                AND parent_definition_key IS NOT NULL
                AND parent_engine_definition_id IS NOT NULL
                AND parent_model_sha256 IS NOT NULL)
        ),
    CONSTRAINT case_provenance_direct_identity
        CHECK (
            start_kind <> 'DIRECT'
            OR (
                owner_type = executor_type
                AND owner_issuer = executor_issuer
                AND owner_subject = executor_subject
                AND owner_start_command_type = 'CASE_START'
                AND owner_start_command_type = case_start_command_type
                AND owner_start_command_id = case_start_command_id
                AND owner_start_request_sha256 = case_start_request_sha256
            )
        ),
    CONSTRAINT case_provenance_linked_identity
        CHECK (
            start_kind <> 'LINKED_BPMN'
            OR (
                executor_type = 'SERVICE'
                AND owner_start_command_type = 'START'
                AND case_start_command_type = 'CASE_START'
                AND parent_model_type = 'BPMN'
            )
        ),

    CONSTRAINT case_provenance_case_definition_fkey
        FOREIGN KEY (tenant_id, release_key, case_model_type, case_definition_key)
        REFERENCES tianji_process_release_definition
            (tenant_id, release_key, model_type, definition_key)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT case_provenance_parent_definition_fkey
        FOREIGN KEY (tenant_id, release_key, parent_model_type, parent_definition_key)
        REFERENCES tianji_process_release_definition
            (tenant_id, release_key, model_type, definition_key)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT case_provenance_owner_command_fkey
        FOREIGN KEY (tenant_id, owner_type, owner_issuer, owner_subject,
                     owner_start_command_type, owner_start_command_id)
        REFERENCES tianji_process_command_receipt
            (tenant_id, subject_type, subject_issuer, subject_id,
             command_type, command_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT case_provenance_executor_command_fkey
        FOREIGN KEY (tenant_id, executor_type, executor_issuer, executor_subject,
                     case_start_command_type, case_start_command_id)
        REFERENCES tianji_process_command_receipt
            (tenant_id, subject_type, subject_issuer, subject_id,
             command_type, command_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE UNIQUE INDEX case_provenance_executor_idx
    ON tianji_case_start_provenance
       (tenant_id, executor_type, executor_issuer, executor_subject,
        case_start_command_type, case_start_command_id);

-- source_id is deliberately absent: registry/config renaming cannot admit a
-- second Case for the same exact native source invocation.
CREATE UNIQUE INDEX case_provenance_occurrence_idx
    ON tianji_case_start_provenance
       (tenant_id, parent_process_instance_id, source_occurrence_id)
    WHERE start_kind = 'LINKED_BPMN';

CREATE TABLE tianji_case_plan_item_receipt (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) COLLATE "C" NOT NULL,
    subject_issuer varchar(64) COLLATE "C" NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    command_type varchar(32) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    case_instance_id varchar(255) COLLATE "C" NOT NULL,
    plan_item_instance_id varchar(255) COLLATE "C" NOT NULL,
    plan_item_definition_key varchar(128) COLLATE "C" NOT NULL,
    action varchar(16) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    definition_key varchar(128) COLLATE "C" NOT NULL,
    previous_state varchar(16) COLLATE "C" NOT NULL,
    previous_version char(64) COLLATE "C" NOT NULL,
    resulting_state varchar(16) COLLATE "C" NOT NULL,
    resulting_version char(64) COLLATE "C" NOT NULL,
    occurred_at timestamptz(6) NOT NULL,
    model_type varchar(8) COLLATE "C" NOT NULL,
    definition_sha256 char(64) COLLATE "C" NOT NULL,
    engine_definition_id varchar(255) COLLATE "C" NOT NULL,
    authorized_role_id bigint NOT NULL,
    auth_version bigint NOT NULL,
    policy_version bigint NOT NULL,

    CONSTRAINT case_plan_receipt_pkey
        PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id,
                     command_type, command_id),
    CONSTRAINT case_plan_tenant_id
        CHECK (tenant_id > 0),
    CONSTRAINT case_plan_subject_type
        CHECK (subject_type = 'USER'),
    CONSTRAINT case_plan_subject_issuer
        CHECK (subject_issuer ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_plan_subject_id
        CHECK (subject_id <> '' AND subject_id = btrim(subject_id)
            AND subject_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_plan_command_type
        CHECK (command_type = 'CASE_PLAN_ITEM'),
    CONSTRAINT case_plan_command_id
        CHECK (command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT case_plan_request_sha256
        CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_plan_case_instance_id
        CHECK (case_instance_id <> '' AND case_instance_id = btrim(case_instance_id)
            AND case_instance_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_plan_item_instance_id
        CHECK (plan_item_instance_id <> ''
            AND plan_item_instance_id = btrim(plan_item_instance_id)
            AND plan_item_instance_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_plan_item_definition_key
        CHECK (plan_item_definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_plan_action
        CHECK (action = ANY(ARRAY['DISABLE', 'ENABLE', 'START', 'TERMINATE'])),
    CONSTRAINT case_plan_release_key
        CHECK (release_key ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_plan_definition_key
        CHECK (definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT case_plan_previous_state
        CHECK (previous_state = ANY(ARRAY[
            'AVAILABLE', 'ENABLED', 'DISABLED', 'ACTIVE', 'COMPLETED', 'TERMINATED'])),
    CONSTRAINT case_plan_previous_version
        CHECK (previous_version ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_plan_resulting_state
        CHECK (resulting_state = ANY(ARRAY[
            'AVAILABLE', 'ENABLED', 'DISABLED', 'ACTIVE', 'COMPLETED', 'TERMINATED'])),
    CONSTRAINT case_plan_resulting_version
        CHECK (resulting_version ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_plan_versions_distinct
        CHECK (previous_version <> resulting_version),
    CONSTRAINT case_plan_model_type
        CHECK (model_type = 'CMMN'),
    CONSTRAINT case_plan_definition_sha256
        CHECK (definition_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_plan_engine_definition_id
        CHECK (engine_definition_id <> ''
            AND engine_definition_id = btrim(engine_definition_id)
            AND engine_definition_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_plan_authorized_role_id
        CHECK (authorized_role_id > 0),
    CONSTRAINT case_plan_auth_version
        CHECK (auth_version >= 0),
    CONSTRAINT case_plan_policy_version
        CHECK (policy_version >= 0),
    CONSTRAINT case_plan_transition
        CHECK (
            (action = 'DISABLE'
                AND previous_state = 'ENABLED'
                AND resulting_state = 'DISABLED')
            OR
            (action = 'ENABLE'
                AND previous_state = 'DISABLED'
                AND resulting_state = 'ENABLED')
            OR
            (action = 'START'
                AND previous_state = 'ENABLED'
                AND resulting_state = 'ACTIVE')
            OR
            (action = 'TERMINATE'
                AND previous_state = 'ACTIVE'
                AND resulting_state = 'TERMINATED')
        ),

    CONSTRAINT case_plan_command_fkey
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id,
                     command_type, command_id)
        REFERENCES tianji_process_command_receipt
            (tenant_id, subject_type, subject_issuer, subject_id,
             command_type, command_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT case_plan_provenance_fkey
        FOREIGN KEY (tenant_id, case_instance_id)
        REFERENCES tianji_case_start_provenance
            (tenant_id, case_instance_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT case_plan_definition_fkey
        FOREIGN KEY (tenant_id, release_key, model_type, definition_key)
        REFERENCES tianji_process_release_definition
            (tenant_id, release_key, model_type, definition_key)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE TABLE tianji_case_completion_inbox (
    tenant_id bigint NOT NULL,
    completion_command_id varchar(128) COLLATE "C" NOT NULL,
    case_instance_id varchar(255) COLLATE "C" NOT NULL,
    service_type varchar(16) COLLATE "C" NOT NULL,
    service_issuer varchar(64) COLLATE "C" NOT NULL,
    service_subject varchar(256) COLLATE "C" NOT NULL,
    source_id varchar(128) COLLATE "C" NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL,
    terminal_status varchar(16) COLLATE "C",
    terminal_sha256 char(64) COLLATE "C",
    terminal_at timestamptz(6),
    created_at timestamptz(6) NOT NULL,
    next_attempt_at timestamptz(6) NOT NULL,
    failure_count integer NOT NULL,
    decided_at timestamptz(6),
    decision_code varchar(64) COLLATE "C",
    applied_command_type varchar(32) COLLATE "C",

    CONSTRAINT completion_inbox_pkey
        PRIMARY KEY (tenant_id, completion_command_id),
    CONSTRAINT case_completion_tenant_id
        CHECK (tenant_id > 0),
    CONSTRAINT case_completion_command_id
        CHECK (completion_command_id ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_completion_case_instance_id
        CHECK (case_instance_id <> '' AND case_instance_id = btrim(case_instance_id)
            AND case_instance_id !~ '[[:cntrl:]]'),
    CONSTRAINT case_completion_service_type
        CHECK (service_type = 'SERVICE'),
    CONSTRAINT case_completion_service_issuer
        CHECK (service_issuer ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_completion_service_subject
        CHECK (service_subject <> '' AND service_subject = btrim(service_subject)
            AND service_subject !~ '[[:cntrl:]]'),
    CONSTRAINT case_completion_source_id
        CHECK (source_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT case_completion_state
        CHECK (state = ANY(ARRAY['PENDING', 'APPLIED', 'IGNORED_LATE', 'DEAD'])),
    CONSTRAINT case_completion_terminal_status
        CHECK (terminal_status IS NULL
            OR terminal_status = ANY(ARRAY['COMPLETED', 'TERMINATED'])),
    CONSTRAINT case_completion_terminal_sha256
        CHECK (terminal_sha256 IS NULL
            OR terminal_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT case_completion_terminal_fields
        CHECK (
            (terminal_status IS NULL
                AND terminal_sha256 IS NULL
                AND terminal_at IS NULL)
            OR
            (terminal_status IS NOT NULL
                AND terminal_sha256 IS NOT NULL
                AND terminal_at IS NOT NULL)
        ),
    CONSTRAINT case_completion_failure_count
        CHECK (failure_count >= 0),
    CONSTRAINT case_completion_decision_code
        CHECK (decision_code IS NULL OR decision_code = ANY(ARRAY[
            'MESSAGE_APPLIED', 'WAIT_ENDED', 'MESSAGE_CONSUMED',
            'CASE_TERMINATED', 'RETRY_EXHAUSTED', 'SOURCE_UNAVAILABLE',
            'INCONSISTENT_NATIVE_FACT'])),
    CONSTRAINT case_completion_applied_command_type
        CHECK (applied_command_type IS NULL
            OR applied_command_type = 'CASE_COMPLETION_SERVICE'),
    CONSTRAINT case_completion_state_fields
        CHECK (
            (state = 'PENDING'
                AND decided_at IS NULL
                AND decision_code IS NULL
                AND applied_command_type IS NULL)
            OR
            (state = 'APPLIED'
                AND terminal_status IS NOT NULL
                AND terminal_status = 'COMPLETED'
                AND terminal_sha256 IS NOT NULL
                AND terminal_at IS NOT NULL
                AND decided_at IS NOT NULL
                AND decision_code IS NOT NULL
                AND decision_code = 'MESSAGE_APPLIED'
                AND applied_command_type IS NOT NULL
                AND applied_command_type = 'CASE_COMPLETION_SERVICE')
            OR
            (state = 'IGNORED_LATE'
                AND decided_at IS NOT NULL
                AND decision_code IS NOT NULL
                AND decision_code = ANY(ARRAY['WAIT_ENDED', 'MESSAGE_CONSUMED'])
                AND applied_command_type IS NULL
                AND (terminal_status IS NULL
                    OR terminal_status = 'COMPLETED'))
            OR
            (state = 'DEAD'
                AND decided_at IS NOT NULL
                AND decision_code IS NOT NULL
                AND decision_code = ANY(ARRAY[
                    'CASE_TERMINATED', 'RETRY_EXHAUSTED',
                    'SOURCE_UNAVAILABLE', 'INCONSISTENT_NATIVE_FACT'])
                AND applied_command_type IS NULL
                AND (decision_code <> 'CASE_TERMINATED'
                    OR (terminal_status IS NOT NULL
                        AND terminal_status = 'TERMINATED')))
        ),
    CONSTRAINT case_completion_terminated_not_applied
        CHECK (terminal_status IS NULL
            OR terminal_status <> 'TERMINATED'
            OR state = 'DEAD'),

    CONSTRAINT case_completion_provenance_fkey
        FOREIGN KEY (tenant_id, case_instance_id)
        REFERENCES tianji_case_start_provenance
            (tenant_id, case_instance_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT case_completion_command_fkey
        FOREIGN KEY (tenant_id, service_type, service_issuer, service_subject,
                     applied_command_type, completion_command_id)
        REFERENCES tianji_process_command_receipt
            (tenant_id, subject_type, subject_issuer, subject_id,
             command_type, command_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION
);

CREATE UNIQUE INDEX case_completion_case_idx
    ON tianji_case_completion_inbox (tenant_id, case_instance_id);

CREATE INDEX case_completion_due_idx
    ON tianji_case_completion_inbox
       (tenant_id, service_issuer, service_subject, source_id,
        next_attempt_at, completion_command_id)
    WHERE state = 'PENDING';

CREATE INDEX case_completion_orphan_due_idx
    ON tianji_case_completion_inbox
       (next_attempt_at, tenant_id, completion_command_id)
    WHERE state = 'PENDING';

-- Validate all widened checks while the old narrow checks still protect the
-- pre-V8 tables.
ALTER TABLE tianji_process_release_definition
    VALIDATE CONSTRAINT tianji_process_release_definition_model_type_v8;

ALTER TABLE tianji_human_task_receipt
    VALIDATE CONSTRAINT human_task_model_type_v8;

ALTER TABLE tianji_human_task_receipt
    VALIDATE CONSTRAINT human_task_start_command_type_v8;

ALTER TABLE tianji_human_task_receipt
    VALIDATE CONSTRAINT human_task_case_kind;

-- Source-confirmed V7 names. Preserve their final names so the version-aware
-- verifier can pin the widened expressions without changing V7 bytes.
ALTER TABLE tianji_human_task_receipt
    DROP CONSTRAINT human_task_model_type;
ALTER TABLE tianji_human_task_receipt
    DROP CONSTRAINT human_task_start_command_type;
ALTER TABLE tianji_human_task_receipt
    RENAME CONSTRAINT human_task_model_type_v8 TO human_task_model_type;
ALTER TABLE tianji_human_task_receipt
    RENAME CONSTRAINT human_task_start_command_type_v8
        TO human_task_start_command_type;

-- The dropped release constraint name is the source-derived standard PostgreSQL
-- generated name. Strict DROP is intentional; pre-deployment catalog mismatch
-- aborts rather than discovering, repairing, or silently accepting drift.
ALTER TABLE tianji_process_release_definition
    DROP CONSTRAINT tianji_process_release_definition_model_type_check;
ALTER TABLE tianji_process_release_definition
    RENAME CONSTRAINT tianji_process_release_definition_model_type_v8
        TO tianji_process_release_definition_model_type_check;
