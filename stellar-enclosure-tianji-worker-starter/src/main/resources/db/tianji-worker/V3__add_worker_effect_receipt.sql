-- Provision externally in the existing Worker Flyway history, before enabling typed execution.
CREATE TABLE tianji_worker_effect_receipt (
    source_id varchar(128) COLLATE "C" NOT NULL,
    tenant_id bigint NOT NULL,
    effect_key varchar(128) COLLATE "C" NOT NULL,
    capability_key varchar(128) COLLATE "C" NOT NULL,
    capability_version bigint NOT NULL,
    input_schema_sha256 char(64) COLLATE "C" NOT NULL,
    business_input_sha256 char(64) COLLATE "C" NOT NULL,
    state varchar(16) COLLATE "C" NOT NULL,
    result_json jsonb,
    business_receipt_id varchar(255) COLLATE "C",
    revision bigint NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    output_schema_sha256 char(64) COLLATE "C" NOT NULL,
    effect_mode varchar(32) COLLATE "C" NOT NULL,
    handler_version varchar(128) COLLATE "C" NOT NULL,
    target_id varchar(128) COLLATE "C",
    effect_fingerprint char(64) COLLATE "C" NOT NULL,
    result_sha256 char(64) COLLATE "C",
    dispatch_generation bigint NOT NULL,
    last_reconciliation jsonb,
    reconciled_at timestamptz(6),
    CONSTRAINT tianji_worker_effect_receipt_pkey PRIMARY KEY (source_id,tenant_id,effect_key),
    CONSTRAINT worker_effect_identity_check CHECK (
        source_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' AND tenant_id>0
        AND effect_key ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$' AND capability_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'
        AND capability_version>=1 AND capability_version<='999999999999999'::bigint
        AND btrim(handler_version)<>'' AND handler_version !~ '[[:cntrl:]]'
        AND input_schema_sha256 ~ '^[0-9a-f]{64}$' AND output_schema_sha256 ~ '^[0-9a-f]{64}$'
        AND business_input_sha256 ~ '^[0-9a-f]{64}$' AND effect_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT worker_effect_mode_check CHECK (
        (effect_mode='TRANSACTIONAL_RECEIPT' AND target_id IS NULL)
        OR (effect_mode = ANY (ARRAY['READ_ONLY','EXTERNAL_IDEMPOTENT']) AND target_id IS NOT NULL
            AND target_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')
    ),
    CONSTRAINT worker_effect_lifecycle_check CHECK (
        revision>=1 AND dispatch_generation>=1 AND dispatch_generation<='999999999999999'::bigint
        AND updated_at>=created_at
        AND (effect_mode='EXTERNAL_IDEMPOTENT' OR (dispatch_generation=1 AND state='COMPLETED'))
    ),
    CONSTRAINT worker_effect_result_check CHECK (
        (state = ANY (ARRAY['STARTED','UNKNOWN']) AND result_json IS NULL
            AND business_receipt_id IS NULL AND result_sha256 IS NULL)
        OR (state='COMPLETED' AND result_json IS NOT NULL AND business_receipt_id IS NOT NULL AND result_sha256 IS NOT NULL
            AND btrim(business_receipt_id)<>'' AND business_receipt_id !~ '[[:cntrl:]]'
            AND result_sha256 ~ '^[0-9a-f]{64}$' AND jsonb_typeof(result_json)='object'
            AND octet_length(regexp_replace(result_json::text,$re$("(?:[^"\\]|\\.)*")| $re$,$re$\1$re$,'g'))<=65536)
    ),
    CONSTRAINT worker_effect_reconciliation_check CHECK (
        (last_reconciliation IS NULL AND reconciled_at IS NULL)
        OR (effect_mode='EXTERNAL_IDEMPOTENT' AND last_reconciliation IS NOT NULL
            AND reconciled_at IS NOT NULL AND reconciled_at>=created_at AND reconciled_at<=updated_at
            AND jsonb_typeof(last_reconciliation)='object' AND octet_length(last_reconciliation::text)<=16384
            AND last_reconciliation ?& ARRAY['status','dispatchGeneration','authorityRef','observedAt','validUntil','evidenceSha256']
            AND last_reconciliation - ARRAY['status','dispatchGeneration','authorityRef','observedAt','validUntil','evidenceSha256']='{}'::jsonb
            AND jsonb_typeof(last_reconciliation->'status')='string'
            AND last_reconciliation->>'status' = ANY (ARRAY['APPLIED','NOT_APPLIED_FINAL'])
            AND jsonb_typeof(last_reconciliation->'dispatchGeneration')='number'
            AND last_reconciliation->>'dispatchGeneration' ~ '^[1-9][0-9]{0,14}$'
            AND jsonb_typeof(last_reconciliation->'authorityRef')='string'
            AND last_reconciliation->>'authorityRef' ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$'
            AND jsonb_typeof(last_reconciliation->'observedAt')='string'
            AND jsonb_typeof(last_reconciliation->'validUntil')='string'
            AND jsonb_typeof(last_reconciliation->'evidenceSha256')='string'
            AND last_reconciliation->>'evidenceSha256' ~ '^[0-9a-f]{64}$')
    )
);
