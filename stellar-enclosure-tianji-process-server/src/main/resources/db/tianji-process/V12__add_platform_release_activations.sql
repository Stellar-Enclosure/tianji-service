-- Append-only channel activation and rollback history in the owning Process schema.
CREATE TABLE tianji_platform_release_activation (
    tenant_id bigint NOT NULL,
    channel varchar(64) COLLATE "C" NOT NULL,
    revision bigint NOT NULL,
    candidate_sha256 char(64) COLLATE "C" NOT NULL,
    previous_candidate_sha256 char(64) COLLATE "C",
    action varchar(8) COLLATE "C" NOT NULL,
    actor_type varchar(16) COLLATE "C" NOT NULL,
    actor_issuer char(64) COLLATE "C" NOT NULL,
    actor_subject varchar(256) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    process_release_version varchar(128) COLLATE "C" NOT NULL,
    process_manifest_sha256 char(64) COLLATE "C" NOT NULL,
    process_definition_key varchar(128) COLLATE "C" NOT NULL,
    process_model_sha256 char(64) COLLATE "C" NOT NULL,
    worker_topic varchar(128) COLLATE "C" NOT NULL,
    worker_schema_version varchar(128) COLLATE "C" NOT NULL,
    worker_binding_sha256 char(64) COLLATE "C" NOT NULL,
    authorization_sha256 char(64) COLLATE "C" NOT NULL,
    dependency_evidence_sha256 char(64) COLLATE "C" NOT NULL,
    trust_generation bigint NOT NULL,
    activated_at timestamptz(6) NOT NULL,
    CONSTRAINT tianji_platform_release_activation_pkey PRIMARY KEY (tenant_id, channel, revision),
    CONSTRAINT tianji_platform_release_activation_review_fk FOREIGN KEY (tenant_id, channel, candidate_sha256)
        REFERENCES tianji_platform_release_review (tenant_id, channel, candidate_sha256),
    CONSTRAINT tianji_platform_release_activation_previous_fk FOREIGN KEY (tenant_id, channel, previous_candidate_sha256)
        REFERENCES tianji_platform_release_review (tenant_id, channel, candidate_sha256),
    CONSTRAINT tianji_platform_release_activation_scope
        CHECK (tenant_id > 0 AND channel ~ '^[a-z][a-z0-9._-]{0,63}$' AND revision > 0
            AND trust_generation > 0 AND candidate_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tianji_platform_release_activation_chain
        CHECK (action = ANY(ARRAY['ACTIVATE', 'ROLLBACK'])
            AND ((revision = 1 AND action = 'ACTIVATE' AND previous_candidate_sha256 IS NULL)
                OR (revision > 1 AND previous_candidate_sha256 IS NOT NULL
                    AND previous_candidate_sha256 ~ '^[0-9a-f]{64}$' AND previous_candidate_sha256 <> candidate_sha256))),
    CONSTRAINT tianji_platform_release_activation_actor
        CHECK (actor_type = 'USER' AND actor_issuer ~ '^[0-9a-f]{64}$'
            AND actor_subject <> '' AND actor_subject = btrim(actor_subject) AND actor_subject !~ '[[:cntrl:]]'
            AND command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT tianji_platform_release_activation_process
        CHECK (process_release_version ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'
            AND process_definition_key ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'
            AND process_manifest_sha256 ~ '^[0-9a-f]{64}$' AND process_model_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tianji_platform_release_activation_evidence
        CHECK (worker_topic ~ '^[A-Za-z_][A-Za-z0-9_.-]{0,127}$'
            AND worker_schema_version ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'
            AND char_length(worker_topic) + char_length(worker_schema_version) + 1 <= 255
            AND worker_binding_sha256 ~ '^[0-9a-f]{64}$'
            AND authorization_sha256 ~ '^[0-9a-f]{64}$' AND dependency_evidence_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE UNIQUE INDEX tianji_platform_release_activation_command_idx
    ON tianji_platform_release_activation (tenant_id, channel, actor_type, actor_issuer, actor_subject, command_id);

CREATE INDEX tianji_platform_release_activation_route_idx
    ON tianji_platform_release_activation (tenant_id, worker_topic, worker_schema_version, worker_binding_sha256);
