CREATE TABLE tianji_process_release (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    release_key varchar(128) COLLATE "C" NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('PREPARING', 'ACTIVE')),
    manifest_sha256 char(64) NOT NULL CHECK (manifest_sha256 ~ '^[0-9a-f]{64}$'),
    publisher_type varchar(16) NOT NULL CHECK (publisher_type IN ('USER', 'SERVICE')),
    publisher_id varchar(256) COLLATE "C" NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, release_key)
);

CREATE TABLE tianji_process_release_definition (
    tenant_id bigint NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    model_type varchar(8) NOT NULL CHECK (model_type IN ('BPMN', 'DMN')),
    definition_key varchar(128) COLLATE "C" NOT NULL,
    engine_definition_id varchar(255) COLLATE "C" NOT NULL,
    engine_deployment_id varchar(255) COLLATE "C" NOT NULL,
    engine_version integer NOT NULL CHECK (engine_version > 0),
    model_sha256 char(64) NOT NULL CHECK (model_sha256 ~ '^[0-9a-f]{64}$'),
    input_schema jsonb NOT NULL CHECK (jsonb_typeof(input_schema) = 'object'),
    PRIMARY KEY (tenant_id, release_key, model_type, definition_key),
    UNIQUE (model_type, engine_definition_id),
    UNIQUE (model_type, engine_deployment_id),
    FOREIGN KEY (tenant_id, release_key) REFERENCES tianji_process_release (tenant_id, release_key)
);

CREATE TABLE tianji_process_command_receipt (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) NOT NULL CHECK (subject_type IN ('USER', 'SERVICE')),
    subject_id varchar(256) COLLATE "C" NOT NULL,
    command_type varchar(32) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    request_sha256 char(64) NOT NULL CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    release_key varchar(128) COLLATE "C" NOT NULL,
    correlation_id varchar(128) COLLATE "C" NOT NULL,
    state varchar(16) NOT NULL CHECK (state IN ('PENDING', 'APPLIED')),
    resource_id varchar(255) COLLATE "C",
    created_at timestamptz NOT NULL,
    CHECK ((state = 'PENDING' AND resource_id IS NULL) OR (state = 'APPLIED' AND resource_id IS NOT NULL)),
    PRIMARY KEY (tenant_id, subject_type, subject_id, command_type, command_id),
    FOREIGN KEY (tenant_id, release_key) REFERENCES tianji_process_release (tenant_id, release_key)
);

CREATE UNIQUE INDEX tianji_process_start_receipt_instance_idx
    ON tianji_process_command_receipt (tenant_id, resource_id)
    WHERE command_type = 'START' AND state = 'APPLIED';
