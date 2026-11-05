# S07 Agent 测试夹具模式隔离

Service baseline `9364b27a96c0ae66687784425055a4fab217ad17`。[Spec](../spec/2026-11-06-06-13-s07-fixture-isolation.md) · [Plan](../plan/2026-11-06-06-14-s07-fixture-isolation.md)

实际 AS 默认扫描会发现上一轮新增的 S07 测试配置。普通费用配置与 S07 配置现在分别核对共同授权标志和互斥模式条件，明确隔离启动路径。有限测试直接使用 Spring 类路径扫描，覆盖默认、仅模式标志、授权普通和授权 S07。

| Gate | Observed result |
| --- | --- |
| R1 | Owning Maven produced one expected scan mismatch with both fixtures in S07 after removing the ordinary exclusion. |
| R2 | Owning Maven produced one expected scan mismatch with both fixtures in ordinary mode after removing the S07 predicate. |
| G1 | Clean source reactor passed 26 finite tests in four suites, including the actual default AS shell and new scan matrix. Zero failures, errors and skips. |
| S1 | Strict Spec and Plan, six exact paths, all 28 existing migration bytes. |

测试夹具实际子进程和 S07 E 仍由独立操作者选择器验收。
