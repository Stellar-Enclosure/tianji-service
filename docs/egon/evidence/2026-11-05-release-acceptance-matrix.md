# Tianji release acceptance matrix and operator runbook

基线为 Service 018b0acf68e2b39692d1a77ffe399f145d11c5fd 和 Web ee026e99fe898989b3affe7641cd1d145353a15d。范围来自 Master §17.1–17.3 和 §19 T4-05。本文件列验收入口和证据要求，不能单独签发 release PASS。2026-11-05 核对源码并复跑了下列有限 U 选择器。真实部署、数据库、监听器、CI 远程、供应商和浏览器均由单独授权的操作者执行。[Master §17 和 T4-05](../../../../docs/plans/agent-framework-upgrade-plan.md#17-单元测试集成测试与端到端验收)

## 状态和报告归属

- **U** 是实际运行的有限单测、原生框架加 Fake、MockMvc 或本地协议测试。静态包和类型检查单独记录。历史 U 仅证明当时锁定的源与配置，不能跨候选复用为本次报告。原生 ADK Runner 加 Fake 可以证明调用协议，不能证明供应商模型质量。
- **I** 是实际运行的隔离 PostgreSQL、pgvector、Flowable 或 Spring 集成。**E** 是启动两个独立 AS/PS 进程和业务 Worker、接入可信 IAM 的实际验收。**L** 是独立审批的小额真实供应商验证。只存在 class 文件、跳过或零测试均不能满足 I/E/L。当前候选未收集全套 I/E/L。
- **Owner 与报告**。AR = stellar-enclosure-tianji-agent-runtime，AS = stellar-enclosure-tianji-agent-server，PS = stellar-enclosure-tianji-process-server，W = stellar-enclosure-tianji-worker-starter，CL = stellar-enclosure-tianji-client，Web = tianji-web。Service 行内类名就是 Surefire selector。报告必须来自 owning module 的 target/surefire-reports/TEST-<source package>.<class>.xml，方法 selector 用 Class#method。收集时保留源文件 package 得到的精确 FQCN、报告原字节 SHA-256、选择器、开始/完成 UTC、测试/失败/错误/跳过数。Web 报告是独立 Vitest JUnit 文件和日志，不能填入 Service Surefire policy。
- **版本绑定**。每次新验收先锁 Service 和 Web 提交，相关 sibling SHA、JDK/Node、构建产物、配置/模型、候选 digest 和 operator-approved I/E profile。各模块命令可复跑。放在 owner-only 私有目录的报告、PG/TLS/租户清单和 E witnesses 使用相对清单引用和 SHA。仓库仅记录摘要及通过范围，凭据、完整 prompt、原始租户业务明细不入库。

| 已留存验证 | 证明范围 |
| --- | --- |
| Source64 | 14 个 Expense U 类 183 通过。两个 E IT 只编译，无执行报告。[验收证据](2026-11-05-expense-four-path-acceptance.md) |
| Source79 | 7 项离线证据工具单测。未提交实际本候选 I/E 证据包。[门禁证据](2026-11-05-expense-release-evidence-gate.md) |
| Source83 | AR owner 36 通过，其中六条固定 Fake 注入向量。属于 S07 U。[注入语料证据](2026-11-05-governed-process-injection-corpus.md) |
| Source84 | 24 定向、47 清洁 package 通过。实际 AS/PS 包、默认关闭和本地 SDK Link；未运行 S12。[接收 Link 证据](2026-11-05-agent-result-receive-span-links.md) |
| Source86 | 14 个现有有限用例通过；新增 AR AdkDurableResumeBoundaryPostgresTest 仅编译，没有执行报告。只探查同一有效 Run 下的 Session 租约更换边界。[持久会话探针](2026-11-05-adk-durable-resume-probe.md) |
| Web Source85 | 256 项 Vitest、typecheck/lint 与实际 Vite/PaC artifact。权限为 LOCAL_UNCOMMITTED。[发布元数据证据](../../../../tianji-web/docs/egon/evidence/2026-11-05-process-release-metadata.md) |
| Web Source87 | 56 项定向 Vitest、typecheck/lint 和官方静态构建；权限声明保持不变，未验证实际 BFF 路由或浏览器。[发布版本对比证据](../../../../tianji-web/docs/egon/evidence/2026-11-05-process-release-comparison.md) |

## S01–S12

| ID and required level | Owner and existing finite anchors | Authorized runtime target and exact remaining proof |
| --- | --- | --- |
| S01 U+I · 单 Agent 正常流 | AS AgentRunHttpTest#replays_from_last_event_id_in_bounded_pages_and_completes_only_at_terminal_watermark、AgentRunEventStreamCapacityTest。AR AdkEventProjectorTest、AdkNativeAgentCompletionTest。Web src/api/agentEvents.test.ts、src/pages/AgentRunDetailPage.test.tsx。 | I-AS AgentRunHttpPostgresTest#http_commands_replay_and_cross_tenant_isolation_use_the_real_transactional_ledger，I-AR AdkAgentRunExecutorPostgresTest#duplicate_dispatch_does_not_subscribe_a_second_time、AgentLedgerPostgresTest。当前候选需观察一次 POST、真实 Runner/Fake 双 delta 顺序、重订阅不增加调用、COMPLETED 与结果同事务。现有类分别覆盖 adapter/ledger/executor，未发现一条当前报告同时证明这条完整链。 |
| S02 U+I · 超时、取消、失联 | AR AgentDeadlineEffectTest、AgentRunDispatcherTest、AgentRunReconcilerTest。Web src/pages/AgentRunDetailPage.test.tsx。 | I-AR AdkAgentRunExecutorPostgresTest#deadline_cancels_the_model_subscription_and_records_timeout、#model_idle_deadline_is_refreshed_by_each_stream_frame、#persisted_cancellation_stops_the_active_model_and_records_a_terminal_result；AgentRunGovernancePostgresTest、AgentReconcilerPostgresTest。需要当前 PG 下持续 delta 总 deadline、排队/运行取消、租约过期后的受控终态或 SUSPENDED 及可查询原因。 |
| S03 U+I · 复合 Agent | AR NativeAgentTransferRunnerTest、AdkNativeAgentCompletionTest、AgentBudgetLockOrderingTest。AS BoundedResearchAgentFactoryTest。 | I-AR AdkAgentRunExecutorPostgresTest#sequential_root_finishes_all_children_before_marking_the_run_complete、#parallel_root_returns_both_outputs_without_inventing_a_single_final_text、#parallel_branches_wait_for_budget_capacity_instead_of_exceeding_it；AgentBudgetPostgresTest#concurrent_reservations_cannot_oversell_model_tokens_or_call_count。补同批父完成、失败/取消分支和 outputKey 冲突证据。 |
| S04 I · Session/Memory 隔离 | AR AdkSessionEventCodecTest、PostgresMemoryAuthorizationTest、PgVectorMemoryIndexTest、MemoryRetentionWorkTest。名称带 Postgres 的 AuthorizationTest 是 controlled JDBC。Web src/api/memory.test.ts、src/pages/MemoryCandidatesPage.test.tsx、src/auth/AuthContext.test.tsx。 | I-AR AdkSessionPostgresTest#native_runner_restores_a_tool_exchange_and_prior_turn_for_the_next_run、#sessions_and_scoped_state_are_isolated_by_tenant_principal_and_application、AdkSessionMigrationPostgresTest、I-MEM MemoryPostgresTest#v10_fact_receipt_index_visibility_forget_concurrency_grants_and_catalog_fences。需要重启/新轮、双租户同主体不同 scope、真实向量过滤及 forget 后检索/摘要证据。 |
| S05 U+I · BPMN→DMN | PS ProcessDecisionServiceTest、FlowablePinnedDecisionExecutorTest、ProcessModelValidatorTest。 | I-PS ProcessServerPostgresTest#engines_persist_and_flowable_and_outbox_commit_or_roll_back_together，内部 verifyDecisionApplications。需要真实引擎的边界值、空命中、多命中、错误输入、旧版本绑定、独立 DecisionClient 不新建 ProcessInstance。E-BUSINESS 的费用 small/large 是增补见证。 |
| S06 E · BPMN→Agent 异步 | PS AgentStartWorkerTest、ProcessAgentBridgePollerTest、FlowableAgentWaitInspectorTest、AgentResultCorrelationServiceTest。I-PS-INBOX AgentResultInboxPostgresTest、I-PS-DISPATCH AgentDispatchStorePostgresTest、I-AS-OUTBOX AgentTerminalOutboxPostgresTest 提供独立事务支持，不能替代 E。 | E-BUSINESS BusinessProcessAgentAcceptanceIT#normalWorkflowAcrossBothTenants、#failedAgentCompensatesAcrossBothTenants；E-RECOVERY WorkerRecoveryIT#recoversLostAcknowledgementEarlyResultAndIndependentRestartsAcrossBothTenants。需要真实等待、早到 PENDING、AS 终态关联、同事件重投且一次推进。 |
| S07 U+I+E · Agent→Process 授权 | AR AdkGovernedProcessLoopTest，Source83 固定六条 Fake 注入向量。AS TianjiPermissionIntegrationTest、PS ProcessPermissionIntegrationTest。 | I-AR-TOOL ToolExecutionJournalPostgresTest、I-AS-LINKED LinkedAgentDispatchHttpPostgresTest、I-PS ProcessServerPostgresTest 提供 journal、linked identity 和 native 边界的各自证据。当前 E 费用场景主要是 BPMN→Agent，尚无已定位的授权/拒绝完整 Agent→Process E selector。需在实际 IAM 单独验允许 START 一次、越权/跨租户/版本和审批篡改拒绝、SERVICE 不代替 USER。 |
| S08 I+E · Human Task | PS FlowableHumanTaskServiceTest、HumanTaskControllerTest、HumanTaskAccessRulesTest。Web src/api/humanTasks.test.ts、src/pages/TaskInboxPage.test.tsx、src/pages/TaskDetailPage.test.tsx。 | I-TASK HumanTaskReceiptPostgresTest#v7_preserves_old_checksums_and_reference_actions_are_atomic_scoped_and_replayable_under_contention，I-PS ProcessServerPostgresTest、FormalCasePostgresTest。E-BUSINESS #manualCaseAndAuthorizedReviewAcrossBothTenants 提供授权审查，不单独证明真实 E 多候选人并发。需并发唯一赢家、非候选拒绝、相同 commandId 原回执。 |
| S09 U+I+E · CMMN | PS CmmnModelValidatorTest、FlowableCaseServiceTest、CaseCompletionCorrelationServiceTest、CaseCompletionInboxTest。Web src/api/cases.test.ts、src/pages/CaseDetailPage.test.tsx。 | I-CASE FormalCasePostgresTest#native_case_lifecycle_and_exact_bpmn_continuation_are_atomic_and_recoverable；E-BUSINESS #manualCaseAndAuthorizedReviewAcrossBothTenants。I/E 分别观察 sentry、item enable/start、补充任务、milestone、完成/终止、精确 BPMN 续跑。费用 E 的 manual 一条路径不是所有 CMMN 分支。 |
| S10 I+E · Worker 成功回执丢失 | W RemoteWorkerPollerTest，PS FlowableWorkerServiceTest、CompensationBusinessFixtureTest、WorkerRecoverySpecificationTest、ProcessWorkerRecoveryServiceTest。Web src/api/processWorkerFailures.test.ts、src/pages/WorkerFailuresPage.test.tsx 只观察。 | I-W-BUSINESS JdbcWorkerExecutionStorePostgresTest#business_write_and_receipt_are_atomic_and_replayed_after_lost_acknowledgement；I-W-EXTERNAL NonTransactionalWorkerPostgresTest；I-PS ProcessServerPostgresTest 内部 verifyWorkerLostAcknowledgement、verifyWorkerConcurrency。E-RECOVERY #recoversLostAcknowledgementEarlyResultAndIndependentRestartsAcrossBothTenants，需同一业务计数/原 receipt、过期后新 lease、旧 lease 拒绝、UNKNOWN 禁自动重做。 |
| S11 I+E · 独立重启 | AR AgentRunReconcilerTest、AgentRecoveryDispositionEvidenceTest、AdkNativeResumeSemanticsTest。Native probe 的未保存结果重做计数为 2，当前 source 无生产自动恢复。 | I-AR AgentReconcilerPostgresTest、AdkSessionPostgresTest；I-RESUME AdkDurableResumeBoundaryPostgresTest（仅编译，独立 Session 租约边界）；I-AS-OUTBOX AgentTerminalOutboxPostgresTest，I-PS ProcessServerPostgresTest；E-RECOVERY 已有 AS replacement + same-event resend、PS replacement + retained Timer/Message 源码。仍需当前 E 原始见证，单独覆盖一般 QUEUED 恢复、UNKNOWN 不重做和不可恢复模式人工处置。 |
| S12 E · 全链路观测 | AS AgentResultSpanObservationTest、AgentResultDeliveryHandlerTest；CL HttpAgentResultTraceLinkTest；PS AgentResultInboxControllerTest、AgentResultInboxLagObservationTest；AR AgentModelUsageStoreTest、AgentModelUsageCaptureTest、AdkToolTelemetryPrivacyTest。Source84 的真 SDK 本地 Link 与包证明只到 receive。I-USAGE AgentModelUsagePostgresTest 是独立 V15 支撑。 | 当前未发现可执行的完整 S12 E selector。需要实际 collector/metrics sink 和 Fake 模型、真实链路 provider/forwarding、从 business/correlation 到 process/run/tool/model/failure 的 bounded owner query、异步重启/Worker Link、UNKNOWN/usage 去重、取消后晚到使用、日志/导出秘密检查、标签 cardinality 和采集窗口。任何 S12 勾选必须来自实际 collector 及关联查询报告。 |

## 环境、现有 profile 和操作者门槛

| Profile | 实际激活 | 独立前置 |
| --- | --- | --- |
| U / PACKAGE | 现有 source reactor exact -Dtest，普通 test 或 -Ppermission-producer verify | JDK25、本地已准备 Maven Wrapper 3.9.14 和依赖缓存、四个 sibling 源路径、私有 settings。无 DB props。PACKAGE 用两份实际 Boot JAR/PaC，不能代替 I/E。 |
| I-AR / I-AS | tianji.postgres.test.url / tianji.http.postgres.test.url | 每类按源码要求独立 fresh loopback DB，运行指定 AR/AS 类，当前本阶段不执行。 |
| I-RESUME | tianji.postgres.resume.probe=true + tianji.postgres.resume.probe.url | 专用一次性 loopback 主库，库名 tianji_it_resume_ 前缀，已有 V1–V9；只运行 AR AdkDurableResumeBoundaryPostgresTest。不是 Run 接管或 S11 通过。 |
| I-MEM / session migration | tianji.memory.postgres.test.url / tianji.session.migration.test.url | 独立 DB、预装兼容 pgvector/固定版本，按测试权限和历史要求。 |
| I-PS / I-CASE / I-TASK | tianji.process.postgres.test.url（PS 与 FormalCase 分开 fresh DB）/ tianji.task.receipt.postgres.test.url | 原生 Flowable 和实际存储；ProcessServerPostgresTest 的 Worker 子场景还有单独 BusinessDB props，不能视一条 DB URL 为全通过。 |
| I-PS-INBOX / I-PS-DISPATCH / I-AS-OUTBOX | tianji.process.inbox.postgres.test.url / tianji.process.dispatch.postgres.test.url / tianji.agent.outbox.postgres.test.url | 读取各类真实 schema、runtime role 与 separate history 条件。 |
| I-AR-TOOL / I-AS-LINKED | tianji.tool.journal.postgres.test.url / tianji.linked.http.postgres.test.url | 仅隔离 tool/linked profile；journal 有独立 pgvector pin。 |
| I-W-BUSINESS / I-W-EXTERNAL | tianji.worker.business.postgres.test.url / tianji.worker.nontransactional.postgres.test.url | 独立 BusinessDB、受限 runtime roles 及测试规定的 schema。 |
| I-USAGE | tianji.postgres.test.url + tianji.postgres.test.model-usage.authorized=true | V15 exact migration、专用模型用量角色/pgvector，独立于 T3-12。 |
| E-BUSINESS / E-RECOVERY | 独立私有 manifests，通过 tianji.expense.e2e.manifest 指定 exact runAuthorized、suite、source 和 artifact lock | 同批当前产物，各使用新的三库和两个租户。只用普通 clean clones 或独立 .git 目录，Driver 当前不支持 linked worktree 的 .git 指针。 |
| E-S12 / L | 当前未发现 S12 dedicated selector。L 保持单独操作者批准 | 可观察环境、采集管道或真实供应商、固定场景、预算和日志方案。缺实际报告则保持待验收。 |

当前 tianji-service POM 中与发布验证相关的实际 profile 只有 AS/PS 的 permission-producer。Master 早期示例的 -Pit 不存在于当前 Tianji POM。I/E 走明确 Surefire selectors 和独立 opt-in 属性。不要将 -DskipITs=true 当成选择器保护，显式 -Dtest=...IT 仍可执行测试。已有 *Test 如 CL HttpAgentResultClientTest 会绑定 loopback listener，禁止进入无监听器 U allowlist。

T3-12 外部供应的 expense E 数据库必须选择 Agent Flyway target=14。Driver 启动子进程前精确检查 Agent V1–V14、Process V1–V10、Worker V1–V2、独立 Outbox 和 Flowable 历史。子进程强制 Flyway/SQL init 关闭，AS 用量功能默认 false。通用 migrate-all 到 V15 会被该 profile 拒绝。现有 expense 合同无需修改，V15 和 S12 单独验收。双 E 报告及其证据要求见下列文档。[Expense 四路径验收](2026-11-05-expense-four-path-acceptance.md) · [Expense 离线门禁](2026-11-05-expense-release-evidence-gate.md)

## 安全离线复跑

仅在依赖及 Maven Wrapper 已在本地就绪后使用本节。Maven -o 不能防止初次 Wrapper 引导下载。执行前记录私有设置路径，确认外部依赖已离线可用且 I/E 连接和 manifest 未设置。第一条 U 命令已在上述 Service 提交用 JDK 25 离线实际复跑：17 个具名测试类、220 项通过，失败、错误、跳过均为零，2026-11-05 20:13:52 +08:00。其余命令是各自验收入口，不能从该 U 结果推断通过。

```sh
# Tianji Service root. Use the reviewed private settings path.
test -n "$acceptance_settings" && test -r "$acceptance_settings"
acceptance_u=AgentRunHttpTest,AgentRunEventStreamCapacityTest,AgentRunReconcilerTest,AdkNativeAgentCompletionTest,AdkSessionEventCodecTest,ProcessModelValidatorTest,AgentStartWorkerTest,AdkGovernedProcessLoopTest,FlowableHumanTaskServiceTest,CmmnModelValidatorTest,RemoteWorkerPollerTest,AdkNativeResumeSemanticsTest,HttpAgentResultTraceLinkTest,AgentResultSpanObservationTest,AgentResultDeliveryHandlerTest,AgentResultInboxControllerTest,AgentResultInboxLagObservationTest
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$acceptance_settings" -s "$acceptance_settings" -o -B -ntp \
  -DskipITs=true -DskipTests=false -Dsurefire.failIfNoSpecifiedTests=false \
  -Dinvoker.skip=true -Darchetype.test.skip=true -Dotel.traces.exporter=none \
  -Dotel.metrics.exporter=none -Dotel.logs.exporter=none "-Dtest=$acceptance_u"

# Compile-only source evidence. skipTests preserves compilation. maven.test.skip does not.
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false STELLAR_VERIFY_GOAL=test \
  ./scripts/verify-source-reactor.sh -gs "$acceptance_settings" -s "$acceptance_settings" -o -B -ntp \
  -DskipTests=true -DskipITs=true -Dinvoker.skip=true -Darchetype.test.skip=true \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none

# Offline expense evidence checker self-tests. No private live bundle is inferred from passing this.
python3 -B -m unittest discover -s scripts -p test_verify_release_evidence.py -v

# Verify an already collected owner-only expense evidence bundle.
python3 -B scripts/verify-release-evidence.py verify \
  --root "$private_evidence_root" --index "$private_index_relative_path" --checkout "$locked_checkout_root" \
  --expect-revision "$expected_service_commit" --expect-candidate-sha256 "$expected_candidate_sha" \
  --audit-from "$audit_from_utc" --audit-until "$audit_until_utc"
```

The exact G7 Expense U, P1 Source84 package, and authorized E commands already live in their reviewed Plans. Run E only as operator. The expense verifier is independent of the source wrapper. The wrapper builds a temporary five-repository Maven reactor without revision validation. The verifier reads a private declared-window bundle, checks A1/artifacts/revisions/raw reports and I/E witnesses, and optionally verifies independent A2. Policy groups are U_SOURCE, I_CASE, E_BUSINESS, E_RECOVERY. It does not run any suite or authorize activation. [Source reactor](../../../scripts/verify-source-reactor.sh) · [Source84 package gates](../plan/2026-11-05-19-20-agent-result-receive-span-links.md) · [Expense evidence policy](../../../scripts/release-evidence-policy-v1.json)

```sh
# Tianji Web root. Node 24 and already verified matching node_modules/toolchain.
npm run typecheck
npm run lint
npm test -- --reporter=junit "--outputFile=$private_web_junit"
PAC_SDK_DIR="$verified_sdk_checkout" PAC_TOOLCHAIN_LOCK="$approved_toolchain_lock" \
  PAC_CONTRACT_ARTIFACT="$verified_contract_jar" npm run build
```

Web source anchors include src/test/expenseFourPathContract.test.ts, src/api/processReleases.test.ts and src/pages/DefinitionReleasePage.test.tsx. Actual Web static U/PaC evidence at the locked SHA is separate from I/E. Earlier local override lock proved matching canonical contract entries only and has LOCAL_UNCOMMITTED authority. Production toolchain requires the exact reviewed artifact/lock and independent registration/grants. No npm install, npm run dev, preview or browser is part of offline validation. [Web release metadata evidence](../../../../tianji-web/docs/egon/evidence/2026-11-05-process-release-metadata.md) · [Web Worker observations evidence](../../../../tianji-web/docs/egon/evidence/2026-11-05-worker-failure-console.md)

## CI readiness and next scope

Service and Web have no tracked .github/workflows. Existing sibling Java workflows show GitHub checkout/setup-java, optional read token and Parent scripts/bootstrap-workspace.sh. That script resolves a broader sibling/install graph than Tianji's source wrapper. Its sibling checkouts are not a reviewed Tianji release lock. Existing sibling Web workflows show Node and PaC SDK pin/artifact fetch. Tianji's checked-in toolchain remains LOCAL_UNCOMMITTED, and this review found no exact cross-repo CI checkout/build authority, approved runner/credential/environment binding, artifact-retention policy or dry run. A copied workflow could access remote dependencies, run unexpected selectors or register artifacts. No workflow is proposed for this slice. [Parent bootstrap](../../../../Stellar-Enclosure-Parent/scripts/bootstrap-workspace.sh) · [Sibling Java CI](../../../../tianshu-service/.github/workflows/ci.yml) · [Sibling Web CI](../../../../yaoguang-web/.github/workflows/ci.yml)

Smallest tracked scope is this one operator document under existing docs/egon/evidence. Existing Master §17/T4-05 defines the matrix and reviewed owner Plans supply the commands. A docs-only publication needs exact-path and reference validation, with no new coding Spec, workflow, test runner, production source, config or DB change. A future CI proposal requires pinned readable sibling refs and artifact provenance, reviewed cache/bootstrap authority, exact U allowlist with report outputs, and independent manual I/E/L approval and secret isolation. SLO targets require a separately agreed workload/hardware and measured p95/p99/error/cardinality results. No platform-wide release approval can be inferred from this inventory.
