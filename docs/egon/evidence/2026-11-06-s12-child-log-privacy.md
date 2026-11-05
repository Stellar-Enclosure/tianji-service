# S12 子进程日志字面隐私验收证据

Service baseline `57d695e5b16e69293c3c07d1082224da3db4dd23`。[Spec](../spec/2026-11-06-05-15-s12-child-log-privacy.md) · [Plan](../plan/2026-11-06-05-16-s12-child-log-privacy.md)

独立 S12 指标验收入口增加 AS/PS 子进程日志的 1 MiB 内存采集。要求两种角色的启动和关闭标记及完整 EOF，溢出仍排空管道并判失败。Agent Fake 对两次确实观察到的现有提示各输出一个固定摘要标记。扫描检查提示前缀与实际使用的指定 HTTP bearer、两端 JDBC 运行密码及 TLS keystore 密码的字面值。原始日志与值不输出或落盘，释放时清零缓冲。

| Gate | Observed result |
| --- | --- |
| R1 | Owning reactor produced exactly one expected missing IllegalStateException on an actual registered secret split across reads when the raw secret predicate was removed. |
| G1 | 25 finite tests across four owning suites passed, including six new privacy cases. Zero failures, errors and skips. |
| P1 | Clean permission-producer repeated all 25. The actual test bytecode includes both role lifecycle markers, the exact opt-in operator method and matching Fake prompt/scanner constants. Both actual executable archives exclude the privacy fixture; configurations and semantic PaC match baseline. No operator E report was generated. |
| S1 | Strict Spec and Plan passed. Eleven exact paths and 28 existing migration files verified byte for byte. |

The exact opt-in operator method and private manifest property are in Plan §8. Actual child logs, Collector output and full S12 are pending. This slice addresses literal prompt and selected credential values; transformed text, exchanged child credentials and production logging require separate evidence.
