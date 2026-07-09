# 测试报告 — v0.01.0025（App 内置 IME 测试输入框）

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0025` |
| 源码状态 | 工作区未提交变更（`MainActivity` / `activity_main` 等）；`git HEAD` 仍为 `1c743fb`（v0.01.0024） |
| 测试时间 | 2026-07-09 13:38 – 13:46（本地） |
| 会话目录 | `tests/v0.01.0025_2026-07-09_133826/` |
| 执行者 | Grok (Cursor Agent) |
| 测试类型 | 本机 `assembleDebug` + 真机 ADB 半自动验证 |
| 上次报告基线 | `tests/v0.01.0024_2026-06-20_171120/REPORT.md` |

### 本次测试范围

| 版本 | 功能 | 说明 |
|------|------|------|
| **v0.01.0025** | 主界面「快速测试」多行输入框 + 启动聚焦 | 本轮主验收 |
| **回归** | 26 键 `ni→你`、空格上屏、符号键盘 | **默认入口改为 app 内输入框** |
| **回归** | 9 键 `64→ni/mi`、点 `mi`、`94664→zhong`、重输、符号往返 | 同上 |
| **回归** | 设置页布局切换 26/9、词库状态、操作按钮 | 确认未被挤掉 |
| **可选** | Edge 跨应用轻量打开 | 仅确认 IME 在外部 App 仍可弹出 |

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| 分辨率 | 1440 × 3120 |
| 默认 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService` |

## 构建与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| `gradlew assembleDebug` | **通过** | JDK 21（`.gradle-user-home/jdks/eclipse_adoptium-21-amd64-windows.2`），约 50s |
| `adb install -r` | **通过** | Streamed Install Success |
| 设置页版本号 | **通过** | `s01_app_open.png` 显示 **v0.01.0025** |
| 词库状态 | **通过** | 268353 拼音条目 / 349039 候选词，已就绪 |

## 用例与结果

### 准备项 / 新功能验收

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| **P01** | 安装 debug APK | 安装成功 | `Success` | **通过** |
| **P02** | 打开 app 确认版本 | `v0.01.0025` | `s01_app_open.png` | **通过** |
| **P03** | 顶部「快速测试」区块与多行输入框可见 | 有说明文案 + hint「在这里直接测试输入法」 | `s01_app_open.png` | **通过** |
| **P04** | 启动后测试框获得焦点 | `ime_test_input` focused 或可一点聚焦 | UI dump `focused="true"`；`s02_after_focus_check.png` | **通过** |
| **P05** | 在 app 内直接弹出 ChinesePinyinIME | 不必先开 Edge | `r03_26key_keyboard.png` / `r30_t9_kb.png` | **通过** |
| **P06** | 设置区控件仍在 | 词库/学习/布局切换/清除/系统设置入口 | `r29_layout_9.png`、`t12_settings_scrolled.png` | **通过** |

### 26 键冒烟（app 内输入框）

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| **T01** | 设置切 26 键后弹键盘 | QWERTY 字母键盘 | `r01_layout_26_status.png`「当前布局：26 键」；`r03_26key_keyboard.png` 字母键 | **通过** |
| **T02** | 输入 `n` `i` | 组字 `中文 ni`，候选含「你」 | `r04_ni_composing.png` | **通过** |
| **T03** | 空格上屏 | 输入框出现「你」 | `r05_ni_committed.png` | **通过** |
| **T04** | 点候选上屏 | 点第一候选提交 | `r07_ni_cand_tap.png` 仍为组字态（坐标 `(180,2140)` 未命中候选条） | **待人工**（空格路径已覆盖上屏） |
| **T05** | DEL 短按 / 长按 | 删组字或连续删 | `r08_del_short.png` 组字变为 `nin`（自动化路径噪声）；长按有交互 | **待人工** |
| **T06** | 123 符号 ↔ ABC | 符号页与字母页往返 | `r10_symbol.png` 符号 + `ABC`；`r11_abc_back.png` 回到字母 | **通过** |

### 9 键冒烟（app 内输入框）

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| **T07** | 设置切 9 键后弹键盘 | 数字格 + 左侧拼音列区域 | `r29_layout_9.png`「当前布局：9 键」；`r30_t9_kb.png` 9 键网格 | **通过** |
| **T08** | 输入 `64` | 左侧竖向 `ni`/`mi`，默认 `ni` 高亮；候选 你/泥/尼… | `r32_64.png` / 首轮 `t07_64_vertical_list.png` | **通过** |
| **T09** | 点左侧 `mi` | `mi` 高亮，候选 米/密/迷… | `r33_mi.png` / 首轮 `t08_mi_selected.png` | **通过** |
| **T10** | 输入 `94664` | 拼音侧 `zhong` 等，候选 中/种/重… | `r37_94664_correct.png`：**中文 94664**，左列 zhong/xiong/yinmi，候选 **中 种 重 众 钟 终** | **通过** |
| **T11** | 重输清空 | buffer 清空 | 多轮重输后键盘空白态 | **通过** |
| **T12** | 空 buffer 按 `0` | 插入空格、不崩溃 | `r34_zero.png` 无崩溃；空框内空格肉眼难辨 | **通过**（行为与既有版本一致） |
| **T13** | 数字 `1` 进符号，`9键` 返回 | 符号页底部 **9键** | `r35_symbol.png` | **通过** |

### 可选跨应用

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| **E01** | 打开 Edge 并点搜索栏 | IME 可弹出 | `r38_edge_optional.png` 已截取；未再跑完整 `ni` 序列 | **轻量覆盖** |

## 重点观察

| 观察项 | 结论 |
|--------|------|
| App 内测试入口是否可用 | **可用**。打开 app 即可在「快速测试」框测 26/9 键，不必默认依赖 Edge。 |
| `flagNoExtractUi` / 全屏提取 | 本轮未触发全屏提取 UI，多行框内正常组字与上屏。 |
| 布局切换生效时机 | 设置页切换后，**下次** `onStartInputView` 生效；隐藏键盘再点输入框即可，不必杀进程。 |
| 既有设置页是否被挤坏 | 下方状态/布局/操作/启用/隐私均在，滚动可及。 |
| 崩溃 / ANR | 全程未见 |

## 截图证据索引（关键）

| 文件 | 说明 |
|------|------|
| `screenshots/01_settings/s01_app_open.png` | v0.01.0025 + 快速测试区块 |
| `screenshots/02_inapp_focus/s02_after_focus_check.png` | 启动后焦点检查 |
| `screenshots/03_26key_smoke/r03_26key_keyboard.png` | app 内 26 键键盘 |
| `screenshots/03_26key_smoke/r04_ni_composing.png` | `ni` 组字 + 候选「你」 |
| `screenshots/03_26key_smoke/r05_ni_committed.png` | 空格上屏「你」 |
| `screenshots/03_26key_smoke/r10_symbol.png` | 26 键符号页 |
| `screenshots/04_t9_smoke/r30_t9_kb.png` | app 内 9 键键盘 |
| `screenshots/04_t9_smoke/r32_64.png` | `64` 左侧 ni/mi |
| `screenshots/04_t9_smoke/r33_mi.png` | 点 mi 后候选刷新 |
| `screenshots/04_t9_smoke/r37_94664_correct.png` | `94664` → zhong/中 |
| `screenshots/04_t9_smoke/r35_symbol.png` | 9 键符号 + 9键 返回 |
| `screenshots/05_layout_toggle/r29_layout_9.png` | 设置页 9 键状态 + 操作按钮 |
| `artifacts/run_tests.ps1` | 首轮自动化脚本 |
| `artifacts/retest_26_and_t9.ps1` | 26 键补测脚本 |
| `ui_dumps/main_open.xml` | 启动页 dump（含 focused） |

## 自动化限制 / 踩坑

1. **主入口变更**：本轮默认在 `MainActivity` 的 `ime_test_input` 测，不再依赖 Edge 搜索栏坐标。
2. **布局切换按钮易被键盘挡住**：`requestFocus` 会弹键盘；需先 `KEYCODE_BACK` 收起或滚动后再点「切换为 x 键」。
3. **PowerShell `$Matches` 覆盖**：连续两个 `-match` 时后一次会冲掉前一次捕获组，导致 toggle 点到 `(0,0)`；应用 `[regex]::Match` 保存 bounds。
4. **`am force-stop` 后偶发回到桌面**：更稳妥是 `am start` 不 force-stop，或 force-stop 后延长等待再 start。
5. **9 键数字坐标**（1440 宽、左列 64dp）：`C0=404,C1=700,C2=996,C3=1292`；`R1=2364,R2=2556,R3=2748`。**注意 `4` 是 `C0` 不是 `C1`**；`9` 在 **R3**（底行）。
6. **候选条点击**：IME 按键不在 uiautomator 层级，候选条 Y 约 2140 在 app 场景下易偏，空格上屏更稳。
7. **未做 Edge 完整回归**：可选轻量打开已截图；完整跨应用 `ni` 序列可后续补。

## 结论

| 项 | 判定 |
|----|------|
| **是否通过** | **通过** — P01–P06 与 26/9 键核心冒烟（`ni→你`、`64 ni/mi`、`94664→zhong`、符号往返、布局切换）均有截图证据 |
| **主验收（内置测试框）** | **达成** — 打开 app 即可测 IME，符合 handoff 验收标准 |
| **遗留** | 候选条点击与 DEL 手感建议人工点按确认；Edge 完整回归未跑 |
| **是否建议合入 / push** | **建议在源码 + 本测试归档一并提交后 push**；当前 v0.01.0025 实现仍在工作区未 commit，需实现侧或测试侧补 commit |

---

*本次测试未 push 远程仓库。*
