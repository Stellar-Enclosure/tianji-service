-- Immutable platform candidate and one review, in the owning Process schema.
CREATE TABLE tianji_platform_release_candidate (
    tenant_id bigint NOT NULL,
    channel varchar(64) COLLATE "C" NOT NULL,
    candidate_sha256 char(64) COLLATE "C" NOT NULL,
    submitter_issuer char(64) COLLATE "C" NOT NULL,
    submitter_subject varchar(256) COLLATE "C" NOT NULL,
    envelope bytea NOT NULL,
    sidecar bytea NOT NULL,
    envelope_sha256 char(64) COLLATE "C" NOT NULL,
    sidecar_sha256 char(64) COLLATE "C" NOT NULL,
    source_revision char(40) COLLATE "C" NOT NULL,
    release_version varchar(128) COLLATE "C" NOT NULL,
    manifest_sha256 char(64) COLLATE "C" NOT NULL,
    signature_issued_at timestamptz(6) NOT NULL,
    signature_expires_at timestamptz(6) NOT NULL,
    trust_generation bigint NOT NULL,
    trust_sha256 char(64) COLLATE "C" NOT NULL,
    signer_key_id char(64) COLLATE "C" NOT NULL,
    signer_issuer char(64) COLLATE "C" NOT NULL,
    signer_subject varchar(256) COLLATE "C" NOT NULL,
    submit_auth_sha256 char(64) COLLATE "C" NOT NULL,
    owner_read_auth_sha256 char(64) COLLATE "C" NOT NULL,
    owner_observed_at timestamptz(6) NOT NULL,
    submitted_at timestamptz(6) NOT NULL,
    CONSTRAINT tianji_platform_release_candidate_pkey PRIMARY KEY (tenant_id, channel, candidate_sha256),
    CONSTRAINT tianji_platform_release_candidate_scope
        CHECK (tenant_id > 0 AND channel ~ '^[a-z][a-z0-9._-]{0,63}$' AND candidate_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tianji_platform_release_candidate_subjects
        CHECK (submitter_issuer ~ '^[0-9a-f]{64}$' AND signer_issuer ~ '^[0-9a-f]{64}$'
            AND submitter_subject <> '' AND submitter_subject = btrim(submitter_subject) AND submitter_subject !~ '[[:cntrl:]]'
            AND signer_subject <> '' AND signer_subject = btrim(signer_subject) AND signer_subject !~ '[[:cntrl:]]'),
    CONSTRAINT tianji_platform_release_candidate_evidence
        CHECK (trust_generation > 0 AND trust_sha256 ~ '^[0-9a-f]{64}$' AND signer_key_id ~ '^[0-9a-f]{64}$'
            AND envelope_sha256 ~ '^[0-9a-f]{64}$' AND sidecar_sha256 ~ '^[0-9a-f]{64}$'
            AND submit_auth_sha256 ~ '^[0-9a-f]{64}$' AND owner_read_auth_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT tianji_platform_release_candidate_release
        CHECK (source_revision ~ '^[0-9a-f]{40}$' AND manifest_sha256 ~ '^[0-9a-f]{64}$'
            AND release_version ~ '^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$'),
    CONSTRAINT tianji_platform_release_candidate_bounds
        CHECK (octet_length(envelope) > 0 AND octet_length(envelope) <= 1048576
            AND octet_length(sidecar) > 0 AND octet_length(sidecar) <= 8192),
    CONSTRAINT tianji_platform_release_candidate_times
        CHECK (signature_issued_at <= owner_observed_at AND owner_observed_at <= submitted_at AND submitted_at < signature_expires_at)
);

CREATE TABLE tianji_platform_release_review (
    tenant_id bigint NOT NULL,
    channel varchar(64) COLLATE "C" NOT NULL,
    candidate_sha256 char(64) COLLATE "C" NOT NULL,
    reviewer_issuer char(64) COLLATE "C" NOT NULL,
    reviewer_subject varchar(256) COLLATE "C" NOT NULL,
    command_id varchar(128) COLLATE "C" NOT NULL,
    decision varchar(8) COLLATE "C" NOT NULL,
    review_auth_sha256 char(64) COLLATE "C" NOT NULL,
    independence_auth_sha256 char(64) COLLATE "C" NOT NULL,
    owner_read_auth_sha256 char(64) COLLATE "C" NOT NULL,
    owner_observed_at timestamptz(6) NOT NULL,
    decided_at timestamptz(6) NOT NULL,
    CONSTRAINT tianji_platform_release_review_pkey PRIMARY KEY (tenant_id, channel, candidate_sha256),
    CONSTRAINT tianji_platform_release_review_candidate_fk FOREIGN KEY (tenant_id, channel, candidate_sha256)
        REFERENCES tianji_platform_release_candidate (tenant_id, channel, candidate_sha256),
    CONSTRAINT tianji_platform_release_review_subject
        CHECK (reviewer_issuer ~ '^[0-9a-f]{64}$' AND reviewer_subject <> '' AND reviewer_subject = btrim(reviewer_subject)
            AND reviewer_subject !~ '[[:cntrl:]]' AND command_id ~ '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT tianji_platform_release_review_evidence
        CHECK (decision = ANY(ARRAY['ACCEPT', 'REJECT']) AND review_auth_sha256 ~ '^[0-9a-f]{64}$'
            AND independence_auth_sha256 ~ '^[0-9a-f]{64}$' AND owner_read_auth_sha256 ~ '^[0-9a-f]{64}$'
            AND owner_observed_at <= decided_at)
);

CREATE UNIQUE INDEX tianji_platform_release_review_command_idx
    ON tianji_platform_release_review (tenant_id, channel, reviewer_issuer, reviewer_subject, command_id);
