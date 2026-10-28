CREATE TABLE tianji_worker_lease_receipt (
    token_sha256 char(64) COLLATE "C" PRIMARY KEY CHECK (token_sha256 ~ '^[0-9a-f]{64}$'),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    subject_issuer char(64) COLLATE "C" NOT NULL CHECK (subject_issuer ~ '^[0-9a-f]{64}$'),
    subject_id varchar(256) COLLATE "C" NOT NULL,
    topic varchar(128) COLLATE "C" NOT NULL,
    schema_version varchar(128) COLLATE "C" NOT NULL,
    job_id varchar(255) COLLATE "C" NOT NULL,
    occurrence_id varchar(255) COLLATE "C" NOT NULL,
    scope_type varchar(8) NOT NULL CHECK (scope_type IN ('BPMN', 'CMMN')),
    scope_id varchar(255) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    definition_id varchar(255) COLLATE "C" NOT NULL,
    element_id varchar(128) COLLATE "C" NOT NULL,
    idempotency_key varchar(128) COLLATE "C" NOT NULL,
    model_sha256 char(64) COLLATE "C" NOT NULL CHECK (model_sha256 ~ '^[0-9a-f]{64}$'),
    input_sha256 char(64) COLLATE "C" NOT NULL CHECK (input_sha256 ~ '^[0-9a-f]{64}$'),
    native_owner varchar(255) COLLATE "C" NOT NULL,
    leased_at timestamptz NOT NULL,
    lease_expires_at timestamptz NOT NULL,
    state varchar(24) NOT NULL CHECK (state IN ('LEASED', 'EXPIRED', 'COMPLETED', 'RETRY_SCHEDULED', 'BUSINESS_REJECTED', 'NEEDS_MANUAL')),
    result_sha256 char(64) COLLATE "C" CHECK (result_sha256 ~ '^[0-9a-f]{64}$'),
    decided_at timestamptz,
    business_receipt_id varchar(255) COLLATE "C",
    failure_kind varchar(24),
    failure_code varchar(64) COLLATE "C",
    CONSTRAINT tianji_worker_lease_bounds_check CHECK (lease_expires_at > leased_at AND lease_expires_at <= leased_at + interval '15 minutes'),
    CONSTRAINT tianji_worker_lease_state_fields_check CHECK (
        (state = 'LEASED' AND decided_at IS NULL AND result_sha256 IS NULL AND business_receipt_id IS NULL
            AND failure_kind IS NULL AND failure_code IS NULL)
        OR (state = 'EXPIRED' AND decided_at IS NOT NULL AND result_sha256 IS NULL AND business_receipt_id IS NULL
            AND failure_kind IS NULL AND failure_code IS NULL)
        OR (state = 'COMPLETED' AND decided_at IS NOT NULL AND result_sha256 IS NOT NULL AND business_receipt_id IS NOT NULL
            AND failure_kind IS NULL AND failure_code IS NULL)
        OR (state = 'RETRY_SCHEDULED' AND decided_at IS NOT NULL AND result_sha256 IS NOT NULL AND business_receipt_id IS NULL
            AND failure_kind IS NOT NULL AND failure_kind = 'RETRYABLE' AND failure_code IS NOT NULL)
        OR (state = 'BUSINESS_REJECTED' AND decided_at IS NOT NULL AND result_sha256 IS NOT NULL AND business_receipt_id IS NULL
            AND failure_kind IS NOT NULL AND failure_kind = 'BUSINESS_REJECTED' AND failure_code IS NOT NULL)
        OR (state = 'NEEDS_MANUAL' AND decided_at IS NOT NULL AND result_sha256 IS NOT NULL AND business_receipt_id IS NULL
            AND failure_kind IS NOT NULL AND failure_kind IN ('RETRYABLE', 'BUSINESS_REJECTED', 'UNKNOWN') AND failure_code IS NOT NULL)
    ),
    UNIQUE (tenant_id, job_id, native_owner),
    FOREIGN KEY (tenant_id, release_key) REFERENCES tianji_process_release (tenant_id, release_key)
);

CREATE UNIQUE INDEX tianji_worker_lease_active_job_idx
    ON tianji_worker_lease_receipt (tenant_id, job_id)
    WHERE state = 'LEASED';
