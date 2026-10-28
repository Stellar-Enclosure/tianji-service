CREATE TABLE tianji_worker_nontransactional_intent (
    source_id varchar(128) COLLATE "C" NOT NULL,
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    idempotency_key varchar(128) COLLATE "C" NOT NULL,
    effect_safety varchar(32) NOT NULL CHECK (effect_safety IN ('READ_ONLY', 'EXTERNAL_IDEMPOTENT')),
    target_id varchar(128) COLLATE "C" NOT NULL,
    handler_version varchar(128) COLLATE "C" NOT NULL,
    input_sha256 char(64) COLLATE "C" NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (source_id, tenant_id, idempotency_key)
);
