# DECISIONS.md（按业务问题索引；主 agent 单写者维护）

## 流程与模型

### 2026-11-05 模型选型（阶段 0 门）
主 agent = GLM glm-5.3（当前会话）；worker = 跟随会话模型（GLM glm-5.3）；reviewer = `new-provider/k3$max`（k3 家族，与 worker 不同家族 ✓）。由用户在阶段 0 选型门选定，依据 multi-agent-pr-flow skill。

### 2026-11-05 reviewer 模型变更（k3$max → deepseek-v4-pro 裸 ID）
阶段 0 选定的 `new-provider/k3$max` 与 `new-provider/k3` 在本 harness 的 workflow 子代理路径实测静默失败（对照组会话模型正常；`new-provider/` 前缀路径整体不可用）。用户改指定 `deepseek-v4-pro`；裸 ID（不带 provider 覆盖）冒烟实测可用。skill 记录的「deepseek-v4-pro 思考模式不可作子代理」限制针对 `new-provider/` 前缀路径，裸 ID 路径实测不受影响。家族 DeepSeek ≠ GLM（worker）✓ 仍合规。

### 2026-11-05 PR #13 合并竞态事故与流程修正
网络抖动导致 rebase 后的 push 失败，但同一命令块内的 `gh pr merge` 仍执行——PR #13 在 eb1efaa（未含 rebase 修复）状态被合并，main 短暂红（TrailExplicitTraceIdTest 引用 #3 已移除的 recentEvents）。补救：PR #15 单提交迁移修复，合并后全量集成绿（d9fedb7）。**流程修正（主 agent 自律）**：任何 push 失败后禁止同块继续 merge 命令；push 与 merge 必须分开校验（push ok 才 merge）。另：PR 分支若 rebase 过，不得用于「补丁 PR」复用（merge-base 判定混乱致 PR #14 冲突），补丁一律从当前 main 新建分支。

### 2026-11-05 离线缓存补齐：okhttp-jvm-5.5.1.jar（issue #7 env 解封）
bootstrap 验证盲区：只验证过 `:trail-okhttp:compileKotlin`（主源集不触碰 typealias），未跑 test。okhttp 5.x KMP 根包 JVM 变体重定向到 `okhttp-jvm:5.5.1`，其 jar 未缓存（缓存里只有 android AAR 与 4.12.0 旧 jar）。以临时工程（java 插件 + mavenCentral）一次性拉取入库（连带 okio-jvm-3.18.1、kotlin-stdlib-2.2.21 传递缓存；仓库内 stdlib 仍 force 2.2.10 不受影响）。**注意**：全局 `~/.gradle/gradle.properties` 配了死代理 127.0.0.1:7897，任何需要联网的 provisioning 调用须加 `-Dhttp.nonProxyHosts=*` 绕过。**KMP typealias 陷阱**：`okhttp3.Proxy`/`ProxySelector`/`HostnameVerifier` 在 JVM 工件中无 class（actual typealias 擦除为 JDK 类型），纯 JVM 模块源码必须 import JDK 原型（`java.net.Proxy` 等）。

### 2026-11-05 契约修订 A：Trail 增加显式 traceId 重载（用户拍板）
issue #7 揭示 PRD 内部不一致：§2.6 冻结「http 事件 traceId = trailTraced() 调用点捕获值」，但 §2.3 的 Trail API 无通道写 LogEvent.traceId（interceptor 跑在 OkHttp 线程，协程上下文已不在；withTrace 生成新 id 而非复用）。用户三选一拍板 A：三工件（debug/noop/spi）同步增加非 inline 重载 `event(tag, name, traceId: String?, audience, level, attrs: Map)`——debug 用显式 traceId 覆盖上下文查找，noop 空体，spi 加桩。被否备选：B 捕获值存保留 attr `trace_id`（§2.6 语义降级，查看器需特判）；C 真实 debug 工件集成测试（JVM 模块禁 android 依赖，不可行）。#2 的 JVM 签名集合同步更新归 #7。

### 2026-11-05 D-1 结项：withTrace 壳类 R8 全消实证（PR #10 / issue #2）
sample 生产代码保留无法常量门消除的真实 `Trail.withTrace` 调用点（SampleTraceSeed，纯 stdlib startCoroutine 驱动），productionRelease 全部 dex 零 `io/github/caizhixing/trail` 库串、零 `trail_sample_` 特征串；R8 `mapping.txt` 显示 `Trail -> R8$$REMOVED$$CLASS`、`usage.txt` 列全部 trail 类已删。反向实证：internalRelease dex 命中库串 ×3 + 特征串 ×9（扫描器非假阴性，且已固化为脚本 Check 4）。**验收 A-② 按严格口径执行，无需壳类豁免降级。**评审附注（记档）：混淆开启时"库命名空间串扫描"存在盲区，混淆无关的硬证据是特征串 + mapping/usage 检查——已追加至 issue #8 行为 8.5（终验时脚本断言升级为 mapping.txt 检查）。

### 2026-11-05 子代理池扩容与并发分配（用户授权）
用户临时将子代理并发上限提至 8，并指定新增可选模型 gpt-5.6 Sol / gpt-6.1 Sol / glm-5.3 flash，要求跨模型分散以规避单模型并发限制。实测（8 种拼写变体冒烟）：仅 `glm-5.3-flash`（连字符）可用，两个 GPT 模型在本 harness 子代理路径不可达。**分配方案**：worker 池 = 会话默认 `glm-5.3`（重逻辑单）+ `glm-5.3-flash`（轻/中单，每波 ≤2 并发）；reviewer 固定 `deepseek-v4-pro`（不兼任 worker，保证 worker≠reviewer 家族）；主 agent（会话 glm-5.3）+ 单模型并发 ≤3。GPT ID 若后续可得（用户提供确切拼写），替入重单。

### 2026-11-05 仓库可见性
用户拍板**保持 PUBLIC**（skill 红线 19 建议私有，风险已知悉：PRD / issue / PR 内容将公开）。内容红线照旧：一切产出禁止携带用户数据。

### 2026-11-05 红线豁免登记
红线 15（git 操作）与红线 16（ADB / 模拟器 / UI 自动化，仅限本仓库 sample-app 包 `io.github.caizhixing.trail.sample*`）在 multi-agent-pr-flow skill 执行期放开。来源：skill 阶段 0（用户 2026-10-30 授权），范围仅本项目。

## 工具链与离线

### 2026-11-05 离线工具链定型（已实机全变体验证）
Gradle 9.8.2 + AGP 9.5.0（内置 Kotlin 2.2.10）+ Compose 1.12.1 / material3 1.5.0-beta02 + compileSdk 37 / minSdk 26 / JBR 25。三个非常规手段：
1. `settings.gradle.kts` 用 `useModule` 直映射插件工件（`com.android.library` 等 plugin marker 未缓存）。
2. 根 buildscript `force kotlin-reflect:2.2.10`（AGP 传递依赖要 2.4.10，未缓存）。
3. `subprojects` `force kotlin-stdlib:2.2.10`（传递默认 2.2.20，未缓存）。

被否备选：Kotlin 2.4.x 组合（marker / reflect 断链）、compose BOM 2025.10.00（映射到未缓存的 1.9.3 线）、compileSdk 36（compose 1.12 AAR metadata 要求 37）。

### 2026-11-05 library 模块单测任务名契约修正（issue #2 blocked 裁定）
AGP 9.5.0 默认只为 library 的 **debug** buildType 创建 host unit test 组件：`:trail-noop:testReleaseUnitTest` 不存在。契约命令修正为 `:trail-noop:testDebugUnitTest`（noop 源集与 buildType 无关，语义等价）。被否备选：解冻 build 文件加 `androidComponents.beforeVariants` DSL（为一个空实现模块动冻结配置不值）、TEMP init-script（reviewer 无法复现同命令，破坏评审门禁 1）。

## 架构（工作流 PRD §2 的定型记录）

### 2026-11-05 Trail / TrailGate 放 impl 工件（纯 Chuck 模式），trail-api 只放模型
产品 PRD §5.3 字面为「api 含常量门声明」，但验收 A-②「dex 无 trail-* 包名类」要求 Trail 门面与门常量在生产 dex 可消失 → 只有同 FQN 双工件（debug / noop 各自持有 inline event + TrailGate）才能既做调用点内联消除又让类本身被 R8 删除。api 只放纯数据契约（生产无引用即被 shrink）。被否备选：「api 持 inline fun + compileOnly 桩」——桩 const 值会被烘进第三方编译产物（见 spi 决策）。

### 2026-11-05 trail-spi 内部桩工件（不发布）
trail-okhttp 需要编译期引用 Trail 又不能钉死变体 → spi 提供非 inline 桩（Trail / TrailGate），okhttp `compileOnly` 之，运行期由 app 类路径上的 debug / noop 真类解析（Kotlin inline fun 也会编译出真实方法，虚拟分派正确命中）。桩函数必须非 inline（防桩常量烘入）。禁止任何发布工件依赖 spi。

### 2026-11-05 withTrace 为非 inline suspend 壳
`suspend` + `inline` 组合被语言禁止；withTrace 必须 wrap 业务块，无法用常量门 guard 消除。生产足迹 = `trail.Trail` 壳类（纯 `block()` 委托，零字符串、零逻辑），预期 R8 平凡内联后整类消除——由 issue #2 实证；若 R8 不消，验收 A-② 降级为「壳类豁免」并升级用户拍板。

### 2026-11-05 JSONL 手写编码器（不引 kotlinx.serialization）
保持 trail-api / trail-debug 依赖面最小（api 零第三方依赖）；JSON 转义为纯函数，100% 行覆盖测试锁定。被否备选：kotlinx.serialization（给 api 引入编译器插件与运行时依赖）。

### 2026-11-05 Maven 坐标（产品 PRD Q1 结项）
group `io.github.caizhixing.trail`；工件 `trail-api` / `trail-debug` / `trail-noop` / `trail-okhttp`。

### 2026-11-05 测试栈 JUnit4 + runBlocking
`kotlinx-coroutines-test` / `kotlin-test-junit` 未缓存。推论：ViewModel 构造注入 `CoroutineDispatcher`（默认 `Dispatchers.Main.immediate`，测试传 `Unconfined`），配自建 Closeable scope，规避 JVM 单测无 Main dispatcher。

## 产品行为（PRD 外决策，交付时逐条供用户确认）

| # | 决策 | 理由 |
|---|---|---|
| P-1 | 查看器「简洁视图记忆」用 SharedPreferences | debug 工具轻量优先，避免 DataStore 依赖 |
| P-2 | 查看器数据源 = 内存环形 ∪ 文件分页（首屏内存，上滑加载历史文件页） | PRD F5 时间范围过滤需要历史数据 |
| P-3 | echo 默认关；sample 的 internalDebug 显式开 | Q2 提案转正 |
| P-4 | 内置脱敏 key 初始清单：token / password / secret / authorization / cookie / set-cookie / api_key / phone（大小写不敏感，命中即 `***`） | F8 默认清单落定 |
| P-5 | okhttp 事件名：http_request / http_response / http_error；trace 归因走 Request tag（`Request.Builder.trailTraced()` 在调用点捕获 currentTraceId） | F3 桥接方式落定 |
| P-6 | attrs 值截断上限 512 字符；网络 body 截断默认 4KB/侧 | §9.3 / F3 默认值落定 |
| P-7 | traceId 格式 `t_` + 16 hex；tid = 线程名 | 可读性与唯一性折中 |
| P-8 | dumpRecent 空 / 未初始化时返回空字符串而非异常 | F7 容错语义 |
