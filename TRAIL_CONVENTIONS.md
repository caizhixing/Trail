# TRAIL_CONVENTIONS.md — Trail 播种约定（AI 播种质量基建，P0）

> **定位**（产品 PRD §6.5）：库随附约定文档，AI 代理在接入 Trail 的项目里播种日志前**必读**；同时作为人类 review AI 播种质量的核对清单。
> **生效范围**：一切接入 Trail 的宿主项目中的事件播种代码。本仓库 `sample-app` 的播种按本文执行，可对照源码（`MainActivity.kt` / `SampleTraceSeed.kt` / `LegacyJava.java`）。
> **前置机制事实**（产品 PRD §5.2 / §5.4）：`TrailGate.ENABLED` 是 impl 工件内的编译期常量——trail-debug 工件 = `true`、trail-noop 工件 = `false`，同 FQN 双工件任一变体类路径上只有一个。**启用门跟随 flavor，与 buildType 无关**；严禁用 `BuildConfig.DEBUG` 之类的 buildType 信号当开关（internalRelease 是开混淆的 release 包，但日志必须工作）。

## 1. tag 命名（模块级小写常量）

- tag 是**模块标识**，不是事件名：一个业务模块一个 tag（如 `widget`、`search`；网络自动采集固定 `network`，宿主不得复用该 tag 记业务事件）。
- 全小写、下划线分词（`home_feed` 合法）；禁止驼峰、禁止中文、禁止空格。
- 必须定义为**模块级常量**，一处定义、全模块引用，禁止逐调用点内联字面量：
  - Kotlin：`private const val TRAIL_TAG = "widget"`（file top-level / object / companion）。
  - Java：`private static final String TRAIL_TAG = "widget";`
- 新模块接入时新增 tag；旧 tag 只加不改语义——`tag` 是导出 JSONL 的过滤维度，改语义 = 破坏既有报表（对外契约红线）。

## 2. event 命名（小写 snake_case 动词式）

- 全小写 snake_case，**动词式**：描述「发生了什么动作」，如 `bind_start`、`bind_success`、`retry_backoff`、`export_completed`。
- 禁止：驼峰（`bindStart`）、纯名词堆砌（`widget_info`）、中文、空格、把模块塞进事件名（`widget_bind_xxx` 里的 widget 已由 tag 表达）。
- 生命周期事件用对偶命名：`xxx_start` / `xxx_success` / `xxx_error`，便于在 trace 时间线上对齐起止。
- 事件名字符串进入导出 JSONL 的 `name` 字段，并被零足迹扫描脚本用作特征串。**事件名改名 = 对外契约变更**（红线 18）：必须显式列出影响面并经确认，禁止当普通重命名顺手执行。
- 宿主项目可约定项目级事件名特征前缀，供零足迹扫描脚本断言 production dex 无残留；本仓库 sample 的特征前缀为 `trail_sample_`。

## 3. audience 判定指南

| audience | 语义 | 判定规则 | 快捷入口 |
|---|---|---|---|
| `AI`（**默认**） | 技术细节：分支决策、中间态、重试、变量快照 | 不显式写 audience 即为 AI——绝大多数播种是细节，默认即可 | `Trail.event(...)` |
| `HUMAN` | 业务里程碑，人话，给测试的时间线 | **显式指定才是里程碑**；查看器「简洁视图」只显示 HUMAN(+BOTH) | `Trail.flow(...)` |
| `BOTH` | 关键失败，两类受众都关心 | 失败破坏业务流程可用性（用户可见失败、流程中断）时 | `Trail.event(..., Audience.BOTH, ...)` |

判定顺序：**关键失败 → BOTH；业务里程碑（测试核对流程要看的节点）→ HUMAN（用 flow）；其余一律默认 AI**。

- 同一业务动作允许同时记 HUMAN + AI 两条（例：`bind_success` 给 HUMAN，`bind_duration_ms` 给 AI）——这是「双受众」的正解，**不靠 level 硬凑**。
- level 与 audience 相互独立：INFO 常规、WARN 可恢复异常 / 降级 / 重试、ERROR 关键失败。okhttp 采集约定：2xx = INFO、非 2xx = WARN、异常 = ERROR。

## 4. 隐私红线（产品 PRD §9 全文，硬约束）

产品 PRD §9 三条全文纳入，任何播种与自动采集通道不得越过：

1. **禁止记录**：用户文件路径 / 内容、剪贴板、联系人、通知内容、安装列表等个人内容（launcher 场景尤需注意）。
2. **一切自动采集通道过 F8 统一脱敏**（v1 仅内置 P-4 敏感 key 清单：token / password / secret / authorization / cookie / set-cookie / api_key / phone——大小写不敏感、命中即 `***`；v1 无公共注册入口，宿主自定义精确 key / 正则规则属 V2 规划、**未实现**）。
3. **attrs 值默认截断上限**（512 字符，`TrailConfig.attrValueMaxLength`），防误灌大对象。

播种自查（review 清单）：

- attrs 里每个 key/value 问一句：属于用户个人内容吗？导出文件被第三方投喂 AI 会话时能接受吗？
- 要表达「某东西存在 / 大小」时，记布尔、计数或短摘要，不记原文。
- 网络采集默认不记 header、body 默认截断 4KB/侧且非文本不记——不要为「看全一点」绕过这些默认值。

## 5. Java guard 规范与 Kotlin 无 guard 的差异

### 5.1 模板（Java 调用的唯一合法形态）

```java
if (TrailGate.ENABLED) {
    Trail.event(TRAIL_TAG, "retry_backoff", Audience.AI, Level.WARN,
            Collections.singletonMap("attempt", String.valueOf(attempt)));
}
```

### 5.2 为何必须（机制差异教学）

Trail 的零足迹消除链（产品 PRD §5.4）依赖 **`const` 编译期常量门 + Kotlin `inline` 函数 + attrs lambda 惰性求值** 三件套。这套链路只对 **Kotlin inline 调用**完整生效；Java 调用只能走**非 inline 的 Map 重载**（真实方法，参数即时求值），必须手动补 guard：

| | Kotlin（inline `event` / `flow`） | Java（非 inline Map 重载） |
|---|---|---|
| 调用形态 | `Trail.event(TAG, "bind_start") { mapOf(...) }` | `Trail.event(TAG, "bind_start", attrs)` |
| attrs 求值 | lambda 惰性，位于消除分支内，不执行 | Map **即时求值**，先于调用发生 |
| 消除机制 | kotlinc：`const` 常量内联 + `inline` 函数体复制进调用点 → `if (false)` 死分支连同 lambda 对象与字符串字面量**编译期即删**（此时还没轮到 R8） | javac：常量变量内联只把 `TrailGate.ENABLED` 折叠进条件（`if (false)`），**不保证删除死代码**；`inline` 对 javac 无效 |
| 无 guard 的后果 | ——（inline + 常量门自动消除，无需 guard） | 参数求值照常执行（字符串拼接、Map 构建）；调用点与事件名字符串残留字节码，只能靠 R8 常量传播清理——做到「零输出」，做不到「零足迹」 |
| R8 的角色 | 双保险（兜底 inline 残留与无引用类） | **必要条件**：productionRelease 必须开 minify，否则 Java 死分支必留残迹 |

守则：

1. **Java 每一处 Trail 调用都必须手写 `if (TrailGate.ENABLED)` guard**——这不是风格，是消除链的一环。
2. **Kotlin 的 inline `event` / `flow` 不加 guard**（加了也无害，属噪音）。但 Kotlin 调用**非 inline 入口**（Map 重载、`init`、`beginTrace`、`dumpRecent`）时与 Java 同理：参数即时求值，**建议手写 guard**——`MainActivity` 对 `Trail.init` 与 Map 重载调用的 guard 即此守则的示范。
3. guard 必须包住**整个调用含 attrs 构造**，不能只包调用行、把 Map 构造留在门外。
4. guard 分支内禁止再叠加运行时开关（如 `BuildConfig.DEBUG`）——那会把启用门错误绑到 buildType 上，破坏「门跟随 flavor」语义（§5.2 前提）。
5. Java 播种必须进 code review 清单**逐处核对 guard 存在**（产品 PRD §13：漏 guard 由约定 + review 把守，P2 可做 lint）。

## 6. 宿主补丁标记规范引用

- **本仓库不设补丁标记区**：Trail 库与 `sample-app` 全部为新建代码（新区），无上游同步目录，「存量区 / 补丁标记」概念在本仓库 n/a（项目级 AGENTS.md 同口径）。
- **宿主项目把播种打进存量区**（上游代码、二开文件）时，补丁标记**以宿主项目级规范为准**：标记名、BEGIN/END 格式、合并前清点流程均遵循宿主项目 AGENTS.md（示例形态 `// <项目标记> BEGIN` … `// <项目标记> END`），本文不另行规定标记名。
- 存量区播种调用应完整落在补丁标记内（guard + attrs 构造一并包入），上游合并前按标记清点全部侵入点。

## 7. 播种示例

> 以下即 `sample-app` 实际播种的形态，可对照源码。`trail_sample_` 特征前缀仅属 sample；宿主用自己的业务命名（§1 / §2）。

### 7.1 Kotlin（inline 调用，免 guard）

示例 1 —— withTrace 包裹：trace 内技术细节 + HUMAN 里程碑（`SampleTraceSeed.kt`，文件级 `private const val TRAIL_TAG = "sample"`）：

```kotlin
Trail.withTrace("trail_sample_trace_scope") {
    Trail.event(TRAIL_TAG, "trail_sample_event_inside_trace") { mapOf("depth" to "with_trace") } // 默认 AI
    Trail.event(TRAIL_TAG, "trail_sample_event_trace_attributed") {
        mapOf("trace_id" to (Trail.currentTraceId() ?: "unresolved"))   // trace 归因演示
    }
    Trail.flow(TRAIL_TAG, "trail_sample_flow_trace_completed") { mapOf("scope" to "with_trace") } // HUMAN 里程碑
}
```

示例 2 —— 显式 audience / level 的完整 inline 重载（`MainActivity.kt`）：

```kotlin
Trail.event(
    TRAIL_TAG,
    "trail_sample_explicit_level_warn",
    Audience.AI,
    Level.WARN,
) { mapOf("demo" to "explicit_overload") }
```

### 7.2 Java（必须 guard + Map 即时构造）

示例 1 —— guard + 显式 BOTH 的关键节点（`LegacyJava.java`）：

```java
public static void onDataLoaded(int itemCount) {
    if (TrailGate.ENABLED) {
        Map<String, String> attrs = Collections.singletonMap("count", String.valueOf(itemCount));
        Trail.event(TRAIL_TAG, "trail_sample_java_data_loaded", Audience.BOTH, Level.INFO, attrs);
    }
}
```

示例 2 —— 回调式代码的 beginTrace 桥接（`LegacyJava.java`）：

```java
public static void onCallbackInvoked(String source) {
    if (TrailGate.ENABLED) {
        // beginTrace pushes onto the current thread's manual stack; close must
        // happen on the SAME thread (appendix A, boundary 3).
        try (AutoCloseable trace = Trail.beginTrace("trail_sample_java_callback_trace")) {
            Trail.event(TRAIL_TAG, "trail_sample_java_callback_event", Trail.currentTraceId(),
                    Audience.AI, Level.INFO, Collections.singletonMap("source", source));
        } catch (Exception e) {
            // AutoCloseable.close() declares `throws Exception`; Trail's close never throws.
        }
    }
}
```

## 附录 A. withTrace 语义边界（v1，宿主必读）

`withTrace` 是**非 inline suspend 壳**（DECISIONS.md「withTrace 为非 inline suspend 壳」）：`inline suspend` 函数若要用 `withContext` 安装上下文并执行调用方块，块必须 `crossinline`，而 `crossinline` 禁止块内挂起调用——等价不可用，故享受不到常量门消除，生产足迹由 R8 平凡内联后整类删除（实证见 DECISIONS.md D-1 结项）。使用时注意三条 v1 语义边界：

1. **裸 launch / async 逃逸**：traceId 经 `CoroutineContext.Element` + 结构化并发传播。块内从**块外 scope**（捕获的 `viewModelScope`、自建 scope 等）派生的 `launch` / `async` **不继承** withTrace 注入的上下文，其中事件 traceId = null。宿主须在块内用 `coroutineScope { }`（或 `withContext`）结构化创建子协程，让子协程从块内上下文派生。块内的 Flow 操作符链、嵌套 `withContext`、块内 scope 派生均正常继承（协程上下文按 key 合并）。
2. **协程边界异常实例可能被复制**：块内抛出的异常跨越协程边界向外传播时，kotlinx-coroutines 可能复制异常实例（栈恢复 / 去重报告机制）；**类型、message、cause 语义不变**。宿主 catch 处不得依赖异常**同一性**（`e === original`）判断，按类型与语义处理即可。
3. **beginTrace 仅同线程 close 有效**：`beginTrace` 走 per-thread 手动栈（ThreadLocal），`close()` 必须与压栈在**同一线程**配对；跨线程 close **静默无效**（不弹栈、不抛错），手动栈会泄漏一帧。回调式代码在回调线程内 try-with-resources，不把 close 交给别的线程。

补充：`currentTraceId()` 解析顺序 = 协程上下文（优先）→ beginTrace 手动栈（其次），协程 trace 活跃时手动栈被遮蔽。跨线程回调、okhttp 等非协程上下文的归因用显式 traceId 重载 `event(tag, name, traceId, audience, level, attrs)`（契约修订 A）：在协程调用点先 `currentTraceId()` 捕获、传入即可。

## 附录 B. 零足迹硬证据：混淆盲区与 mapping / usage 检查

- 混淆开启时，「dex 库命名空间串扫描」存在**盲区**：R8 会把保留的类改名成任意短名（如 `l`），dex 中不再出现 `io/github/caizhixing/trail` 字面串——**扫不出串 ≠ 没有库代码**。命名空间扫描只能证明「串不存在」，不能单独证明「类被删除」。
- **混淆无关的硬证据**（`scripts/check_zero_footprint.ps1` Check 5，产物目录 `sample-app/build/outputs/mapping/productionRelease/`）：
  1. `mapping.txt`：任何 `io.github.caizhixing.trail.<库类>`（排除 sample 自身命名空间）的映射行，目标必须含 `R8$$REMOVED$$CLASS` 标记；出现库类映射**不带**该标记（即类被保留/改名存留）= **FAIL**。
  2. `usage.txt`：R8 自己记录的已删类清单。Check 5b 以**硬编码的库类清单**（枚举自库源码实际类面）逐类断言：production 工件类——`Trail` / `TrailGate` / `TrailImpl` + `model.LogEvent` / `model.Audience` / `model.Level` / `model.TrailSchema` / `model.TrailConfig` 共 8 类——逐类断言列为已删；trail-debug 独有类（engine / export / trace / viewer 全部顶层类，含 `*Kt` 文件类）不在 production classpath（Check 1 已排除 trail-debug），断言 `usage.txt` **完全不含**其条目（另设 `debug.` 命名空间前缀兜底覆盖嵌套 / lambda / 合成类）。该记录与混淆改名无关，是「类真的没了」的硬证据。
- 特征串（`trail_sample_` 前缀）仍是必要证据：它证明播种的**事件名字符串**没有进 production dex——Java 播种的事件名同以该前缀纳入扫描覆盖。
- review 口诀：**特征串证明「字面量没进 dex」，mapping / usage 证明「类真的被删了」**——两组证据合起来才构成零足迹验收（产品 PRD 验收 A）。
