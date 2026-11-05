# PRD：Trail 日志框架 V1（机制骨架 + 消费侧 + 网络钩子）

- **milestone**: Trail V1
- **日期 / 对谈轮次**: 2026-11-05 / 1（基于产品 PRD `Trail-PRD.md` v0.3 适配独立仓库场景）
- **状态**: draft（待用户定稿冻结）

---

## 1. 背景与目标

产品 PRD（根目录 `Trail-PRD.md`）定义了 Trail：面向 AI 的业务全流程日志框架骨架——internal 变体全量采集结构化事件流，production 变体编译期零足迹。本 PRD 将其 **M1 + M2 + M3 + M4 文档部分** 拆解为可在本仓库并行开发的任务：

- 成功标准 = 产品 PRD 验收 A（零足迹，脚本化）+ F1–F8 的行为清单全绿 + 逐模块单测绿。
- 本仓库交付物：四个库模块（api/debug/noop/okhttp）+ 内部桩（spi）+ sample-app。
- **不在范围**：验收 D（dogfood 演练，需宿主 App 与独立 AI 会话）、验收 E（测试同学试用）、验收 C 的真机性能实测（预算表作为设计约束，性能语义用单测锁定）、Maven 实际发布动作。

## 2. 冻结接口与数据模型

> worker 不得自行修改本节；要改 = 改所有相关 issue 契约 + 变更记录。架构定型理由见 `DECISIONS.md`「架构」节。

### 2.1 模块与包结构

```
io.github.caizhixing.trail            ← Trail / TrailGate / TrailImpl 所在包（trail-debug 与 trail-noop 同 FQN 双工件）
io.github.caizhixing.trail.model      ← trail-api：LogEvent / Audience / Level / TrailSchema / TrailConfig
io.github.caizhixing.trail.okhttp     ← trail-okhttp：TrailInterceptor 等
io.github.caizhixing.trail.debug.*    ← trail-debug 内部实现（engine / trace / viewer / export / mask 包）
```

### 2.2 trail-api（纯数据契约，零第三方依赖）

```kotlin
package io.github.caizhixing.trail.model

data class LogEvent(
    val ts: Long,                    // epoch millis
    val traceId: String?,            // trace 外事件为 null，仍记录
    val tag: String,                 // 模块标识，如 "widget" / "search" / "network"
    val name: String,                // 小写 snake_case 动词式，如 "bind_start"
    val audience: Audience,          // 默认语义见 2.4
    val level: Level,
    val attrs: Map<String, String>,  // 值一律已字符串化 + 已截断 + 已脱敏（入库前完成）
    val tid: String?,                // 线程名；捕获不到为 null
)

enum class Audience { HUMAN, AI, BOTH }
enum class Level { INFO, WARN, ERROR }

object TrailSchema {
    const val VERSION = 1
    // JSONL 行的 key 常量（导出/落盘编码唯一来源，值即上面字段名的字符串字面量）
    const val KEY_TS = "ts"
    const val KEY_TRACE_ID = "traceId"
    const val KEY_TAG = "tag"
    const val KEY_NAME = "name"
    const val KEY_AUDIENCE = "audience"
    const val KEY_LEVEL = "level"
    const val KEY_ATTRS = "attrs"
    const val KEY_TID = "tid"
    // 导出 meta 首行 key
    const val KEY_META_SCHEMA = "schema"
    const val KEY_META_APP = "app"
    const val KEY_META_VERSION = "version"
    const val KEY_META_DEVICE = "device"
    const val KEY_META_EXPORTED_AT = "exported_at"
    const val KEY_META_FILTER = "filter"
}

data class TrailConfig(
    val ringCapacity: Int = 2000,
    val maxFileBytes: Long = 5L * 1024 * 1024,
    val maxFiles: Int = 3,
    val attrValueMaxLength: Int = 512,
    val logcatEcho: Boolean = false,
)
```

### 2.3 Trail 公共入口（trail-debug 与 trail-noop 同 FQN 同签名）

```kotlin
package io.github.caizhixing.trail

object TrailGate {
    const val ENABLED: Boolean   // trail-debug = true；trail-noop = false
}

object Trail {
    // 初始化（仅 debug 有行为；noop 内联空体，生产编译期消除）
    fun init(context: android.content.Context, config: TrailConfig = TrailConfig())

    // Kotlin 主入口（inline + 常量门 + attrs 惰性求值）
    inline fun event(tag: String, name: String, attrs: () -> Map<String, String> = { emptyMap() })
    inline fun event(tag: String, name: String, audience: Audience, level: Level, attrs: () -> Map<String, String> = { emptyMap() })
    inline fun flow(tag: String, name: String, attrs: () -> Map<String, String> = { emptyMap() })  // 语义快捷 = HUMAN + INFO

    // Java 存量区入口（非 inline 真实方法，map 即时求值；Java 侧必须手写 guard）
    fun event(tag: String, name: String, attrs: Map<String, String>)
    fun event(tag: String, name: String, audience: Audience, level: Level, attrs: Map<String, String>)
    fun flow(tag: String, name: String, attrs: Map<String, String>)

    // trace（非 inline suspend 壳，理由见 DECISIONS.md；block 内 suspending 调用合法）
    suspend fun <T> withTrace(name: String, block: suspend () -> T): T
    fun currentTraceId(): String?          // 协程上下文优先，其次手动桥接 ThreadLocal 栈顶
    fun beginTrace(name: String): AutoCloseable   // 回调/Java 桥接（P1）：ThreadLocal 压栈，close 弹栈

    // 事故现场（F7）：环形缓冲切片 JSONL 文本；空/未初始化返回 ""
    fun dumpRecent(count: Int): String
}

@PublishedApi internal object TrailImpl {   // debug=引擎门面；noop=全空体；同 FQN
    internal fun event(e: LogEvent) {}
    internal suspend fun <T> withTrace(name: String, block: suspend () -> T): T = block()
    // …（实现细节归各 issue，本行签名仅为占位说明）
}
```

### 2.4 trail-spi（内部桩，不发布；签名 = 2.3 的非 inline 子集）

```kotlin
package io.github.caizhixing.trail
// 仅 event/flow 的 Map 重载 + withTrace + currentTraceId + dumpRecent；全部非 inline；
// TrailGate 桩常量值无意义（compileOnly，永不上运行类路径）。禁止任何发布工件依赖 spi。
```

### 2.5 JSONL 与导出格式（schema v1）

- 事件行字段顺序固定：`ts, traceId, tag, name, audience, level, attrs, tid`；`traceId`/`tid` 为 null 时输出 `null`（非字符串）；`audience`/`level` 输出枚举名；attrs 为 JSON 对象，key 按插入序。
- 导出文件首行 meta：`{"schema":1,"app":"<applicationId>","version":"<versionName>","device":"<Build.MODEL 拦截异常则 unknown>","exported_at":"<ISO-8601 带时区>","filter":"<过滤条件摘要>"}`。
- 文件名 `trail_export_<yyyyMMdd_HHmmss>.jsonl`，经 FileProvider share intent 发出。

### 2.6 网络事件契约（trail-okhttp）

| 事件 | name | 关键 attrs |
|---|---|---|
| 请求 | `http_request` | `method`,`url` |
| 响应 | `http_response` | `method`,`url`,`status`,`duration_ms`,`body`（截断≤4KB，可关） |
| 异常 | `http_error` | `method`,`url`,`error`（异常类名+消息，≤256字符） |

- `tag = "network"`，`audience = AI`，`level`：2xx=INFO、非 2xx=WARN、异常=ERROR。
- trace 归因：`Request.Builder.trailTraced(): Request.Builder` 扩展在调用点把 `currentTraceId()` 写入 request tag；interceptor 读到则事件带该 traceId，否则 null。
- 一切 attrs 过统一脱敏（F8）；header 不入 attrs（默认不记 header，避免隐私面扩大）。

## 3. 任务拆分预案与合并顺序

| 序号 | issue 主题 | 触碰模块 | 依赖 / 冲突提示 |
|---|---|---|---|
| 1 | trail-api 数据契约模块 | trail-api | 无 |
| 2 | 零足迹机制骨架 + spi + 变体接线 + 验收A脚本 | trail-spi / trail-debug(Trail/TrailGate/TrailImpl 壳) / trail-noop / sample-app / scripts | 依赖 #1；后续所有单的地基 |
| 3 | 采集引擎（环形/文件/异步/脱敏/dumpRecent） | trail-debug engine/* + TrailImpl 接线 | 依赖 #2；与 #4 同文件不同区（TrailImpl），串行合并 |
| 4 | trace 传播 + 回调桥接 | trail-debug trace/* + TrailImpl | 依赖 #2；与 #3 串行合并（都改 TrailImpl） |
| 5 | 查看器（Compose）+ 通知入口 | trail-debug viewer/* + manifest + TrailImpl.init 一行 | 依赖 #3（数据源） |
| 6 | 导出（share/meta/范围） | trail-debug export/* + viewer 菜单挂点 + manifest(FileProvider) | 依赖 #5 |
| 7 | okhttp 网络钩子 | trail-okhttp（独立模块，零冲突） | 依赖 #2（需 spi+api） |
| 8 | TRAIL_CONVENTIONS.md + sample 播种 + README | docs / sample-app / 根 README | 依赖 #2–#7 全并后收尾 |

- 开发波次（fan-out 并发 ≤3）：wave1 = {1}；wave2 = {2}；wave3 = {3, 4, 7} 并行；wave4 = {5}；wave5 = {6}；wave6 = {8}。
- 合并顺序 = 序号顺序（同波次内按 3 → 4 → 7）；每合并一个，后续 worktree rebase。

## 4. 逐任务行为清单（验收标准）

### 任务 1：trail-api 数据契约
- 行为 1.1：`LogEvent` 8 字段存在且类型精确（ts:Long / traceId:String? / tag:String / name:String / audience:Audience / level:Level / attrs:Map<String,String> / tid:String?）—— 构造 + 属性断言测试
- 行为 1.2：`Audience` 恰为 HUMAN/AI/BOTH 且 `Level` 恰为 INFO/WARN/ERROR（`entries` 精确断言，防误加值破坏契约）
- 行为 1.3：`TrailSchema.VERSION == 1`；全部 KEY_ 常量值与 §2.2 字面量一致（逐常量断言）
- 行为 1.4：`TrailConfig` 默认值 = 2000 / 5MiB / 3 / 512 / echo=false（断言）
- 行为 1.5：模块零第三方运行时依赖（编译产物即证，n/a 测试）

### 任务 2：零足迹机制骨架
- 行为 2.1：debug 工件 `TrailGate.ENABLED == true`，noop 工件 == false（各自模块单测）
- 行为 2.2：debug 下 `Trail.event(tag,name){attrs}` 经 inline 路径构造完整 LogEvent 送达 TrailImpl 内部收集点（#2 为内存桩收集器）：tag/name 透传、audience=AI、level=INFO、attrs=惰性求值结果、ts>0、tid=当前线程名、traceId=null（trace 未实现前）—— 桩收集器断言
- 行为 2.3：`flow(tag,name){attrs}` 等价 audience=HUMAN + level=INFO
- 行为 2.4：audience/level 显式重载透传正确
- 行为 2.5：noop 下 `event`/`flow` 的 attrs lambda **不执行**（副作用计数=0）、无任何输出（桩不存在即证）
- 行为 2.6：`withTrace`（#2 为直通壳）：返回块值；noop 同；块内异常原样传播
- 行为 2.7：`currentTraceId()` 返回 null（debug 壳期与 noop）；`dumpRecent(n)` 返回 ""（壳期与 noop）
- 行为 2.8：Map 重载（Java 入口）行为与 lambda 版等价（debug）；noop 为空体
- 行为 2.9：spi 桩签名与 debug/noop 实签名 JVM 一致（trail-okhttp 以 compileOnly 引用编译通过即证；三模块各自 `Class.forName("io.github.caizhixing.trail.Trail")` 非空）
- 行为 2.10：sample-app 全变体 assemble 绿：internalDebug / internalRelease(R8 on) / productionRelease(R8 on)
- 行为 2.11：`scripts/check_zero_footprint.ps1` 对 productionRelease APK：①依赖树无 trail-debug（`dependencies --configuration productionReleaseRuntimeClasspath` grep）②dex 字节扫描无 `io/github/caizhixing/trail` 字符串 ③dex 无 sample 事件名特征串（`trail_sample_` 前缀）；对 internalRelease 反向断言依赖树含 trail-debug；**withTrace 壳是否被 R8 全消记入 issue 评论**（结果回填 DECISIONS.md P 决策）

### 任务 3：采集引擎
- 行为 3.1：`event` 调用线程只入队即返（断言：入队路径无磁盘 IO——引擎的 file writer 在注入的测试 dispatcher 上运行，调用线程观察不到 IO）
- 行为 3.2：环形缓冲容量语义：容量 N，写入 N+k 条后保留最后 N 条（RingBuffer 纯类，边界 0/1/N/N+1 全测）
- 行为 3.3：队列满丢最旧 + 一次性 WARN（注入 logger 断言恰好一次，且后续不再刷屏）
- 行为 3.4：JSONL 编码（手写）：字段顺序、null 字面量、枚举名、attrs 对象、字符串转义（`"`、`\`、控制字符 \u0000-\u001F、非 ASCII 保留原文）—— 转义测试表逐例断言；畸形输入（含换行的值）不产生非法 JSON
- 行为 3.5：meta 行格式（§2.5）逐 key 断言；device 取不到时 "unknown"
- 行为 3.6：脱敏：内置 key（DECISIONS P-4 清单）命中→`***`（大小写不敏感、attrs key 全字匹配）；自定义精确 key 与正则注册生效；未命中值不动；对 key 与 value 双向规则可区分
- 行为 3.7：attrs 值 >512 字符截断（截断后长度=512，无省略标记以外的信息泄漏）
- 行为 3.8：滚动文件：单文件超 maxFileBytes 切新文件；文件数超 maxFiles 删最旧（临时目录实测，含边界：恰好等于上限不切）
- 行为 3.9：`init` 幂等（二次调用不重建引擎不丢数据）；未 init 时 event 不崩且入环形缓冲（文件挂起，init 后补写或丢弃——**选丢弃并记入注释**）
- 行为 3.10：`dumpRecent(n)`：返回环形切片按时间升序 JSONL；n>容量=全部；n<=0=""；未 init=""
- 行为 3.11：logcatEcho=true 时事件同步 echo 到 Logcat（注入 logger 断言内容格式 `[Trail] tag name attrs摘要`）；默认关
- 行为 3.12：10,000 条连续入队（注入 dispatcher 排空后）环形保留最后 2000、无异常、文件滚动符合上限

### 任务 4：trace 传播
- 行为 4.1：`withTrace` 块内 `currentTraceId()` 稳定非 null；出块后恢复 null；返回块值
- 行为 4.2：嵌套 withTrace：内层独立新 id；出内层恢复外层 id
- 行为 4.3：传播矩阵（runBlocking 全 JVM）：子 `launch` / `async.await()` / `withContext(Dispatchers.Default)` / `flow{...}.collect` 内 / `map`/`onEach` 操作符内，均能读到外层 traceId
- 行为 4.4：`withContext(NonCancellable)` 之外的取消传播不破坏 trace（trace 随 Job 结构化并发走，协程结束清理无泄漏——弱断言：出块后 ThreadLocal/上下文无残留）
- 行为 4.5：trace 内 `Trail.event` 事件带 traceId == 块内 currentTraceId；trace 外事件 traceId=null（引擎集成断言）
- 行为 4.6：`beginTrace(name)`：同线程内 currentTraceId 变为新 id；`close()` 后恢复；AutoCloseable use 语法可用；与协程 trace 并存时协程优先（有协程 trace 时 ThreadLocal 不参与）
- 行为 4.7：traceId 格式 `t_` + 16 hex；并发 1000 个 withTrace 无重复 id（简单碰撞断言）
- 行为 4.8：noop 侧 withTrace/currentTraceId/beginTrace：直通/null/空 Closeable，均零副作用

### 任务 5：查看器
- 行为 5.1：首启默认简洁视图（仅 HUMAN+BOTH）；切换全量后记忆（重开保持）——SharedPreferences（P-1）
- 行为 5.2：过滤组合求值（纯域函数）：audience × tag × level × traceId × 关键词（tag/name/attrs 值包含）× 时间范围 [from,to]；六维组合的边界（空选集=不限制）
- 行为 5.3：数据源 = 内存环形 ∪ 文件页（P-2）：首屏内存；触发加载更早后并入文件页；统一按 ts 降序分页（页大小 200，边界：总数<页大小、恰好整页、空集）
- 行为 5.4：点事件 traceId → 视图切换为该 trace 过滤（等价 traceId 过滤器置位，可清除恢复）
- 行为 5.5：详情视图展示存储值完整 attrs（不再截断显示）
- 行为 5.6：通知入口：init 后出现常驻通知（channel id `trail_viewer` 常量断言 + 构建期 manifest/permission 静态检查：Android 13+ POST_NOTIFICATIONS 声明）；点击打开查看器 Activity（exported=false，PendingIntent 显式 Intent）
- 行为 5.7：ViewModel：UiState 为 sealed/data 结构经 StateFlow 暴露；全逻辑 JVM 单测（注入 dispatcher + 假仓库）≥90% 行覆盖
- 行为 5.8：Compose 列表 LazyColumn + 分页触发（列表逻辑由 5.3 域函数覆盖，UI 层不设数字）
- 行为 5.9：代码入口 `TrailViewerActivity.intent(context)` 工厂（internal 或 public 由实现定，不影响契约）

### 任务 6：导出
- 行为 6.1：三种范围（当前过滤结果 / 近 N 小时 / 指定 trace）选择器纯域逻辑：各自产出正确的 LogEvent 选择谓词（边界：N=0、未来时间、不存在的 trace → 空集不报错）
- 行为 6.2：导出文件 = meta 行 + 事件行（ts 升序）；空结果导出仅含 meta 行
- 行为 6.3：文件写入流式（逐行写，不整载内存——以注入 writer 断言逐行调用次数）
- 行为 6.4：文件名格式 `trail_export_<yyyyMMdd_HHmmss>.jsonl`（时钟注入断言）
- 行为 6.5：share intent 经 FileProvider 发出（ContentUri 权限 FLAG_GRANT_READ）——manifest/provider 声明静态检查 + 构建目标类存在
- 行为 6.6：导出数据 = 已脱敏已截断存储值（不二次脱敏也不绕过）

### 任务 7：okhttp 钩子
- 行为 7.1：一次请求产生 `http_request` + `http_response` 两条事件（fake Chain：tag/name/attrs 断言 §2.6；`duration_ms` ≥0）
- 行为 7.2：非 2xx → level=WARN；2xx → INFO；IOException/超时 → `http_error` level=ERROR（error attrs ≤256 字符）
- 行为 7.3：body 记录：默认 4KB/侧截断（fake body 实测）；配置可关；非文本 content-type 不记录 body（只记 `body=<omitted>`）
- 行为 7.4：`Request.Builder.trailTraced()`：在协程 trace 内调用则写入 tag；interpreter 读到 → 两事件 traceId=该值；无 tag → null（runBlocking 断言）
- 行为 7.5：`chain.proceed` 恰好调用一次（fake chain 计数）；interceptor 抛错不吞（原样重抛并先记 http_error）
- 行为 7.6：attrs 过脱敏（authorization 之类 key 若进 attrs 则 `***`——本契约默认不记 header，以注入的额外 header attrs 场景断言）
- 行为 7.7：`TrailInterceptor` 构造配置（body 开关/上限）data class 冻结默认值断言
- 行为 7.8：模块 JVM 纯净（compileOnly spi + implementation api + okhttp；无 android 依赖）

### 任务 8：约定文档 + sample 播种
- 行为 8.1：`TRAIL_CONVENTIONS.md` 存在且含七节：tag 命名 / event 命名 / audience 判定指南 / 隐私红线（§9 全文纳入）/ Java guard 规范（`if (TrailGate.ENABLED)` 模板）/ 宿主补丁标记规范引用（说明标记名以宿主项目为准）/ Kotlin+Java 播种示例
- 行为 8.2：sample-app：Kotlin ≥3 处（含 withTrace 包裹 + event + flow）+ Java ≥3 处（含 guard + Map 重载 + beginTrace 桥接）调用点，全变体编译绿
- 行为 8.3：根 README.md：三行接入接线图（api + internalImplementation debug + productionImplementation noop）、变体矩阵表、零足迹扫描命令
- 行为 8.4：零足迹扫描脚本对最终播种后的 productionRelease 重跑全绿（A 级验证 + 脚本）

## 5. 测试策略

| 任务 | 层级与覆盖目标 | 特殊测试点 |
|---|---|---|
| 1 | 纯域 100% | 契约常量逐字面量断言 |
| 2 | 纯域/壳 100%（可达部分）；样板装配 n/a | noop 惰性不执行；R8 消除实证（脚本 + 反编译抽查记录） |
| 3 | engine 纯件（Ring/Jsonl/Mask/Roll）100%；编排层 ≥90% | 转义表全例；队列满降级；文件滚动边界；init 幂等；10000 条压测语义 |
| 4 | trace 纯域 100% | 传播矩阵；嵌套恢复；桥接 ThreadLocal；id 唯一性 |
| 5 | 过滤/分页/记忆纯域 100%；ViewModel ≥90%；Compose n/a | 简洁视图默认+记忆；六维过滤边界；空集 |
| 6 | 范围选择/命名/流式纯域 100%；FileProvider 胶水 n/a | 空导出；时钟注入；share 权限 flag |
| 7 | 全模块 100%（fake Chain） | body 截断/非文本；proceed 恰一次；异常重抛 |
| 8 | 文档 A 级（静态检查）+ 全变体 assemble + 扫描脚本 | — |

- 测试栈：JUnit4 + runBlocking；调度器/时钟/logger 全部构造注入；禁 mock 库（未缓存）——以手写 fake 代替。
- 覆盖率以行覆盖计，worker `tested` 里程碑附数字（模块 `test` / `testDebugUnitTest` 任务输出或 Jacoco 缺席时以用例清单估算并注明口径）。

## 6. PRD 外决策清单（预填；交付时逐条供用户确认/否决）

| # | 决策 | 理由 | 代码位置 |
|---|---|---|---|
| D-1 | withTrace 生产壳类若 R8 不消 → 验收 A-② 降级「壳类豁免」 | suspend+inline 语言禁令 | issue #2 实证回填 |
| D-2 | JSONL 手写编码器 | api 零依赖 | trail-debug JsonlEncoder |
| D-3 | 未 init 时事件丢弃（不缓冲补写） | 简化语义 | 引擎 init 路径 |
| D-4 | 网络默认不记 header | 隐私面最小化 | TrailInterceptor |
| D-5 | 非 2xx = WARN（非 ERROR） | ERROR 留给异常 | TrailInterceptor |
| D-6 | viewer 通知 Android 13+ 需宿主已授 POST_NOTIFICATIONS，库只声明不请求 | 权限归 app | manifest |
| D-7 | P-1~P-8（见 DECISIONS.md「产品行为」表） | — | 各处 |

## 7. 契约影响面（红线 18 核对项）

全仓为**新建**契约（无存量契约可破坏），本次冻结的契约即 AGENTS.md「对外契约清单」六项 + §2.5 导出格式 + §2.6 网络事件契约。涉契约 diff 的 PR（尤其 #1 #2 #6 #7）reviewer 逐条对照本节。

## 8. 状态协议

默认按 multi-agent-pr-flow `references/status-protocol.md` 执行；本 PRD 无额外里程碑。返工轮上限 3，触顶冻结升级用户。
