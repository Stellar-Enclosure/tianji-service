# Step 36 — CMMN Case HTTP SDK 与 Contract-First OpenAPI 证据

- Baseline: `tianji-service main@4584acb35f87f4a811596e80a54c85b8fdbf42e1`，实施前 clean。
- Source authority: 原用户继续蓝图剩余范围、分步提交并在完成后审查的授权；本 Step 只交付 Case REST consumer SDK，不宣称 provider/runtime 完成。
- [Spec](../spec/2026-11-01-23-45-case-http-client.md)：V9 Review，SHA-256 `e6a47e758b685097d3641a1e913c8baa5f249b0f76757e16da82852e14b82cd8`。
- [Plan](../plan/2026-11-01-23-45-case-http-client.md)：V5 Review，SHA-256 `c0c8f238fa7d150b25fa97ed26d02cdcca091b752533352d500a4326238ec83c`。
- Root full-read 最终 Spec 与 Plan，修正 definition ID 示例与 non-2xx/invalid-2xx 流程、执行 strict validators 和 hash 后 GO；没有冒称用户 Accepted。
- Packaged `META-INF/tianji/openapi/case-v1.json` SHA-256：`7526efca2d2549b89c6611c108a773d3acc9305d33f7c1766be0ad8deaee1edc`。

## 实现与设计边界

复用现有 Adapter：`HttpProcessClient implements CaseClient`，三个方法精确 POST `cases/start`、`cases/get`、`cases/act-plan-item`，保留 base prefix。现有 `Function<byte[],T>` decoder extension 选择独立 `CaseResponseCodec`，无需新 Strategy type、依赖或 transport abstraction；专用 strict codec 隔离 legacy JSON 行为。

Case guard 在 token supplier 前要求 normalized HTTPS 或 HTTP loopback，重定向策略保持 NEVER；请求发送 Bearer、Accept/Content-Type application/json 与 Cache-Control no-store，不发送 tenant/actor/role claims。每次调用只 sendAsync 一次，沿用 token retrieval 之后的 HTTP timeout；timeout/interruption 取消 future，oversize 取消实际 subscriber。Case response 上限为128KiB，旧 request9MiB/response2MiB 不变。

Case success 仅接受 exact200 JSON/+json；关闭 duplicate/unknown/trailing/coercion/numeric-enum，要求全部成功字段及 explicit nullable finishedAt，限制16 items/4 actions，复用 C constructors 的 cross-field/transition/uniqueness invariants。start 绑定 definition/release/business/correlation；get 绑定 Case ID；act 绑定 command/Case/item/action/previousVersion。Terminal start replay 与 sparse historical snapshot 保留。

`submittedAt`、nonnull `finishedAt`、`occurredAt` 在 Jackson 转换前以 JDK `Instant.parse` 验证 absolute timestamp grammar，拒绝 Jackson3.1.7 仍可能接受的 quoted numeric epochs；offset input 与 nanoseconds 保留为同一 Instant，无默认时区、Clock 生成、chronology/freshness 检查或持久化改变。

Error body 只含 code 与 optional nullable correlationId；仅九个 code/status pairs 可信，其他 body/status 走安全 fallback。Case exception 不泄露 correlation/body/cause/token；同步 sendAsync RuntimeException 仅在 Case 分支屏蔽，旧分支仍抛原对象。Retry-After 仅是 bounded metadata，`172800` 秒截为一天，malformed private header→null 且不泄露，SDK 不自动 retry。

OAS3.1 resource 是 consumer contract-first authority，3 POST、11 schemas、200+8 error statuses、bearer security；每个 error schema 保留 `$ref` 与 sibling `properties.code.enum`。示例 `primaryTask`/`supplementalTask` 指 XML definition IDs，不是 planItem element IDs。无 springdoc/provider annotation、`/v3/api-docs` 或 route readiness 证明。

## 实际验证

Root 在 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` 执行同一有限 offline 命令；隔离 settings 只含 `/Users/mario/maven/repository` 与 noninteractive，不复制用户 credentials/mirrors。只有 Root 运行 Maven，未启动应用、listener 或外部服务。

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -o -B -ntp -DskipTests=false -Dtest=CaseResponseCodecTest,HttpCaseClientTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

| Gate | Actual result | Time / log |
| --- | --- | --- |
| RED：resource 已存在，codec reject-all / Case methods UOE | 13 tests，9 failures，0 errors/skips；four passes 为 strict negatives、bounds、OAS 与 legacy smoke；失败来自缺失行为，无 fixture/compiler failure；exit1 | 14.161s，2026-11-04 15:24:02 +08:00；`/tmp/tianji-case-http-client-red.log` |
| GREEN：final codec/client | 13 tests，0 failures/errors/skips；BUILD SUCCESS，exit0；codec7 + HTTP6 | 22.187s，2026-11-04 15:25:37 +08:00；`/tmp/tianji-case-http-client-green.log` |
| Strict documents / artifact | final Spec/Plan strict PASS；packaged OAS field/reflection/local-ref/security/status-code checks通过 | 最终文档与 resource hashes 如上 |
| Legacy/static/scope | constructor、old error、evaluate-through-other legacy methods、helper tail byte-identical；共享 call 仅增加局部 Case branches；旧 limits 不变；控制 fake smoke与原同步异常对象通过 | Root diff/static audit；无 POM、Mapper、properties、C、SQL 或 permission 变化 |
| Excluded snapshot | `/tmp/tianji-step36-baseline.json` 的184路径逐 SHA unchanged，其中23 SQL、全部 POM/C Java、旧 HumanTask codec/BoundedBodySubscriber/ProcessClientException | snapshot SHA-256 `02c654a576a2358a151ab03d459e65ae45ae191aabf7eb11978f76cdd4610d29` |

HTTP fake 使用实际 JDK BodyHandler/BoundedBodySubscriber，没有真实 HTTP I/O；incomplete-future 调用有 preemptive2s assertion 与 finally cancellation，标准 CompletableFuture 足够，无冗余 TrackingFuture forwarding。quoted epoch regressions 分别覆盖 submittedAt、nonnull finishedAt、occurredAt。上述结果只证明指定两类 focused tests，未运行 listener-starting `HttpProcessClientTest` 或完整测试套件。

## API Blocking Gates

| Gate ID | Status | Actual evidence / limit |
| --- | --- | --- |
| API-GATE-001 | PASS | 三个独立消费目标及一调用请求断言，无 fetch-then-forward |
| API-GATE-002 | PASS | start/act Command、get Query；REST-only，无新增 bus/read store |
| API-GATE-003 | PASS | exact POST/path/prefix/header/200/status/media/binding/one-send tests；provider idempotency implementation deferred |
| API-GATE-004 | N/A | 无 GraphQL schema、operation 或 consumer |
| API-GATE-005 | PASS | OAS/C reflection、strict codec、required/null/enum/time/quoted-epoch tests；C constructors 不变 |
| API-GATE-006 | PASS | pre-token guard、无 identity headers、safe code/status与private detail suppression tests；provider auth 未验证 |
| API-GATE-007 | PASS | client-only contract-first resource；无 springdoc/Springfox/dependency或伪生成文档 |
| API-GATE-008 | PASS | 实际 packaged artifact test 检查 operation schemas、local refs、security及error `$ref` sibling code enums；不是完整第三方 OAS/runtime验证 |
| API-GATE-009 | PASS | Root全文核对Spec/Plan，focused GREEN13覆盖字段、时间、错误、兼容；exact8/static hashes一致 |

## Blocking Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 沿用 client technical Adapter/package，无新层或module | 当前步骤已验证 | None |
| MC-REUSE-001 | Applicable | PASS | HttpClient、bounded subscriber、Jackson、C constructors、Function decoder复用 | 当前步骤已验证 | None |
| MC-DEP-001 | Applicable | PASS | 全部 POM SHA不变，零新依赖 | 当前步骤已验证 | None |
| MC-NAME-001 | Applicable | PASS | CaseResponseCodec与两Test语义明确，无新carrier | 当前步骤已验证 | None |
| MC-VALID-001 | Applicable | PASS | 非null C输入、pre-token URI、closed decoder、binding negatives通过；无新增Spring layer handoff | 当前步骤已验证 | None |
| MC-MODEL-001 | Not applicable | N/A | C records/enum不变，未新增数据模型 | 当前步骤不涉及该职责 | None |
| MC-CONVERT-001 | Not applicable | N/A | 同一C类型直接JSON decode，无跨模型mapper/PO conversion | 当前步骤不涉及该职责 | None |
| MC-LOG-001 | Not applicable | N/A | 无业务logging类或新增log | 当前步骤不涉及该职责 | None |
| MC-BEAN-001 | Not applicable | N/A | 无Spring Bean/DI变化 | 当前步骤不涉及该职责 | None |
| MC-UTIL-001 | Applicable | PASS | JDK与既有Jackson，无新utility dependency | 当前步骤已验证 | None |
| MC-JSON-001 | Applicable | PASS | strict Jackson/OAS/reflection/status-pair/unknown/duplicate/coercion tests通过 | 当前步骤已验证 | None |
| MC-TIME-001 | Applicable | PASS | Instant grammar/offset/nanos/nullable finish/no-chronology、future cancellation与Retry-After cap tests通过 | 当前步骤已验证 | None |
| MC-CONFIG-001 | Not applicable | N/A | 所有既有properties不变，无profile/docs endpoint配置 | 当前步骤不涉及该职责 | None |
| MC-PATTERN-001 | Applicable | PASS | 现有Adapter与Function decoder分离传输/Case decoding variation，无额外抽象 | 当前步骤已验证 | None |
| MC-SCOPE-001 | Applicable | PASS | exact8、184 excluded hashes不变；无C/PS/POM/SQL/permission修改 | 当前步骤已验证 | None |
| MC-TEST-001 | Applicable | PASS | 实际 RED13/9fail→GREEN13/0fail，strict/static闭合；无listener或完整套件声明 | 当前步骤已验证 | None |
| MC-BLOCKER-001 | Applicable | PASS | 当前consumer Step gates闭合；provider readiness仍属后续范围 | 当前步骤已验证 | None |


## Literal Rule Gate

| Literal rule | Applicability | Status | Diff/path/symbol evidence | Test/static evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | 既有Adapter、新Codec/Test语义命名，无新carrier | GREEN13 + source/static scope audit | 现有规范在当前范围内满足 | None |
| Rule 2 | Not applicable | N/A | 无Spring service/layer handoff；既有C constructor与consumer边界校验已实测 | exact8 paths / unchanged C and POM snapshot | 当前步骤无对应修改 | None |
| Rule 3 | Not applicable | N/A | 未新增普通POJO、record或mapper；C模型保持不变 | exact8 paths / unchanged C and POM snapshot | 当前步骤无对应修改 | None |
| Rule 4 | Not applicable | N/A | 无业务Bean、DI或日志变化 | exact8 paths / unchanged C and POM snapshot | 当前步骤无对应修改 | None |
| Rule 5 | Applicable | PASS | JDK与既有Jackson，POM不变 | GREEN13 + source/static scope audit | 现有规范在当前范围内满足 | None |
| Rule 6 | Applicable | PASS | Spring Boot管理版本的既有Jackson3 stack；closed wire/OAS drift tests通过，无竞争JSON库 | GREEN13 + source/static scope audit | 现有规范在当前范围内满足 | None |
| Rule 7 | Not applicable | N/A | 全部配置键与profiles不变 | exact8 paths / unchanged C and POM snapshot | 当前步骤无对应修改 | None |
| Rule 9 | Applicable | PASS | 复用Adapter及Function decoder variation point，避免复制transport或新增Strategy层 | GREEN13 + source/static scope audit | 现有规范在当前范围内满足 | None |
| Rule 10 | Applicable | PASS | 仅java.time；三timestamp grammar与precision、Duration/cancellation/Retry-After tests通过 | GREEN13 + source/static scope audit | 现有规范在当前范围内满足 | None |
| Rule 11 | Applicable | PASS | 保留现有client module/package与依赖方向，无层、ORM、DDL或CQE基础设施扩展 | GREEN13 + source/static scope audit | 现有规范在当前范围内满足 | None |


## 提交与未验证边界

Exact8：修改 `stellar-enclosure-tianji-client/src/main/java/top/egon/cola/platform/tianji/client/HttpProcessClient.java`；新增同package的 `CaseResponseCodec.java`、test package的 `CaseResponseCodecTest.java`/`HttpCaseClientTest.java`、`src/main/resources/META-INF/tianji/openapi/case-v1.json`，以及本 Spec/Plan/Evidence三文档。所有路径均以client module或docs/egon为根，未加入临时日志、settings、snapshot或build outputs。

单一语义提交：`feat(client): add governed Case HTTP SDK contract`。引入本 Evidence 的真实 commit 在提交后用 `git log -1 --format=%H -- docs/egon/evidence/2026-11-01-case-http-client.md` 取得，再核对该commit的exact8与clean checkpoint；不在提交前自写自身hash。

未执行真实HTTP I/O/listener、server/controller/provider route、OAuth/IAM/permission授权、PostgreSQL/DB迁移、native CMMN lifecycle、部署、push或浏览器。SDK成功不证明provider存在或可用；advertised actions不替代后续trusted tenant/actor、release policy、native state及expected-version授权检查；response no-store为future provider要求，SDK未强制该响应头。
