# Human Task completion timing evidence

Service baseline 7072e5b9153d1cda4ee38be60eeb9651b102a885. [Spec](../spec/2026-11-05-22-31-human-task-completion-duration.md) and [Plan](../plan/2026-11-05-22-32-human-task-completion-duration.md) pass strict validation.

The optional Timer measures native creation to the applied Human Task COMPLETE receipt database time. The existing locked snapshot supplies creation and due time. The existing applied receipt supplies the completion time. The sample is emitted after commit. Replay, rollback, failed commit, invalid duration and unavailable metrics produce no sample. The only tag values are scope process or case and due on_time, late or unscheduled.

| Gate | Actual observation |
| --- | --- |
| R1 | One expected assertion failure for absent class. |
| R2 | One expected assertion failure for an inert callback producing zero samples. |
| G1 | 47 tests passed across FlowableHumanTaskServiceTest, ProcessApiConfigurationTest, HumanTaskSnapshotTest and HumanTaskReceiptStoreTest. |
| P1 | Clean offline permission-producer verify repeated 47 passing tests. Actual PS package has the single false completion-metrics-enabled flag. AS and PS permission contents match the baseline outside build provenance. |
| S1 | Strict documents, exact ten paths, byte preservation of all excluded files and all 27 existing migrations. |

The PS archive SHA-256 is 0652ce1b48914de03002566551d2f9238977d0417284cd78142fd3a2f8c0eccf and packaged YAML SHA-256 is b6cabd1720bc8300d518484926f5c009b875ae0a910819162e6fef16f92145ed. The raw PS PaC SHA-256 is 02a9063486470118001be900491d44c07d24e4fee7b0cb33ddd2a575e0040f74. The supported reactor verify goal and existing permission-producer profile produced the real package. The plan command was aligned to the reactor's supported goals before source execution.

Manual and applicable literal rules cover current task and metric reuse, no new SQL read, immutable receipt and timing validation, finite tags, existing named configuration and scope, and actual transaction-synchronization tests. This metric covers committed completions only. Live exporter, pending and overdue queue exposure, provider SLA, and S12 remain separate.
