-- Process automation technical storage. Apply externally in the existing Process Flyway history.
CREATE TABLE tianji_process_automation_release (
    tenant_id bigint NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    root_definition_key varchar(128) COLLATE "C" NOT NULL,
    source_descriptor jsonb NOT NULL,
    source_sha256 char(64) COLLATE "C" NOT NULL,
    compiler_version varchar(64) COLLATE "C" NOT NULL,
    compiled_sha256 char(64) COLLATE "C" NOT NULL,
    created_at timestamptz NOT NULL,
    source_draft_id varchar(128) COLLATE "C" NOT NULL,
    source_draft_revision bigint NOT NULL,
    draft_content_sha256 char(64) COLLATE "C" NOT NULL,
    dependency_manifest jsonb NOT NULL,
    dependency_sha256 char(64) COLLATE "C" NOT NULL,
    CONSTRAINT tianji_process_automation_release_pkey PRIMARY KEY (tenant_id,release_key),
    CONSTRAINT automation_release_identity_check CHECK (
        tenant_id>0 AND release_key<>'' AND root_definition_key<>'' AND compiler_version<>'' AND source_draft_id<>''
        AND source_draft_revision>=1 AND source_draft_revision<='999999999999999'::bigint
        AND source_sha256 ~ '^[0-9a-f]{64}$' AND compiled_sha256 ~ '^[0-9a-f]{64}$'
        AND draft_content_sha256 ~ '^[0-9a-f]{64}$' AND dependency_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT automation_release_json_check CHECK (
        jsonb_typeof(source_descriptor)='object' AND octet_length(source_descriptor::text)<=4194304
        AND jsonb_typeof(dependency_manifest)='object' AND octet_length(dependency_manifest::text)<=262144
    )
);

CREATE TABLE tianji_process_draft (
    tenant_id bigint NOT NULL,
    draft_id varchar(128) COLLATE "C" NOT NULL,
    definition_key varchar(128) COLLATE "C" NOT NULL,
    title varchar(128) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    base_release_key varchar(128) COLLATE "C",
    base_source_sha256 char(64) COLLATE "C",
    state varchar(16) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    content_json jsonb NOT NULL,
    content_sha256 char(64) COLLATE "C" NOT NULL,
    published_release_key varchar(128) COLLATE "C",
    published_revision bigint,
    publish_command_id varchar(128) COLLATE "C",
    published_at timestamptz(6),
    last_command_id varchar(128) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    CONSTRAINT process_draft_pkey PRIMARY KEY (tenant_id,draft_id),
    CONSTRAINT process_draft_identity_check CHECK (
        tenant_id>0 AND draft_id ~ '^pd[A-Za-z0-9_.:-]{0,126}$' AND definition_key<>'' AND btrim(title)<>'' AND title !~ '[[:cntrl:]]'
        AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$'
        AND owner_id<>'' AND last_command_id<>'' AND content_sha256 ~ '^[0-9a-f]{64}$'
        AND revision>=1 AND revision<='999999999999999'::bigint
    ),
    CONSTRAINT process_draft_base_check CHECK (
        (base_release_key IS NULL AND base_source_sha256 IS NULL)
        OR (base_release_key IS NOT NULL AND base_release_key<>'' AND base_source_sha256 IS NOT NULL
            AND base_source_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT process_draft_state_check CHECK (
        (state = ANY (ARRAY['EDITING','DISCARDED']) AND published_release_key IS NULL AND published_revision IS NULL
            AND publish_command_id IS NULL AND published_at IS NULL)
        OR (state='PUBLISHED' AND published_release_key IS NOT NULL AND published_release_key<>''
            AND published_revision IS NOT NULL AND published_revision>0 AND published_revision=revision-1
            AND publish_command_id IS NOT NULL AND publish_command_id=last_command_id
            AND published_at IS NOT NULL AND published_at=updated_at)
    ),
    CONSTRAINT process_draft_content_check CHECK (
        updated_at>=created_at AND jsonb_typeof(content_json)='object' AND octet_length(content_json::text)<=5242880
        AND content_json ?& ARRAY['models','inputSchemaSource','outputSchemaSource','nodeBindingsSource','eventRoutesSource']
        AND content_json - ARRAY['models','inputSchemaSource','outputSchemaSource','nodeBindingsSource','eventRoutesSource']='{}'::jsonb
        AND jsonb_typeof(content_json->'models') = ANY (ARRAY['array','null']) AND jsonb_typeof(content_json->'inputSchemaSource') = ANY (ARRAY['string','null'])
        AND jsonb_typeof(content_json->'outputSchemaSource') = ANY (ARRAY['string','null']) AND jsonb_typeof(content_json->'nodeBindingsSource') = ANY (ARRAY['string','null'])
        AND jsonb_typeof(content_json->'eventRoutesSource') = ANY (ARRAY['string','null'])
    )
);

CREATE TABLE tianji_process_draft_command (
    tenant_id bigint NOT NULL,
    actor_type varchar(16) COLLATE "C" NOT NULL,
    actor_issuer char(64) COLLATE "C" NOT NULL,
    actor_id varchar(256) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    operation varchar(16) COLLATE "C" NOT NULL,
    draft_id varchar(128) COLLATE "C" NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    result_json jsonb NOT NULL,
    completed_at timestamptz(6) NOT NULL,
    CONSTRAINT process_draft_command_pkey PRIMARY KEY (tenant_id,actor_type,actor_issuer,actor_id,command_id),
    CONSTRAINT process_draft_command_identity_check CHECK (
        tenant_id>0 AND actor_type = ANY (ARRAY['USER','SERVICE']) AND actor_issuer ~ '^[0-9a-f]{64}$'
        AND actor_id<>'' AND command_id<>'' AND request_sha256 ~ '^[0-9a-f]{64}$'
        AND operation = ANY (ARRAY['CREATE','SAVE','DISCARD','PUBLISH'])
    ),
    CONSTRAINT process_draft_command_result_check CHECK (
        jsonb_typeof(result_json)='object' AND octet_length(result_json::text)<=8192
    )
);

CREATE TABLE tianji_process_automation_activation (
    tenant_id bigint NOT NULL,
    definition_key varchar(128) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    previous_revision bigint,
    actor_type varchar(16) COLLATE "C" NOT NULL,
    actor_issuer char(64) COLLATE "C" NOT NULL,
    actor_id varchar(256) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    source_sha256 char(64) COLLATE "C" NOT NULL,
    compiled_sha256 char(64) COLLATE "C" NOT NULL,
    dependency_sha256 char(64) COLLATE "C" NOT NULL,
    authorization_sha256 char(64) COLLATE "C" NOT NULL,
    dependency_evidence_sha256 char(64) COLLATE "C" NOT NULL,
    release_signature_sha256 char(64) COLLATE "C" NOT NULL,
    trust_generation bigint NOT NULL,
    admission_proof jsonb NOT NULL,
    observed_at timestamptz(6) NOT NULL,
    proof_valid_until timestamptz(6) NOT NULL,
    activated_at timestamptz(6) NOT NULL,
    CONSTRAINT automation_activation_pkey PRIMARY KEY (tenant_id,definition_key,revision),
    CONSTRAINT automation_activation_identity_check CHECK (
        tenant_id>0 AND definition_key<>'' AND release_key<>'' AND actor_type='USER'
        AND actor_issuer ~ '^[0-9a-f]{64}$' AND actor_id<>'' AND command_id<>''
        AND revision>=1 AND revision<='999999999999999'::bigint AND trust_generation>0
        AND request_sha256 ~ '^[0-9a-f]{64}$' AND source_sha256 ~ '^[0-9a-f]{64}$'
        AND compiled_sha256 ~ '^[0-9a-f]{64}$' AND dependency_sha256 ~ '^[0-9a-f]{64}$'
        AND authorization_sha256 ~ '^[0-9a-f]{64}$' AND dependency_evidence_sha256 ~ '^[0-9a-f]{64}$'
        AND release_signature_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT automation_activation_previous_check CHECK (
        (revision=1 AND previous_revision IS NULL)
        OR (revision>1 AND previous_revision IS NOT NULL AND previous_revision=revision-1)
    ),
    CONSTRAINT automation_activation_proof_check CHECK (
        jsonb_typeof(admission_proof)='object' AND octet_length(admission_proof::text)<=65536
        AND observed_at<=activated_at AND proof_valid_until>activated_at
    )
);

CREATE TABLE tianji_process_instance_binding (
    tenant_id bigint NOT NULL,
    instance_id varchar(255) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    definition_key varchar(128) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer varchar(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    profile varchar(16) COLLATE "C" NOT NULL,
    configuration jsonb NOT NULL,
    configuration_sha256 char(64) COLLATE "C" NOT NULL,
    start_request_sha256 char(64) COLLATE "C" NOT NULL,
    created_at timestamptz NOT NULL,
    activation_revision bigint NOT NULL,
    source_command_type varchar(32) COLLATE "C" NOT NULL,
    source_command_id varchar(128) COLLATE "C" NOT NULL,
    resolved_start_sha256 char(64) COLLATE "C" NOT NULL,
    admission_kind varchar(8) COLLATE "C" NOT NULL,
    source_event_receipt_id varchar(128) COLLATE "C",
    admission_evidence jsonb NOT NULL,
    admitted_at timestamptz(6) NOT NULL,
    CONSTRAINT tianji_process_instance_binding_pkey PRIMARY KEY (tenant_id,instance_id),
    CONSTRAINT instance_binding_identity_check CHECK (
        tenant_id>0 AND instance_id<>'' AND release_key<>'' AND definition_key<>'' AND profile='AUTOMATION_V2'
        AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$' AND owner_id<>''
        AND configuration_sha256 ~ '^[0-9a-f]{64}$' AND start_request_sha256 ~ '^[0-9a-f]{64}$'
        AND resolved_start_sha256 ~ '^[0-9a-f]{64}$'
        AND activation_revision>0 AND source_command_type='START' AND source_command_id<>''
    ),
    CONSTRAINT instance_binding_admission_check CHECK (
        ((admission_kind='DIRECT' AND source_event_receipt_id IS NULL)
            OR (admission_kind='EVENT' AND source_event_receipt_id IS NOT NULL AND source_event_receipt_id<>''))
        AND jsonb_typeof(admission_evidence)='object' AND octet_length(admission_evidence::text)<=65536
        AND admitted_at<=created_at
    ),
    CONSTRAINT instance_binding_configuration_check CHECK (
        jsonb_typeof(configuration)='object' AND octet_length(configuration::text)<=262144
    )
);

CREATE TABLE tianji_process_node_execution (
    tenant_id bigint NOT NULL,
    instance_id varchar(255) COLLATE "C" NOT NULL,
    occurrence_id varchar(128) COLLATE "C" NOT NULL,
    attempt integer NOT NULL,
    node_execution_ref varchar(128) COLLATE "C" NOT NULL,
    node_id varchar(255) COLLATE "C" NOT NULL,
    kind varchar(32) COLLATE "C" NOT NULL,
    parent_scope_id varchar(255) COLLATE "C" NOT NULL,
    attempt_scope_id varchar(255) COLLATE "C" NOT NULL,
    branch_index integer,
    iteration_index integer,
    excluded boolean NOT NULL DEFAULT false,
    native_job_id varchar(255) COLLATE "C",
    native_subscription_id varchar(255) COLLATE "C",
    capability_key varchar(128) COLLATE "C",
    capability_version bigint,
    effect_key varchar(128) COLLATE "C",
    input_json jsonb NOT NULL,
    input_sha256 char(64) COLLATE "C" NOT NULL,
    input_schema_sha256 char(64) COLLATE "C" NOT NULL,
    output_schema_sha256 char(64) COLLATE "C" NOT NULL,
    configuration_sha256 char(64) COLLATE "C" NOT NULL,
    state varchar(32) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    output_json jsonb,
    output_sha256 char(64) COLLATE "C",
    agent_run_id varchar(64) COLLATE "C",
    side_effect_outcome varchar(16) COLLATE "C" NOT NULL,
    error_code varchar(128) COLLATE "C",
    started_at timestamptz NOT NULL,
    node_deadline_at timestamptz NOT NULL,
    attempt_deadline_at timestamptz NOT NULL,
    accepted_at timestamptz,
    finished_at timestamptz,
    retry_command_id varchar(128) COLLATE "C",
    retry_request_sha256 char(64) COLLATE "C",
    worker_effect_recovery jsonb,
    CONSTRAINT tianji_process_node_execution_pkey PRIMARY KEY (tenant_id,instance_id,occurrence_id,attempt),
    CONSTRAINT node_execution_identity_check CHECK (
        tenant_id>0 AND instance_id<>'' AND occurrence_id<>'' AND attempt>=1 AND attempt<=10
        AND node_execution_ref<>'' AND node_id<>'' AND parent_scope_id<>'' AND attempt_scope_id<>''
        AND kind = ANY (ARRAY['HUMAN','SERVICE','RULE','AGENT','EVENT_WAIT','TIMER','SUBPROCESS','LOOP','MULTI_INSTANCE','AND_JOIN'])
        AND revision>0
        AND (branch_index IS NULL OR branch_index>=0) AND (iteration_index IS NULL OR iteration_index>=0)
        AND input_sha256 ~ '^[0-9a-f]{64}$' AND input_schema_sha256 ~ '^[0-9a-f]{64}$'
        AND output_schema_sha256 ~ '^[0-9a-f]{64}$' AND configuration_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT node_execution_capability_check CHECK (
        (capability_key IS NULL AND capability_version IS NULL AND effect_key IS NULL AND kind<>'SERVICE')
        OR (kind = ANY (ARRAY['SERVICE','RULE']) AND capability_key IS NOT NULL AND capability_key<>''
            AND capability_version IS NOT NULL AND capability_version>0 AND effect_key IS NOT NULL AND effect_key<>'')
    ),
    CONSTRAINT node_execution_state_check CHECK (
        state = ANY (ARRAY['SCHEDULED','RUNNING','WAITING','RESULT_ACCEPTED','WAIT_RECOVERY','COMPLETED','FAILED','TIMED_OUT','CANCELLED'])
        AND side_effect_outcome = ANY (ARRAY['NONE','CONFIRMED','UNKNOWN'])
        AND (error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,127}$')
        AND (agent_run_id IS NULL OR (kind='AGENT' AND agent_run_id<>''))
        AND (state = ANY (ARRAY['COMPLETED','FAILED','TIMED_OUT','CANCELLED'])) = (finished_at IS NOT NULL)
        AND (state<>'RESULT_ACCEPTED' OR accepted_at IS NOT NULL)
    ),
    CONSTRAINT node_execution_data_check CHECK (
        jsonb_typeof(input_json)='object' AND octet_length(input_json::text)<=65536
        AND ((output_json IS NULL AND output_sha256 IS NULL)
            OR (output_json IS NOT NULL AND output_sha256 IS NOT NULL AND output_sha256 ~ '^[0-9a-f]{64}$'
                AND jsonb_typeof(output_json)='object' AND octet_length(output_json::text)<=65536))
    ),
    CONSTRAINT node_execution_time_check CHECK (
        attempt_deadline_at>started_at AND attempt_deadline_at<=node_deadline_at
        AND (accepted_at IS NULL OR (accepted_at>=started_at AND accepted_at<attempt_deadline_at))
        AND (finished_at IS NULL OR (finished_at>=started_at AND (accepted_at IS NULL OR finished_at>=accepted_at)))
    ),
    CONSTRAINT node_execution_retry_check CHECK (
        (retry_command_id IS NULL AND retry_request_sha256 IS NULL)
        OR (attempt>=2 AND retry_command_id IS NOT NULL AND retry_command_id<>'' AND retry_request_sha256 IS NOT NULL
            AND retry_request_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT node_execution_recovery_check CHECK (
        worker_effect_recovery IS NULL
        OR (attempt>=2 AND effect_key IS NOT NULL AND retry_command_id IS NOT NULL AND retry_request_sha256 IS NOT NULL
            AND kind = ANY (ARRAY['SERVICE','RULE'])
            AND jsonb_typeof(worker_effect_recovery)='object' AND octet_length(worker_effect_recovery::text)<=4096
            AND worker_effect_recovery ?& ARRAY['mode','observedGeneration','observedAt','validUntil','authorityRef','evidenceSha256','businessReceiptId','resultSha256']
            AND worker_effect_recovery - ARRAY['mode','observedGeneration','observedAt','validUntil','authorityRef','evidenceSha256','businessReceiptId','resultSha256']='{}'::jsonb
            AND jsonb_typeof(worker_effect_recovery->'mode')='string'
            AND jsonb_typeof(worker_effect_recovery->'observedGeneration')='number'
            AND (worker_effect_recovery->>'observedGeneration') ~ '^[1-9][0-9]{0,14}$'
            AND jsonb_typeof(worker_effect_recovery->'observedAt')='string'
            AND jsonb_typeof(worker_effect_recovery->'validUntil')='string'
            AND jsonb_typeof(worker_effect_recovery->'authorityRef')='string'
            AND length(worker_effect_recovery->>'authorityRef')>=1 AND length(worker_effect_recovery->>'authorityRef')<=128
            AND jsonb_typeof(worker_effect_recovery->'evidenceSha256')='string'
            AND (worker_effect_recovery->>'evidenceSha256') ~ '^[0-9a-f]{64}$'
            AND (((worker_effect_recovery->>'mode')='REPLAY_APPLIED'
                    AND jsonb_typeof(worker_effect_recovery->'businessReceiptId')='string'
                    AND length(worker_effect_recovery->>'businessReceiptId')>=1 AND length(worker_effect_recovery->>'businessReceiptId')<=255
                    AND jsonb_typeof(worker_effect_recovery->'resultSha256')='string'
                    AND (worker_effect_recovery->>'resultSha256') ~ '^[0-9a-f]{64}$')
                 OR ((worker_effect_recovery->>'mode')='RETRY_NOT_APPLIED'
                    AND (worker_effect_recovery->'businessReceiptId')='null'::jsonb
                    AND (worker_effect_recovery->'resultSha256')='null'::jsonb)))
    )
);

ALTER TABLE tianji_process_automation_release ADD CONSTRAINT automation_release_draft_uq UNIQUE (tenant_id,source_draft_id);
ALTER TABLE tianji_process_automation_release ADD CONSTRAINT automation_release_root_uq UNIQUE (tenant_id,release_key,root_definition_key);
ALTER TABLE tianji_process_automation_release ADD CONSTRAINT automation_release_base_fk FOREIGN KEY (tenant_id,release_key) REFERENCES tianji_process_release (tenant_id,release_key) ON DELETE NO ACTION;
ALTER TABLE tianji_process_automation_release ADD CONSTRAINT automation_release_draft_fk FOREIGN KEY (tenant_id,source_draft_id) REFERENCES tianji_process_draft (tenant_id,draft_id) ON DELETE NO ACTION;
ALTER TABLE tianji_process_draft ADD CONSTRAINT draft_base_fk FOREIGN KEY (tenant_id,base_release_key,definition_key) REFERENCES tianji_process_automation_release (tenant_id,release_key,root_definition_key) ON DELETE NO ACTION;
ALTER TABLE tianji_process_draft ADD CONSTRAINT draft_published_fk FOREIGN KEY (tenant_id,published_release_key,definition_key) REFERENCES tianji_process_automation_release (tenant_id,release_key,root_definition_key) ON DELETE NO ACTION;
ALTER TABLE tianji_process_draft_command ADD CONSTRAINT draft_command_scope_fk FOREIGN KEY (tenant_id,draft_id) REFERENCES tianji_process_draft (tenant_id,draft_id) ON DELETE NO ACTION;
ALTER TABLE tianji_process_automation_activation ADD CONSTRAINT automation_activation_binding_uq UNIQUE (tenant_id,definition_key,revision,release_key);
ALTER TABLE tianji_process_automation_activation ADD CONSTRAINT activation_release_fk FOREIGN KEY (tenant_id,release_key,definition_key) REFERENCES tianji_process_automation_release (tenant_id,release_key,root_definition_key) ON DELETE NO ACTION;
ALTER TABLE tianji_process_automation_activation ADD CONSTRAINT activation_previous_fk FOREIGN KEY (tenant_id,definition_key,previous_revision) REFERENCES tianji_process_automation_activation (tenant_id,definition_key,revision) ON DELETE NO ACTION;
ALTER TABLE tianji_process_instance_binding ADD CONSTRAINT instance_activation_fk FOREIGN KEY (tenant_id,definition_key,activation_revision,release_key) REFERENCES tianji_process_automation_activation (tenant_id,definition_key,revision,release_key) ON DELETE NO ACTION;
ALTER TABLE tianji_process_automation_activation ADD CONSTRAINT automation_activation_command_uq UNIQUE (tenant_id,definition_key,actor_type,actor_issuer,actor_id,command_id);
ALTER TABLE tianji_process_instance_binding ADD CONSTRAINT instance_release_fk FOREIGN KEY (tenant_id,release_key) REFERENCES tianji_process_automation_release (tenant_id,release_key) ON DELETE NO ACTION;
ALTER TABLE tianji_process_instance_binding ADD CONSTRAINT instance_start_source_uq UNIQUE (tenant_id,owner_type,owner_issuer,owner_id,source_command_type,source_command_id);
ALTER TABLE tianji_process_instance_binding ADD CONSTRAINT instance_start_fk FOREIGN KEY (tenant_id,owner_type,owner_issuer,owner_id,source_command_type,source_command_id) REFERENCES tianji_process_command_receipt (tenant_id,subject_type,subject_issuer,subject_id,command_type,command_id) ON DELETE NO ACTION;
ALTER TABLE tianji_process_node_execution ADD CONSTRAINT node_binding_fk FOREIGN KEY (tenant_id,instance_id) REFERENCES tianji_process_instance_binding (tenant_id,instance_id) ON DELETE NO ACTION;
ALTER TABLE tianji_process_node_execution ADD CONSTRAINT node_execution_ref_uq UNIQUE (tenant_id,node_execution_ref);
CREATE UNIQUE INDEX instance_event_source_uq ON tianji_process_instance_binding (tenant_id,source_event_receipt_id) WHERE source_event_receipt_id IS NOT NULL;
CREATE UNIQUE INDEX process_draft_published_uq ON tianji_process_draft (tenant_id,published_release_key) WHERE published_release_key IS NOT NULL;
CREATE INDEX process_draft_owner_state_idx ON tianji_process_draft (tenant_id,owner_type,owner_issuer,owner_id,state,created_at DESC,draft_id DESC);
CREATE INDEX process_draft_owner_definition_idx ON tianji_process_draft (tenant_id,owner_type,owner_issuer,owner_id,definition_key,state,created_at DESC,draft_id DESC);
CREATE UNIQUE INDEX node_retry_command_uq ON tianji_process_node_execution (tenant_id,instance_id,retry_command_id) WHERE retry_command_id IS NOT NULL;
CREATE INDEX node_execution_page_idx ON tianji_process_node_execution (tenant_id,instance_id,started_at,occurrence_id,attempt);
CREATE INDEX node_execution_scope_idx ON tianji_process_node_execution (tenant_id,instance_id,parent_scope_id,branch_index,iteration_index);
