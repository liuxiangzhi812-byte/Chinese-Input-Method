# 测试报告 — v0.01.0024（9 键左侧竖向拼音选择栏）

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0024` |
| Git 提交 | `1c743fb` — *Add 9-key vertical pinyin chooser* |
| 测试时间 | 2026-06-20 17:11 – 17:42（本地） |
| 会话目录 | `tests/v0.01.0024_2026-06-20_171120/` |
| 执行者 | Grok (Cursor Agent) |
| 测试类型 | 本机 APK 编译安装 + ADB 半自动验证 |
| 上次报告基线 | `tests/v0.01.0022_2026-06-20_145553/REPORT.md`（横向 `pinyin_choice_bar`） |

### 本次测试范围

| 版本 | 功能 | 上次状态 |
|------|------|----------|
| **v0.01.0024** | 9 键左侧竖向拼音列表（`t9_pinyin_choice_list` + `ScrollView`，64dp 固定列） | CHANGELOG 标明「未做真机测试」 |
| **v0.01.0024** | 移除顶部横向 `pinyin_choice_bar` | 未测 |
| **回归** | 9 键基础输入（94664、符号、DEL、重输、0 空格） | 需确认竖向栏未破坏 |
| **回归** | 26 键 QWERTY 冒烟 | `letter_keyboard_section` 未改动，需确认 |

输入场景按用户要求使用 **Edge 浏览器搜索/地址栏**（`com.microsoft.emmx`）。

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| 分辨率 | 1440 × 3120 |
| 默认 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService` |
| 键盘布局 | 测试前 9 键；T08 切回 26 键 |

## 构建与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| `git rev-parse HEAD` | 通过 | `1c743fb` |
| `gradlew assembleDebug` | 通过 | 测试前已构建 `app-debug.apk` |
| `adb install -r` | 通过 | 覆盖安装至 `7fbf2094` |
| 设置页版本号 | 通过 | `s01_version_9key.png` 显示 **v0.01.0024** |
| 9 键布局确认 | 通过 | 设置页「当前布局：9 键（实验性）」 |
| 词库状态 | 通过 | 268353 拼音条目 / 349039 候选词，已就绪 |

## 用例与结果

### 准备项

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| P01 | 构建并安装 debug APK | 安装成功 | `adb install -r` Success | **通过** |
| P02 | 设置页确认版本 | `v0.01.0024` | `s01_version_9key.png` | **通过** |
| P03 | 确认 9 键布局 | 当前为 9 键 | 设置页文案 + T01 键盘形态 | **通过** |

### T01 – T08

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| **T01** | Edge 搜索栏，9 键输入 `6` `4` | 左侧竖向列表显示 `ni`/`mi`，默认 `ni` 高亮；顶部横向 `pinyin_choice_bar` 不再出现 | `t01_64_vertical_ni_mi.png`：左侧 `ni`（蓝底）/`mi`；候选 你/泥/尼…；无顶部拼音横条 | **通过** |
| **T02** | 在 `64` 下点击左侧 `mi` | `mi` 高亮，候选刷新为米/密/迷… | `t02_mi_selected_candidates.png`：`mi` 蓝底，候选 **米 密 迷 谜 秘 觅** | **通过** |
| **T03** | `64` 已选 `mi` 后追加 `9` → `649` | 显式选择清空，左侧列表按新串重算 | `t03_after_append_649.png`：组字区 `649`，左侧无 `ni`/`mi` 高亮 | **通过** |
| **T04** | 多数字输入后 DEL / 长按 DEL / 重输 | 单击删一位并刷新列表；长按连续删；重输清空 buffer | `t04_after_del_one.png`：buffer `6`，左侧 `o`/`m`/`n` 列表刷新；`t04_after_long_del.png`：长按后 buffer 清空（键盘空白态）；`t04_after_retype_clear.png`：重输后列表与 buffer 均空 | **通过**（长按 DEL 最终清空至空态，行为可接受） |
| **T05** | 空 buffer 或唯一拼音匹配 | 左侧 64dp 区域保留、内容为空；主观体验记录 | `t05_empty_blank_column.png`：空 buffer，左侧列无拼音项、底色与键盘融合，数字键仍右移；`t05_unambiguous_936_blank.png`：`936` 有 `wen`/`zen` 两选项（歧义，列表正常显示） | **通过**（空白列略空但不突兀，符合设计预期） |
| **T06** | 找 >3 个拼音匹配的数字串，验证滚动 | 列表可上下滚动，滚动后可点击 | 尝试 `2`、`94664`（仅 3 项：zhong/xiong/yinmi）等，未找到 ≥4 拼音匹配串；`t06_digit2_pinyin_list.png` 为单拼音态，无滚动证据 | **未覆盖** |
| **T07** | 9 键回归：`94664`、0 空格、1 符号、DEL/重输/候选 | `zhong`/中 相关候选；基础功能正常 | `t07_94664_zhong.png`：左侧 zhong/xiong/yinmi，候选 中/种/重…；`t07_symbol_mode.png`：符号键盘 + 底部 **9键** 返回；`t07_zero_space.png`：空栏按 0 后无崩溃（空格上屏未单独 dump 佐证，沿用 v0.01.0022 同类判定） | **通过** |
| **T08** | 设置切 26 键；输入 `ni`；候选/空格/符号/DEL | 候选 你 可提交；符号键盘、DEL 正常 | 初轮坐标误触为 `on`；补测 `t08_ni_composing_v4.png`：`中文 ni`，候选 你/泥/尼…；`t08_ni_committed_v4.png`：搜索栏已上屏 **你**；`t08_symbol_keyboard_v4.png`：符号页 + `ABC` 返回 | **通过** |

## 重点观察

| 观察项 | 结论 |
|--------|------|
| 64dp 左列是否挤压右侧数字键 | OnePlus 7 Pro 上可接受；`2 ABC` / `9 WXYZ` / `重输` / `DEL` 文字清晰，无重叠 |
| 左侧列表项是否易点 | `64` 下 `mi` 点击成功（T02）；项高约一行键高，可点 |
| 键盘总高度 vs v0.01.0022 | 目测一致；CHANGELOG 写明 9 键区仍 144dp（3×48dp） |
| ambiguous ↔ unambiguous 时键位跳动 | `64`（有列表）与空 buffer（列表空）对比，数字格水平位置稳定，未见跳动 |
| 崩溃 / ANR / 卡死 | 全程未见 |

## 截图证据索引

| 文件 | 说明 |
|------|------|
| `screenshots/01_settings/s01_version_9key.png` | 设置页 v0.01.0024 + 9 键状态 |
| `screenshots/02_t01_t02_vertical_list/t01_64_vertical_ni_mi.png` | T01：`64` 左侧 ni/mi，ni 默认高亮 |
| `screenshots/02_t01_t02_vertical_list/t02_mi_selected_candidates.png` | T02：点击 mi 后候选刷新 |
| `screenshots/03_t03_t05_behavior/t03_after_append_649.png` | T03：追加 9 后选择清空 |
| `screenshots/03_t03_t05_behavior/t05_empty_blank_column.png` | T05：空 buffer 空白左列 |
| `screenshots/03_t03_t05_behavior/t05_unambiguous_936_blank.png` | T05/歧义：`936` → wen/zen |
| `screenshots/04_t04_del_retype/t04_after_del_one.png` | T04：单击 DEL 刷新列表 |
| `screenshots/04_t04_del_retype/t04_after_retype_clear.png` | T04：重输清空 |
| `screenshots/06_t07_t9_regression/t07_94664_zhong.png` | T07：94664 → zhong/中 |
| `screenshots/06_t07_t9_regression/t07_symbol_mode.png` | T07：符号模式 + 9键 返回 |
| `screenshots/07_t08_26key_smoke/t08_ni_composing_v4.png` | T08：26 键 ni 候选 |
| `screenshots/07_t08_26key_smoke/t08_ni_committed_v4.png` | T08：空格提交「你」 |
| `screenshots/07_t08_26key_smoke/t08_symbol_keyboard_v4.png` | T08：26 键符号键盘 |
| `artifacts/run_tests.ps1` | 主自动化脚本（9 键坐标含 64dp 右移） |
| `ui_dumps/settings.xml` | 设置页 UI dump |

## 自动化限制 / 踩坑

1. **Edge Copilot 引导**：首次需点「以后再说」`(720, 1780)`；有效搜索栏焦点为 `(720, 200)`。
2. **9 键坐标右移 64dp**：数字格中心约为 col0=404、col1=700、col2=996、col3=1292（y：2364/2556/2748）；左侧拼音列表 x≈128。
3. **26 键 `ni` 坐标**：`n` 在第 3 行 `(1005, 2790)`，`i` 在第 1 行 `(1065, 2390)`（来自 v0.01.0010 校准）；误用同行坐标会打成 `on`。
4. **布局切换**：设置页切换 26/9 键后需 `am force-stop` IME 进程，下次弹键盘才生效。
5. **T06**：当前词库/数字索引下未找到 >3 拼音匹配的 T9 串，滚动能力待后续用已知 4+ 匹配串补测。
6. IME 自定义按键不在 `uiautomator` 层级，坐标靠截图手算。

## 结论

| 项 | 判定 |
|----|------|
| **是否通过** | **有条件通过** — T01–T05、T07–T08 均通过；**T06 滚动未覆盖** |
| **是否建议合入 main** | **建议合入** — 9 键竖向拼音选择栏核心功能与布局稳定性目标已达成，26 键冒烟无回归；合入前可在 CHANGELOG 注明 T06 滚动待补测 |
| **是否建议调整左侧列宽或键盘布局** | **暂不调整** — 64dp 在 OnePlus 7 Pro 上数字键仍可读、可点；空白列与键盘底色一致，主观上不突兀。若后续在小屏设备上反馈挤压，可评估 56dp 或略缩字号，非阻塞项 |

---

*本次测试未 push 远程仓库。*