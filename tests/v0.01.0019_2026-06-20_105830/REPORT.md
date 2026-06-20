# 测试报告 — v0.01.0019

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0019` |
| 测试时间 | 2026-06-20 10:58 – 11:09（本地） |
| 会话目录 | `tests/v0.01.0019_2026-06-20_105830/` |
| 执行者 | Claude Code (Sonnet 4.6) |
| 测试类型 | 本机编译 + 真机安装 + ADB 半自动验证 |
| 被测功能 | Shift 图标化（`⇧`/`▲`）、ZH/EN 切换键移至底栏、以及此前累积的全部核心功能回归 |
| 代码状态 | 测试时改动**尚未提交到 git**（工作区改动，对应 CHANGELOG 里的 v0.01.0019 条目）；已提交的最新版本是 v0.01.0018（commit `9e669a8`） |

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| 分辨率 | 1440 × 3120 |
| IME 窗口 frame | `[0,2116][1440,3120]`（`dumpsys window` 实测，用于推算按键坐标） |
| 当前 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService`（测试前已是默认输入法） |

## 构建与安装

| 步骤 | 结果 | 说明 |
|------|------|------|
| `compileDebugJavaWithJavac` | 通过 | 本机 JDK 21（含 `jlink.exe`，`.gradle-user-home` 下的 Eclipse Adoptium 21），`--offline` |
| `assembleDebug` | 通过 | `BUILD SUCCESSFUL`，33 个 task |
| `adb install -r app-debug.apk` | 通过 | `Performing Streamed Install` → `Success` |
| 启动页版本号 | 通过 | MainActivity 截图显示 `v0.01.0019` |
| IME 已启用且为默认 | 通过 | `settings get secure default_input_method` 返回本应用 service |

补充说明：`ENVIRONMENT_SETUP.md` 中记录的"命令行编译因缺 `jlink.exe` 被阻塞"问题在本次会话中**未出现**——本机 `.gradle-user-home` 下已有完整 JDK 21（含 `jlink.exe`），编译与打包均一次性顺利完成。

## 测试范围

对照 `CHANGELOG.md` v0.01.0019 条目 + `PROJECT_HANDOFF.md` Testing Checklist 全量回归：

1. Shift 键图标化（`⇧` 待激活 / `▲` 激活+高亮）、字母键面大小写联动
2. `ZH/EN` 切换键移至底栏（`123 | ZH/EN | space | enter`）
3. 候选词组字、排序、分页（含 `‹`/`›`）、空格提交当前页首位
4. `DEL` 长按连续删除（字母键盘 + 符号键盘）
5. `123`/`ABC` 符号键盘切换，`EN` 三行 ASCII、`ZH` 数字+中文标点
6. 中文标点直接提交、组字中按标点先清空缓冲
7. **额外发现**：底栏重排后，符号键盘内能否直接切换 `ZH`/`EN`（此前版本文档记载为"不支持，需先回到 ABC"）

## 用例与结果

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T01 | 安装后打开 MainActivity | 显示 `v0.01.0019` | 截图确认正确 | 通过 |
| T02 | 短信新建，聚焦短信输入框 | 弹出 ChinesePinyinIME，默认 `中文` | 状态栏显示「中文」，底栏为 `123\|ZH\|space\|enter` | 通过 |
| T03 | 输入 `ni` | 组字显示 `中文 ni`，候选栏出现你/泥/尼/拟/逆等 + `›` | 截图 `t04_ni_composing.png` 一致，排序「你」在第一位 | 通过 |
| T04 | 空格提交候选 | 提交当前页第一候选「你」 | 短信框出现「你」，截图 `t05_ni_space_commit.png` | 通过 |
| T05 | 输入 `yi`，候选翻页 | 显示分页，点 `›` 进入下一页并出现 `‹` | `t06_yi_page1.png` → `t07_yi_page2.png`，两个方向箭头都按预期出现/消失 | 通过 |
| T06 | 点击底栏 `ZH/EN` | 切换到 English，底栏变为 `123\|EN\|space\|enter` | `t08_en_mode.png` 确认 | 通过 |
| T07 | `EN` 模式点 `⇧` | 图标变实心 `▲`+蓝色高亮背景，26 键全部转大写键面 | `t09_shift_armed.png` 确认字母区全大写、Shift 高亮 | 通过 |
| T08 | Shift 激活后点字母 `h` | 提交大写 `H`，Shift 自动复位为 `⇧`/小写键面 | `t10_shift_committed.png`：短信框出现「H」，键面恢复小写 | 通过 |
| T09 | 输入 `Hello` 后长按 `DEL` ≥1s | 连续删除直至清空 | `t11`→`t12`：5 个字符在约 1.2s 内被清空 | 通过 |
| T10 | `EN` 模式点 `123` | 进入英文符号键盘（数字/符号/标点 3 行），按钮变 `ABC` | `t13_en_symbol.png` 确认三行布局、半角字符 | 通过 |
| T11 | 英文符号键盘点 `1`、`@` | 直接提交半角 `1@` | `t14_en_symbol_commit.png`：短信框「1@」 | 通过 |
| T12 | 回 `ABC`，切回 `ZH`，再点 `123` | 进入中文符号键盘：数字行 + 两行中文标点 + `DEL` | `t15_zh_symbol.png` 确认无英文符号行 | 通过 |
| T13 | **（新发现）** 仍在 `ZH` 符号键盘内点底栏 `ZH/EN` | 此前文档记载"不支持，需先回 ABC" | 实测**直接切换成功**：符号键盘从中文标点布局变为英文 ASCII 布局，且仍停留在符号模式（按钮仍是 `ABC`），未跳回字母键盘 | **行为已改进，需更新文档**（见下方问题与建议） |
| T14 | 切回 `ZH` 符号键盘，回 `ABC`，输入 `ni`（组字中） | 组字显示 `中文 ni` | `t18_ni_composing_before_punc.png` 确认 | 通过 |
| T15 | 组字中点 `123` | 拼音缓冲被清空，状态栏变回 `中文`（无残留拼音） | `t19_symbol_after_composing.png` 确认缓冲已清空 | 通过 |
| T16 | 点中文标点 `，` | 直接提交 U+FF0C 全角逗号（非半角 ASCII `,`） | 用 `uiautomator dump` 校验短信框文本节点，实际字符 `，`，确认为全角 | 通过 |
| T17 | 连续点 `。`、`？`、`！` 后长按符号键盘 `DEL` ≥1s | 连续删除四个标点 | `t21_symbol_del_longpress.png`：短信框清空 | 通过 |

## 截图证据索引

| 文件 | 说明 |
|------|------|
| `screenshots/01_baseline/01_main_activity.png` | 启动页版本号 `v0.01.0019` |
| `screenshots/01_baseline/02_sms_open.png` | 短信新建页，键盘默认中文、底栏新布局 |
| `screenshots/02_input_tests/03_focus_compose.png` | 聚焦短信正文输入框 |
| `screenshots/02_input_tests/t04_ni_composing.png` ~ `t21_symbol_del_longpress.png` | 见上方用例表，按 ID 对应 |

## UI 导出

- 本次未保存独立 `ui_dumps` 文件（IME 自身按键不出现在 `uiautomator` 层级里，和此前版本报告的结论一致）；用于核对全角标点字符的 `uiautomator dump` 临时文件已在测试后清理，未归档。

## 自动化限制（与历史报告一致）

1. IME 自定义按键没有无障碍节点，全部通过**屏幕坐标 `input tap`** 完成，本次坐标基于 `dumpsys window` 实测的 IME frame `[0,2116][1440,3120]` 与 `keyboard_view.xml` 的 dp 布局换算得出（density=4），过程中一次坐标换算错误（候选分页 `›` 按钮的 y 坐标）已现场用像素采样修正。
2. `adb shell input swipe <x> <y> <x> <y> <duration>`（起止点相同）用于模拟长按，验证了 500ms 阈值 + 80ms 间隔的连续删除逻辑。

## 问题与建议

1. **文档需要更新**：`PROJECT_HANDOFF.md` 当前写的是"符号模式下不直接提供 ZH/EN 切换；需要先点 ABC 返回字母键盘再切换"——这是 v0.01.0010 时期的限制。本次实测确认 v0.01.0019 把 `ZH/EN` 按钮移到底栏（`letterKeyboardSection`/`symbolKeyboardSection` 之外）之后，**这个限制已经不存在**：符号键盘内可以直接切换语言，且会正确切换中/英文符号布局。这是一个好的副作用改进，但文档没有同步，下一次更新 handoff 时应该把这条"已知限制"改成"已支持"。
2. 本次会话验证的改动**仍未提交到 git**（CHANGELOG 已写好 v0.01.0019 条目，但代码/布局/资源改动还在工作区）。建议确认测试通过后再 `git add` + `commit`，避免未测试代码长期停留在工作区。
3. 候选词分页按钮的点击坐标比预期偏上（候选栏整体高度比早期估算小），如果以后要写自动化脚本辅助测试，建议直接用 `dumpsys window` 实测 IME frame 再按 dp 换算，不要复用旧版本报告里的经验坐标。
4. 未专门测试：Enter 在多行/`imeOptions` 不同 action 下的行为（短信单行发送场景已间接触发但未单独截图记录）；建议下次测试单独覆盖搜索框、单行 `IME_ACTION_DONE` 场景。

## 总体结论

| 维度 | 结论 |
|------|------|
| 本机编译安装 | 通过（解决了 ENVIRONMENT_SETUP.md 记录的 `jlink.exe` 顾虑——本机环境实际可用） |
| 版本展示 | 通过 |
| 默认中文模式 | 通过 |
| 拼音组字 + 候选排序 + 分页 + 空格提交 | 通过 |
| `DEL` 长按连删（字母键盘 + 符号键盘） | 通过 |
| Shift 图标化 + 单次大写 + 键面联动 | 通过 |
| `123`/`ABC` 符号键盘（EN 三行 / ZH 数字+标点） | 通过 |
| 中文标点直接提交 + 组字中标点清空缓冲 | 通过 |
| 符号模式内 `ZH/EN` 切换 | **通过，且优于文档描述**（建议更新 handoff） |

**建议**：测试通过，可以提交本次改动；提交时顺带更新 `PROJECT_HANDOFF.md` 里关于"符号模式语言切换"的描述。

## 关联变更

- 代码版本：`CHANGELOG.md` → v0.01.0019（提交前）
- 文档待办：`PROJECT_HANDOFF.md` → Known Limitations / Current Completed Features 中"符号模式不支持 ZH/EN 切换"描述已过时，需修正
