# 测试报告 — v0.01.0020 基础设置页

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0020` |
| 测试时间 | 2026-06-20 11:42 – 11:48（本地） |
| 会话目录 | `tests/v0.01.0020_2026-06-20_114254/` |
| 执行者 | Codex |
| 测试类型 | 本机编译 + 真机安装 + ADB 半自动验证 |
| 被测功能 | 基础设置页、词库状态、学习数据状态、清除已学习词频、跳转系统输入法设置 |

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| Android 版本 | 12 |
| 分辨率 | 1440 × 3120 |
| 物理密度 | 600 |
| 覆盖密度 | 640 |
| 当前默认输入法 | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService` |

## 构建与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| `compileDebugJavaWithJavac` | 通过 | 使用项目内完整 JDK 21 与离线 Gradle 缓存 |
| `assembleDebug` | 通过 | `BUILD SUCCESSFUL`，生成 debug APK |
| `adb install -r app-debug.apk` | 通过 | `Performing Streamed Install` → `Success` |
| 启动 MainActivity | 通过 | 设置页成功打开 |

## 用例与结果

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T01 | 启动 App | 显示基础设置页和版本号 `v0.01.0020` | UI dump 显示 `ChinesePinyinIME` 和 `v0.01.0020` | 通过 |
| T02 | 等待词库状态读取完成 | 显示词库已就绪和统计数据 | 显示 `268353` 个拼音条目、`349039` 个候选词 | 通过 |
| T03 | 查看学习数据状态 | 显示本地已学习词频统计 | 初始状态显示 `2` 组候选，累计选择 `2` 次 | 通过 |
| T04 | 点击“清除已学习词频” | 学习数据归零 | 状态变为 `0` 组候选，累计选择 `0` 次 | 通过 |
| T05 | 检查 App 私有目录 | `user_frequency.tsv` 被删除 | `run-as ... ls files` 中没有 `user_frequency.tsv` | 通过 |
| T06 | 点击“打开系统输入法设置” | 跳转 Android 系统输入法设置 | 前台页面为 `com.android.settings/.Settings$AvailableVirtualKeyboardActivity`，列表包含 `ChinesePinyinIME` 且开关为开启 | 通过 |

## 证据索引

| 文件 | 说明 |
|------|------|
| `screenshots/01_settings/01_settings_initial.png` | 设置页初始状态，版本、词库状态、学习数据状态、按钮与隐私说明可见 |
| `screenshots/01_settings/02_after_clear.png` | 点击清除后，学习数据状态归零 |
| `screenshots/01_settings/03_input_settings.png` | 跳转到系统“管理输入法”页面 |
| `ui_dumps/01_settings_initial.xml` | 设置页初始 UI 层级 |
| `ui_dumps/02_after_clear.xml` | 清除学习数据后的 UI 层级 |
| `ui_dumps/03_input_settings.xml` | 系统输入法设置页 UI 层级 |
| `artifacts/adb_commands.txt` | 本次主要 ADB 命令 |

## 备注

- 自定义设置页使用普通 Android View，UIAutomator 可以读取节点文本和按钮边界；本次按钮点击坐标来自 UI dump 中的 bounds。
- 曾尝试用脚本额外写入一份测试学习数据，但手机 shell 引号未正确传递，未作为证据使用。本次清除测试使用设备上已有的真实学习数据：清除前 `2` 组候选、累计 `2` 次，清除后归零并确认文件删除。
- 本次只验证 v0.01.0020 设置页相关功能，没有重新跑 v0.01.0019 的完整键盘回归；键盘完整回归见 `tests/v0.01.0019_2026-06-20_105830/REPORT.md`。

## 总体结论

| 维度 | 结论 |
|------|------|
| 编译与打包 | 通过 |
| 安装与启动 | 通过 |
| 版本展示 | 通过 |
| 词库状态显示 | 通过 |
| 学习数据状态显示 | 通过 |
| 清除已学习词频 | 通过 |
| 打开系统输入法设置 | 通过 |

**结论：v0.01.0020 基础设置页真机测试通过。**
