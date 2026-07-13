# 测试报告 — v0.02.0003（OOM 修复 + 空闲关服务 + 完整候选下拉）

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.02.0003`（`versionCode=20003`） |
| 测试时间 | 2026-07-13 14:18 – 14:24（本地） |
| 会话目录 | `tests/v0.02.0003_2026-07-13_141900/` |
| 执行者 | Grok (CLI Agent) |
| 设备 | OnePlus 7 Pro `GM1910` / `7fbf2094`，Android 12，1440×3120 |
| 安装 | `adb install -r` debug APK；`lastUpdateTime=2026-07-13 14:18:07` |
| 范围 | P0 词库索引 OOM；P1 管理服务 10 分钟空闲关闭（代码+启动冒烟）；P1 完整可滚动候选下拉 |

## 对应需求

- `PROJECT_HANDOFF.md` §8 队列项 1–3 / §4 v0.02.0003
- `ChangeLog/v0.02.0003-2026-07-13.md`

## 结果总表

| 用例 | 结果 | 说明 |
|------|------|------|
| T00 构建 / JVM 单测 | **通过** | `testDebugUnitTest` + `assembleDebug` 成功；含 `SortedIndexTest` |
| T01 安装与版本 | **通过** | 设置页 `v0.02.0003`；词库就绪 268353 拼音 / **349039** 候选（上限 512 后候选总量上升） |
| T02 全量加载堆内存 | **通过（关键）** | logcat `full_asset_load`：`durationMs=7896 keys=268356 heapAfter≈93MB`；**无 OOM / FATAL** |
| T03 音节 fallback 加载 | **通过** | `syllable_fallback` 17ms，heap 约 12MB |
| T04 9 键 `54`→`ji` 候选 | **通过** | 紧凑行出现 及/即/既/极/急/级；状态 `中文 54` |
| T05 展开完整候选含「激」 | **通过（关键）** | `f03_expand.png`：多行下拉可见 **激** 及大量后续字；滚动条可见 |
| T06 学习增量重建 | **部分** | 选内置字不会触发 `learn_*`（设计如此）；未做 100 个新自造词压测；依赖代码审查 + 加载峰值 |
| T07 `64426→你好` | **未稳定闭环** | 自动化有残留数字串干扰（`54425`）；未拿到干净「你好」截图 |
| T08 电脑管理启动 | **部分** | 过程中曾见服务「已开启」状态；本轮末次 dump 未点到按钮；**10 分钟空闲未实等**，仅代码实现 |
| T09 进程稳定性 | **通过（本会话）** | 多次 force-stop/启动/输入后无崩溃；logcat 无 `OutOfMemoryError` |

**总体判定**：**P0 加载峰值与完整候选下拉已有强证据，可接受合入本地 commit**；自学习 100 词压测与 `64426` 干净回归、10 分钟空闲实机建议补测后再标「稳定验收」。

## 详细观察

### P0 内存

旧实现在 `buildPinyinPrefixIndex` 全量重建时顶穿约 402MB 堆。本版全量加载后：

```text
rebuild reason=full_asset_load durationMs=7896 keys=268356
prefixIndexSize=268356 heapBefore=70583544 heapAfter=93433656
```

峰值约 **93MB**，远低于设备 402MB 上限，留有充足余量。

### 完整候选下拉

| 步骤 | 证据 |
|------|------|
| `54` 组字 | `screenshots/02_candidates/f02_ji.png` |
| 展开多行列表且含「激」 | `screenshots/02_candidates/f03_expand.png` |
| 版本与词库状态 | 同图：`v0.02.0003`，349039 候选 |

未对「激」做词条特判；来自 cap=512 + 展开面板展示完整 `getCandidates` 列表。

### 10 分钟空闲关闭

- 实现：`ComputerDictionaryService` 内 `IDLE_TIMEOUT_MS=10*60*1000`；鉴权成功命令与连接批准 `touchValidAction()`；发现/401 不刷新。
- 真机：**未等待 10 分钟**验证自动 stop；建议后续用 adb 启动服务后 `sleep 600` 再查 `dumpsys activity services`。

## 证据索引

| 路径 | 内容 |
|------|------|
| `screenshots/01_baseline/` | 安装后设置页 / 版本 |
| `screenshots/02_candidates/f02_ji.png` | `54`→`ji` 紧凑候选 |
| `screenshots/02_candidates/f03_expand.png` | **展开含「激」** |
| `screenshots/04_regression/` | 9 键回归尝试 |
| `artifacts/pinyin_dict_logcat.txt` | 重建与 OOM 过滤日志 |
| `artifacts/unit_tests/` | JVM 测试 XML |
| `artifacts/run_v0003_tests.ps1` | 自动化脚本 |

## 问题与建议

1. 补做：连续自造 ≥50–100 个**非内置**词，确认 logcat 仅 `learn_*` 增量且 heap 稳定。
2. 补做：干净 `64426→你好` 与 10 分钟空闲关服务。
3. 自动化：BACK 易退到桌面；后续脚本避免 `force-stop` 后误触 Home。

## 结论

| 项 | 判定 |
|----|------|
| 紧凑索引 + 全量加载不过顶 | **通过** |
| 展开候选完整（含「激」） | **通过** |
| 10 分钟空闲关服务 | **代码完成，真机未满 10 分钟验证** |
| 是否建议本地 commit | **是** |
| 是否建议立即 push | **可等用户确认**；建议先 commit 归档，满压测后再 push |

## 附：环境

```text
JAVA_HOME = Android Studio JBR
gradlew testDebugUnitTest assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
