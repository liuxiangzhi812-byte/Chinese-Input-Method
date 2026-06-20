# 测试报告 — v0.01.0022（歧义拼音选择 UI + 词库加载性能首测）

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0022` |
| 测试时间 | 2026-06-20 15:10 – 15:22（本地） |
| 会话目录 | `tests/v0.01.0022_2026-06-20_145553/` |
| 执行者 | Grok (Cursor Agent) |
| 测试类型 | 本机 APK 安装 + ADB 半自动验证 |
| 上次报告基线 | `tests/v0.01.0021_2026-06-20_131827/REPORT.md`（v0.01.0021） |

### 本次测试范围（CHANGELOG 上次报告后未测项）

| 版本 | 功能 | 上次状态 |
|------|------|----------|
| **v0.01.0022** | 9 键歧义拼音选择条（`pinyin_choice_bar`） | CHANGELOG 标明「未做真机测试」 |
| **v0.01.0022** | `getPinyinKeysForDigits` / 点击拼音标签刷新候选 | 未测 |
| **跨版本 Must-Do** | 词库冷启动加载耗时实测（`PROJECT_HANDOFF.md` 最高优先级） | 自 v0.01.0011 起从未测量 |

另补测 v0.01.0021 遗留项：**T13 空 buffer 下按 `0` 插入空格**（上次标记待人工）。

输入场景按用户要求使用 **Edge 浏览器搜索/地址栏**（`com.microsoft.emmx`），不使用短信收件人框。

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| 分辨率 | 1440 × 3120，density 4 |
| 默认 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService` |
| 键盘布局 | 9 键（实验性），测试前已在设置页确认 |

## 构建与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| 使用既有 `app-debug.apk` | 通过 | 含临时 `PinyinDictPerf` 日志（按 handoff 测量方案） |
| `adb install -r` | 通过 | 测试期间多次覆盖安装 |
| 启动页版本号 | 通过 | `v0.01.0022`（见 `s01_main_activity.png`） |
| 词库状态 | 通过 | 268353 拼音条目 / 349039 候选词，状态「已就绪」 |

## 词库加载性能实测（最高优先级）

### 方法

按 `PROJECT_HANDOFF.md`「Dictionary Loading Performance — Needs Real-Device Measurement」：

1. `PinyinDictionary.loadAsync()` 内临时 `Log.i("PinyinDictPerf", "load took …ms")`（测量用，**非永久功能**）。
2. 每轮 `am force-stop com.mercury.chinesepinyinime` 保证 IME 进程冷启动。
3. 立即打开 Edge → 点击搜索/地址栏 `(720, 200)` 触发 `onCreateInputView()`。
4. 等待 5s 后 `adb logcat -d -s PinyinDictPerf:I` 取数。
5. 重复 5 轮。

### 结果（OnePlus 7 Pro，5 轮冷进程）

| 轮次 | 加载耗时 |
|------|----------|
| Run 1 | **947 ms** |
| Run 2 | **977 ms** |
| Run 3 | **947 ms** |
| Run 4 | **622 ms** |
| Run 5 | **968 ms** |

**区间：622 – 977 ms（中位约 947 ms，均值约 892 ms）**

### 解读

| 维度 | 结论 |
|------|------|
| 是否「无感」 | **否**。handoff 参考阈值 200–300 ms；实测约 **0.6–1.0 s**，在旗舰机上已可感知。 |
| 用户能否「跑赢」加载 | **能**。安装后立刻点搜索栏并输入 1–2 音节，很可能仍在 ~100 条 fallback 词库上组字，且无「正在加载」提示。 |
| 设备代表性 | 仅测旗舰机；中低端机预期更慢，handoff 要求后续补测。 |
| v0.01.0021 影响 | 加载路径现含 `buildDigitIndex()`，比 v0.01.0011 纯解析更重；本次数字佐证该风险。 |

证据：`artifacts/perf_results.txt`、`screenshots/02_dict_perf/perf_run1.png` … `perf_run5.png`。

**建议下一步（不在本次范围）**：确认 fallback 对常用音节覆盖；评估 build 期预索引 / 缩减解析量；测量完成后移除 `PinyinDictPerf` 临时代码。

## 用例与结果 — v0.01.0022 歧义拼音选择 UI

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T01 | Edge 搜索栏，9 键输入 `6` `4` | 组字「中文 64」，选择条显示 `ni`/`mi`，默认 `ni` 高亮 | `t01c_before_mi.png`：`ni` 蓝底，`mi` 白底；候选 你/泥/尼/拟/逆/曖 | **通过** |
| T02 | 点击选择条 `mi` | `mi` 高亮，候选变为米/咪/迷… | `t02_mi_selected_v3.png`：`mi` 蓝底，候选 **米 密 迷 谜 秘 觅** | **通过** |
| T03 | 在 `64` 已选 `mi` 后追加 `9` → `649` | 拼音选择清空，回到新数字串默认解析 | `t03_after_append_649.png`：组字区仅显示 `9`，选择条不再显示 `ni`/`mi` | **通过** |
| T04 | 重输后输入 `9` `3` `6` | 唯一拼音时不显示选择条；多拼音时显示 | `t04_unambiguous_936.png`：显示 `wen`/`zen` 两标签（**936 在词库中对应两条拼音**，属歧义，显示选择条符合设计） | **通过**（更正 CHANGELOG 示例：`936` 并非唯一拼音） |
| T05 | 设置页基线 | `v0.01.0022`、9 键状态、词库统计 | `s01_main_activity.png`、`s02_t9_enabled.png` | **通过** |

## 用例与结果 — v0.01.0021 遗留补测

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T13 | 空数字缓冲时按 `0` | 向输入框插入字面空格 | `t05_space_v3.png` 视觉为空；`ui_dumps/edge_field_after_space.xml` 中搜索框 `text=" "`（单空格） | **通过** |

## 截图证据索引

| 文件 | 说明 |
|------|------|
| `screenshots/01_baseline/s01_main_activity.png` | 设置页 v0.01.0022 + 词库/学习统计 |
| `screenshots/01_baseline/s02_t9_enabled.png` | 当前布局：9 键（实验性） |
| `screenshots/02_dict_perf/perf_run1.png` … `perf_run5.png` | 5 轮冷启动后 Edge 搜索栏 + 9 键键盘已弹出 |
| `screenshots/03_t9_pinyin_choice/t01c_before_mi.png` | `64` 歧义选择条（ni 默认） |
| `screenshots/03_t9_pinyin_choice/t02_mi_selected_v3.png` | 点击 `mi` 后候选刷新 |
| `screenshots/03_t9_pinyin_choice/t03_after_append_649.png` | 追加数字后选择条清空 |
| `screenshots/03_t9_pinyin_choice/t04_unambiguous_936.png` | `936` → `wen`/`zen` 歧义选择 |
| `screenshots/04_deferred/t05_space_v3.png` + `ui_dumps/edge_field_after_space.xml` | 空 buffer `0` 键插入空格 |
| `artifacts/perf_results.txt` | 5 轮 `PinyinDictPerf` 原始日志 |
| `artifacts/run_tests_edge.ps1` | 本次自动化脚本（含 Edge 坐标校准） |

## 自动化限制 / 踩坑

1. **Edge 首次启动 Copilot 引导层**会挡住搜索栏；需先点「以后再说」`(720, 1780)`。
2. **NTP 大搜索框 `(720, 600)` 无法唤起 IME**；有效焦点为顶部地址/搜索栏 **`(720, 200)`**（NTP 与恢复标签页均适用）。
3. **9 键坐标**：`4` 在第 2 行 `(198, 2556)`，非第 1 行；第 1 行 `col0` 是「1/符号」。初版脚本误触导致进入符号键盘（已修正）。
4. **拼音选择条点击**：y≈2075、x≈330 为 `mi` 标签；过低会误触候选「泥」并上屏。
5. IME 按键仍不在 `uiautomator` 层级；T9 / 选择条坐标靠截图手算。
6. **`PinyinDictPerf` 为临时埋点**，提交前应移除或改为设置页可开关的诊断项。

## 问题与建议

1. **词库加载（P0）**：实测 ~0.6–1.0 s，高于 handoff「可能无感」区间；建议优先立项优化（预索引、减解析、加载态提示），并在中低端机复测。
2. **fallback 窗口**：安装后立刻输入仍可能命中小词库，建议用 `ni`/`wo`/`de` 等高频音节在冷启动后 500 ms 内实测候选来源。
3. **CHANGELOG 验证说明**：`936` 在完整词库中同时映射 `wen` 与 `zen`，选择条出现是正确行为；文档示例宜改为真正唯一拼音（如更长数字串）以免误解。
4. **v0.01.0022 功能**：歧义拼音选择 UI 已通过真机验证，可合入并推送。

## 总体结论

| 维度 | 结论 |
|------|------|
| v0.01.0022 歧义拼音选择 UI | **通过** |
| v0.01.0021 遗留 T13（0 键空格） | **通过** |
| 词库冷启动性能首测 | **已完成**；结果 **偏慢（622–977 ms）**，需跟进优化 |
| 编译安装 / 设置页 / 9 键布局 | **通过** |

**建议**：提交测试归档与报告；移除临时 `PinyinDictPerf` 日志；将词库加载优化提升为下一迭代 P0 工程项。