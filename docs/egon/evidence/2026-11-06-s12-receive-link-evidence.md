# S12 receive-link fixture evidence

Service baseline `f7641a373892bd9e054f79ec97bfbb9dfc8c8e45`. [Spec](../spec/2026-11-06-00-37-s12-receive-link-evidence.md) and [Plan](../plan/2026-11-06-00-38-s12-receive-link-evidence.md).

The separately gated fixture prepares two owner-isolated HTTPS paths. Its collector gate requires each durable enqueue to link to one delivery and one PS receive span, with six distinct trace IDs and one recorded native advance per result. It rejects shared trace IDs, unexpected fields and substituted Link targets. The test launcher rechecks the two SDK entries against source-pinned digests.

| Gate | Observed result |
| --- | --- |
| R1 | Exactly one intended parser assertion, expected true and actual false, with zero errors and skips. |
| G1 | 43 finite tests across nine suites passed, including SDK/parser, ordinary expense fixture and existing AS, CL and PS Link checks. |
| P1 | Clean permission-producer build repeated all 43 passing tests. The actual AS and PS executable archives exclude the S12 fixture and collector config. PaC matches the prior manifests after removing only build identity fields, and PS contains only the approved OTel API, Context and Common. Two class-only launcher checks passed against the actual archives with exact SDK hashes. |
| S1 | Strict Spec and Plan pass. The scope is 10 Java test files, one test resource and four documents. All 27 existing migration files are byte-identical. The S12 and ordinary E classes compiled into test output, with no E reports. |

AS archive SHA-256 `cd2ec643caccda382a1d2ece316499999e0231dbcfaaae5dddb981b4327baeaf` and manifest SHA-256 `093fff3eb950e109a585c4b3c71cbf279670d30d49fb56b2e7dfba712c1531bd`. PS archive SHA-256 `300d99eeda6c725cc98df6ee53aa0403eaa1b66c51964ff527be9346b1f7484d` and manifest SHA-256 `04c41d827f68afd4478ba44ca184e929b6813d8680b45f0dbfb182f22e04e989`.

The source gate launched no application, collector, PostgreSQL, provider or E fixture. Actual collector receipt, restart/replay, Worker, V15, log and metric gates, and full S12 remain operator acceptance items. [Operator procedure](2026-11-06-s12-receive-link-operator.md)
