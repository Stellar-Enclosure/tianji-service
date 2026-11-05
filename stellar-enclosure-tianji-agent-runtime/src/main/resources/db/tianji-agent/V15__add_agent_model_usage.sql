-- Actual MODEL measurement for one existing Agent budget reservation.
-- Apply externally. The owning transaction verifies MODEL kind and the exact lease epoch.
CREATE TABLE tianji_agent.agent_model_usage (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    reservation_id varchar(128) COLLATE "C" NOT NULL,
    lease_epoch bigint NOT NULL,
    model_route_ref varchar(256) COLLATE "C" NOT NULL,
    model_name varchar(512) COLLATE "C" NOT NULL,
    price_version_ref char(64) COLLATE "C",
    currency char(3) COLLATE "C",
    input_rate_per_million numeric(32, 8),
    output_rate_per_million numeric(32, 8),
    state varchar(16) COLLATE "C" NOT NULL DEFAULT 'PENDING',
    actual_input_tokens bigint,
    actual_output_tokens bigint,
    actual_cost numeric(24, 8),
    safe_reason varchar(32) COLLATE "C",
    final_sha256 char(64) COLLATE "C",
    created_at timestamptz(6) NOT NULL,
    measured_at timestamptz(6),
    CONSTRAINT agent_model_usage_pkey PRIMARY KEY (tenant_id, run_id, reservation_id),
    CONSTRAINT agent_model_usage_budget_fkey
        FOREIGN KEY (tenant_id, run_id, reservation_id)
        REFERENCES tianji_agent.agent_budget_reservation (tenant_id, run_id, reservation_id)
        ON UPDATE NO ACTION ON DELETE NO ACTION,
    CONSTRAINT agent_model_usage_scope CHECK (tenant_id > 0 AND lease_epoch > 0),
    CONSTRAINT agent_model_usage_model CHECK (
        model_route_ref <> '' AND model_route_ref = btrim(model_route_ref) AND model_route_ref !~ '[[:cntrl:]]'
        AND model_name <> '' AND model_name = btrim(model_name) AND model_name !~ '[[:cntrl:]]'
    ),
    CONSTRAINT agent_model_usage_price CHECK (
        CASE WHEN price_version_ref IS NULL THEN
            currency IS NULL AND input_rate_per_million IS NULL AND output_rate_per_million IS NULL
        ELSE
            currency IS NOT NULL AND input_rate_per_million IS NOT NULL AND output_rate_per_million IS NOT NULL
            AND price_version_ref ~ '^[0-9a-f]{64}$' AND currency ~ '^[A-Z]{3}$'
            AND input_rate_per_million >= 0 AND input_rate_per_million < 'Infinity'::numeric
            AND output_rate_per_million >= 0 AND output_rate_per_million < 'Infinity'::numeric
        END
    ),
    CONSTRAINT agent_model_usage_tokens CHECK (
        CASE WHEN actual_input_tokens IS NULL THEN
            actual_output_tokens IS NULL
        ELSE
            actual_output_tokens IS NOT NULL AND actual_input_tokens >= 0 AND actual_output_tokens >= 0
            AND actual_input_tokens::numeric + actual_output_tokens::numeric <= '9223372036854775807'::numeric
        END
    ),
    CONSTRAINT agent_model_usage_cost CHECK (
        CASE WHEN actual_cost IS NULL THEN true
        ELSE actual_cost >= 0 AND actual_cost < 'Infinity'::numeric
        END
    ),
    CONSTRAINT agent_model_usage_digest CHECK (final_sha256 IS NULL OR final_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT agent_model_usage_time CHECK (
        CASE WHEN measured_at IS NULL THEN isfinite(created_at)
        ELSE isfinite(created_at) AND isfinite(measured_at) AND measured_at >= created_at
        END
    ),
    CONSTRAINT agent_model_usage_lifecycle CHECK (
        CASE state
        WHEN 'PENDING' THEN
            actual_input_tokens IS NULL AND actual_output_tokens IS NULL AND actual_cost IS NULL
            AND safe_reason IS NULL AND final_sha256 IS NULL AND measured_at IS NULL
        WHEN 'UNKNOWN' THEN
            actual_input_tokens IS NULL AND actual_output_tokens IS NULL AND actual_cost IS NULL
            AND safe_reason IS NOT NULL AND safe_reason = ANY (ARRAY['MISSING_USAGE','INVALID_USAGE','MODEL_ERROR'])
            AND final_sha256 IS NULL AND measured_at IS NULL
        WHEN 'UNPRICED' THEN
            actual_input_tokens IS NOT NULL AND actual_output_tokens IS NOT NULL AND actual_cost IS NULL
            AND final_sha256 IS NOT NULL AND measured_at IS NOT NULL
            AND CASE safe_reason
                WHEN 'PRICE_MISSING' THEN price_version_ref IS NULL
                WHEN 'PRICE_OVERFLOW' THEN price_version_ref IS NOT NULL
                ELSE false
            END
        WHEN 'PRICED' THEN
            actual_input_tokens IS NOT NULL AND actual_output_tokens IS NOT NULL AND actual_cost IS NOT NULL
            AND safe_reason IS NULL AND final_sha256 IS NOT NULL AND measured_at IS NOT NULL
            AND price_version_ref IS NOT NULL
        ELSE false
        END
    )
);
