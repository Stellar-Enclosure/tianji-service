CREATE TABLE tianji_agent.agent_context_snapshot (
    tenant_id bigint NOT NULL CONSTRAINT agent_context_tenant_positive CHECK (tenant_id > 0),
    run_id varchar(64) NOT NULL,
    reservation_id varchar(128) NOT NULL,
    model_call_number integer NOT NULL CONSTRAINT agent_context_call_positive CHECK (model_call_number > 0),
    attempt integer NOT NULL CONSTRAINT agent_context_attempt_positive CHECK (attempt > 0),
    lease_epoch bigint NOT NULL CONSTRAINT agent_context_epoch_positive CHECK (lease_epoch > 0),
    metadata jsonb NOT NULL,
    metadata_sha256 char(64) COLLATE "C" NOT NULL CONSTRAINT agent_context_metadata_hash CHECK (metadata_sha256 ~ '^[0-9a-f]{64}$'),
    captured_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, run_id, reservation_id),
    UNIQUE (tenant_id, run_id, model_call_number),
    FOREIGN KEY (tenant_id, run_id, reservation_id)
        REFERENCES tianji_agent.agent_budget_reservation (tenant_id, run_id, reservation_id) ON DELETE CASCADE,
    CONSTRAINT agent_context_metadata_size CHECK (octet_length(metadata::text) <= 294912),
    CONSTRAINT agent_context_metadata_object CHECK (jsonb_typeof(metadata) = 'object'),
    CONSTRAINT agent_context_metadata_version CHECK (metadata->>'schemaVersion' = '1'),
    CONSTRAINT agent_context_projection_version CHECK (metadata->>'projectionVersion' = 'ADK_GENAI_MODEL_V1'),
    CONSTRAINT agent_context_metadata_keys CHECK (metadata ?& ARRAY[
        'agentName', 'availableHistoryEventCount', 'definitionContentSha256', 'effectiveOutputLimit',
        'estimatedInputTokens', 'executionSnapshotSha256', 'historyEventIdsSha256', 'lastHistoryEventId',
        'leaseEpoch', 'modelAdapterSha256', 'nativeInvocationId', 'nativeRequestSha256', 'nativeSessionId',
        'ownerSha256', 'projectionVersion', 'requestSha256', 'reservationId', 'runId', 'schemaVersion',
        'sourcePolicySha256', 'sources', 'trimmingPolicySha256', 'trims', 'window'
    ]),
    CONSTRAINT agent_context_metadata_closed CHECK ((metadata - ARRAY[
        'agentName', 'availableHistoryEventCount', 'definitionContentSha256', 'effectiveOutputLimit',
        'estimatedInputTokens', 'executionSnapshotSha256', 'historyEventIdsSha256', 'lastHistoryEventId',
        'leaseEpoch', 'modelAdapterSha256', 'nativeInvocationId', 'nativeRequestSha256', 'nativeSessionId',
        'ownerSha256', 'projectionVersion', 'requestSha256', 'reservationId', 'runId', 'schemaVersion',
        'sourcePolicySha256', 'sources', 'trimmingPolicySha256', 'trims', 'window'
    ]) = '{}'::jsonb),
    CONSTRAINT agent_context_run_binding CHECK (metadata->'runId' = to_jsonb(run_id)),
    CONSTRAINT agent_context_call_binding CHECK (metadata->'reservationId' = to_jsonb(reservation_id)),
    CONSTRAINT agent_context_epoch_binding CHECK (metadata->'leaseEpoch' = to_jsonb(lease_epoch)),
    CONSTRAINT agent_context_window_object CHECK (jsonb_typeof(metadata->'window') = 'object'),
    CONSTRAINT agent_context_sources_array CHECK (jsonb_typeof(metadata->'sources') = 'array'),
    CONSTRAINT agent_context_sources_size CHECK (jsonb_array_length(metadata->'sources') >= 1 AND jsonb_array_length(metadata->'sources') <= 128),
    CONSTRAINT agent_context_trims_array CHECK (jsonb_typeof(metadata->'trims') = 'array'),
    CONSTRAINT agent_context_trims_size CHECK (jsonb_array_length(metadata->'trims') <= 128)
);
