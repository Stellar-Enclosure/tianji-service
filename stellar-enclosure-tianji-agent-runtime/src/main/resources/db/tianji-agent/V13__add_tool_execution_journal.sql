-- Tool execution journal. Provision explicitly in the owning Agent history; runtime never applies DDL.
-- Baseline: tianji-service 2c0fc5e, ToolExecutionGrant BindingVO/ReservationVO/EvidenceVO.
-- One V13 tool-governance change; V1-V12 remain byte-identical.
-- Storage and authorization contract:
-- 1. Existing table definitions/keys remain unchanged. tenant/run FKs prove existence;
--    Store joins must separately prove exact current owner and budget lease/kind/tool.
-- 2. approval_source_namespace_sha256 is computed by a trusted host registry from stable
--    logical Process authority/source ID + exact OAuth resource/audience + tenant+issuer.
--    It EXCLUDES consumer/policy/tool so switching aliases/consumers cannot reuse a receipt.
-- 3. logical_identity_sha256 uses OrchestrationRequestDigests.hash over namespace
--    TIANJI_TOOL_ACTION_LOGICAL_IDENTITY_V1 + owner.identityFingerprint() + actionKey +
--    businessKey + businessVersion and empty map. This independent full SHA does not
--    alter or duplicate ToolActionRequest.intentId's existing namespace; compare both
--    full logical SHA and all original identity/request fields on replay or UUID collision.
-- 4. Audit/outcome JSON CHECKs constrain closed shape, not authenticity; proof JSON remains
--    bounded-object only until its exact source/binding/approval consumer codec exists. Strict Jackson
--    codecs reconstruct current domain values; only verified clients supply proof/audit.
-- STORE RULES THAT CHECKs CANNOT ENFORCE WITHOUT A SECOND ENGINE OR TRIGGERS:
-- Immutable identity/first expiry; monotonic revision/epochs; own short same-DS transactions;
-- tenant/run FKs do not prove owner equality: every Store must join full namespace;
-- current run/owner/lease/deadline/cancel/budget checks before every dispatch; independent
-- tool authority outside DB transactions; no ambient transaction around network calls.
-- UNKNOWN NEVER authorizes another dispatch. Only receipt lookup/reconcile can confirm it.
-- AWAITING_APPROVAL ends the first run's invocation (typed empty outcome, finishedAt),
-- releases its active budget/resources, and leaves the action intent open for a later run.
-- Late outcome updates its exact invocation generation+nonce; it cannot rewrite a terminal
-- run or replace a different current action dispatch. Budget late reconciliation must use
-- the original reservation, not create new capacity or reinterpret unknown cost as zero.
-- No soft-delete key release, cascading deletes, bearer token or raw SecurityContext.


CREATE TABLE tianji_agent.agent_tool_capability (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    tool_key varchar(128) COLLATE "C" NOT NULL,
    tool_version bigint NOT NULL,
    tool_sha256 char(64) COLLATE "C" NOT NULL,
    backend_binding_sha256 char(64) COLLATE "C" NOT NULL,
    purpose varchar(96) COLLATE "C" NOT NULL,
    resource_uri varchar(2048) COLLATE "C" NOT NULL,
    authorization_resource_key varchar(128) COLLATE "C" NOT NULL,
    source_client_id varchar(128) COLLATE "C",
    source_app_id varchar(128) COLLATE "C",
    source_business_code varchar(128) COLLATE "C",
    source_application_code varchar(128) COLLATE "C",
    source_environment varchar(128) COLLATE "C",
    source_resource_uri varchar(2048) COLLATE "C",
    capability_id uuid NOT NULL,
    binding_sha256 char(64) COLLATE "C" NOT NULL,
    issue_epoch bigint NOT NULL,
    grant_id varchar(63) COLLATE "C",
    ciphertext text,
    expires_at timestamptz,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    deadline_at timestamptz NOT NULL,
    CONSTRAINT agent_tool_capability_pkey PRIMARY KEY (tenant_id,run_id,tool_key,tool_version),
    CONSTRAINT agent_tool_capability_id_key UNIQUE (tenant_id,capability_id),
    CONSTRAINT agent_tool_capability_binding_key UNIQUE (tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256),
    CONSTRAINT agent_tool_capability_run_fkey FOREIGN KEY (tenant_id,run_id) REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT tool_capability_identity CHECK (
        tenant_id>0 AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$' AND (owner_id <> '' AND owner_id = btrim(owner_id) AND owner_id !~ '[[:cntrl:]]') AND tool_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$' AND tool_version>0 AND tool_sha256 ~ '^[0-9a-f]{64}$' AND backend_binding_sha256 ~ '^[0-9a-f]{64}$' AND binding_sha256 ~ '^[0-9a-f]{64}$' AND authorization_resource_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$'
    ),
    CONSTRAINT tool_capability_resource CHECK (
        purpose ~ '^TIANJI_TOOL_[A-Z][A-Z0-9_]{0,83}$' AND resource_uri ~* '^https://[^/?#@[:space:][:cntrl:]]+(/[^?#[:space:][:cntrl:]]*)?$' AND octet_length(resource_uri)=length(resource_uri)
    ),
    CONSTRAINT tool_capability_source CHECK (
        (owner_type='USER' AND source_client_id IS NULL AND source_app_id IS NULL AND source_business_code IS NULL AND source_application_code IS NULL AND source_environment IS NULL AND source_resource_uri IS NULL)
        OR (owner_type='SERVICE' AND source_client_id IS NOT NULL AND source_client_id=owner_id AND (source_client_id IS NOT NULL AND (source_client_id <> '' AND source_client_id = btrim(source_client_id) AND source_client_id !~ '[[:cntrl:]]')) AND (source_app_id IS NOT NULL AND (source_app_id <> '' AND source_app_id = btrim(source_app_id) AND source_app_id !~ '[[:cntrl:]]')) AND (source_business_code IS NOT NULL AND (source_business_code <> '' AND source_business_code = btrim(source_business_code) AND source_business_code !~ '[[:cntrl:]]')) AND (source_application_code IS NOT NULL AND (source_application_code <> '' AND source_application_code = btrim(source_application_code) AND source_application_code !~ '[[:cntrl:]]')) AND (source_environment IS NOT NULL AND (source_environment <> '' AND source_environment = btrim(source_environment) AND source_environment !~ '[[:cntrl:]]')) AND source_resource_uri IS NOT NULL AND source_resource_uri ~* '^https://[^/?#@[:space:][:cntrl:]]+(/[^?#[:space:][:cntrl:]]*)?$' AND octet_length(source_resource_uri)=length(source_resource_uri))
    ),
    CONSTRAINT tool_capability_ticket CHECK (
        (grant_id IS NULL AND ciphertext IS NULL AND expires_at IS NULL)
        OR (grant_id IS NOT NULL AND ciphertext IS NOT NULL AND expires_at IS NOT NULL AND octet_length(ciphertext)<=32768 AND ciphertext ~ '^ENC:v1:[A-Za-z0-9][A-Za-z0-9._-]{0,63}:[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$' AND ((owner_type='USER' AND grant_id ~ '^9[0-9]{62}$') OR (owner_type='SERVICE' AND grant_id ~ '^8[0-9]{62}$')) AND expires_at>created_at AND expires_at<=deadline_at)
    ),
    CONSTRAINT tool_capability_time CHECK (
        issue_epoch>0 AND updated_at>=created_at AND deadline_at>created_at
    )
);

CREATE TABLE tianji_agent.agent_tool_action_intent (
    tenant_id bigint NOT NULL,
    intent_id uuid NOT NULL,
    logical_identity_sha256 char(64) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    action_key varchar(128) COLLATE "C" NOT NULL,
    business_key varchar(255) COLLATE "C" NOT NULL,
    business_version varchar(128) COLLATE "C" NOT NULL,
    tool_key varchar(128) COLLATE "C" NOT NULL,
    tool_version bigint NOT NULL,
    tool_sha256 char(64) COLLATE "C" NOT NULL,
    binding_sha256 char(64) COLLATE "C" NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    descriptor jsonb NOT NULL,
    arguments jsonb NOT NULL,
    origin_run_id varchar(64) NOT NULL,
    origin_snapshot_sha256 char(64) COLLATE "C" NOT NULL,
    risk varchar(32) COLLATE "C" NOT NULL,
    approval_policy_ref varchar(128) COLLATE "C",
    approval_source_namespace_sha256 char(64) COLLATE "C",
    approval_receipt_id varchar(255) COLLATE "C",
    approval_proof_sha256 char(64) COLLATE "C",
    approval_proof jsonb,
    approval_approved boolean,
    approval_consumed_at timestamptz,
    approval_expires_at timestamptz,
    state varchar(32) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    dispatch_epoch bigint NOT NULL DEFAULT 0,
    dispatch_nonce_sha256 char(64) COLLATE "C",
    dispatch_run_id varchar(64),
    dispatch_invocation_id uuid,
    executor_issuer char(64) COLLATE "C",
    executor_id varchar(256) COLLATE "C",
    side_effect_outcome varchar(16) COLLATE "C" NOT NULL,
    business_receipt_id varchar(255) COLLATE "C",
    result jsonb,
    last_error_code varchar(64) COLLATE "C",
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    closed_at timestamptz,
    CONSTRAINT agent_tool_action_intent_pkey PRIMARY KEY (tenant_id,intent_id),
    CONSTRAINT agent_tool_action_logical_key UNIQUE (tenant_id,logical_identity_sha256),
    CONSTRAINT agent_tool_action_binding_key UNIQUE (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk),
    CONSTRAINT agent_tool_action_origin_fkey FOREIGN KEY (tenant_id,origin_run_id) REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_action_dispatch_run_fkey FOREIGN KEY (tenant_id,dispatch_run_id) REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT tool_action_identity CHECK (
        tenant_id>0 AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$' AND (owner_id <> '' AND owner_id = btrim(owner_id) AND owner_id !~ '[[:cntrl:]]') AND action_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$' AND (business_key <> '' AND business_key = btrim(business_key) AND business_key !~ '[[:cntrl:]]') AND (business_version <> '' AND business_version = btrim(business_version) AND business_version !~ '[[:cntrl:]]') AND logical_identity_sha256 ~ '^[0-9a-f]{64}$' AND tool_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$' AND tool_version>0 AND tool_sha256 ~ '^[0-9a-f]{64}$' AND binding_sha256 ~ '^[0-9a-f]{64}$' AND request_sha256 ~ '^[0-9a-f]{64}$' AND origin_snapshot_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT tool_action_json CHECK (
        jsonb_typeof(descriptor)='object' AND octet_length(descriptor::text)<=131072 AND jsonb_typeof(arguments)='object' AND octet_length(arguments::text)<=131072
    ),
    CONSTRAINT tool_action_policy CHECK (
        (risk='IDEMPOTENT_WRITE' AND approval_policy_ref IS NULL) OR (risk='APPROVAL_REQUIRED' AND approval_policy_ref IS NOT NULL AND approval_policy_ref ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$')
    ),
    CONSTRAINT tool_action_approval_group CHECK (
        (approval_source_namespace_sha256 IS NULL AND approval_receipt_id IS NULL AND approval_proof_sha256 IS NULL AND approval_proof IS NULL AND approval_approved IS NULL AND approval_consumed_at IS NULL AND approval_expires_at IS NULL)
        OR (risk='APPROVAL_REQUIRED' AND approval_source_namespace_sha256 IS NOT NULL AND approval_source_namespace_sha256 ~ '^[0-9a-f]{64}$' AND approval_receipt_id IS NOT NULL AND (approval_receipt_id <> '' AND approval_receipt_id = btrim(approval_receipt_id) AND approval_receipt_id !~ '[[:cntrl:]]') AND approval_proof_sha256 IS NOT NULL AND approval_proof_sha256 ~ '^[0-9a-f]{64}$' AND approval_proof IS NOT NULL AND approval_approved IS TRUE AND approval_consumed_at IS NOT NULL AND approval_expires_at IS NOT NULL AND approval_consumed_at>=created_at AND approval_expires_at>approval_consumed_at)
    ),
    CONSTRAINT tool_action_approval_proof CHECK (
        approval_proof IS NULL OR (jsonb_typeof(approval_proof)='object' AND octet_length(approval_proof::text)<=16384)
    ),
    CONSTRAINT tool_action_dispatch_group CHECK (
        (dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND dispatch_run_id IS NULL AND dispatch_invocation_id IS NULL AND executor_issuer IS NULL AND executor_id IS NULL)
        OR (dispatch_epoch>0 AND dispatch_nonce_sha256 IS NOT NULL AND dispatch_nonce_sha256 ~ '^[0-9a-f]{64}$' AND dispatch_run_id IS NOT NULL AND dispatch_invocation_id IS NOT NULL AND executor_issuer IS NOT NULL AND executor_issuer ~ '^[0-9a-f]{64}$' AND executor_id IS NOT NULL AND (executor_id <> '' AND executor_id = btrim(executor_id) AND executor_id !~ '[[:cntrl:]]') AND (risk<>'APPROVAL_REQUIRED' OR approval_proof IS NOT NULL))
    ),
    CONSTRAINT tool_action_state CHECK (
        state = ANY (ARRAY['PREPARED','AWAITING_APPROVAL','DISPATCHING','APPLIED','REJECTED','FAILED_KNOWN','UNKNOWN','EXPIRED']) AND revision>0 AND dispatch_epoch>=0 AND side_effect_outcome = ANY (ARRAY['NONE','CONFIRMED','UNKNOWN'])
    ),
    CONSTRAINT tool_action_lifecycle CHECK (
        ((state = ANY (ARRAY['PREPARED','AWAITING_APPROVAL']) AND dispatch_epoch=0 AND result IS NULL AND last_error_code IS NULL AND business_receipt_id IS NULL AND side_effect_outcome='NONE' AND closed_at IS NULL)
        OR (state='DISPATCHING' AND dispatch_epoch>0 AND result IS NULL AND last_error_code IS NULL AND business_receipt_id IS NULL AND side_effect_outcome='UNKNOWN' AND closed_at IS NULL)
        OR (state='EXPIRED' AND dispatch_epoch=0 AND result IS NULL AND last_error_code IS NOT NULL AND last_error_code='INTENT_EXPIRED' AND business_receipt_id IS NULL AND side_effect_outcome='NONE' AND closed_at IS NOT NULL)
        OR (state='APPLIED' AND dispatch_epoch>0 AND result IS NOT NULL AND last_error_code IS NULL AND business_receipt_id IS NOT NULL AND side_effect_outcome='CONFIRMED' AND closed_at IS NOT NULL)
        OR (state = ANY (ARRAY['REJECTED','FAILED_KNOWN']) AND result IS NOT NULL AND last_error_code IS NOT NULL AND business_receipt_id IS NULL AND side_effect_outcome='NONE' AND closed_at IS NOT NULL)
        OR (state='UNKNOWN' AND dispatch_epoch>0 AND result IS NOT NULL AND last_error_code IS NOT NULL AND business_receipt_id IS NULL AND side_effect_outcome='UNKNOWN' AND closed_at IS NOT NULL))
        AND (state<>'AWAITING_APPROVAL' OR (risk='APPROVAL_REQUIRED' AND approval_proof IS NULL))
        AND (state<>'PREPARED' OR risk='IDEMPOTENT_WRITE' OR approval_proof IS NOT NULL)
    ),
    CONSTRAINT tool_action_error_receipt CHECK (
        (business_receipt_id IS NULL OR (business_receipt_id <> '' AND business_receipt_id = btrim(business_receipt_id) AND business_receipt_id !~ '[[:cntrl:]]')) AND (last_error_code IS NULL OR last_error_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
    ),
    CONSTRAINT tool_action_time CHECK (
        updated_at>=created_at AND expires_at>created_at AND expires_at<=created_at+'168:00:00'::interval AND (closed_at IS NULL OR (closed_at>=created_at AND updated_at>=closed_at))
    ),
    CONSTRAINT tool_action_outcome CHECK (
        result IS NULL OR ((jsonb_typeof(result)='object' AND result ?& ARRAY['status','output','businessReceiptId','errorCode','actualCost'] AND result - ARRAY['status','output','businessReceiptId','errorCode','actualCost'] = '{}'::jsonb) AND jsonb_typeof(result->'status')='string' AND result->>'status'=CASE WHEN state='APPLIED' THEN 'SUCCEEDED' ELSE state END AND jsonb_typeof(result->'output')='object' AND octet_length(result::text)<=131072 AND ((business_receipt_id IS NULL AND result->'businessReceiptId'='null'::jsonb) OR (business_receipt_id IS NOT NULL AND jsonb_typeof(result->'businessReceiptId')='string' AND result->>'businessReceiptId'=business_receipt_id)) AND ((last_error_code IS NULL AND result->'errorCode'='null'::jsonb) OR (last_error_code IS NOT NULL AND jsonb_typeof(result->'errorCode')='string' AND result->>'errorCode'=last_error_code)) AND (result->>'status'='SUCCEEDED' OR result->'output'='{}'::jsonb) AND (result->'actualCost'='null'::jsonb OR (jsonb_typeof(result->'actualCost')='number' AND (result->>'actualCost')::numeric>=0::numeric AND (result->>'actualCost')::numeric<10000000000000000::numeric AND scale((result->>'actualCost')::numeric)<=8)))
    )
);

CREATE UNIQUE INDEX agent_tool_approval_consumed_idx ON tianji_agent.agent_tool_action_intent (tenant_id,approval_source_namespace_sha256,approval_receipt_id) WHERE approval_receipt_id IS NOT NULL;

CREATE INDEX agent_tool_action_owner_idx ON tianji_agent.agent_tool_action_intent (tenant_id,owner_issuer,owner_type,owner_id,created_at,intent_id);

CREATE INDEX agent_tool_action_reconcile_idx ON tianji_agent.agent_tool_action_intent (tenant_id,state,updated_at,intent_id) WHERE state = ANY (ARRAY['DISPATCHING','UNKNOWN']);

CREATE TABLE tianji_agent.agent_tool_invocation (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    invocation_id uuid NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    capability_id uuid NOT NULL,
    capability_binding_sha256 char(64) COLLATE "C" NOT NULL,
    capability_issue_epoch bigint NOT NULL,
    lease_epoch bigint NOT NULL,
    reservation_id varchar(128) NOT NULL,
    tool_key varchar(128) COLLATE "C" NOT NULL,
    tool_version bigint NOT NULL,
    tool_sha256 char(64) COLLATE "C" NOT NULL,
    binding_sha256 char(64) COLLATE "C" NOT NULL,
    parameters_sha256 char(64) COLLATE "C" NOT NULL,
    risk varchar(32) COLLATE "C" NOT NULL,
    intent_id uuid,
    state varchar(32) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    dispatch_epoch bigint NOT NULL DEFAULT 0,
    dispatch_nonce_sha256 char(64) COLLATE "C",
    executor_issuer char(64) COLLATE "C",
    executor_id varchar(256) COLLATE "C",
    authorization_audit jsonb,
    side_effect_outcome varchar(16) COLLATE "C" NOT NULL,
    business_receipt_id varchar(255) COLLATE "C",
    outcome jsonb,
    error_code varchar(64) COLLATE "C",
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    dispatch_not_after timestamptz NOT NULL,
    dispatched_at timestamptz,
    finished_at timestamptz,
    CONSTRAINT agent_tool_invocation_pkey PRIMARY KEY (tenant_id,run_id,invocation_id),
    CONSTRAINT agent_tool_invocation_budget_key UNIQUE (tenant_id,run_id,reservation_id),
    CONSTRAINT agent_tool_invocation_dispatch_key UNIQUE (tenant_id,run_id,invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id),
    CONSTRAINT agent_tool_invocation_run_fkey FOREIGN KEY (tenant_id,run_id) REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_invocation_budget_fkey FOREIGN KEY (tenant_id,run_id,reservation_id) REFERENCES tianji_agent.agent_budget_reservation (tenant_id,run_id,reservation_id) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_invocation_capability_fkey FOREIGN KEY (tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,binding_sha256) REFERENCES tianji_agent.agent_tool_capability (tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_invocation_intent_fkey FOREIGN KEY (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk) REFERENCES tianji_agent.agent_tool_action_intent (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk) ON DELETE NO ACTION,
    CONSTRAINT tool_invocation_identity CHECK (
        tenant_id>0 AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$' AND (owner_id <> '' AND owner_id = btrim(owner_id) AND owner_id !~ '[[:cntrl:]]') AND tool_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$' AND tool_version>0 AND tool_sha256 ~ '^[0-9a-f]{64}$' AND binding_sha256 ~ '^[0-9a-f]{64}$' AND parameters_sha256 ~ '^[0-9a-f]{64}$' AND capability_binding_sha256 ~ '^[0-9a-f]{64}$' AND capability_issue_epoch>0 AND lease_epoch>0 AND (reservation_id <> '' AND reservation_id = btrim(reservation_id) AND reservation_id !~ '[[:cntrl:]]')
    ),
    CONSTRAINT tool_invocation_risk CHECK (
        (risk='READ_ONLY' AND intent_id IS NULL) OR (risk = ANY (ARRAY['IDEMPOTENT_WRITE','APPROVAL_REQUIRED']) AND intent_id IS NOT NULL)
    ),
    CONSTRAINT tool_invocation_state CHECK (
        state = ANY (ARRAY['PREPARED','AWAITING_APPROVAL','DISPATCHING','SUCCEEDED','REJECTED','FAILED_KNOWN','UNKNOWN']) AND revision>0 AND dispatch_epoch>=0 AND side_effect_outcome = ANY (ARRAY['NONE','CONFIRMED','UNKNOWN'])
    ),
    CONSTRAINT tool_invocation_authorization CHECK (
        authorization_audit IS NULL OR ((jsonb_typeof(authorization_audit)='object' AND authorization_audit ?& ARRAY['schemaVersion','localCheckId','boundary','decision','subjectType','grantId','authVersion','catalogVersion','policyVersion','checkedAt','expiresAt'] AND authorization_audit - ARRAY['schemaVersion','localCheckId','boundary','decision','subjectType','grantId','authVersion','catalogVersion','policyVersion','checkedAt','expiresAt'] = '{}'::jsonb) AND authorization_audit->'schemaVersion'='1'::jsonb AND jsonb_typeof(authorization_audit->'localCheckId')='string' AND (authorization_audit->>'localCheckId') ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' AND jsonb_typeof(authorization_audit->'boundary')='string' AND authorization_audit->>'boundary' = ANY (ARRAY['PREPARE','DISPATCH','RECONCILE']) AND jsonb_typeof(authorization_audit->'decision')='string' AND authorization_audit->>'decision' = ANY (ARRAY['ALLOW','DENY','UNAVAILABLE']) AND jsonb_typeof(authorization_audit->'subjectType')='string' AND authorization_audit->>'subjectType'=owner_type AND jsonb_typeof(authorization_audit->'checkedAt')='string' AND ((authorization_audit->>'decision' = ANY (ARRAY['DENY','UNAVAILABLE']) AND authorization_audit->'grantId'='null'::jsonb AND authorization_audit->'authVersion'='null'::jsonb AND authorization_audit->'catalogVersion'='null'::jsonb AND authorization_audit->'policyVersion'='null'::jsonb AND authorization_audit->'expiresAt'='null'::jsonb) OR (authorization_audit->>'decision'='ALLOW' AND jsonb_typeof(authorization_audit->'grantId')='string' AND jsonb_typeof(authorization_audit->'policyVersion')='number' AND (authorization_audit->>'policyVersion') ~ '^[0-9]{1,19}$' AND (authorization_audit->>'policyVersion')::numeric<=9223372036854775807::numeric AND jsonb_typeof(authorization_audit->'expiresAt')='string' AND ((owner_type='USER' AND (authorization_audit->>'grantId') ~ '^9[0-9]{62}$' AND jsonb_typeof(authorization_audit->'authVersion')='number' AND (authorization_audit->>'authVersion') ~ '^[0-9]{1,19}$' AND (authorization_audit->>'authVersion')::numeric<=9223372036854775807::numeric AND authorization_audit->'catalogVersion'='null'::jsonb) OR (owner_type='SERVICE' AND (authorization_audit->>'grantId') ~ '^8[0-9]{62}$' AND authorization_audit->'authVersion'='null'::jsonb AND jsonb_typeof(authorization_audit->'catalogVersion')='number' AND (authorization_audit->>'catalogVersion') ~ '^[1-9][0-9]{0,18}$' AND (authorization_audit->>'catalogVersion')::numeric<=9223372036854775807::numeric)))) AND octet_length(authorization_audit::text)<=4096)
    ),
    CONSTRAINT tool_invocation_dispatch_group CHECK (
        (dispatch_epoch=0 AND dispatch_nonce_sha256 IS NULL AND executor_issuer IS NULL AND executor_id IS NULL AND dispatched_at IS NULL)
        OR (dispatch_epoch>0 AND dispatch_nonce_sha256 IS NOT NULL AND dispatch_nonce_sha256 ~ '^[0-9a-f]{64}$' AND executor_issuer IS NOT NULL AND executor_issuer ~ '^[0-9a-f]{64}$' AND executor_id IS NOT NULL AND (executor_id <> '' AND executor_id = btrim(executor_id) AND executor_id !~ '[[:cntrl:]]') AND dispatched_at IS NOT NULL AND authorization_audit IS NOT NULL AND authorization_audit ?& ARRAY['decision','boundary'] AND authorization_audit->'decision'='"ALLOW"'::jsonb AND authorization_audit->'boundary'='"DISPATCH"'::jsonb)
    ),
    CONSTRAINT tool_invocation_lifecycle CHECK (
        (state='PREPARED' AND dispatch_epoch=0 AND outcome IS NULL AND error_code IS NULL AND business_receipt_id IS NULL AND side_effect_outcome='NONE' AND finished_at IS NULL)
        OR (state='AWAITING_APPROVAL' AND risk='APPROVAL_REQUIRED' AND dispatch_epoch=0 AND outcome IS NOT NULL AND error_code IS NULL AND business_receipt_id IS NULL AND side_effect_outcome='NONE' AND finished_at IS NOT NULL)
        OR (state='DISPATCHING' AND dispatch_epoch>0 AND outcome IS NULL AND error_code IS NULL AND business_receipt_id IS NULL AND side_effect_outcome=CASE WHEN risk='READ_ONLY' THEN 'NONE' ELSE 'UNKNOWN' END AND finished_at IS NULL)
        OR (state='SUCCEEDED' AND dispatch_epoch>0 AND outcome IS NOT NULL AND error_code IS NULL AND finished_at IS NOT NULL AND ((risk='READ_ONLY' AND business_receipt_id IS NULL AND side_effect_outcome='NONE') OR (risk<>'READ_ONLY' AND business_receipt_id IS NOT NULL AND side_effect_outcome='CONFIRMED')))
        OR (state = ANY (ARRAY['REJECTED','FAILED_KNOWN']) AND outcome IS NOT NULL AND error_code IS NOT NULL AND business_receipt_id IS NULL AND side_effect_outcome='NONE' AND finished_at IS NOT NULL)
        OR (state='UNKNOWN' AND dispatch_epoch>0 AND outcome IS NOT NULL AND error_code IS NOT NULL AND business_receipt_id IS NULL AND side_effect_outcome=CASE WHEN risk='READ_ONLY' THEN 'NONE' ELSE 'UNKNOWN' END AND finished_at IS NOT NULL)
    ),
    CONSTRAINT tool_invocation_wait CHECK (
        state<>'AWAITING_APPROVAL' OR risk='APPROVAL_REQUIRED'
    ),
    CONSTRAINT tool_invocation_error_receipt CHECK (
        (business_receipt_id IS NULL OR (business_receipt_id <> '' AND business_receipt_id = btrim(business_receipt_id) AND business_receipt_id !~ '[[:cntrl:]]')) AND (error_code IS NULL OR error_code ~ '^[A-Z][A-Z0-9_]{0,63}$')
    ),
    CONSTRAINT tool_invocation_time CHECK (
        updated_at>=created_at AND dispatch_not_after>created_at AND (dispatched_at IS NULL OR (dispatched_at>=created_at AND dispatched_at<dispatch_not_after AND updated_at>=dispatched_at)) AND (finished_at IS NULL OR (finished_at>=created_at AND updated_at>=finished_at AND (dispatched_at IS NULL OR finished_at>=dispatched_at)))
    ),
    CONSTRAINT tool_invocation_outcome CHECK (
        outcome IS NULL OR ((jsonb_typeof(outcome)='object' AND outcome ?& ARRAY['status','output','businessReceiptId','errorCode','actualCost'] AND outcome - ARRAY['status','output','businessReceiptId','errorCode','actualCost'] = '{}'::jsonb) AND jsonb_typeof(outcome->'status')='string' AND outcome->>'status'=state AND jsonb_typeof(outcome->'output')='object' AND octet_length(outcome::text)<=131072 AND ((business_receipt_id IS NULL AND outcome->'businessReceiptId'='null'::jsonb) OR (business_receipt_id IS NOT NULL AND jsonb_typeof(outcome->'businessReceiptId')='string' AND outcome->>'businessReceiptId'=business_receipt_id)) AND ((error_code IS NULL AND outcome->'errorCode'='null'::jsonb) OR (error_code IS NOT NULL AND jsonb_typeof(outcome->'errorCode')='string' AND outcome->>'errorCode'=error_code)) AND (outcome->>'status'='SUCCEEDED' OR outcome->'output'='{}'::jsonb) AND (outcome->'actualCost'='null'::jsonb OR (jsonb_typeof(outcome->'actualCost')='number' AND (outcome->>'actualCost')::numeric>=0::numeric AND (outcome->>'actualCost')::numeric<10000000000000000::numeric AND scale((outcome->>'actualCost')::numeric)<=8)))
    )
);



-- Backlink ties the action projection to exactly its own immutable invocation, dispatch
-- generation+nonce and actual SERVICE executor. MATCH SIMPLE deliberately permits all-null
-- pre-dispatch links; tool_action_dispatch_group forbids partial-null bypass after dispatch.
ALTER TABLE tianji_agent.agent_tool_action_intent
    ADD CONSTRAINT agent_tool_action_invocation_fkey
    FOREIGN KEY (tenant_id,dispatch_run_id,dispatch_invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id)
    REFERENCES tianji_agent.agent_tool_invocation (tenant_id,run_id,invocation_id,intent_id,dispatch_epoch,dispatch_nonce_sha256,executor_issuer,executor_id)
    ON DELETE NO ACTION;
CREATE INDEX agent_tool_invocation_intent_idx ON tianji_agent.agent_tool_invocation (tenant_id,intent_id,created_at,invocation_id) WHERE intent_id IS NOT NULL;
CREATE INDEX agent_tool_invocation_reconcile_idx ON tianji_agent.agent_tool_invocation (tenant_id,state,updated_at,run_id,invocation_id) WHERE state = ANY (ARRAY['DISPATCHING','UNKNOWN']);

-- Mandatory checks for current and future writers; schema constraints do not authenticate a caller:
-- * Reconstruct BindingVO exactly from current ledger material, compare capabilityId,
--   bindingSha256, owner/deadline/source six tuple and immutable tool descriptor.
-- * capability_issue_epoch on invocation is immutable issued-evidence history. It does not
--   FK to the mutable capability.issue_epoch: refreshing a slot must not destroy history.
-- * Lock reservation and verify kind=TOOL, exact canonical tool key@version, lease epoch,
--   immutable request bounds and counters via the existing AgentBudgetService.
-- * Recompute complete logical identity SHA, action UUID, parameter/request SHA and all JSON
--   schema hashes. JSONB key uniqueness must be checked before storage by strict Jackson.
-- * Later closed proof codec validates exact {schemaVersion,source,binding,approval},
--   all nested formats/limits, exact binding/source request,
--   wrapper proof SHA, explicit approved=true matching approval_approved and stable PS source namespace; no constructed DTO is authentication.
--   Parse its UTC Instants, require verified<=consume<expiry and <=30s verification window,
--   retain source occurredAt, compare proof expiry to the indexed approval_expires_at.
-- * Authorized real SERVICE executor is verified transport identity, not the run owner or
--   an arbitrary request field. Full signed/attested evidence is checked outside transactions.
-- * Audit checkedAt/expiresAt are strict UTC Instants and upstream grant/current versions
--   match the verified decision; localCheckId is generated locally and never called upstream
--   decisionId. DENY/UNAVAILABLE may have no upstream evidence and cannot enter DISPATCHING.
-- * Terminal outcome content reuses ToolExecutionOutcome.validated against the exact registered
--   output schema. String formats/cost precision/unknown accounting are not relaxed on replay.
-- * Receipts and immutable intent keys outlive maximum retry windows; no auto-delete permits
--   reuse. No automatic retries from UNKNOWN, and no state-only CHECK can prove legal CAS.
-- * CHECKs retain ordinary grouped AND/OR, compatible with existing booleanSignature.
--   Optional scalar groups have an all-NULL arm and an explicit all-NOT-NULL active arm.
--   JSON audit/outcome groups require every closed key and its JSON type before comparison,
--   so missing/JSON-null fields contribute FALSE, not a SQL-UNKNOWN bypass. Proof JSON at
--   this stage is only bounded-object storage; approval_approved IS TRUE is the atomic
--   nonnullable decision in the consumed group. Source/binding/approval closed decoding,
--   true decision consistency and canonical proof SHA are mandatory later consumer gates.
-- * Enum membership uses explicit = ANY (ARRAY[...]); JSON numeric comparison bounds
--   explicitly use ::numeric to align expected source with PostgreSQL catalog rendering.
--   The seven-day elapsed expiry uses explicit '168:00:00'::interval for the same reason.
--   This has NOT been verified against actual pg_get_expr; PG rendering remains unverified.
-- * Verify named checks with grouped booleanSignature, keys/indexes/opclasses/collations,
--   exact defaults/table kinds, validated/enforced constraints, RLS/trigger/privileges,
--   existing migration checksums and this one final V13 checksum. Never auto-apply or repair.
