# Trail 项目级 AGENTS.md

## 项目定位

- Trail = 面向 AI 的业务全流程日志框架骨架（独立库仓库）。
- 产品 PRD：仓库根 `Trail-PRD.md`（v0.3，需求基线，勿改）。
- 工作流 PRD：`docs/prd/trail-lib-v1.md`（冻结接口 + 任务拆分 + 行为清单，fan-out 唯一事实来源）。
- 本仓库只做库本体 + `sample-app` 演示；宿主 App 集成不在本仓库范围。

## 工具链（冻结；改动 = `blocked(env)`）

| 项 | 值 |
|---|---|
| Gradle | 9.8.2 wrapper（distributionUrl 已指向本机缓存发行版） |
| AGP | 9.5.0，**内置 Kotlin/KGP 2.2.10**：Android 模块【禁止】再 apply `kotlin-android` |
| JVM 模块 KGP | 2.2.10（settings.gradle.kts `useModule` 直映射，勿改） |
| Compose | ui/foundation/tooling-preview 1.12.1 + material3 1.5.0-beta02（显式版本，无 BOM） |
| SDK | compileSdk 37 / targetSdk 36 / **minSdk 26** / jvmTarget 17 |
| JDK | JBR 25（`gradle.properties` 的 `org.gradle.java.home`），toolchain 自动下载已禁用 |

- 离线铁律：一切 gradle 命令必须加 `--offline`；禁止任何网络下载；依赖只允许 `gradle/libs.versions.toml` 已有条目（红线 1）。
- `kotlinx-coroutines-test`、`kotlin-test-junit`、robolectric、mockwebserver 均未缓存：禁用。

## 冻结文件（worker 不得修改；需改 = `blocked(contract)`）

- `settings.gradle.kts`、根 `build.gradle.kts`、`gradle.properties`、`gradle/libs.versions.toml`、`gradle/wrapper/*`。
- 各模块 `build.gradle.kts`（确需新依赖/改配置：`blocked(env)` 上报主 agent，不得自行加）。
- `DECISIONS.md`：主 agent 单写者维护，worker 只读。
- `Trail-PRD.md`、`docs/prd/trail-lib-v1.md`：契约文件，worker 只读。

## 模块地图

| 模块 | 类型 | 职责 |
|---|---|---|
| `:trail-api` | Kotlin JVM | 纯数据契约：LogEvent / Audience / Level / TrailSchema / TrailConfig |
| `:trail-spi` | Kotlin JVM | 内部编译占位桩（**不发布**）：Trail / TrailGate 非 inline 桩签名，供 trail-okhttp `compileOnly` |
| `:trail-debug` | Android library | 真实实现：Trail（inline 常量门）/ TrailGate(true) / TrailImpl / 引擎 / 查看器 / 导出 |
| `:trail-noop` | Android library | 纯空实现：同 FQN Trail / TrailGate(false) / TrailImpl |
| `:trail-okhttp` | Kotlin JVM | 网络自动采集 Interceptor |
| `:sample-app` | Android app | internal / production flavor（dimension `config`）演示 + 零足迹验证载体 |

## 构建任务速查（`--offline` 必带）

```powershell
# JVM 模块（api / spi / okhttp）
.\gradlew --offline :trail-api:test
# Android 库（debug / noop）
.\gradlew --offline :trail-debug:testDebugUnitTest
.\gradlew --offline :trail-noop:testReleaseUnitTest
# 变体 APK
.\gradlew --offline :sample-app:assembleInternalDebug
.\gradlew --offline :sample-app:assembleInternalRelease
.\gradlew --offline :sample-app:assembleProductionRelease
# 零足迹扫描（issue #2 交付后）
.\scripts\check_zero_footprint.ps1
```

## 对外契约清单（红线 18；改名 / 删除 / 改语义必须先列清单经确认）

1. `LogEvent` JSONL schema v1 字段名：`ts` / `traceId` / `tag` / `name` / `audience` / `level` / `attrs` / `tid`（工作流 PRD §2）。
2. 导出 meta 首行：`{"schema":1,"app":…,"version":…,"device":…,"exported_at":…,"filter":…}`。
3. `Trail` 公共 API 签名（工作流 PRD §2 冻结代码块）。
4. `TrailGate.ENABLED` 语义：debug 工件 = true / noop 工件 = false，同 FQN `io.github.caizhixing.trail.TrailGate`。
5. Maven 坐标：group `io.github.caizhixing.trail`，工件 `trail-api` / `trail-debug` / `trail-noop` / `trail-okhttp`。
6. 枚举值域：Audience = HUMAN/AI/BOTH；Level = INFO/WARN/ERROR。

## 编码规范

- 全仓皆「新区」：全局 `~/.agents/AGENTS.md` 全部适用（Compose 无状态 / ViewModel→StateFlow→Composable / 禁 `!!` / 禁 GlobalScope、Thread、Handler / 禁 `Any` 业务类型 / ResultState 统一）。
- 一文件一主类型，文件名与主类型一致。
- 测试栈冻结：JUnit4 + `runBlocking`；协程调度一律构造注入（默认 `Dispatchers.Main.immediate`，测试传 `Unconfined`）；禁 `Dispatchers.setMain`。
- 本仓库无上游同步目录：存量区 / 补丁标记概念 n/a（TRAIL_CONVENTIONS.md 面向宿主接入场景编写）。
- minSdk 26：高于 26 的框架 API 必须入口处运行时守卫（守卫在触碰类型之前）。

## git 约定

- 分支 `feat/issue-<N>`；PR body 写 `Relates to #<N>`；禁止直接 push `main`。
- worktree：`E:\work\Trail-wt\wt-issue-<N>`（reviewer 用 `wt-review-<P>`）。
