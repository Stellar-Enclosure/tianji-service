CREATE TABLE tianji_agent.agent_memory_scope (
    tenant_id bigint NOT NULL CONSTRAINT memory_scope_tenant CHECK (tenant_id > 0),
    owner_issuer char(64) COLLATE "C" NOT NULL CONSTRAINT memory_scope_issuer CHECK (owner_issuer ~ '^[0-9a-f]{64}$'),
    owner_type varchar(16) COLLATE "C" NOT NULL CONSTRAINT memory_scope_owner_type CHECK (owner_type = ANY (ARRAY['USER', 'TEAM', 'APPLICATION'])),
    owner_id varchar(256) COLLATE "C" NOT NULL CONSTRAINT memory_scope_owner_id CHECK (length(owner_id) > 0),
    scope_id varchar(128) COLLATE "C" NOT NULL CONSTRAINT memory_scope_id CHECK (length(scope_id) > 0),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id),
    CONSTRAINT memory_scope_dates CHECK (updated_at >= created_at)
);

CREATE TABLE tianji_agent.agent_memory_entry (
    tenant_id bigint NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    scope_id varchar(128) COLLATE "C" NOT NULL,
    memory_id varchar(128) COLLATE "C" NOT NULL CONSTRAINT memory_entry_id CHECK (length(memory_id) > 0),
    kind varchar(16) NOT NULL CONSTRAINT memory_entry_kind CHECK (kind = ANY (ARRAY['FACT', 'PREFERENCE', 'EXPERIENCE'])),
    current_revision bigint NOT NULL CONSTRAINT memory_entry_revision CHECK (current_revision > 0),
    state varchar(16) NOT NULL CONSTRAINT memory_entry_state CHECK (state = ANY (ARRAY['CANDIDATE', 'ACTIVE', 'FORGOTTEN', 'EXPIRED'])),
    content_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_entry_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    confirmed boolean NOT NULL,
    sensitivity varchar(16) NOT NULL CONSTRAINT memory_entry_sensitivity CHECK (sensitivity = ANY (ARRAY['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'])),
    retention_policy_ref varchar(256) COLLATE "C" NOT NULL,
    index_model_ref varchar(256) COLLATE "C" NOT NULL,
    effective_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    retired_at timestamptz,
    PRIMARY KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id),
    FOREIGN KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id)
        REFERENCES tianji_agent.agent_memory_scope (tenant_id, owner_issuer, owner_type, owner_id, scope_id),
    CONSTRAINT memory_entry_confirmation CHECK ((state <> 'ACTIVE' OR confirmed) AND (state <> 'CANDIDATE' OR NOT confirmed)),
    CONSTRAINT memory_entry_dates CHECK (expires_at > effective_at AND updated_at >= created_at),
    CONSTRAINT memory_entry_retired CHECK ((state = ANY (ARRAY['FORGOTTEN', 'EXPIRED'])) = (retired_at IS NOT NULL))
);

CREATE UNIQUE INDEX agent_memory_content_dedup_idx
    ON tianji_agent.agent_memory_entry (tenant_id, owner_issuer, owner_type, owner_id, scope_id, kind, content_sha256)
    WHERE state = ANY (ARRAY['CANDIDATE', 'ACTIVE']);
CREATE INDEX agent_memory_expiry_idx
    ON tianji_agent.agent_memory_entry (expires_at, tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id)
    WHERE state = ANY (ARRAY['CANDIDATE', 'ACTIVE']);

CREATE TABLE tianji_agent.agent_memory_revision (
    tenant_id bigint NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    scope_id varchar(128) COLLATE "C" NOT NULL,
    memory_id varchar(128) COLLATE "C" NOT NULL,
    revision bigint NOT NULL CONSTRAINT memory_revision_number CHECK (revision > 0),
    supersedes_revision bigint,
    state varchar(16) NOT NULL CONSTRAINT memory_revision_state CHECK (state = ANY (ARRAY['CANDIDATE', 'ACTIVE', 'SUPERSEDED', 'FORGOTTEN', 'EXPIRED'])),
    content_json jsonb,
    source_json jsonb,
    content_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_revision_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    source_evidence_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_revision_evidence CHECK (source_evidence_sha256 ~ '^[0-9a-f]{64}$'),
    source_actor_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_revision_actor CHECK (source_actor_sha256 ~ '^[0-9a-f]{64}$'),
    confidence numeric(7, 6) NOT NULL CONSTRAINT memory_revision_confidence CHECK (confidence >= 0::numeric AND confidence <= 1::numeric),
    confirmed boolean NOT NULL,
    sensitivity varchar(16) NOT NULL CONSTRAINT memory_revision_sensitivity CHECK (sensitivity = ANY (ARRAY['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'])),
    retention_policy_ref varchar(256) COLLATE "C" NOT NULL,
    index_model_ref varchar(256) COLLATE "C" NOT NULL,
    effective_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    written_at timestamptz NOT NULL,
    reason_code varchar(64) NOT NULL CONSTRAINT memory_revision_reason CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    redacted_at timestamptz,
    PRIMARY KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, revision),
    FOREIGN KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id)
        REFERENCES tianji_agent.agent_memory_entry (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id) ON DELETE CASCADE,
    CONSTRAINT memory_revision_supersedes CHECK (supersedes_revision IS NULL OR (supersedes_revision > 0 AND supersedes_revision < revision)),
    CONSTRAINT memory_revision_confirmation CHECK ((state <> 'ACTIVE' OR confirmed) AND (state <> 'CANDIDATE' OR NOT confirmed)),
    CONSTRAINT memory_revision_dates CHECK (expires_at > effective_at),
    CONSTRAINT memory_revision_redaction CHECK (
        (state = ANY (ARRAY['FORGOTTEN', 'EXPIRED']) AND content_json IS NULL AND source_json IS NULL AND redacted_at IS NOT NULL)
        OR (state = ANY (ARRAY['CANDIDATE', 'ACTIVE', 'SUPERSEDED']) AND content_json IS NOT NULL AND source_json IS NOT NULL AND redacted_at IS NULL)),
    CONSTRAINT memory_revision_content_size CHECK (octet_length(content_json::text) <= 131072),
    CONSTRAINT memory_revision_content_shape CHECK (jsonb_typeof(content_json) = 'object'
        AND content_json ?& ARRAY['facts', 'text'] AND (content_json - ARRAY['facts', 'text']) = '{}'::jsonb
        AND jsonb_typeof(content_json->'facts') = 'object'
        AND jsonb_typeof(content_json->'text') = ANY (ARRAY['string', 'null'])),
    CONSTRAINT memory_revision_source_size CHECK (octet_length(source_json::text) <= 8192),
    CONSTRAINT memory_revision_source_shape CHECK (jsonb_typeof(source_json) = 'object'
        AND source_json ?& ARRAY['evidenceSha256', 'reference', 'sourceEventId', 'sourceRunId']
        AND (source_json - ARRAY['evidenceSha256', 'reference', 'sourceEventId', 'sourceRunId']) = '{}'::jsonb
        AND jsonb_typeof(source_json->'reference') = 'string'
        AND source_json->'evidenceSha256' = to_jsonb(source_evidence_sha256)
        AND jsonb_typeof(source_json->'sourceEventId') = ANY (ARRAY['string', 'null'])
        AND jsonb_typeof(source_json->'sourceRunId') = ANY (ARRAY['string', 'null'])
        AND ((source_json->'sourceRunId' = 'null'::jsonb) = (source_json->'sourceEventId' = 'null'::jsonb)))
);

ALTER TABLE tianji_agent.agent_memory_entry ADD CONSTRAINT memory_entry_current_revision_fk
    FOREIGN KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, current_revision)
    REFERENCES tianji_agent.agent_memory_revision (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, revision)
    DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE tianji_agent.agent_memory_command_receipt (
    tenant_id bigint NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    scope_id varchar(128) COLLATE "C" NOT NULL,
    actor_type varchar(16) COLLATE "C" NOT NULL CONSTRAINT memory_command_actor CHECK (actor_type = ANY (ARRAY['USER', 'SERVICE'])),
    actor_id varchar(256) COLLATE "C" NOT NULL CONSTRAINT memory_command_subject CHECK (length(actor_id) > 0),
    operation varchar(16) COLLATE "C" NOT NULL CONSTRAINT memory_command_operation CHECK (operation = ANY (ARRAY['UPSERT', 'CORRECT', 'FORGET'])),
    command_id varchar(128) COLLATE "C" NOT NULL CONSTRAINT memory_command_id CHECK (length(command_id) > 0),
    request_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_command_hash CHECK (request_sha256 ~ '^[0-9a-f]{64}$'),
    result_memory_id varchar(128) COLLATE "C" NOT NULL,
    result_revision bigint NOT NULL CONSTRAINT memory_command_revision CHECK (result_revision > 0),
    result_outcome varchar(16) NOT NULL CONSTRAINT memory_command_outcome CHECK (result_outcome = ANY (ARRAY['CREATED', 'DEDUPLICATED', 'CORRECTED', 'FORGOTTEN'])),
    result_state varchar(16) NOT NULL CONSTRAINT memory_command_state CHECK (result_state = ANY (ARRAY['CANDIDATE', 'ACTIVE', 'FORGOTTEN'])),
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, actor_type, actor_id, operation, command_id),
    FOREIGN KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, result_memory_id, result_revision)
        REFERENCES tianji_agent.agent_memory_revision (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, revision),
    CONSTRAINT memory_command_result CHECK (
        (operation = 'UPSERT' AND result_outcome = ANY (ARRAY['CREATED', 'DEDUPLICATED']) AND result_state = ANY (ARRAY['CANDIDATE', 'ACTIVE']))
        OR (operation = 'CORRECT' AND result_outcome = 'CORRECTED' AND result_state = 'ACTIVE')
        OR (operation = 'FORGET' AND result_outcome = 'FORGOTTEN' AND result_state = 'FORGOTTEN'))
);

CREATE TABLE tianji_agent.agent_memory_index_manifest (
    tenant_id bigint NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    scope_id varchar(128) COLLATE "C" NOT NULL,
    memory_id varchar(128) COLLATE "C" NOT NULL,
    content_revision bigint NOT NULL CONSTRAINT memory_manifest_revision CHECK (content_revision > 0),
    model_ref varchar(256) COLLATE "C" NOT NULL,
    document_id uuid NOT NULL,
    vector_table varchar(63) COLLATE "C" NOT NULL CONSTRAINT memory_manifest_table CHECK (vector_table ~ '^agent_memory_vector(_[a-z0-9]+)?$'),
    dimensions integer NOT NULL CONSTRAINT memory_manifest_dimensions CHECK (dimensions >= 1 AND dimensions <= 8192),
    content_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_manifest_hash CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    index_state varchar(16) NOT NULL CONSTRAINT memory_manifest_state CHECK (index_state = ANY (ARRAY['PENDING', 'ACTIVE', 'DELETED'])),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    last_verified_at timestamptz,
    deleted_at timestamptz,
    PRIMARY KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, content_revision, model_ref),
    UNIQUE (document_id),
    FOREIGN KEY (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, content_revision)
        REFERENCES tianji_agent.agent_memory_revision (tenant_id, owner_issuer, owner_type, owner_id, scope_id, memory_id, revision),
    CONSTRAINT memory_manifest_dates CHECK (updated_at >= created_at),
    CONSTRAINT memory_manifest_verified CHECK (index_state <> 'ACTIVE' OR last_verified_at IS NOT NULL),
    CONSTRAINT memory_manifest_deleted CHECK ((index_state = 'DELETED') = (deleted_at IS NOT NULL))
);

CREATE TABLE tianji_agent.agent_memory_vector_v1 (
    id uuid PRIMARY KEY,
    content text NOT NULL CONSTRAINT memory_vector_content_size CHECK (octet_length(content) <= 65536),
    metadata jsonb NOT NULL,
    embedding public.vector(${tianjiMemoryDimensions}) NOT NULL,
    CONSTRAINT memory_vector_metadata_size CHECK (octet_length(metadata::text) <= 8192),
    CONSTRAINT memory_vector_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT memory_vector_metadata_keys CHECK (metadata ?& ARRAY[
        'contentRevision', 'contentSha256', 'dimensions', 'effectiveAtMillis', 'expiresAtMillis', 'issuerFingerprint', 'kind', 'memoryId',
        'modelRef', 'ownerId', 'ownerType', 'retentionPolicyRef', 'scopeId', 'sensitivity', 'tenantId']),
    CONSTRAINT memory_vector_metadata_closed CHECK ((metadata - ARRAY[
        'contentRevision', 'contentSha256', 'dimensions', 'effectiveAtMillis', 'expiresAtMillis', 'issuerFingerprint', 'kind', 'memoryId',
        'modelRef', 'ownerId', 'ownerType', 'retentionPolicyRef', 'scopeId', 'sensitivity', 'tenantId']) = '{}'::jsonb)
);
CREATE INDEX agent_memory_vector_metadata_idx ON tianji_agent.agent_memory_vector_v1 USING gin (metadata jsonb_path_ops);

CREATE TABLE tianji_agent.agent_memory_run_grant (
    tenant_id bigint NOT NULL,
    run_id varchar(64) COLLATE "C" NOT NULL,
    source_binding_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_grant_source CHECK (source_binding_sha256 ~ '^[0-9a-f]{64}$'),
    owner_issuer char(64) COLLATE "C" NOT NULL CONSTRAINT memory_grant_issuer CHECK (owner_issuer ~ '^[0-9a-f]{64}$'),
    owner_type varchar(16) COLLATE "C" NOT NULL CONSTRAINT memory_grant_owner_type CHECK (owner_type = ANY (ARRAY['USER', 'TEAM', 'APPLICATION'])),
    owner_id varchar(256) COLLATE "C" NOT NULL,
    scope_id varchar(128) COLLATE "C" NOT NULL,
    actor_type varchar(16) COLLATE "C" NOT NULL CONSTRAINT memory_grant_actor_type CHECK (actor_type = ANY (ARRAY['USER', 'SERVICE'])),
    actor_id varchar(256) COLLATE "C" NOT NULL,
    binding_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT memory_grant_binding CHECK (binding_sha256 ~ '^[0-9a-f]{64}$'),
    intent_id uuid NOT NULL,
    issue_epoch bigint NOT NULL CONSTRAINT memory_grant_epoch CHECK (issue_epoch > 0),
    grant_id varchar(63) COLLATE "C",
    ciphertext text,
    expires_at timestamptz,
    updated_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, run_id, source_binding_sha256),
    UNIQUE (tenant_id, intent_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES tianji_agent.agent_run (tenant_id, run_id) ON DELETE CASCADE,
    CONSTRAINT memory_grant_ticket CHECK ((grant_id IS NULL AND ciphertext IS NULL AND expires_at IS NULL)
        OR (grant_id IS NOT NULL AND grant_id ~ '^9[0-9]{62}$' AND ciphertext IS NOT NULL AND ciphertext LIKE 'ENC:v1:%'
            AND octet_length(ciphertext) <= 32768 AND expires_at IS NOT NULL))
);

CREATE VIEW tianji_agent.agent_memory_vector_v1_active WITH (security_barrier=true,security_invoker=true) AS
SELECT v.id,v.content,v.metadata,v.embedding
FROM tianji_agent.agent_memory_vector_v1 v
JOIN tianji_agent.agent_memory_index_manifest m ON m.document_id=v.id
JOIN tianji_agent.agent_memory_entry e
  ON e.tenant_id=m.tenant_id AND e.owner_issuer=m.owner_issuer AND e.owner_type=m.owner_type
 AND e.owner_id=m.owner_id AND e.scope_id=m.scope_id AND e.memory_id=m.memory_id
WHERE m.index_state='ACTIVE' AND e.state='ACTIVE' AND e.confirmed
  AND e.current_revision=m.content_revision AND e.content_sha256=m.content_sha256 AND e.index_model_ref=m.model_ref
  AND m.vector_table='agent_memory_vector_v1' AND m.dimensions=${tianjiMemoryDimensions}
  AND e.effective_at<=clock_timestamp() AND e.expires_at>clock_timestamp()
  AND v.metadata->'tenantId'=to_jsonb(e.tenant_id::text)
  AND v.metadata->'issuerFingerprint'=to_jsonb(e.owner_issuer)
  AND v.metadata->'ownerType'=to_jsonb(e.owner_type)
  AND v.metadata->'ownerId'=to_jsonb(e.owner_id)
  AND v.metadata->'scopeId'=to_jsonb(e.scope_id)
  AND v.metadata->'memoryId'=to_jsonb(e.memory_id)
  AND v.metadata->'contentRevision'=to_jsonb(m.content_revision::text)
  AND v.metadata->'modelRef'=to_jsonb(m.model_ref)
  AND v.metadata->'contentSha256'=to_jsonb(m.content_sha256)
  AND v.metadata->'dimensions'=to_jsonb(m.dimensions::text)
  AND v.metadata->'kind'=to_jsonb(e.kind)
  AND v.metadata->'sensitivity'=to_jsonb(e.sensitivity)
  AND v.metadata->'retentionPolicyRef'=to_jsonb(e.retention_policy_ref)
  AND v.metadata->'effectiveAtMillis'=to_jsonb(floor(EXTRACT(epoch FROM e.effective_at)*1000::numeric)::bigint)
  AND v.metadata->'expiresAtMillis'=to_jsonb(floor(EXTRACT(epoch FROM e.expires_at)*1000::numeric)::bigint);
