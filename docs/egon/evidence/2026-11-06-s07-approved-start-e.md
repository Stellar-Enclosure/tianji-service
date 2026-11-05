# S07 原生审批后一次受治理 START 验收入口

Service baseline `0f15cf7ec45952e3e2b217358eb8493c1d5285cd`。[Spec](../spec/2026-11-06-06-13-s07-approved-start-e.md) · [Plan](../plan/2026-11-06-06-14-s07-approved-start-e.md)

独立双租户 S07 审批夹具从原 V13 等待动作取得精确请求绑定，由同一 USER 发起独立审批 BPMN，核对申请人不得自审、独立审核人领取和完成任务。只读观察者只选择原生回执引用。既有固定 verifier 通过独立 SERVICE 校验 V7 审批回执，第二个原 USER Run 消费该回执，并要求原执行 SERVICE 只有一条目标 START 和一个原生实例。

| Gate | Observed result |
| --- | --- |
| R1 | Removing the current business version comparison produced one expected missing IllegalStateException before opaque receipt selection. |
| R2 | Removing the pinned approval source model digest and input comparison produced one expected missing IllegalStateException. |
| G1 | 105 finite tests passed across nine suites, zero failures, errors and skips. |
| P1 | Clean permission-producer repeated all 105. Exact operator class compiled. Both actual Boot loaders resolved fixture classes from the corresponding test tree without initialization. Both executable archives exclude the approval fixture. Packaged config and semantic PaC match baseline. No operator E report. |
| S1 | Strict Spec and Plan, 12 exact paths and all 28 existing migration bytes verified. |

AS archive SHA-256 `c26c24694f1a137c5eaf40c728066e6870919b30c30721b2ce0a9a7bc2bd4d78`. PS archive SHA-256 `b34dd88b6b39f65d51162ca63c41a9745a13bbb2d808f79a6dfedcfb8dbde65d`.

实际双租户 PostgreSQL、IAM、HTTPS、原生任务及目标 START 通过 Plan §8 的独立操作者入口验收。审批场景的 V14 缓存复读属于后续范围。
