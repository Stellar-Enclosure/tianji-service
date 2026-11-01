# Step 33 — CMMN Schema 安全解析边界证据

- Baseline: `main@5881106ca80a9bb109f2c85e596bd4943b715845`，实施前 clean。
- Source authority: 原用户继续蓝图未完成工作并分步提交、完成后审查；本步骤是 T3-08 必要前置，不宣称完整 T3-08 完成。
- [Spec](../spec/2026-11-01-22-35-cmmn-schema-boundary.md)：V9 Review，SHA-256 `5f73d81cae7ff61c3b257227326aafe2841483fddd3a3467dd9d0b0380c01429`。
- [Plan](../plan/2026-11-01-22-35-cmmn-schema-boundary.md)：V5 Review，SHA-256 `fe66594c924645b960a37b4cb1d43fefbabc5ab52f6af28005c8eeb5bfcdfc38`。
- Root 已 full-read 两文档、执行 strict validators、核对当前源码与固定8.0.0 jar 后 GO；没有把派生 Review 冒充用户另行 Accepted。

## 实现与边界

`SafeModelXml.parseCmmn(byte[])` 是 package-private 静态入口。新旧入口通过私有 `Supplier<Schema>` 复用同一 hardened parser body；Supplier 直到原 schema 选择位置才调用，独立 `CmmnSchema` holder 不改变 BPMN/DMN holder 初始化。每次解析创建自己的 DOM/StAX/Validator，只有 compiled Schema 共享。

原 parser body 与 baseline 逐字比较，仅 validator 获取语句由 `(bpmn ? Schemas.BPMN : Schemas.DMN).newValidator()` 改为 `schemaSupplier.get().newValidator()`。字节/UTF-8/scan/DOM/外部访问限制和 generic no-cause 输入错误不变。缺失打包 schema 的内部初始化失败沿用原语义，不伪装成客户端输入错误。

正式 fixture 只在 test resources：`formalCase` / `casePlan`、primary/supplemental/milestone 三 plan item、两 HumanTask、一 milestone、三 sentry。required/manual rules 为空，没有 EL、Forms、自定义扩展、脚本或业务类。`autoComplete=false`；本次只证明 XSD 与固定 converter 结构保留，不证明引擎生命周期。

XSD 的 `definitionRef` 可选。后续语义 validator 必须验证必需引用、固定拓扑与可执行扩展白名单；本 parser 不能提供发布许可。

## 实际验证

所有 Maven 命令在 `/Users/mario/SelfProject/Stellar-Enclosure/tianji-service` 使用 JDK25、现有 source reactor、两处 `/tmp/tianji-compat-fix/settings.xml`；设置 `ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false` 与 OTel 三 exporter 为 none。完整共同命令为：

```bash
ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false \
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
./scripts/verify-source-reactor.sh \
  -gs /tmp/tianji-compat-fix/settings.xml -s /tmp/tianji-compat-fix/settings.xml \
  -B -ntp -DskipTests=false -Dtest=CmmnModelXmlTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dotel.traces.exporter=none -Dotel.metrics.exporter=none -Dotel.logs.exporter=none
```

| Gate | Actual result | Time / log |
| --- | --- | --- |
| RED：compile-ready reject-all stub | 7 tests，6 failures，0 errors，0 skips；两个模型和四个边界正例因缺少行为失败，纯拒绝矩阵通过；exit1 | 19.919s，2026-11-01 22:48:17；`/tmp/tianji-cmmn-schema-boundary-red.log` |
| GREEN1 | 7 tests，1 failure，0 errors，0 skips；缺少 definitionRef 的负例被 XSD 合法接受，暴露测试假设错误；exit1 | 18.384s，22:49:06；`/tmp/tianji-cmmn-schema-boundary-green.log` |
| Correction | 读取固定 jar 的 tPlanItem/tDefinitions：definitionRef 无 required，targetNamespace 必需；只改负例为缺少 targetNamespace，并添加 vector 索引诊断。生产 parser 未弱化；Plan CLAR-003 记录 | 源码/XSD 检查 |
| GREEN2 | 7 tests，0 failures/errors/skips；exit0 | 17.446s，22:50:59；`/tmp/tianji-cmmn-schema-boundary-green-v2.log` |
| Final 5 classes | 35 tests，0 failures/errors/skips；exit0 | 17.388s，22:51:33；`/tmp/tianji-cmmn-schema-boundary-final.log` |
| Strict documents | Spec 与 Plan validators 均 PASS | 当前最终文件/上述 SHA |
| Static/body/scope | hardened body 对比 PASS；baseline snapshot 32 路径逐 SHA 相同，包含23 SQL、全部 POM、原 Validator/ValidatedProcessModel；git diff --check PASS | `/tmp/tianji-step33-baseline.json` |

Final selector：`CmmnModelXmlTest,ProcessModelValidatorTest,HumanTaskModelTest,HumanTaskTimerModelTest,ProcessReleaseServiceTest`。各类分别 7、10、5、5、8 项；只运行有限 JUnit/真实 converter 与受控存储替身，不连接数据库。既有 Jackson deprecated API / unchecked test source 和 JDK ByteBuddy 动态 agent 提示不影响 exit0；没有新增依赖。

新测试覆盖 XML1.1、PI、错误声明编码、非法 UTF-8、畸形 XML、错误 namespace、BPMN/DMN wrong dialect、XInclude、缺少必需 namespace、外部 schema hint、DOCTYPE；每一拒绝验证 exact generic message、null cause、无 private path。精确接受/拒绝边界为512KiB/512KiB+1、depth24/25、elements2000/2001、attributes32/33。

## 资源与独立审查

固定 `flowable-cmmn-converter:8.0.0` 本地 jar 的 `org/flowable/impl/cmmn/parser/` 五文件与 closed resolver 白名单一致：

| Resource | SHA-256 |
| --- | --- |
| CMMN11.xsd | `2f49c41a4c5ba23f4a88b709ac48fe7691e9b3b15b65b125d6be40d0753f2e28` |
| CMMN11CaseModel.xsd | `9480c77b8b382e232050303059e57c53581a7a927e8b09bcce6838cd28d55af1` |
| CMMNDI11.xsd | `62b279ef5121fb80fa5ce450df620efa2824bc01c767f77419e2bfdd1f695d3b` |
| DC.xsd | `3e3b82047cb4f28948b51d71c2ae819c73e1751cb20400cd1be87d3faac61f83` |
| DI.xsd | `dc1e56fd6f1e73aa5052f5373c7a87b3f72285c8b5fa7386c153f00e2c2dcfc0` |

Root 已读取[固定 tag 官方 schema](https://raw.githubusercontent.com/flowable/flowable-engine/flowable-8.0.0/modules/flowable-cmmn-converter/src/main/resources/org/flowable/impl/cmmn/parser/CMMN11.xsd)交叉核对，执行权威为本地固定 jar。独立安全审查确认旧 body/时机、holder 隔离、五资源、fixture 顺序和 converter accessors 无实质问题；两次静态审查误认为 definitionRef 必需，已由 GREEN1 和原 XSD 纠正，不能以静态审查代替真实测试。

## Blocking Manual Checks

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| MC-ARCH-001 | Applicable | PASS | 三 source/test/resource 路径，原 release package | technical utility 保持现有模块；没有业务架构转换 | None |
| MC-REUSE-001 | Applicable | PASS | body byte comparison、现有 schema resolver/Flowable converter | 没有复制安全管线 | None |
| MC-DEP-001 | Applicable | PASS | 全 POM SHA 原样；只加 JDK Supplier import | 零新依赖 | None |
| MC-NAME-001 | Applicable | PASS | SafeModelXml/CmmnModelXmlTest/CmmnSchema | 沿用 utility/test 命名；无新 carrier | None |
| MC-VALID-001 | Not applicable | N/A | 无层间 DTO/Bean Validation 边界 | XML 输入由既有技术 parser 边界校验 | None |
| MC-MODEL-001 | Not applicable | N/A | 无 POJO/DTO/entity/enum 新建 | JDK DOM/Schema 不属业务建模 | None |
| MC-CONVERT-001 | Not applicable | N/A | converter 只作 test oracle；无项目映射 | 无 BaseConverter/MapStruct 变动 | None |
| MC-LOG-001 | Not applicable | N/A | 无业务类或 log | 没有新敏感日志 | None |
| MC-BEAN-001 | Not applicable | N/A | utility/test 无 Spring Bean/注入 | 无 Bean 命名或构造注入变化 | None |
| MC-UTIL-001 | Applicable | PASS | JDK Supplier/DOM/StAX/Schema；复用 resolver | 没有引入自定义 utility 框架 | None |
| MC-JSON-001 | Not applicable | N/A | 无 JSON/wire/前端 enum | XML 之外合同不变 | None |
| MC-TIME-001 | Not applicable | N/A | 无时间字段/运算/import | 无时区/精度变化 | None |
| MC-CONFIG-001 | Not applicable | N/A | 六路径没有配置文件 | profile parity 不变 | None |
| MC-PATTERN-001 | Not applicable | N/A | Simple 固定 selector | 无需额外 Strategy/Factory 类型 | None |
| MC-SCOPE-001 | Applicable | PASS | 精确六路径；32 排除路径原 SHA | 无 release/API/DDL/enum/权限修改 | None |
| MC-TEST-001 | Applicable | PASS | 实际 RED、GREEN1纠正、GREEN2、final35；strict docs | 测试证明 parser/XSD/结构及旧回归 | None |
| MC-BLOCKER-001 | Applicable | PASS | 全部当前步骤 MC 关闭 | 此前置切片无剩余阻塞；完整T3-08继续进行 | None |

## Literal Rule Gate

| Literal rule | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| Rule 1 | Applicable | PASS | utility/test/holder names | 无模糊新 carrier | None |
| Rule 2 | Not applicable | N/A | 无层间 DTO 边界 | 既有技术 XML 验证完整复用 | None |
| Rule 3 | Not applicable | N/A | 无业务 POJO/对象映射 | 无 Lombok/MapStruct 需要 | None |
| Rule 4 | Not applicable | N/A | 无业务类/Spring Bean/DI | 不增加注解/依赖 | None |
| Rule 5 | Applicable | PASS | 只加 JDK Supplier | 符合 utility 复用要求 | None |
| Rule 6 | Not applicable | N/A | 无 JSON/外部 enum | wire不变 | None |
| Rule 7 | Not applicable | N/A | 无配置变动 | profile不变 | None |
| Rule 9 | Not applicable | N/A | Simple 固定schema选择 | 直接私有方法足够 | None |
| Rule 10 | Not applicable | N/A | 无时间变动 | time不变 | None |
| Rule 11 | Applicable | PASS | 原 technical Process Server release package及现有调用 | 用户要求保持现有结构；无新层/ORM/CRUD/DDL | None |

## 提交与未验证边界

精确六路径：SafeModelXml.java、CmmnModelXmlTest.java、formal-case.cmmn、本 Spec、Plan、Evidence。单一逻辑提交 `feat(process): add safe CMMN schema boundary`；提交标识由本文件首次引入的 Git commit 唯一确定（`git log -1 --format=%H -- docs/egon/evidence/2026-11-01-cmmn-schema-boundary.md`），Root 的会话 checkpoint 另存完整 SHA。

未启动应用、引擎、DB、容器、浏览器或 HTTP listener；未执行 Case transport、发布、部署、push、PR、权限注册/激活/授予。未验证 native lifecycle/OR sentry 运行效果、角色授权、Case API、共享事务、关联 Inbox、BPMN Message 回接或 PostgreSQL。下一步仍需封闭语义 validator、正式 Case 合同/执行/任务权限和唯一后续 ProcessDB 迁移；本次既有23SQL逐字不变。
