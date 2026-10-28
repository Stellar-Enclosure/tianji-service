ALTER TABLE tianji_process_release
    ADD COLUMN publisher_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (publisher_issuer = 'LEGACY' OR publisher_issuer ~ '^[0-9a-f]{64}$');

ALTER TABLE tianji_process_command_receipt
    ADD COLUMN subject_issuer varchar(64) COLLATE "C" NOT NULL DEFAULT 'LEGACY'
        CHECK (subject_issuer = 'LEGACY' OR subject_issuer ~ '^[0-9a-f]{64}$');

ALTER TABLE tianji_process_command_receipt DROP CONSTRAINT tianji_process_command_receipt_pkey;

ALTER TABLE tianji_process_command_receipt
    ADD CONSTRAINT tianji_process_command_receipt_pkey
    PRIMARY KEY (tenant_id, subject_type, subject_issuer, subject_id, command_type, command_id);
