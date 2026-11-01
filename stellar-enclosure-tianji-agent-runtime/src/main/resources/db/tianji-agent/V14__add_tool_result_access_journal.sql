-- Immutable first-access audit for current-authorized reads of known START results.
-- The V13 action remains the sole owner of result content, receipts and source cost.
-- Writers must verify current RECONCILE authority and commit this fact with its own TOOL budget.

CREATE TABLE tianji_agent.agent_tool_result_access (
    tenant_id bigint NOT NULL,
    run_id varchar(64) NOT NULL,
    access_id uuid NOT NULL,
    reservation_id varchar(128) COLLATE "C" NOT NULL,
    owner_type varchar(16) COLLATE "C" NOT NULL,
    owner_issuer char(64) COLLATE "C" NOT NULL,
    owner_id varchar(256) COLLATE "C" NOT NULL,
    capability_id uuid NOT NULL,
    capability_binding_sha256 char(64) COLLATE "C" NOT NULL,
    capability_issue_epoch bigint NOT NULL,
    lease_epoch bigint NOT NULL,
    tool_key varchar(128) COLLATE "C" NOT NULL,
    tool_version bigint NOT NULL,
    tool_sha256 char(64) COLLATE "C" NOT NULL,
    backend_binding_sha256 char(64) COLLATE "C" NOT NULL,
    parameters_sha256 char(64) COLLATE "C" NOT NULL,
    intent_id uuid NOT NULL,
    request_sha256 char(64) COLLATE "C" NOT NULL,
    risk varchar(32) COLLATE "C" NOT NULL,
    source_action_revision bigint NOT NULL,
    source_result_sha256 char(64) COLLATE "C" NOT NULL,
    authorization_audit jsonb NOT NULL,
    authorization_checked_at timestamptz NOT NULL,
    authorization_expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL,
    CONSTRAINT agent_tool_result_access_pkey PRIMARY KEY (tenant_id,run_id,access_id),
    CONSTRAINT agent_tool_result_access_budget_key UNIQUE (tenant_id,run_id,reservation_id),
    CONSTRAINT agent_tool_result_access_run_fkey FOREIGN KEY (tenant_id,run_id) REFERENCES tianji_agent.agent_run (tenant_id,run_id) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_result_access_budget_fkey FOREIGN KEY (tenant_id,run_id,reservation_id) REFERENCES tianji_agent.agent_budget_reservation (tenant_id,run_id,reservation_id) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_result_access_capability_fkey FOREIGN KEY (tenant_id,run_id,capability_id,capability_binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) REFERENCES tianji_agent.agent_tool_capability (tenant_id,run_id,capability_id,binding_sha256,tool_key,tool_version,tool_sha256,backend_binding_sha256) ON DELETE NO ACTION,
    CONSTRAINT agent_tool_result_access_intent_fkey FOREIGN KEY (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,backend_binding_sha256,risk) REFERENCES tianji_agent.agent_tool_action_intent (tenant_id,intent_id,owner_type,owner_issuer,owner_id,tool_key,tool_version,tool_sha256,binding_sha256,risk) ON DELETE NO ACTION,
    CONSTRAINT tool_result_access_identity CHECK (
        tenant_id>0 AND reservation_id=('tool:'||access_id::text) AND owner_type = ANY (ARRAY['USER','SERVICE']) AND owner_issuer ~ '^[0-9a-f]{64}$' AND (owner_id <> '' AND owner_id = btrim(owner_id) AND owner_id !~ '[[:cntrl:]]') AND capability_binding_sha256 ~ '^[0-9a-f]{64}$' AND capability_issue_epoch>0 AND lease_epoch>0 AND tool_key ~ '^[A-Za-z][A-Za-z0-9_.:-]{0,127}$' AND tool_version>0 AND tool_sha256 ~ '^[0-9a-f]{64}$' AND backend_binding_sha256 ~ '^[0-9a-f]{64}$' AND parameters_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT tool_result_access_source CHECK (
        request_sha256 ~ '^[0-9a-f]{64}$' AND risk='IDEMPOTENT_WRITE' AND source_action_revision>0 AND source_result_sha256 ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT tool_result_access_authorization CHECK (
        (jsonb_typeof(authorization_audit)='object' AND authorization_audit ?& ARRAY['schemaVersion','localCheckId','boundary','decision','subjectType','grantId','authVersion','catalogVersion','policyVersion','checkedAt','expiresAt'] AND authorization_audit - ARRAY['schemaVersion','localCheckId','boundary','decision','subjectType','grantId','authVersion','catalogVersion','policyVersion','checkedAt','expiresAt'] = '{}'::jsonb)
        AND authorization_audit->'schemaVersion'='1'::jsonb
        AND jsonb_typeof(authorization_audit->'localCheckId')='string' AND (authorization_audit->>'localCheckId') ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        AND authorization_audit->'boundary'='"RECONCILE"'::jsonb AND authorization_audit->'decision'='"ALLOW"'::jsonb
        AND jsonb_typeof(authorization_audit->'subjectType')='string' AND authorization_audit->>'subjectType'=owner_type
        AND jsonb_typeof(authorization_audit->'grantId')='string'
        AND jsonb_typeof(authorization_audit->'policyVersion')='number' AND (authorization_audit->>'policyVersion') ~ '^[0-9]{1,19}$' AND (authorization_audit->>'policyVersion')::numeric<=9223372036854775807::numeric
        AND jsonb_typeof(authorization_audit->'checkedAt')='string' AND (authorization_audit->>'checkedAt') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{3}([0-9]{3})?)?Z$' AND (authorization_audit->>'checkedAt')::timestamptz=authorization_checked_at
        AND jsonb_typeof(authorization_audit->'expiresAt')='string' AND (authorization_audit->>'expiresAt') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\.[0-9]{3}([0-9]{3})?)?Z$' AND (authorization_audit->>'expiresAt')::timestamptz=authorization_expires_at
        AND ((owner_type='USER' AND (authorization_audit->>'grantId') ~ '^9[0-9]{62}$' AND jsonb_typeof(authorization_audit->'authVersion')='number' AND (authorization_audit->>'authVersion') ~ '^[0-9]{1,19}$' AND (authorization_audit->>'authVersion')::numeric<=9223372036854775807::numeric AND authorization_audit->'catalogVersion'='null'::jsonb)
        OR (owner_type='SERVICE' AND (authorization_audit->>'grantId') ~ '^8[0-9]{62}$' AND authorization_audit->'authVersion'='null'::jsonb AND jsonb_typeof(authorization_audit->'catalogVersion')='number' AND (authorization_audit->>'catalogVersion') ~ '^[1-9][0-9]{0,18}$' AND (authorization_audit->>'catalogVersion')::numeric<=9223372036854775807::numeric))
        AND octet_length(authorization_audit::text)<=4096
    ),
    CONSTRAINT tool_result_access_time CHECK (
        authorization_checked_at<=created_at AND created_at<authorization_expires_at
    )
);

CREATE INDEX agent_tool_result_access_intent_idx ON tianji_agent.agent_tool_result_access (tenant_id,intent_id,created_at,run_id,access_id);
