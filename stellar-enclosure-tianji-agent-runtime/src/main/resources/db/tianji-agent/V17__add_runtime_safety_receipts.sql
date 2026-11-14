-- Runtime V2 technical safety evidence. Apply explicitly in the existing Agent Flyway history.
CREATE TABLE tianji_agent.agent_runtime_binding (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    runtime_profile varchar(16) COLLATE "C" NOT NULL,
    snapshot_sha256 char(64) COLLATE "C" NOT NULL,
    configuration_sha256 char(64) COLLATE "C" NOT NULL,
    template_sha256 char(64) COLLATE "C" NOT NULL,
    safety_policy jsonb NOT NULL,
    node_execution_ref varchar(128) COLLATE "C" NOT NULL,
    attempt_deadline_at timestamptz NOT NULL,
    model_calls integer NOT NULL,
    tool_calls integer NOT NULL,
    active_calls integer NOT NULL,
    revision bigint NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT agent_runtime_binding_pkey PRIMARY KEY (tenant_id,run_id),
    CONSTRAINT agent_runtime_binding_run_fkey FOREIGN KEY (tenant_id,run_id)
        REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT runtime_binding_identity CHECK (
        tenant_id>0 AND run_id<>'' AND runtime_profile='RUNTIME_V2'
        AND snapshot_sha256 ~ '^[0-9a-f]{64}$' AND configuration_sha256 ~ '^[0-9a-f]{64}$'
        AND template_sha256 ~ '^[0-9a-f]{64}$' AND node_execution_ref<>''
        AND node_execution_ref=btrim(node_execution_ref) AND node_execution_ref !~ '[[:cntrl:]]'
    ),
    CONSTRAINT runtime_binding_policy CHECK (
        jsonb_typeof(safety_policy)='object' AND octet_length(safety_policy::text)<=16384
        AND safety_policy ?& ARRAY['totalTimeout','modelIdleTimeout','toolTimeout','maxModelCalls','maxToolCalls','maxParallelism','contextWindowTokens','maxOutputTokensPerCall']
        AND safety_policy - ARRAY['totalTimeout','modelIdleTimeout','toolTimeout','maxModelCalls','maxToolCalls','maxParallelism','contextWindowTokens','maxOutputTokensPerCall']='{}'::jsonb
    ),
    CONSTRAINT runtime_binding_counters CHECK (
        model_calls>=0 AND tool_calls>=0 AND active_calls>=0 AND revision>0 AND attempt_deadline_at>created_at
    )
);

CREATE TABLE tianji_agent.agent_runtime_call_receipt (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    call_id uuid NOT NULL,
    kind varchar(24) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    lease_epoch bigint NOT NULL,
    capability_id uuid,
    capability_binding_sha256 char(64) COLLATE "C",
    capability_issue_epoch bigint,
    grant_id varchar(63) COLLATE "C",
    tool_key varchar(128) COLLATE "C",
    tool_version bigint,
    tool_sha256 char(64) COLLATE "C",
    backend_binding_sha256 char(64) COLLATE "C",
    risk varchar(32) COLLATE "C",
    input_sha256 char(64) COLLATE "C" NOT NULL,
    context_metadata jsonb NOT NULL,
    intent_id uuid,
    dispatch_epoch bigint,
    dispatch_nonce_sha256 char(64) COLLATE "C",
    executor_issuer char(64) COLLATE "C",
    executor_id varchar(256) COLLATE "C",
    source_action_revision bigint,
    source_result_sha256 char(64) COLLATE "C",
    state varchar(16) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    deadline_at timestamptz NOT NULL,
    result_sha256 char(64) COLLATE "C",
    input_tokens bigint,
    output_tokens bigint,
    error_code varchar(128) COLLATE "C",
    created_at timestamptz NOT NULL,
    dispatched_at timestamptz,
    finished_at timestamptz,
    CONSTRAINT agent_runtime_call_receipt_pkey PRIMARY KEY (tenant_id,run_id,call_id),
    CONSTRAINT runtime_call_dispatch_uq UNIQUE (tenant_id,run_id,call_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id),
    CONSTRAINT runtime_call_binding_fkey FOREIGN KEY (tenant_id,run_id)
        REFERENCES tianji_agent.agent_runtime_binding (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT runtime_call_capability_fkey FOREIGN KEY (tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256)
        REFERENCES tianji_agent.agent_tool_capability (tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) ON DELETE NO ACTION,
    CONSTRAINT runtime_call_action_fkey FOREIGN KEY (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,risk)
        REFERENCES tianji_agent.agent_tool_action_intent (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk) ON DELETE NO ACTION,
    CONSTRAINT runtime_call_identity CHECK (
        tenant_id>0 AND run_id<>'' AND kind = ANY (ARRAY['MODEL','TOOL','TOOL_RESULT_READ'])
        AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$'
        AND owner_id<>'' AND owner_id=btrim(owner_id) AND owner_id !~ '[[:cntrl:]]'
        AND lease_epoch>0 AND revision>0 AND input_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT runtime_call_capability CHECK (
        (kind='MODEL' AND capability_id IS NULL AND capability_binding_sha256 IS NULL AND capability_issue_epoch IS NULL
            AND grant_id IS NULL AND tool_key IS NULL AND tool_version IS NULL AND tool_sha256 IS NULL
            AND backend_binding_sha256 IS NULL AND risk IS NULL)
        OR (kind<>'MODEL' AND capability_id IS NOT NULL AND capability_binding_sha256 IS NOT NULL AND capability_binding_sha256 ~ '^[0-9a-f]{64}$'
            AND capability_issue_epoch IS NOT NULL AND capability_issue_epoch>0 AND tool_key IS NOT NULL AND tool_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$'
            AND tool_version IS NOT NULL AND tool_version>0 AND tool_sha256 IS NOT NULL AND tool_sha256 ~ '^[0-9a-f]{64}$'
            AND backend_binding_sha256 IS NOT NULL AND backend_binding_sha256 ~ '^[0-9a-f]{64}$'
            AND risk IS NOT NULL AND risk = ANY (ARRAY['READ_ONLY','IDEMPOTENT_WRITE','APPROVAL_REQUIRED'])
            AND (grant_id IS NULL OR (owner_type='USER' AND grant_id ~ '^9[0-9]{62}$') OR (owner_type='SERVICE' AND grant_id ~ '^8[0-9]{62}$')))
    ),
    CONSTRAINT runtime_call_dispatch CHECK (
        ((kind='MODEL' OR kind='TOOL_RESULT_READ' OR risk='READ_ONLY' OR dispatched_at IS NULL)
            AND dispatch_epoch IS NULL AND dispatch_nonce_sha256 IS NULL AND executor_issuer IS NULL AND executor_id IS NULL)
        OR (kind='TOOL' AND risk<>'READ_ONLY' AND dispatched_at IS NOT NULL
            AND dispatch_epoch IS NOT NULL AND dispatch_epoch>0 AND dispatch_nonce_sha256 IS NOT NULL
            AND dispatch_nonce_sha256 ~ '^[0-9a-f]{64}$' AND executor_issuer IS NOT NULL AND executor_issuer ~ '^[0-9a-f]{64}$'
            AND executor_id IS NOT NULL AND executor_id<>'' AND executor_id=btrim(executor_id) AND executor_id !~ '[[:cntrl:]]')
    ),
    CONSTRAINT runtime_call_source CHECK (
        (kind<>'TOOL_RESULT_READ' AND source_action_revision IS NULL AND source_result_sha256 IS NULL
            AND ((kind='TOOL' AND risk<>'READ_ONLY' AND intent_id IS NOT NULL)
                 OR ((kind='MODEL' OR risk='READ_ONLY') AND intent_id IS NULL)))
        OR (kind='TOOL_RESULT_READ' AND risk='IDEMPOTENT_WRITE' AND intent_id IS NOT NULL
            AND source_action_revision IS NOT NULL AND source_action_revision>0
            AND source_result_sha256 IS NOT NULL AND source_result_sha256 ~ '^[0-9a-f]{64}$')
    ),
    CONSTRAINT runtime_call_metadata CHECK (
        jsonb_typeof(context_metadata)='object' AND octet_length(context_metadata::text)<=65536
        AND (
            (kind='MODEL' AND context_metadata ? 'modelRef'
                AND jsonb_typeof(context_metadata->'modelRef')='string'
                AND (context_metadata->>'modelRef') ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$'
                AND context_metadata - ARRAY['modelRef','sources']='{}'::jsonb)
            OR (kind='TOOL' AND context_metadata - ARRAY['sources']='{}'::jsonb)
            OR (kind='TOOL_RESULT_READ' AND context_metadata - ARRAY['authorization','sources']='{}'::jsonb)
        )
        AND (NOT (context_metadata ? 'sources') OR jsonb_typeof(context_metadata->'sources')='array')
        AND (kind<>'TOOL_RESULT_READ' OR
            (context_metadata ? 'authorization' AND jsonb_typeof(context_metadata->'authorization')='object'
                AND octet_length((context_metadata->'authorization')::text)<=16384
                AND (context_metadata->'authorization') ?& ARRAY['decisionId','checkedAt','expiresAt','scopeSha256','decision']
                AND (context_metadata->'authorization') - ARRAY['decisionId','checkedAt','expiresAt','scopeSha256','decision']='{}'::jsonb
                AND (context_metadata->'authorization'->'decision')='"ALLOW"'::jsonb))
    ),
    CONSTRAINT runtime_call_state CHECK (
        (state='PREPARED' AND dispatched_at IS NULL AND finished_at IS NULL AND result_sha256 IS NULL AND error_code IS NULL)
        OR (state='DISPATCHED' AND dispatched_at IS NOT NULL AND finished_at IS NULL AND result_sha256 IS NULL AND error_code IS NULL)
        OR (state='SUCCEEDED' AND dispatched_at IS NOT NULL AND finished_at IS NOT NULL AND result_sha256 IS NOT NULL AND error_code IS NULL)
        OR (state='FAILED' AND finished_at IS NOT NULL AND result_sha256 IS NULL AND error_code IS NOT NULL)
        OR (state='UNKNOWN' AND dispatched_at IS NOT NULL AND finished_at IS NOT NULL AND result_sha256 IS NULL AND error_code IS NOT NULL)
    ),
    CONSTRAINT runtime_call_outcome CHECK (
        (result_sha256 IS NULL OR result_sha256 ~ '^[0-9a-f]{64}$')
        AND (error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,127}$')
        AND (input_tokens IS NULL OR (kind='MODEL' AND finished_at IS NOT NULL AND dispatched_at IS NOT NULL AND input_tokens>=0))
        AND (output_tokens IS NULL OR (kind='MODEL' AND finished_at IS NOT NULL AND dispatched_at IS NOT NULL AND output_tokens>=0))
    ),
    CONSTRAINT runtime_call_time CHECK (
        deadline_at>created_at AND (dispatched_at IS NULL OR (dispatched_at>=created_at AND dispatched_at<deadline_at))
        AND (finished_at IS NULL OR (finished_at>=created_at AND (dispatched_at IS NULL OR finished_at>=dispatched_at)))
    )
);

CREATE INDEX runtime_call_pending_idx ON tianji_agent.agent_runtime_call_receipt (tenant_id,run_id,state,deadline_at)
    WHERE state = ANY (ARRAY['PREPARED','DISPATCHED']);

ALTER TABLE tianji_agent.agent_tool_action_intent
    ADD COLUMN action_profile varchar(16) COLLATE "C" NOT NULL DEFAULT 'LEGACY_V1',
    ADD COLUMN dispatch_call_id uuid,
    DROP CONSTRAINT tool_action_dispatch_group,
    DROP CONSTRAINT tool_action_outcome,
    ADD CONSTRAINT tool_action_profile CHECK (action_profile = ANY (ARRAY['LEGACY_V1','RUNTIME_V2'])),
    ADD CONSTRAINT tool_action_runtime_dispatch_fkey
        FOREIGN KEY (tenant_id,dispatch_run_id,dispatch_call_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id)
        REFERENCES tianji_agent.agent_runtime_call_receipt (tenant_id,run_id,call_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id) ON DELETE NO ACTION,
    ADD CONSTRAINT tool_action_dispatch_group CHECK (
        (dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND dispatch_run_id IS NULL AND dispatch_invocation_id IS NULL
            AND dispatch_call_id IS NULL AND executor_issuer IS NULL AND executor_id IS NULL)
        OR (dispatch_epoch>0 AND dispatch_nonce_sha256 IS NOT NULL AND dispatch_nonce_sha256 ~ '^[0-9a-f]{64}$'
            AND dispatch_run_id IS NOT NULL
            AND ((action_profile='LEGACY_V1' AND dispatch_invocation_id IS NOT NULL AND dispatch_call_id IS NULL)
                OR (action_profile='RUNTIME_V2' AND dispatch_invocation_id IS NULL AND dispatch_call_id IS NOT NULL))
            AND executor_issuer IS NOT NULL AND executor_issuer ~ '^[0-9a-f]{64}$' AND executor_id IS NOT NULL
            AND (executor_id <> '' AND executor_id = btrim(executor_id) AND executor_id !~ '[[:cntrl:]]')
            AND (risk<>'APPROVAL_REQUIRED' OR approval_proof IS NOT NULL))
    ),
    ADD CONSTRAINT tool_action_outcome CHECK (
        result IS NULL OR (action_profile='LEGACY_V1' AND
            ((jsonb_typeof(result)='object' AND result ?& ARRAY['status','output','businessReceiptId','errorCode','actualCost']
                AND result - ARRAY['status','output','businessReceiptId','errorCode','actualCost'] = '{}'::jsonb)
             AND jsonb_typeof(result->'status')='string' AND result->>'status'=CASE WHEN state='APPLIED' THEN 'SUCCEEDED' ELSE state END
             AND jsonb_typeof(result->'output')='object' AND octet_length(result::text)<=131072
             AND ((business_receipt_id IS NULL AND result->'businessReceiptId'='null'::jsonb) OR
                 (business_receipt_id IS NOT NULL AND jsonb_typeof(result->'businessReceiptId')='string' AND result->>'businessReceiptId'=business_receipt_id))
             AND ((last_error_code IS NULL AND result->'errorCode'='null'::jsonb) OR
                 (last_error_code IS NOT NULL AND jsonb_typeof(result->'errorCode')='string' AND result->>'errorCode'=last_error_code))
             AND (result->>'status'='SUCCEEDED' OR result->'output'='{}'::jsonb)
             AND (result->'actualCost'='null'::jsonb OR (jsonb_typeof(result->'actualCost')='number'
                 AND (result->>'actualCost')::numeric>=0::numeric AND (result->>'actualCost')::numeric<10000000000000000::numeric
                 AND scale((result->>'actualCost')::numeric)<=8))))
        OR (action_profile='RUNTIME_V2' AND
            ((jsonb_typeof(result)='object' AND result ?& ARRAY['status','output','businessReceiptId','errorCode']
                AND result - ARRAY['status','output','businessReceiptId','errorCode'] = '{}'::jsonb)
             AND jsonb_typeof(result->'status')='string' AND result->>'status'=CASE WHEN state='APPLIED' THEN 'SUCCEEDED' ELSE state END
             AND jsonb_typeof(result->'output')='object' AND octet_length(result::text)<=131072
             AND ((business_receipt_id IS NULL AND result->'businessReceiptId'='null'::jsonb) OR
                 (business_receipt_id IS NOT NULL AND jsonb_typeof(result->'businessReceiptId')='string' AND result->>'businessReceiptId'=business_receipt_id))
             AND ((last_error_code IS NULL AND result->'errorCode'='null'::jsonb) OR
                 (last_error_code IS NOT NULL AND jsonb_typeof(result->'errorCode')='string' AND result->>'errorCode'=last_error_code))
             AND (result->>'status'='SUCCEEDED' OR result->'output'='{}'::jsonb)))
    );
