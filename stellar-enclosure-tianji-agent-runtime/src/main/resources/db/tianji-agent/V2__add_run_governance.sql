ALTER TABLE tianji_agent.agent_run
    ADD COLUMN model_calls_reserved integer NOT NULL DEFAULT 0 CHECK (model_calls_reserved >= 0),
    ADD COLUMN tool_calls_reserved integer NOT NULL DEFAULT 0 CHECK (tool_calls_reserved >= 0),
    ADD COLUMN active_model_calls integer NOT NULL DEFAULT 0 CHECK (active_model_calls >= 0),
    ADD COLUMN active_tool_calls integer NOT NULL DEFAULT 0 CHECK (active_tool_calls >= 0),
    ADD COLUMN input_tokens_reserved bigint NOT NULL DEFAULT 0 CHECK (input_tokens_reserved >= 0),
    ADD COLUMN output_tokens_reserved bigint NOT NULL DEFAULT 0 CHECK (output_tokens_reserved >= 0),
    ADD COLUMN estimated_cost_reserved numeric(24, 8) NOT NULL DEFAULT 0 CHECK (estimated_cost_reserved >= 0);

CREATE TABLE tianji_agent.agent_run_command_receipt (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) NOT NULL CHECK (subject_type IN ('USER', 'SERVICE')),
    subject_id varchar(256) NOT NULL,
    command_id varchar(128) NOT NULL,
    operation varchar(32) NOT NULL,
    request_hash char(64) NOT NULL,
    run_id varchar(64) NOT NULL,
    result jsonb,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (tenant_id, subject_type, subject_id, operation, command_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES tianji_agent.agent_run (tenant_id, run_id)
);

CREATE TABLE tianji_agent.agent_budget_reservation (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    reservation_id varchar(128) NOT NULL,
    lease_epoch bigint NOT NULL CHECK (lease_epoch > 0),
    kind varchar(16) NOT NULL CHECK (kind IN ('MODEL', 'TOOL')),
    tool_version varchar(256),
    input_tokens bigint NOT NULL CHECK (input_tokens >= 0),
    output_tokens bigint NOT NULL CHECK (output_tokens >= 0),
    estimated_cost numeric(24, 8) CHECK (estimated_cost >= 0),
    state varchar(16) NOT NULL CHECK (state IN ('RESERVED', 'SETTLED', 'UNKNOWN')),
    actual_input_tokens bigint CHECK (actual_input_tokens >= 0),
    actual_output_tokens bigint CHECK (actual_output_tokens >= 0),
    actual_cost numeric(24, 8) CHECK (actual_cost >= 0),
    created_at timestamptz NOT NULL,
    settled_at timestamptz,
    CHECK ((kind = 'MODEL' AND tool_version IS NULL) OR (kind = 'TOOL' AND tool_version IS NOT NULL)),
    PRIMARY KEY (tenant_id, run_id, reservation_id),
    FOREIGN KEY (tenant_id, run_id) REFERENCES tianji_agent.agent_run (tenant_id, run_id)
);
