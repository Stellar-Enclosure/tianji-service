ALTER TABLE tianji_process_command_receipt
    ADD COLUMN decision_result jsonb;

ALTER TABLE tianji_process_command_receipt
    ADD CONSTRAINT tianji_process_decision_receipt_result_check CHECK (
        (command_type = 'DECISION' AND state = 'PENDING' AND decision_result IS NULL)
        OR (command_type = 'DECISION' AND state = 'APPLIED' AND decision_result IS NOT NULL
            AND jsonb_typeof(decision_result) = 'object')
        OR (command_type <> 'DECISION' AND decision_result IS NULL)
    );
