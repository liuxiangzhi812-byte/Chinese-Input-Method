# 测试报告 — v0.01.0010 补测

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0010` |
| 测试时间 | 2026-06-20 00:14 – 00:20（本地） |
| 会话目录 | `tests/v0.01.0010_2026-06-20_001406/` |
| 执行者 | Codex |
| 测试类型 | 真机安装 + ADB 坐标半自动验证 |
| 被测功能 | v0.01.0010 符号键盘补测；同时补测上次待人工项：`ni` 空格提交、候选分页、DEL 长按 |

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| Android | `12` |
| 分辨率 | `1440x3120` |
| 当前默认 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService` |

## 环境与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| `adb devices -l` | 通过 | 设备在线，状态 `device` |
| `adb install -r app-debug.apk` | 通过 | `Performing Streamed Install` → `Success` |
| 启动页版本号 | 通过 | 已导出 `ui_dumps/ime_main.xml` 与截图 `ime_main.png` |
| 默认输入法 | 通过 | `settings get secure default_input_method` 返回 ChinesePinyinIME |
| 短信输入框聚焦 | 通过 | `mInputShown=true`，键盘弹出 |

说明：本次使用已有 `ChinesePinyinIME/app/build/outputs/apk/debug/app-debug.apk` 安装测试，未重新执行 Gradle 构建。

## 测试范围

本次重点补测 `CHANGELOG.md` v0.01.0010 与上次报告的待测项目：

1. `123` / `ABC` 符号键盘切换
2. ZH 模式全角符号 / 数字输出
3. EN 模式半角符号 / 数字输出
4. `ni` 候选显示与空格提交首候选
5. 候选分页
6. DEL 长按连续删除

## 用例与结果

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T01 | 安装 APK 并启动 App | 可安装，版本为 `v0.01.0010` | 安装成功，启动页截图已保存 | 通过 |
| T02 | 聚焦短信输入框 | ChinesePinyinIME 弹出 | `mInputShown=true`，键盘弹出 | 通过 |
| T03 | 长按 DEL 约 1.8 秒 | 连续删除输入框残留文字 | 输入框从较长残留内容减少到单个 `@` | 通过 |
| T04 | 中文模式输入 `ni` | 顶部显示 `中文 ni`，候选显示 `你 / 泥 / 尼...` | 截图显示 `中文 ni`，候选含 `你、泥、尼、拟、逆、暱`，且出现 `›` | 通过 |
| T05 | `ni` 后按空格 | 上屏当前页首候选 `你`，候选栏清空 | 输入框显示 `你@`，候选栏清空 | 通过 |
| T06 | 输入 `yi` 并点击 `›` | 进入候选第二页，显示 `‹` 和新候选 | 第二页显示 `‹`、`易、依、亿、懿、宜、异`、`›` | 通过 |
| T07 | 点击 `123` | 显示数字 / 符号键盘，底栏按钮变 `ABC` | 截图显示 `1-0`、`@ # $ ...`、标点行和 `ABC` | 通过 |
| T08 | ZH + 符号键盘按 `1` | 输入全角 `１` | 截图显示中文模式；后续 UI dump 文本为 `１1＠`，首字符为全角 `１` | 通过 |
| T09 | 切回字母键盘，切 EN，再进符号键盘按 `1` | 输入半角 `1` | UI dump 文本为 `１1＠`，第二字符为半角 `1`，键盘状态为 `English` | 通过 |

## 证据索引

### 截图

| 文件 | 说明 |
|------|------|
| `screenshots/01_baseline/ime_main.png` | 启动页基线截图 |
| `screenshots/02_input_tests/keyboard_focused.png` | 短信输入框聚焦，ChinesePinyinIME 弹出 |
| `screenshots/02_input_tests/t01_after_long_delete.png` | DEL 长按后，残留文本被连续删除到单个 `@` |
| `screenshots/02_input_tests/t02_ni_candidates.png` | 第一轮坐标偏移，实际输入 `o`，候选显示「哦」等，用作坐标校准记录 |
| `screenshots/02_input_tests/t03_ni_space_commit.png` | 第一轮坐标偏移后的空格提交记录 |
| `screenshots/02_input_tests/t04_ni_candidates_calibrated.png` | 校准后 `ni` 候选显示，含 `你 / 泥 / 尼...` 和 `›` |
| `screenshots/02_input_tests/t05_ni_space_commit_calibrated.png` | `ni` + 空格提交 `你` |
| `screenshots/02_input_tests/t06_yi_candidates_page1.png` | `yi` 候选第一页 |
| `screenshots/02_input_tests/t07_yi_candidates_page2.png` | `yi` 候选第二页，含 `‹` / `›` |
| `screenshots/02_input_tests/t08_symbol_keyboard.png` | `123` 符号键盘布局 |
| `screenshots/02_input_tests/t09_zh_fullwidth_1.png` | ZH 模式符号键盘输入 `1` |
| `screenshots/02_input_tests/t10_en_halfwidth_1.png` | EN 模式符号键盘输入 `1` |

### UI Dump

| 文件 | 说明 |
|------|------|
| `ui_dumps/ime_main.xml` | 启动页 UI 层级 |
| `ui_dumps/sms_keyboard.xml` | 短信界面 + 键盘弹出 UI 层级 |
| `ui_dumps/after_symbol_text.xml` | 符号输出后的 UI 层级；输入框文本为 `１1＠` |

### 命令记录

| 文件 | 说明 |
|------|------|
| `artifacts/adb_commands.txt` | 本次关键 ADB 命令记录 |

## 自动化限制

- IME 内部按键仍不暴露为可直接点击的 uiautomator 节点，因此本次继续使用屏幕坐标测试。
- 第一轮 `ni` 测试坐标偏移，误触成 `o`；随后校准坐标后 `ni` 候选与空格提交均通过。
- 数字 `1` 的全角 / 半角在截图中肉眼不易区分，本次使用 `uiautomator dump` 的输入框文本 `１1＠` 作为主要证据。

## 总体结论

| 功能 | 结论 |
|------|------|
| APK 安装 | 通过 |
| IME 弹出 | 通过 |
| 默认中文模式 | 通过 |
| `ni` 候选显示 | 通过 |
| 空格提交首候选 | 通过 |
| 候选分页 | 通过 |
| DEL 长按连删 | 通过 |
| `123` / `ABC` 符号键盘 | 通过 |
| ZH 全角输出 | 通过 |
| EN 半角输出 | 通过 |

本次补测把上一轮 `v0.01.0010` 报告中的主要待人工项都覆盖了。建议下一步进入 `PROJECT_HANDOFF.md` 推荐的后续功能：中文标点专用输入。
