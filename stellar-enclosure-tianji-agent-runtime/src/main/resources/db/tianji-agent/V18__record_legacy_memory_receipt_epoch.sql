ALTER TABLE tianji_agent.agent_memory_command_receipt
    ADD COLUMN authority_epoch bigint CONSTRAINT memory_command_authority_epoch CHECK (authority_epoch > 0);
