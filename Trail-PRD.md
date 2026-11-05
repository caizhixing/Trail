# Trail 产品需求文档（PRD）

> 项目正式名 **Trail**（定名记录见 D5）。本文档是需求基线，不含实现代码。
> 文中少量调用形态 / 依赖接线片段是**接口级需求示例**，最终签名以技术方案评审为准。

| 项 | 内容 |
|---|---|
| 状态 | Draft v0.3，待评审 |
| 日期 | 2026-11-05 |
| 需求来源 | Chuck（github.com/jgilfelt/chuck）模式讨论 + 五项已确认决策 |
| 事实依据 | 宿主 App 仓库实际配置（变体定义、模块命名与依赖惯例） |
| 关联模块 | 宿主既有埋点上报模块（边界见 NG5 / Q4） |

**版本记录**

| 版本 | 日期 | 变更 |
|---|---|---|
| v0.1 | 2026-11-05 | 初稿（工作名 flowlog） |
| v0.2 | 2026-11-05 | 项目定名 **Trail**：API 入口 `FlowLog`→`Trail`、工件 `flowlog-*`→`trail-*`；Q1 库名部分结项 |
| v0.3 | 2026-11-05 | 补入零足迹消除链机制详述（§5.4）；全文去除宿主业务专属命名，改用通用表述 |

---

## 0. 已确认决策（需求基线，变更需重新评审）

| # | 决策点 | 结论 |
|---|---|---|
| D1 | 测试包形态 | 测试日常使用 **internal + release**（minified QA 包）；**production 只存在 production + release** 一种线上包；internal + debug 为开发自用。⇒ 日志启用由 **flavor 决定，与 buildType 无关**（推论见 §5.2） |
| D2 | 线上足迹 | production 变体**纯 no-op**：零输出、零兜底日志，理想目标零字节码（验收 A） |
| D3 | 自动采集钩子 | 需要；首期网络层（OkHttp/Retrofit） |
| D4 | 分发结构 | 方案 A：`api` / `debug` / `noop` 三工件，面向 Maven 发布（仓库内物理布局见 Q8） |
| D5 | 项目命名 | 定名 **Trail**：API 入口对象 `Trail`，Maven 工件 `trail-api` / `trail-debug` / `trail-noop` / `trail-okhttp`；宿主仓库内模块名随宿主命名惯例（如 `<前缀>-trail`）。被否备选：Clue（CLUE 为知名中文 NLP benchmark，语义撞车，且"碎片线索"弱化全流程关联）、FlowLog（Kotlin 协程 Flow 语义垄断，易被误读为"记录 Flow 的库"） |

> 事实校正：口述的 "product" 在宿主仓库中实际 flavor 名为 **`production`**（dimension = `config`）。本文统一使用 `production`。

---

## 1. 一句话定位

**面向 AI 的业务全流程日志框架骨架**：internal 变体全量采集结构化事件流，供 AI / 测试 / 开发三种角色消费；production 变体编译期零足迹；库只提供机制（采集、trace 关联、双受众过滤、存储、查看、导出、脱敏），**不含任何业务事件内容**——事件由各项目在迭代中由 AI 按约定持续播种。

> Trail 的双重语义即本库定位：**全流程轨迹**（traceId 串起的因果链）+ **事后追寻**（AI / 测试沿轨迹定位问题）。

## 2. 背景与问题

1. 现状日志散落在 Logcat：自由文本、无结构、无业务流程关联，AI 无法据此重建因果链。
2. AI 已深度参与迭代与修 bug，但"加日志"没有统一约定与载体，修完即散、复用性差。
3. 测试验证业务流程时不需要开发级细节，需要一条人话级时间线，目前没有过滤手段。
4. 线上包（productionRelease）不允许任何日志残留（性能 / 安全 / 合规）。

## 3. 目标与非目标

### 3.1 目标（G）

| # | 目标 | 衡量 |
|---|---|---|
| G1 | AI 拿到导出日志可重建业务因果链并定位问题 | 验收 D（dogfood 演练） |
| G2 | 测试在 internal + release 包内自助核对业务流程 | 验收 E |
| G3 | production + release 零日志足迹 | 验收 A |
| G4 | AI 播种一条日志 = 一处一行级改动，无清单 / 注册仪式 | 验收 B-1 |

### 3.2 非目标（NG）

| # | 内容 | 理由 |
|---|---|---|
| NG1 | 不替代 Logcat / Timber 系统级开发日志 | 域不同，双轨并存（提供 Logcat echo 桥接即可） |
| NG2 | 不做日志上传 / 远端聚合 | 本地采集本地消费；上报域归既有埋点模块 |
| NG3 | 不做崩溃收集系统 | 只提供"事故现场"数据出口（F7），处理器归 App 既有机制 |
| NG4 | 不内置任何通用业务事件词典 | 库是骨架，内容归项目（核心诉求） |
| NG5 | 不做埋点 / 分析上报 | 归既有埋点上报域；两者事件命名空间不混用（Q4） |
| NG6 | V1 不做跨进程 trace 聚合、span 树 | 控制首期范围（Q5 / Q6） |

## 4. 角色与场景

| 角色 | 使用变体 | 典型场景 | 核心诉求 |
|---|---|---|---|
| AI（消费者） | internalRelease 导出 / internalDebug 现场 | 读日志定位 bug | 稳定 JSONL schema、trace 因果链、事故现场切片 |
| AI（生产者） | — | 迭代 / 修 bug 时播种日志 | 一行式 API + 明确约定文档 |
| 测试 | internalRelease | 验证业务流程 | 简洁视图（仅人话里程碑）、按 tag / 流程过滤 |
| 开发 | internalDebug | 日常开发 | 全量视图、Logcat echo、快速导出 |
| 线上用户 | productionRelease | — | 完全无感知（零足迹） |

## 5. 变体矩阵与分发架构

### 5.1 变体矩阵

> 依据宿主 App 根 build.gradle 既有注释：`internalDebug = daily dev, internalRelease = minified QA build for testers, productionRelease = the published build`。

| 变体 | 用途 | 日志实现 | R8/minify |
|---|---|---|---|
| internal + debug | 开发自用 | trail-debug | 关 |
| internal + release | **测试日常** | trail-debug | **开（minified QA）** |
| production + release | 线上发布 | trail-noop（纯空实现） | 开 |
| production + debug | 不存在（宿主已做变体过滤） | —（其他项目接入若存在，挂 noop 无害） | — |

### 5.2 关键推论：启用门必须跟随 flavor，绝不能跟随 buildType

internal + release 是 release 构建且**已确认开混淆**，但日志必须工作；因此**严禁**用 `BuildConfig.DEBUG` 之类的 buildType 信号做启用开关。启用开关只能是 impl 工件内的编译期常量（debug 工件 = true，noop 工件 = false）。

### 5.3 模块与接线（接口级需求）

```
trail-api       接口 + 数据模型 + 常量门声明（两个 impl 工件都依赖）
trail-debug     真实实现：采集、存储、查看器、导出、通知
trail-noop      纯空实现
trail-okhttp    可选：网络自动采集钩子（M3）
```

```kotlin
// app 侧接线（示意，flavor 名以宿主仓库实际 production 为准）
implementation(project(":trail-api"))
internalImplementation(project(":trail-debug"))
productionImplementation(project(":trail-noop"))
internalImplementation(project(":trail-okhttp")) // M3 起
```

Maven 发布：三（+1）工件**同版本成套**发布（`trail-api` / `trail-debug` / `trail-noop` / `trail-okhttp`），禁止跨版本混搭（api 与 impl 版本不匹配需编译期或启动期快速失败）。

### 5.4 零足迹机制（production 变体）

采用 Chuck 同款的**同 FQN 双工件**模式：debug 与 noop 提供同全限定名、同签名的门类 / 实现类，任一变体下类路径上只有一个。

**机制骨架（示意）**

```kotlin
// trail-noop 工件（与 trail-debug 同 FQN，任一变体类路径上只有一个）
object TrailGate {
    const val ENABLED = false        // trail-debug 工件里为 true
}

// trail-api 工件
inline fun event(tag: String, name: String, attrs: () -> Map<String, String>) {
    if (TrailGate.ENABLED) {
        TrailImpl.event(tag, name, attrs)
    }
}
```

三件套：**`const val` 编译期常量门 + `inline` 函数 + `attrs` lambda 惰性求值**。

**消除链与分工**（谁在哪一步删掉什么）：

| 层 | 机制 | 性质 | 消灭什么 |
|---|---|---|---|
| kotlinc | `const` 常量内联（调用方编译期以字面量替换）+ `inline` 函数体复制进调用点 | 语言规范行为，非优化技巧 | Kotlin 调用点整段死分支：`if (false)` 连同 `attrs` lambda 对象构造与内部字符串拼接 |
| javac | 常量变量内联（JLS 规范行为） | 语言规范行为 | Java 调用点 guard 条件常量化为 `if (false)` |
| R8 | 常量传播（constant propagation）+ shrink | 编译器优化 | Java 死分支及其中的字符串字面量（事件名、tag 不进 dex）；无引用的 api 残留类与门类 |

- **Kotlin 调用点**：production 变体下类路径上是 noop 工件，kotlinc 读到的门常量是 false；`inline` + 常量内联叠加后，调用点出现 `if (false) { … }`，kotlinc 直接删除死分支（含 lambda 构造）——**此时还没轮到 R8**。R8 仅兜底清理 inline 函数残留字节码与无引用类。
- **Java 调用点（存量区）**：inline 对 javac 无效，约定必须手写常量门 guard：`if (Trail.ENABLED) { Trail.event(…); }`。javac 将常量替换进条件但**不保证删除死代码**，字节码可能残留——删除与字符串清理交给 R8 常量传播完成。
- **前提**：productionRelease 开启 R8 minify（宿主现状即如此）。对 Kotlin 调用点 R8 是双保险，对 Java 调用点是必要条件；写入接入文档。
- **为何不能只用"接口 + 空实现"（Chuck 原版形态）**：空方法体零输出，但**参数求值照样执行**——每次调用的字符串拼接、map 构建在 production 里照跑，且接口类与事件名常量留在 dex。lambda 惰性求值解决参数求值；inline + 常量门连调用本身一起消除——这是"**零足迹**"而非"零输出"。
- **编码选型**：宿主栈内已有 kotlinx.serialization 与 OkHttp，JSONL 编码（手写转义 vs kotlinx.serialization）由技术方案定；trail-debug 须自带 consumer ProGuard rules（internalRelease 混淆下工作，见 §10）。

## 6. 核心概念与数据模型

### 6.1 LogEvent（对 AI 的稳定契约，schema 版本化）

| 字段 | 类型 | 说明 |
|---|---|---|
| ts | Long | epoch millis |
| traceId | String? | 所属业务流程；trace 外事件为 null，仍记录 |
| tag | String | 模块标识（项目级常量，如 `widget` `search`） |
| name | String | 事件名，小写 snake_case，动词式（`bind_start` `retry_backoff`） |
| audience | Audience | HUMAN / AI / BOTH |
| level | Level | INFO / WARN / ERROR |
| attrs | Map&lt;String,String&gt; | 结构化上下文；值一律字符串化，超长截断 |
| tid | String? | 线程标记（AI 关联异步线索用） |

**契约条款**：字段名与语义是对 AI 的对外契约，破坏性变更必须升 schema 版本并在导出 meta 中声明（对齐"契约不许顺手改"红线）。

### 6.2 Audience 语义与默认规则

| 值 | 语义 | 示例 |
|---|---|---|
| HUMAN | 业务里程碑，人话 | "小组件添加成功 provider=clock" |
| AI | 技术细节：分支决策、中间态、重试、变量快照 | "retry_backoff attempt=2 delay=300" |
| BOTH | 两类受众都关心 | 关键失败 |

- **默认 AI**：AI 播种的日志绝大多数是细节，显式标 HUMAN 才是里程碑。
- 同一业务动作允许同时记 HUMAN + AI 两条，这是"双受众"的正解，不靠 level 硬凑。

### 6.3 Trace 模型

- `Trail.withTrace(name) { ... }`：协程作用域内自动生成并传播 traceId（CoroutineContext.Element，随结构化并发自动传播）。
- 传播验证清单（P0）：viewModelScope、lifecycleScope、flow 操作符、withContext 切换、子 launch / async。
- trace 外事件允许存在（traceId = null）。
- **回调 / Java 桥接（P1）**：`Trail.currentTraceId()` + 手动接续 API，覆盖存量区回调式代码（launcher 场景常见，如 widget bind 回调）。

### 6.4 调用形态约束（接口级需求，最终签名技术方案定）

```kotlin
Trail.withTrace("widget_add") {
    Trail.event(WIDGET, "bind_start") { mapOf("provider" to cn) }  // 默认 audience=AI
    Trail.flow(WIDGET, "widget_added") { mapOf("provider" to cn) } // 语义快捷 = HUMAN
}
```

- 单一主入口 `event()` + `flow()` 快捷；`attrs` 一律 lambda 惰性求值。
- **Java 可调用（P0）**：存量区（Java 文件）必须能直接调用——大量 bug 位于存量区，不支持 Java 等于丢掉半个库的价值；Java 侧必须遵守常量门 guard 约定。
- 存量区播种日志遵循宿主项目补丁标记规则（标记名以项目级规范为准），写入 TRAIL_CONVENTIONS.md。

### 6.5 TRAIL_CONVENTIONS.md（AI 播种质量基建，P0）

库随附约定文档，AI 代理加日志前必读，包含：tag / event 命名规则、audience 判定指南、隐私红线（§9）、Java guard 规范、补丁标记规范、示例。该文件同时作为人类 review AI 播种质量的清单。

## 7. 功能需求

> 优先级：P0 = M1 / M2 必须；P1 = M3；P2 = backlog。

### F1 采集 API（trail-api 模块）P0
一行式、lambda 惰性、inline 常量门、Java 可调（见 §6.4）。
验收：形态冻结进 TRAIL_CONVENTIONS；production 消除归入验收 A。

### F2 Trace 传播 P0 / 回调桥接 P1
见 §6.3。验收：传播验证清单全绿（自动化用例）。

### F3 网络自动采集钩子（trail-okhttp）P1【已确认需要】
- Interceptor 自动记录请求 / 响应事件：url、method、状态码、耗时、异常 / 超时，audience=AI，tag=`network`。
- 关联发起方 trace（桥接方式技术方案定，需求是"网络事件必须能归因到业务 trace"）。
- body 记录截断（默认 4KB / 侧，可配）、header 脱敏走 §F8 统一机制。
- 与栈内既有 okhttp-logging-interceptor（HttpLoggingInterceptor → Logcat）并存：定位不同（结构化事件 vs Logcat 文本），是否替代由技术方案定。
- 验收：internal 包一次典型业务请求在查看器中呈现完整网络事件且带 traceId。

### F4 采集与存储（trail-debug 模块）P0
- 内存环形缓冲（默认 2000 条，可配）+ 滚动 JSONL 文件（默认 5MB × 3 个，可配）。
- 落盘异步（独立 dispatcher + channel），调用线程只入队；队列满丢最旧（尽力而为语义，一次性 WARN 提示，不刷屏）。
- Logcat echo（P1，默认 internalDebug 开、internalRelease 关，可配）。
- 验收：见 §11-C 性能预算；StrictMode 无磁盘 / 网络主线程违规。

### F5 查看器（trail-debug 模块，Compose）P0
- 入口：常驻通知（Chuck 式）+ 代码入口各一。
- 列表过滤：audience × tag × level × traceId × 关键词 × 时间范围。
- **简洁视图**：一键 = 仅 HUMAN(+BOTH)；**首次打开默认简洁视图并记忆选择**（为测试设计）。
- 事件详情：attrs 展开完整值、点击 traceId 跳转该 trace 完整时间线。
- 统计页（P2）：按 tag / name 聚合计数，供迭代清点日志噪音。
- 验收：5000 条事件下可流畅滚动（分页 / 懒加载）。

### F6 导出 P0
- Share intent 导出 `.jsonl`；范围：当前过滤结果 / 近 N 小时 / 指定 trace。
- 首行 meta：`{"schema":1,"app":…,"version":…,"device":…,"exported_at":…,"filter":…}`。
- 验收：导出文件可直接投喂 AI 会话（验收 D 使用）。

### F7 事故现场 P1
- `Trail.dumpRecent(count)` 输出环形缓冲切片（JSONL 文本）；由 App 既有崩溃处理器自行附加。
- 库**不**自行注册 UncaughtExceptionHandler（避免与既有机制冲突）。
- 验收：模拟崩溃后现场切片完整含 crash 前事件。

### F8 隐私脱敏 P0（默认清单）/ P1（自定义注册）
- 内置敏感 key 默认掩码：token、password、secret、authorization、cookie、phone 等（命中即 `***`）。
- 项目可注册额外规则（精确 key / 正则）。
- 一切自动采集通道（含 F3）过统一脱敏。
- 验收：内置清单命中用例 100% 掩码。

## 8. 性能与资源预算（internal 变体）

| 指标 | 预算 |
|---|---|
| production 变体运行时开销 | 0（编译期消除，验收 A） |
| 采集调用同步开销（入队前，中端机） | p99 < 50µs |
| 连续采集 1 万条 | 主线程无卡顿、无 StrictMode 违规 |
| 内存常驻（环形 + 索引） | < 10MB |
| 磁盘 | 滚动上限硬顶（默认 15MB） |
| 冷启动影响 | < 5ms |

## 9. 隐私红线（写入 TRAIL_CONVENTIONS.md，硬约束）

1. 禁止记录：用户文件路径 / 内容、剪贴板、联系人、通知内容、安装列表等个人内容（launcher 场景尤需注意）。
2. 一切自动采集通道过 F8 统一脱敏。
3. attrs 值默认截断上限，防误灌大对象。

## 10. 技术约束（对齐项目规范）

- Kotlin（新区）；查看器 UI 一律 Compose + ViewModel + StateFlow；禁止 GlobalScope / Thread / Handler。
- 协程异步；禁止 `Any` 业务类型、禁止 `!!`。
- trail-debug 需自带 consumer ProGuard rules（internalRelease 开混淆仍须工作）。
- minSdk 默认对齐宿主仓库（26，Q7 确认）；高于 minSdk 的 API 按宿主守卫规范。
- 依赖一律走 `libs.versions.toml`，禁止臆造依赖（红线 1）。
- 验证流程按项目分级：模块级最小编译（`--offline`）+ 零足迹扫描脚本（A 级静态 + 脚本化）。

## 11. 验收标准

- **A 零足迹（productionRelease，R8 on）**：① 依赖树无 trail-debug；② dex 无 trail-* 包名类；③ 字符串常量池无事件名样本 / 特征串；④ Kotlin / Java 调用点各抽查 ≥3 处反编译无残留。脚本化，纳入 productionRelease 常规检查。
- **B 场景用例**：四角色各 1 条 E2E；B-1 = AI 一次一行级改动完成播种（G4）。
- **C 性能**：§8 全表达标（基准机型固定并记录）。
- **D AI dogfood（G1 核心验收）**：选 ≥3 个历史已解 bug 在 internal 包复现，导出日志投喂**未参与过的** AI 会话，≥2/3 定位到根因模块。
- **E 测试易用性（G2）**：测试同学零培训完成一次业务流程核对。

## 12. 里程碑

| 阶段 | 内容 | 出口 |
|---|---|---|
| M1 机制骨架 | 三模块 + 模型 + F1 / F2 + 环形 / 文件存储 + **验收 A 打通** | 零足迹风险最先证伪 |
| M2 消费侧 | F5 查看器（简洁视图）+ F6 导出 + schema 版本化 | 验收 B / E 可跑 |
| M3 钩子与安全 | F3 okhttp + F2 回调桥接 + F7 事故现场 + F8 自定义脱敏 + echo | 验收 B 补全 |
| M4 约定与试运行 | TRAIL_CONVENTIONS.md + dogfood 演练 + 参数调优 | 验收 D 通过 |

## 13. 风险与对策

| 风险 | 对策 |
|---|---|
| 工具链升级破坏 inline + 常量消除链 | 验收 A 脚本化入常规检查，升级 Kotlin / R8 后必跑 |
| AI 播种质量漂移（命名乱、隐私越界） | TRAIL_CONVENTIONS + review 清单 + 默认脱敏兜底 |
| 日志噪音随迭代膨胀 | 简洁视图隔离 + 滚动上限 + 统计页清点（P2） |
| internalRelease 混淆致运行时坏 | consumer rules + B 级验收含混淆包用例 |
| Java 存量区调用漏 guard | 约定 + code review 清单（P2 可做 lint 检查） |

## 14. 开放问题

| # | 问题 | 默认提案 |
|---|---|---|
| Q1 | Maven group / namespace 坐标 | ~~库名~~ 已定 **Trail**（D5），仅剩 group 坐标待定（宿主现有模块 namespace 仍为占位符）；工件前缀 `trail-` 已定 |
| Q2 | Logcat echo 默认开关 | internalDebug 开 / internalRelease 关 |
| Q3 | 环形 / 文件默认参数 | 2000 条 / 5MB×3，M4 按实测调 |
| Q4 | 与宿主埋点上报模块的域边界 | trail=本地诊断日志零上报；埋点模块=上报分析；命名空间不混用 |
| Q5 | trace 嵌套 / span 树 | V2 再议 |
| Q6 | 多进程采集 | V1 不做，各进程独立文件 |
| Q7 | minSdk | 对齐宿主 26 |
| Q8 | 仓库内物理布局 | `modules/trail/{api,debug,noop,okhttp}`；注意：宿主其他 kit 模块是"单模块镜像 config flavor"形态，与 D4 三工件形态不同——若确定长期仅仓库内使用，可在技术方案评审时评估同构替代（机制等价：编译期常量门 + R8），D4 仍为当前基线 |

## 15. 术语表

| 术语 | 定义 |
|---|---|
| Trail | 本库正式名；双重语义：全流程轨迹 + 事后追寻；同时是 API 入口对象名 |
| trace | 一次业务操作的端到端事件集合，由 traceId 关联 |
| audience | 日志受众维度：HUMAN / AI / BOTH |
| 简洁视图 | 查看器中仅 HUMAN(+BOTH) 的过滤预设 |
| 零足迹 | productionRelease 中无输出行为、无类、无字符串残留（区别于"零输出"：后者参数求值与常量仍留在产物中） |
| 播种 | AI / 开发在业务代码中新增日志事件的行为 |
| dogfood | 用本库日志喂 AI 复现定位历史 bug 的验收演练 |
| 门 / gate | impl 工件内的编译期启用常量（debug=true / noop=false，同 FQN） |
| 常量传播 | R8 的编译器优化：将常量值代入分支判断，删除死分支及其字符串常量 |
