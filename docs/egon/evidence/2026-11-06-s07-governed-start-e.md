# S07 双租户 Agent 到 Process 幂等 START 验收入口

Service baseline `cc2c6818a7e122e81ea5d2a59172894b2c29656c`。[Spec](../spec/2026-11-06-05-51-s07-governed-start-e.md) · [Plan](../plan/2026-11-06-05-52-s07-governed-start-e.md)

独立 S07 夹具使用原 USER 的真实授权链，按完整 owner、业务来源和业务键从隔离库读取对象版本。Process 先发布固定 BPMN 与 DMN，Agent 按核验后的模型摘要和现有权限配置绑定固定 Tool。操作者用例核对第一次 V13 派发、原 SERVICE 的单条 Process START 收据与待办，随后验证 V14 授权复读及跨租户对象拒绝。流程和收据数量是实际操作者验收断言。

| Gate | Observed result |
| --- | --- |
| R1 | Exact owning Maven selector failed once when full business owner equality was removed. |
| R2 | Exact owning Maven selector failed once when complete Process model digest and input comparison was removed. |
| G1 | 91 finite tests passed in five suites, including six new fixture cases, with zero errors, failures or skips. |
| P1 | Clean permission-producer repeated all 91. Exact operator class compiled, both actual Boot executable loaders resolved fixture classes from their own test trees without initialization, and both archives exclude S07 fixture classes. Packaged configs and semantic PaC match baseline. No operator E report was generated. |
| S1 | Strict Spec and Plan, 13 exact paths, and all 28 existing migration files verified byte for byte. |

AS archive SHA-256 `0bd8014fa32d765f2e32c5353567a0f66ac8b18d254cf4cfaeeb639651fb6438`. PS archive SHA-256 `5c40e6e0e4bd631ffa16600770338b0a8c44f3c84b44da4c9cc10fb2820674db`.

此用例覆盖幂等写入分支，实际双租户 IAM、HTTPS、PostgreSQL 和独立审批场景仍待运行验收。现有授权范围和操作者前置条件见 Plan §8。
