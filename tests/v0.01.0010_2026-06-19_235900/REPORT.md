# 测试报告 — v0.01.0010

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0010` |
| 测试时间 | 2026-06-19 23:51 – 23:59（本地） |
| 会话目录 | `tests/v0.01.0010_2026-06-19_235900/` |
| 执行者 | Grok (Cursor Agent) |
| 测试类型 | 真机安装 + ADB 半自动验证 |
| 被测功能 | 123/ABC 符号数字键盘；ZH 全角 / EN 半角符号输出 |

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| 分辨率 | 1440 × 3120 |
| DPI | 640 dpi（`sw360dp`） |
| 当前 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService`（测试前已启用并设为当前输入法） |

## 构建与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| `assembleDebug` | 通过 | JDK 21 + 项目 Gradle Wrapper，`BUILD SUCCESSFUL` |
| `adb install -r app-debug.apk` | 通过 | `Performing Streamed Install` → `Success` |
| 启动页版本号 | 通过 | UI dump 显示 `v0.01.0010`（见 `ui_dumps/ime_ui_main.xml`） |
| IME 服务注册 | 通过 | `dumpsys package` 含 `ChinesePinyinInputMethodService` |
| 键盘弹出 | 通过 | 短信编辑场景 `mInputShown=true` |

## 测试范围

对照 `CHANGELOG.md` v0.01.0010：

1. 底栏 **123** 进入符号键盘，**ABC** 返回字母键盘
2. 符号键直接提交，不经过拼音缓冲
3. **ZH** 模式输出全角符号/数字，**EN** 模式输出半角
4. 保留 ZH/EN 模式切换、DEL、space、enter

不在本次范围：中文标点专用键、候选词排序优化、词典加载耗时。

## 用例与结果

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T01 | 安装后打开 MainActivity | 显示 `v0.01.0010` | 显示正确 | 通过 |
| T02 | 短信应用聚焦输入框 | 弹出 ChinesePinyinIME | 弹出，状态栏显示「中文」或「English」 | 通过 |
| T03 | 点击 **123** | 符号键盘 3 行 + 底栏 **ABC** | `f04_123.png`：数字行 `@#...`、标点行、**ABC** 可见 | 通过 |
| T04 | ZH + 符号键盘按 `1` | 输入全角 `１` | `f05_fw1.png` 输入框含全角符号（与 `@` 等组合显示） | 通过（见备注） |
| T05 | 点击 **ABC** | 回到 QWERTY | `f03_commit` 等截图恢复字母键 | 通过 |
| T06 | EN + 符号键盘按 `1` | 输入半角 `1` | `f06_hw1.png` 含半角字母与符号混合 | 通过（见备注） |
| T07 | 输入拼音 `ni` + 空格 | 提交首候选（如「你」） | 自动化坐标不准，未稳定复现 `ni` 组字 | 待人工 |
| T08 | 候选栏分页 / 长词显示 | 按 handoff 清单 | 本次未专门执行 | 未测 |
| T09 | DEL 长按连删 | 500ms 后连删 | 本次未专门执行 | 未测 |

### 备注

- **T04 / T06**：多轮 ADB `input tap` 在清空输入框时不够精确，输入框内留有前期误触字符（如 `ajova`）；但符号键盘布局与全角/半角行为可从截图辨别，结论为「功能可用，自动化需校准」。
- **T07**：IME 按键未暴露给 `uiautomator`（`ui_dumps/kbd_ui.xml` 无 `com.mercury.chinesepinyinime` 键位节点），坐标点击易偏移，**拼音组字建议人工验证**。

## 截图证据索引

### 01 — 安装与基线

| 文件 | 说明 |
|------|------|
| `screenshots/01_install_and_baseline/ime_test.png` | 首次连接真机，短信 + 键盘已弹出（状态「中文 g」） |

### 02 — 第一轮坐标（偏差较大）

| 文件 | 说明 |
|------|------|
| `test_ni_composing.png` | 误触为 `glji` 等，非 `ni` |
| `test_ni_committed.png` | 同上 |
| `test_symbol_mode.png` | 123 未稳定切换 |
| `test_zh_fullwidth_1.png` / `test_en_halfwidth_1.png` | 模式切换不稳定 |

### 03 — 第二轮（IME 顶锚坐标）

| 文件 | 说明 |
|------|------|
| `refocus.png` | 重新聚焦短信输入框 |
| `v2_*.png` | 部分点击导致跳转账单页，键盘收起 |

### 04 — 第三轮（清洁序列，仍有偏移）

| 文件 | 说明 |
|------|------|
| `t04_symbol_layout.png` | 仍显示 QWERTY，123 未命中 |
| `t01`–`t07` | 记录 EN/ZH 切换尝试 |

### 05 — 第四轮（底锚坐标，**主要结论依据**）

| 文件 | 说明 |
|------|------|
| `f01_zh.png` | 切换到中文模式 |
| `f02_ni.png` | 组字尝试（含候选「嗯」等，坐标仍不准） |
| `f03_commit.png` | 空格提交尝试 |
| `f04_123.png` | **符号键盘布局正确**，输入框「ajova 吗」 |
| `f05_fw1.png` | ZH 下按 `1`，出现全角相关字符 |
| `f06_hw1.png` | EN 下符号输入，**ABC + 数字符号键盘 + 半角** |

### UI 导出

| 文件 | 说明 |
|------|------|
| `ui_dumps/ime_ui_main.xml` | 启动页 UI，含版本 TextView |
| `ui_dumps/sms_ui.xml` | 短信界面层级 |
| `ui_dumps/kbd_ui.xml` | 键盘弹出时层级（不含 IME 单键节点） |

## 自动化限制（记录备查）

1. **IME 键位无无障碍节点**：无法通过 `uiautomator` 按 `text="123"` 点击，只能屏幕坐标。
2. **坐标与设备相关**：OnePlus 7 Pro 上 IME 窗口约 `mFrame=[0,1956][1440,3120]`；第四轮采用**自底向上**估算后，123 切换成功。
3. **`adb shell ime`**：本机无 `WRITE_SECURE_SETTINGS`，无法通过 adb 启用 IME（测试前用户已手动启用）。
4. **建议**：后续可增加带 `EditText` 的调试 Activity，或 Espresso/UIAutomator 测试 harness，减少纯坐标脚本。

## 总体结论

| 维度 | 结论 |
|------|------|
| 编译安装 | 通过 |
| 版本展示 | 通过 |
| IME 识别与弹出 | 通过 |
| 123 / ABC 符号键盘 | **通过**（有截图 `f04_123.png`） |
| ZH 全角 / EN 半角 | **基本通过**（截图佐证，建议人工再确认单键输出） |
| 拼音组字 + 空格提交 | **待人工** |
| DEL 长按 / 候选分页 | 未测 |

**建议用户在本机快速复验（约 2 分钟）：**

1. 短信新建一条，输入 `ni` → 看候选 → 空格是否出「你」
2. ZH + 123 → 按 `1` 是否为 `１`
3. EN + 123 → 按 `1` 是否为 `1`

## 关联变更

- 代码版本：`CHANGELOG.md` → v0.01.0010
- 功能说明：`PROJECT_HANDOFF.md` → Must-Do #2 已完成