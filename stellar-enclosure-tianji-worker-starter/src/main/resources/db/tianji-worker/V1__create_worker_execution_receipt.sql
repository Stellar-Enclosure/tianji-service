CREATE TABLE tianji_worker_execution_receipt (
    source_id varchar(128) COLLATE "C" NOT NULL,
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    idempotency_key varchar(128) COLLATE "C" NOT NULL,
    task_id varchar(255) COLLATE "C" NOT NULL,
    occurrence_id varchar(255) COLLATE "C" NOT NULL,
    topic varchar(128) COLLATE "C" NOT NULL,
    schema_version varchar(128) COLLATE "C" NOT NULL,
    effect_safety varchar(32) NOT NULL CHECK (effect_safety IN ('READ_ONLY', 'TRANSACTIONAL_RECEIPT', 'EXTERNAL_IDEMPOTENT')),
    handler_version varchar(128) COLLATE "C" NOT NULL,
    input_sha256 char(64) COLLATE "C" NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
    result_sha256 char(64) COLLATE "C" NOT NULL CHECK (result_sha256 ~ '^[0-9a-f]{64}$'),
    business_receipt_id varchar(255) COLLATE "C" NOT NULL,
    output_json jsonb NOT NULL CHECK (jsonb_typeof(output_json) = 'object' AND octet_length(output_json::text) <= 131072),
    committed_at timestamptz NOT NULL,
    PRIMARY KEY (source_id, tenant_id, idempotency_key)
);
