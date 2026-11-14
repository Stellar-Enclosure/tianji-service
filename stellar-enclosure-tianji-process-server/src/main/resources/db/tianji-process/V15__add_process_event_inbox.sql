-- Process event intake and wait reservations. Apply externally in the existing Process Flyway history.
CREATE TABLE tianji_process_event_inbox (
    tenant_id bigint NOT NULL,
    producer_issuer char(64) COLLATE "C" NOT NULL,
    producer_subject varchar(256) COLLATE "C" NOT NULL,
    event_id varchar(128) COLLATE "C" NOT NULL,
    receipt_id varchar(128) COLLATE "C" NOT NULL,
    fingerprint char(64) COLLATE "C" NOT NULL,
    route_key varchar(128) COLLATE "C" NOT NULL,
    route_version bigint NOT NULL,
    route_sha256 char(64) COLLATE "C" NOT NULL,
    release_key varchar(128) COLLATE "C" NOT NULL,
    mode varchar(8) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C",
    wait_token_sha256 char(64) COLLATE "C",
    payload jsonb NOT NULL,
    correlation_key varchar(255) COLLATE "C" NOT NULL,
    state varchar(24) COLLATE "C" NOT NULL,
    instance_id varchar(255) COLLATE "C",
    node_execution_ref varchar(128) COLLATE "C",
    occurred_at timestamptz NOT NULL,
    received_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    decided_at timestamptz,
    next_retry_at timestamptz,
    attempts integer NOT NULL,
    decision_code varchar(64) COLLATE "C",
    definition_key varchar(128) COLLATE "C" NOT NULL,
    activation_revision bigint NOT NULL,
    admitted_at timestamptz(6) NOT NULL,
    admission_not_after timestamptz(6),
    admission_json jsonb,
    CONSTRAINT tianji_process_event_inbox_pkey PRIMARY KEY (tenant_id,producer_issuer,producer_subject,event_id),
    CONSTRAINT process_event_receipt_uq UNIQUE (tenant_id,receipt_id),
    CONSTRAINT event_instance_fk FOREIGN KEY (tenant_id,instance_id)
        REFERENCES tianji_process_instance_binding (tenant_id,instance_id) ON DELETE NO ACTION,
    CONSTRAINT event_activation_fk FOREIGN KEY (tenant_id,definition_key,activation_revision,release_key)
        REFERENCES tianji_process_automation_activation (tenant_id,definition_key,revision,release_key) ON DELETE NO ACTION,
    CONSTRAINT event_inbox_identity_check CHECK (
        tenant_id>0 AND producer_issuer ~ '^[0-9a-f]{64}$' AND producer_subject<>''
        AND event_id<>'' AND receipt_id<>'' AND route_key<>'' AND release_key<>'' AND definition_key<>'' AND correlation_key<>''
        AND fingerprint ~ '^[0-9a-f]{64}$' AND route_sha256 ~ '^[0-9a-f]{64}$' AND route_version>0 AND activation_revision>0
    ),
    CONSTRAINT event_inbox_payload_check CHECK (
        jsonb_typeof(payload)='object' AND octet_length(payload::text)<=65536
    ),
    CONSTRAINT event_inbox_admission_check CHECK (
        admitted_at=received_at AND expires_at>received_at
        AND ((mode='START' AND command_id IS NOT NULL AND command_id ~ '^evt-v2-[0-9a-f]{64}$'
            AND wait_token_sha256 IS NULL AND node_execution_ref IS NULL
            AND admission_not_after IS NOT NULL AND admission_not_after>admitted_at AND admission_not_after<=expires_at
            AND EXTRACT(epoch FROM (admission_not_after-admitted_at))<=86400::numeric
            AND admission_json IS NOT NULL AND jsonb_typeof(admission_json)='object' AND octet_length(admission_json::text)<=524288)
        OR (mode='SIGNAL' AND command_id IS NULL AND wait_token_sha256 IS NOT NULL AND wait_token_sha256 ~ '^[0-9a-f]{64}$'
            AND admission_not_after IS NULL AND admission_json IS NULL))
    ),
    CONSTRAINT event_inbox_state_check CHECK (
        attempts>=0 AND (instance_id IS NULL OR instance_id<>'')
        AND (node_execution_ref IS NULL OR (node_execution_ref<>'' AND instance_id IS NOT NULL))
        AND ((state='RECEIVED' AND decided_at IS NULL AND decision_code IS NULL AND node_execution_ref IS NULL
            AND instance_id IS NULL AND next_retry_at IS NOT NULL AND next_retry_at>=received_at)
        OR (state = ANY (ARRAY['APPLIED','IGNORED_LATE','REJECTED','DEAD']) AND decided_at IS NOT NULL
            AND decided_at>=received_at AND decision_code IS NOT NULL AND decision_code<>'' AND next_retry_at IS NULL
            AND ((state='APPLIED' AND decision_code='APPLIED' AND instance_id IS NOT NULL
                AND (mode='START' OR node_execution_ref IS NOT NULL))
                OR (state<>'APPLIED' AND decision_code<>'APPLIED'))))
    )
);

CREATE TABLE tianji_process_event_subscription (
    tenant_id bigint NOT NULL,
    token_sha256 char(64) COLLATE "C" NOT NULL,
    instance_id varchar(255) COLLATE "C" NOT NULL,
    parent_scope_id varchar(255) COLLATE "C" NOT NULL,
    target_node_id varchar(255) COLLATE "C" NOT NULL,
    reservation_id uuid NOT NULL,
    route_key varchar(128) COLLATE "C" NOT NULL,
    route_version bigint NOT NULL,
    source_ref varchar(128) COLLATE "C" NOT NULL,
    native_execution_id varchar(255) COLLATE "C",
    native_subscription_id varchar(255) COLLATE "C",
    node_execution_ref varchar(128) COLLATE "C",
    state varchar(16) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    deadline_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    closed_at timestamptz,
    CONSTRAINT tianji_process_event_subscription_pkey PRIMARY KEY (tenant_id,token_sha256),
    CONSTRAINT event_reservation_uq UNIQUE (tenant_id,reservation_id),
    CONSTRAINT subscription_instance_fk FOREIGN KEY (tenant_id,instance_id)
        REFERENCES tianji_process_instance_binding (tenant_id,instance_id) ON DELETE NO ACTION,
    CONSTRAINT event_subscription_identity_check CHECK (
        tenant_id>0 AND token_sha256 ~ '^[0-9a-f]{64}$' AND instance_id<>'' AND parent_scope_id<>'' AND target_node_id<>''
        AND route_key<>'' AND route_version>0 AND source_ref<>'' AND revision>0 AND deadline_at>created_at
    ),
    CONSTRAINT event_subscription_native_check CHECK (
        (state = ANY (ARRAY['RESERVED','CLOSED']) AND native_execution_id IS NULL AND native_subscription_id IS NULL AND node_execution_ref IS NULL)
        OR (state = ANY (ARRAY['BOUND','CONSUMED','CLOSED'])
            AND native_execution_id IS NOT NULL AND native_execution_id<>''
            AND native_subscription_id IS NOT NULL AND native_subscription_id<>''
            AND node_execution_ref IS NOT NULL AND node_execution_ref<>'')
    ),
    CONSTRAINT event_subscription_state_check CHECK (
        (state = ANY (ARRAY['RESERVED','BOUND']) AND closed_at IS NULL)
        OR (state = ANY (ARRAY['CONSUMED','CLOSED']) AND closed_at IS NOT NULL AND closed_at>=created_at)
    )
);

CREATE INDEX process_event_pending_idx ON tianji_process_event_inbox (next_retry_at,tenant_id,receipt_id)
    WHERE state='RECEIVED';
CREATE INDEX process_event_wait_idx ON tianji_process_event_inbox (tenant_id,wait_token_sha256,received_at)
    WHERE mode='SIGNAL' AND state='RECEIVED';
CREATE INDEX event_subscription_scope_idx ON tianji_process_event_subscription (tenant_id,instance_id,parent_scope_id,target_node_id)
    WHERE state = ANY (ARRAY['RESERVED','BOUND']);

ALTER TABLE tianji_process_instance_binding ADD CONSTRAINT instance_event_source_fk
    FOREIGN KEY (tenant_id,source_event_receipt_id) REFERENCES tianji_process_event_inbox (tenant_id,receipt_id) ON DELETE NO ACTION;
