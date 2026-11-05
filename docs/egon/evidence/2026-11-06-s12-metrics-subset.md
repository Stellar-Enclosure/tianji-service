# S12 已提交时延指标源码证据

Service 基线 `e42b0982f4afdb82629522122cf9e77f24ac1efb`。 [Spec](../spec/2026-11-06-04-38-s12-metrics-subset.md) · [Plan](../plan/2026-11-06-04-39-s12-metrics-subset.md)

独立套件复用正常的两条 tenant 路径，使用已存在的 AS 首次领取 Timer 与 PS Inbox 决定 Timer。操作验收需要由真实独立 Collector 写出的私有原始记录，每个固定角色一个时间序列、两个样本。PS 只有 `outcome=applied`，AS 无标签。

| Gate | Observed result |
| --- | --- |
| R1 | 仅松开 raw PS 标签键的 Java 变体由所属 Maven reactor 编译，精确选择 `S12MetricsCollectorEvidenceTest#rejects_sensitive_fields_incomplete_counts_stale_samples_and_duplicate_keys`。插入 `runId` 的 assertThrows 产生唯一预期 AssertionFailedError，零跳过。 |
| G1 | 所属 Maven reactor 在 JDK 25 编译全部 fixture 改动与 E 入口。5 个 telemetry 和 3 个 raw evidence 用例全部通过。额外复跑既有 harness，11 项通过。 |
| Class-only | 真实 AS、PS Boot 包分别用 JDK class probe 解析 PS 测试树中的 initializer/registry，且各自 fixture main 的 class origin 符合测试树归属。Probe 不调用 fixture main。 |
| P1/S1 | 干净 permission-producer 重复通过 8 项，实际 AS/PS 包不含验收夹具或 Collector YAML，打包配置及语义 PaC 与基线一致。无 E 报告。严格 Spec/Plan、14 条路径和 28 份 SQL 逐字节验证。 |
| E1 | 待独立操作者运行。 |

### Class-only 检查

以下命令在两包和两个完整测试类树存在后使用。准备 JDK probe `ScratchMetricsClassPathProbe.java`。其 main 仅用 `Class.forName(name, false, contextLoader)` 加载共享 initializer、registry 与对应 fixture main，再将 `ProtectionDomain.codeSource.location` 对照对应类树并确认 Registry 父类型是已有的 `io.micrometer.core.instrument.simple.SimpleMeterRegistry`。

```sh
metrics_jdk=$(/usr/libexec/java_home -v 25)
metrics_scratch=/Users/mario/.codex/tmp/tianji-agent-upgrade-20261105/t4-05/s12-metrics
metrics_probe="$metrics_scratch/root-loader"
mkdir -p "$metrics_probe"
metrics_as="$PWD/stellar-enclosure-tianji-agent-server/target"
metrics_ps="$PWD/stellar-enclosure-tianji-process-server/target"
"$metrics_jdk/bin/javac" -d "$metrics_probe" "$metrics_scratch/ScratchMetricsClassPathProbe.java"
env -i "$metrics_jdk/bin/java" -Dloader.main=ScratchMetricsClassPathProbe \
  "-Dloader.path=$metrics_probe,$metrics_as/test-classes,$metrics_ps/test-classes" \
  -cp "$metrics_as/stellar-enclosure-tianji-agent-server-exec.jar" org.springframework.boot.loader.launch.PropertiesLauncher \
  "$metrics_ps/test-classes" "$metrics_as/test-classes" \
  top.egon.cola.platform.tianji.agent.server.acceptance.ExpenseAgentAcceptanceMain
env -i "$metrics_jdk/bin/java" -Dloader.main=ScratchMetricsClassPathProbe \
  "-Dloader.path=$metrics_probe,$metrics_ps/test-classes" \
  -cp "$metrics_ps/stellar-enclosure-tianji-process-server-exec.jar" org.springframework.boot.loader.launch.PropertiesLauncher \
  "$metrics_ps/test-classes" "$metrics_ps/test-classes" \
  top.egon.cola.platform.tianji.process.server.acceptance.ExpenseProcessAcceptanceMain
```

实际探针基于本次 P1 的两个 executable 和两个完整测试类树，探针源码 SHA-256 为 `e0711b6241fd3694ca21d47ee39e7782d5bbeca495f5024898ee4c900712302f`。用例 `S12MetricsAcceptanceIT#collects_two_initial_claims_and_two_committed_inbox_decisions_with_fixed_tags` 的实际通过、外部 Collector 版本证据和私有 0600 JSONL 以 Plan §8 E1 的操作者证据为准。
