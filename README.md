# Tianji Service

Tianji is the business process and intelligent orchestration platform for Stellar Enclosure. This initial delivery establishes the engine-neutral Agent contract and module boundaries; it does not yet run agents or expose HTTP APIs.

## Modules

- `stellar-enclosure-tianji-contract`: JDK-only Agent client contract, commands, results and events.
- `stellar-enclosure-tianji-client`: reserved for the HTTP client implementation; no transport is exposed yet.
- `stellar-enclosure-tianji-agent-runtime`: the embedded runtime boundary; disabled by default.
- `stellar-enclosure-tianji-agent-server`: non-web Spring Boot shell with an in-process health endpoint.

The selected dependency line is Java 25, Spring Boot 4.1.2, Spring AI 2.0.2 and Google ADK 1.13.0. The runtime does not yet depend on the Agent Flow starter: the updated Components artifact must first be consumed through a verified source reactor or a separately versioned publication. Contract and client modules do not depend on either engine or an AI framework.

`stellar.tianji.agent.runtime.enabled` is false by default. Setting it to true currently fails startup because the runtime, trusted identity and persistence are not implemented. No Agent or process endpoint is enabled in this delivery. Input artifact references and policy overrides are requests, not authorization; later runtime work must check ownership and intersect overrides with the permitted policy.

For a JDK 25 environment with the Stellar-Enclosure platform parent and Components BOM available in Maven, run:

```bash
./mvnw -B -ntp test
```

Tests use in-memory Spring contexts without opening a listening port. Real model providers, MCP, PostgreSQL, and process engines require separate integration and acceptance work.
