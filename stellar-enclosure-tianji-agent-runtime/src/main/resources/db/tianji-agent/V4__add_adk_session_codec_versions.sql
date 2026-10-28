ALTER TABLE tianji_agent.agent_session
    ADD COLUMN state_codec_version smallint NOT NULL DEFAULT 1 CHECK (state_codec_version IN (1, 2));

ALTER TABLE tianji_agent.agent_session_scope_state
    ADD COLUMN value_codec_version smallint NOT NULL DEFAULT 1 CHECK (value_codec_version IN (1, 2));
