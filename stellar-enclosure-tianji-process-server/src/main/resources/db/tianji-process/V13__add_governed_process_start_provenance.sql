-- Append-only provenance for the activation revision that supplied a governed START.
CREATE TABLE tianji_governed_process_start (
    tenant_id bigint NOT NULL,
    subject_type varchar(16) COLLATE "C" NOT NULL,
    subject_issuer varchar(64) COLLATE "C" NOT NULL,
    subject_id varchar(256) COLLATE "C" NOT NULL,
    command_type varchar(32) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    channel varchar(64) COLLATE "C" NOT NULL,
    activation_revision bigint NOT NULL,
    governed_request_sha256 char(64) COLLATE "C" NOT NULL,
    resolved_request_sha256 char(64) COLLATE "C" NOT NULL,
    authorization_sha256 char(64) COLLATE "C" NOT NULL,
    instance_id varchar(255) COLLATE "C" NOT NULL,
    created_at timestamptz(6) NOT NULL,
    CONSTRAINT tianji_governed_process_start_pkey
        PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_governed_process_start_receipt_fk
        FOREIGN KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id)
        REFERENCES tianji_process_command_receipt (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id),
    CONSTRAINT tianji_governed_process_start_activation_fk
        FOREIGN KEY (tenant_id, channel, activation_revision)
        REFERENCES tianji_platform_release_activation (tenant_id, channel, revision),
    CONSTRAINT tianji_governed_process_start_scope
        CHECK (tenant_id > 0 AND activation_revision > 0 AND channel ~ '^[a-z][a-z0-9._-]{0,63}$'
            AND command_type = 'START' AND command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT tianji_governed_process_start_actor
        CHECK (subject_type = ANY(ARRAY['USER', 'SERVICE']) AND subject_issuer ~ '^[0-9a-f]{64}$'
            AND subject_id <> '' AND subject_id = btrim(subject_id) AND subject_id !~ '[[:cntrl:]]'),
    CONSTRAINT tianji_governed_process_start_evidence
        CHECK (governed_request_sha256 ~ '^[0-9a-f]{64}$' AND resolved_request_sha256 ~ '^[0-9a-f]{64}$'
            AND authorization_sha256 ~ '^[0-9a-f]{64}$'
            AND instance_id <> '' AND instance_id = btrim(instance_id) AND instance_id !~ '[[:cntrl:]]')
);
