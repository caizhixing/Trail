# Trail

面向 AI 的业务全流程日志框架骨架。

Trail 为 internal 构建提供结构化、本地可消费的业务事件流，用 `traceId` 串起一次业务操作的因果链，帮助 AI、测试和开发定位问题；production 构建使用纯 no-op 实现，并通过编译期常量门、`inline` 与 R8 消除日志调用，目标是生产包零日志足迹。

> 当前仓库交付范围：Trail 库本体、网络采集模块、内部 SPI 桩和 `sample-app` 演示。宿主 App 接入、远端日志上报、崩溃收集与 AI dogfood 演练不在本仓库范围内。
>
> 在接入项目里播种事件前，AI 代理与开发者**必读** [`TRAIL_CONVENTIONS.md`](TRAIL_CONVENTIONS.md)：tag / event 命名、audience 判定、隐私红线、Java guard 规范与 withTrace 语义边界。

## 特性

- **结构化事件**：固定 JSONL schema v1，字段包括 `ts`、`traceId`、`tag`、`name`、`audience`、`level`、`attrs` 和 `tid`。
- **双受众模型**：`HUMAN` 面向测试和业务流程核对，`AI` 面向技术细节，`BOTH` 同时面向两者。
- **Trace 关联**：通过 `withTrace` 在协程作用域内传播业务 trace，并支持回调或 Java 代码的手动桥接。
- **零足迹生产变体**：`internal` 使用真实实现，`production` 使用同 FQN 的 no-op 实现。
- **本地消费**：内存环形缓冲、滚动 JSONL 文件、Compose 查看器、过滤和导出能力。
- **网络采集**：`trail-okhttp` 提供可选的 OkHttp 请求、响应和异常事件采集，并关联业务 trace。
- **隐私保护**：自动采集统一经过脱敏和长度限制；默认掩码 `token`、`password`、`secret`、`authorization`、`cookie`、`phone` 等敏感 key。

## 模块

| 模块 | 类型 | 职责 |
|---|---|---|
| `trail-api` | Kotlin/JVM | `LogEvent`、`Audience`、`Level`、`TrailSchema`、`TrailConfig` 等纯数据契约 |
| `trail-spi` | Kotlin/JVM | 供 `trail-okhttp` 编译使用的内部 API 桩，不发布、不进入运行时类路径 |
| `trail-debug` | Android library | internal 构建使用的真实实现、采集引擎、查看器和导出能力 |
| `trail-noop` | Android library | production 构建使用的纯空实现，`TrailGate.ENABLED = false` |
| `trail-okhttp` | Kotlin/JVM | 可选的 OkHttp 网络自动采集拦截器 |
| `sample-app` | Android app | internal / production 变体演示与零足迹验证载体 |

## 接入方式

宿主 App 应按 flavor 选择实现工件，不能使用 `BuildConfig.DEBUG` 作为日志开关：`internalRelease` 是开启混淆的测试包，也必须保留日志能力。三行接入接线：

```kotlin
dependencies {
    implementation(project(":trail-api"))                  // 所有变体：纯数据契约
    internalImplementation(project(":trail-debug"))        // internal：真实实现
    productionImplementation(project(":trail-noop"))       // production：纯空实现
}
```

需要网络自动采集时，internal 变体追加 `trail-okhttp` 并注册拦截器：

```kotlin
dependencies {
    internalImplementation(project(":trail-okhttp"))       // internal（可选）
}
```

```kotlin
OkHttpClient.Builder()
    .addInterceptor(TrailInterceptor())
    .build()
```

网络事件归因到业务 trace：在协程调用点用 `request.trailTraced()`（`Request.Builder` 扩展）捕获当时的 `currentTraceId()`，interceptor 产生的 `http_request` / `http_response` / `http_error` 事件即携带该 traceId。

Maven 发布时，`trail-api`、`trail-debug`、`trail-noop` 和 `trail-okhttp` 必须使用同一版本，禁止跨版本混搭。坐标约定：

```text
io.github.caizhixing.trail:trail-api
io.github.caizhixing.trail:trail-debug
io.github.caizhixing.trail:trail-noop
io.github.caizhixing.trail:trail-okhttp
```

## 基本用法

播种约定（命名 / audience / 隐私 / guard）见 [`TRAIL_CONVENTIONS.md`](TRAIL_CONVENTIONS.md)。

### Kotlin

Kotlin 调用使用 lambda 传入属性，以保证 no-op 变体不会执行属性构造逻辑：

```kotlin
Trail.init(context)

Trail.withTrace("widget_add") {
    Trail.event("widget", "bind_start") {
        mapOf("provider" to providerName)
    }

    Trail.flow("widget", "widget_added") {
        mapOf("provider" to providerName)
    }
}
```

默认 `event` 语义为 `audience = AI`、`level = INFO`；`flow` 是 `audience = HUMAN`、`level = INFO` 的业务里程碑快捷入口。需要显式指定受众和级别时：

```kotlin
Trail.event(
    tag = "sync",
    name = "retry_backoff",
    audience = Audience.AI,
    level = Level.WARN,
) {
    mapOf("attempt" to "2", "delay_ms" to "300")
}
```

`withTrace` 的 v1 语义边界（块内裸 `launch`/`async` 从块外 scope 派生时不继承 trace；协程边界异常实例可能被复制但类型/message/cause 语义不变；`beginTrace` 仅同线程 close 有效）详见 [`TRAIL_CONVENTIONS.md`](TRAIL_CONVENTIONS.md) 附录 A。

### Java

Java 调用不是 inline：inline 常量门只对 Kotlin inline 调用生效，Java 只能走非 inline 的 Map 重载（参数即时求值），javac 折叠常量但**不保证删除死代码**。因此存量 Java 代码必须手写常量门 guard，production 包再由 R8 完成死分支清理；漏 guard 的调用点只能做到「零输出」，做不到「零足迹」。完整机制差异教学见 [`TRAIL_CONVENTIONS.md`](TRAIL_CONVENTIONS.md) §5。

```java
if (TrailGate.ENABLED) {
    Trail.event(
        "widget",
        "bind_start",
        Collections.singletonMap("provider", providerName)
    );
}
```

## 变体矩阵

依据产品 PRD §5.1（`internalDebug = daily dev, internalRelease = minified QA build for testers, productionRelease = the published build`）：

| 变体 | 用途 | 日志实现 | R8/minify |
|---|---|---|---|
| `internalDebug` | 开发自用 | `trail-debug` | 关 |
| `internalRelease` | **测试日常** | `trail-debug` | **开（minified QA）** |
| `productionRelease` | 线上发布 | `trail-noop`（纯空实现） | 开 |
| `productionDebug` | 本 sample 不生成；宿主若存在，挂 noop 无害 | — | — |

生产变体的目标不是「空方法但仍执行参数求值」，而是通过 `const val` 编译期门、Kotlin `inline`、lambda 惰性求值和 R8 删除调用点及其事件字符串。Java 调用点则必须遵守上面的 guard 约定，并依赖 R8 完成死分支清理。

## 数据契约

事件名使用小写 snake_case 的动词式命名，例如 `bind_start`、`retry_backoff`。事件行 schema v1 的字段顺序固定为：

```text
ts, traceId, tag, name, audience, level, attrs, tid
```

`traceId` 和 `tid` 没有值时输出 JSON `null`，而不是字符串 `"null"`。导出文件首行为 meta：

```json
{"schema":1,"app":"<applicationId>","version":"<versionName>","device":"<model>","exported_at":"<ISO-8601>","filter":"<summary>"}
```

`Audience` 只有 `HUMAN`、`AI`、`BOTH`；`Level` 只有 `INFO`、`WARN`、`ERROR`。这些字段名和值域属于对外契约，修改需要同步升级 schema 或重新评审。

## 隐私边界

Trail 是本地诊断日志机制，不是埋点或日志上报系统。禁止记录以下内容：

- 用户文件路径或文件内容
- 剪贴板、联系人、通知内容
- 应用安装列表
- 未经脱敏的 token、密码、Cookie、Authorization 等敏感信息

所有自动采集通道，包括网络采集，都必须经过统一脱敏和属性值长度限制。Trail 不自行注册全局崩溃处理器；宿主 App 可在既有崩溃处理器中调用 `Trail.dumpRecent(count)` 附加事故现场切片。完整隐私红线见 [`TRAIL_CONVENTIONS.md`](TRAIL_CONVENTIONS.md) §4。

## 构建与验证

项目使用 Gradle Wrapper、Gradle 9.8.2、JDK 25、compileSdk 37、targetSdk 36、minSdk 26 和 JVM target 17。受限环境下所有 Gradle 命令都使用 `--offline`。

```powershell
# API 模块单元测试
.\gradlew --offline :trail-api:test

# Android 库单元测试
.\gradlew --offline :trail-debug:testDebugUnitTest
.\gradlew --offline :trail-noop:testDebugUnitTest

# sample 变体构建
.\gradlew --offline :sample-app:assembleInternalDebug
.\gradlew --offline :sample-app:assembleInternalRelease
.\gradlew --offline :sample-app:assembleProductionRelease

# production 零足迹扫描（脚本会按需离线构建 release 变体）
.\scripts\check_zero_footprint.ps1
```

零足迹脚本会检查：

1. `productionReleaseRuntimeClasspath` 不包含 `trail-debug`；
2. productionRelease APK 的 dex 不包含 Trail 库包名串和 `trail_sample_` 事件名特征串；
3. `internalReleaseRuntimeClasspath` 反向包含 `trail-debug`；
4. internalRelease APK 反向保留 Trail 库串与特征串，证明扫描本身有效（非假阴性）；
5. productionRelease `mapping.txt` 中任何 `io.github.caizhixing.trail.<库类>`（sample 自身命名空间除外）必须带 `R8$$REMOVED$$CLASS` 删除标记，且 `usage.txt` 将这些 trail 库类列为已删——**混淆无关的零足迹硬证据**。

关于第 5 项的必要性：混淆开启时，dex 命名空间串扫描存在盲区——R8 会把保留的类改名成任意短名，dex 中不再出现库包名字面串，「扫不出串」不能单独证明「类被删除」。因此类级删除证据取自 R8 自己的 `mapping.txt` / `usage.txt`（与混淆改名无关）；特征串（`trail_sample_`）则证明播种的事件名字面量没有进 production dex。两组证据合起来构成零足迹验收（产品 PRD 验收 A）。

## 开发路线

| 阶段 | 内容 |
|---|---|
| M1 | API 契约、零足迹机制、Trace 基础、环形缓冲和文件存储 |
| M2 | Compose 查看器、简洁视图、过滤、导出和 schema 版本化 |
| M3 | OkHttp 网络钩子、回调桥接、事故现场、自定义脱敏和 Logcat echo |
| M4 | 约定文档、sample 播种、dogfood 演练和参数调优 |

详细的需求基线见 [`Trail-PRD.md`](Trail-PRD.md)，冻结的接口、行为清单和任务拆分见 [`docs/prd/trail-lib-v1.md`](docs/prd/trail-lib-v1.md)。

## 当前边界

Trail 不替代 Logcat / Timber，不负责远端聚合或崩溃收集，不内置业务事件词典，也不做 V1 的跨进程 trace 聚合和 span 树。事件内容由接入项目在业务代码中按约定播种，库只提供采集、关联、存储、查看、导出和脱敏机制。
