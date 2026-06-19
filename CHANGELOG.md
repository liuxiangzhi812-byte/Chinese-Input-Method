# Changelog

All notable changes to ChinesePinyinIME are recorded here.

Version format: `vMAJOR.MINOR.DEBUG` (e.g. `v0.01.0008`).

## v0.01.0011 — 2026-06-20

执行者: Codex

### 修改

- 优化大词库加载：
  - `InputMethodService.onCreateInputView()` 不再同步解析 `assets/pinyin_dict.txt`。
  - 新增 `PinyinDictionary`，键盘先使用内置小词库立即可用，再在后台线程加载大词库；加载完成后刷新候选栏。
- 优化候选栏宽度监听：
  - 候选栏 `OnGlobalLayoutListener` 现在会在键盘视图 detach 或服务销毁时移除，降低旧视图引用和重复监听风险。
- 拆分主服务职责：
  - 新增 `CandidatePager` 负责候选词按实际宽度分页。
  - `ChinesePinyinInputMethodService` 主要保留输入交互、键盘状态和 UI 绑定。
- 更新启动页版本号为 `v0.01.0011`。
- 明确设计：符号模式下不直接提供 `ZH`/`EN` 切换；需要先点 `ABC` 返回字母键盘再切换语言模式。
- 优化本地构建配置：
  - 移除 Foojay 自动工具链解析插件和生成的 Gradle Daemon JVM 下载配置，避免在已有本地 JDK 21 的机器上被迫联网下载工具链。
  - 将 `gradle/gradle-daemon-jvm.properties` 加入忽略规则，避免 Android Studio 重新生成后误提交。

### 修改文件

- `.gitignore`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidatePager.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/PinyinDictionary.java`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `ChinesePinyinIME/settings.gradle.kts`
- `ChinesePinyinIME/gradle/gradle-daemon-jvm.properties`（删除）
- `CHANGELOG.md`
- `PROJECT_HANDOFF.md`

### 验证

- `git diff --check` 通过。
- 已尝试使用本地 JDK 21 + 工作区 Gradle 缓存运行 `assembleDebug`；构建推进到 Android SDK 解析阶段后，当前 Codex 沙盒无法读取 `C:\Users\76556\AppData\Local\Android\Sdk\platforms\android-36.1\package.xml`，因此未能完成本机编译验证。
- 待真机测试：重点观察键盘首次弹出速度、输入 `ni`/`yi` 候选刷新、候选分页、123/ABC、DEL 长按。

---

## v0.01.0010 — 2026-06-19

执行者: Grok (Cursor Agent)

### 新增

- 123 / ABC 符号数字键盘切换：
  - 底栏 `123` 键进入符号/数字键盘，按钮文案变为 `ABC`；点击 `ABC` 返回 QWERTY 字母键盘。
  - 新增 `symbol_keyboard_section`（数字 0-9 + 常用英文符号 3 行），与 `letter_keyboard_section` 通过可见性切换。
  - 符号键直接提交到输入框，不经过拼音缓冲；切换布局时清空组字状态。
  - **ZH 模式**下符号/数字输出为**全角**（如 `１`、`＠`、`（`）；**EN 模式**下输出为**半角**（如 `1`、`@`、`(`）。符号区键面固定显示半角，实际宽度由 `chineseMode` 决定。
  - 符号模式下保留 DEL（含长按连删）、space、enter 行为；切换 123/ABC 时保留当前语言模式。符号模式下不直接提供 ZH/EN 切换，需要先点 `ABC` 返回字母键盘再切换。

### 修改

- DEL 键绑定改为按 `action:delete` 标签识别（不再仅依赖 `R.id.key_delete`），使符号键盘区的 DEL 也支持长按连删。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `PROJECT_HANDOFF.md`
- `tests/`（真机测试归档，见 `tests/v0.01.0010_2026-06-19_235900/REPORT.md`）

### 验证

- 使用项目 Gradle Wrapper + JDK 21 运行 `assembleDebug`，编译/打包成功。
- 真机（OnePlus 7 Pro，`7fbf2094`）：`adb install -r` 安装成功；启动页版本 `v0.01.0010` 正确；IME 已启用并可弹出。
- 真机 ADB 半自动验证（详见 `tests/v0.01.0010_2026-06-19_235900/REPORT.md`）：
  - **通过**：123/ABC 符号键盘切换（截图 `f04_123.png`）。
  - **基本通过**：ZH 全角 / EN 半角符号输出（截图 `f05_fw1.png`、`f06_hw1.png`）。
  - **待人工**：拼音 `ni` + 空格提交候选（ADB 坐标点击不稳定）；DEL 长按、候选分页本次未专门复测。

---

## v0.01.0009 — 2026-06-19

执行者: Claude Code (Sonnet 4.6)

### 修改

- 默认输入模式改为中文：`chineseMode` 初始值由 `false` 改为 `true`，键盘首次弹出即为 `ZH` 模式（之前默认是英文）。同步更新 `keyboard_view.xml` 中状态栏与模式键的初始展示文案，避免渲染前出现一闪而过的 "English"/"EN"。
- DEL 键支持长按连续删除：短按仍是删除一次；按住超过 500ms 后开始以 80ms 间隔连续删除，直到松手为止。通过 `OnTouchListener` + `Handler.postDelayed` 实现，松手或 `ACTION_CANCEL` 时停止重复并清理回调，避免内存泄漏；`onFinishInput` 中也会主动停止，防止切换输入框后残留的删除循环。
- 候选词框改为按文字宽度自适应布局，不再是固定的 5 等分格子：
  - 候选词不再用预先放置的 5 个等宽 `TextView`，改为运行时向 `candidate_list_container`（`LinearLayout`，`layout_width=0dp` + `layout_weight=1`）动态添加/移除 `TextView`，每个候选词宽度按文字内容自适应（`wrap_content`）。
  - 每页能放多少个候选词由该容器的实际可用像素宽度决定：通过 `ViewTreeObserver.OnGlobalLayoutListener` 监听容器宽度变化，结合 `measure()` 逐个测量候选词所需宽度，贪心填充直到放不下为止，从而让长词（3 字及以上）也能完整显示而不被裁切。
  - 分页索引改为基于"页起始下标列表"（`candidatePageStarts`），不再假设每页固定 5 个，翻页按钮（`‹` `›`）仅在还有上一页/下一页时显示。
  - 分页按钮改为固定宽度（28dp），不再参与候选词的弹性分配。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/themes.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`

### 验证

- 使用项目自带 Gradle Wrapper 的 JDK 21 运行 `compileDebugJavaWithJavac` 与 `assembleDebug`，均编译/打包成功。
- 未在真机 / 模拟器上进行人工功能测试，建议重点验证：默认中文模式、DEL 长按连续删除手感（间隔是否合适）、长候选词（3 字及以上）显示是否完整、候选词换页是否流畅。

---

## v0.01.0008 — 2026-06-19

执行者: Claude Code (Sonnet 4.6)

### 新增

- 候选词分页功能：
  - 候选栏左右两侧新增 `‹` / `›` 分页按钮（仅在存在上一页/下一页时显示）。
  - 新增 `candidatePageIndex` 状态，每页展示 5 个候选词（`CANDIDATES_PER_PAGE = 5`）。
  - 拼音内容发生变化（输入字母、删除、提交、清空、切换中英模式、`onStartInput`/`onFinishInput`）时自动重置到第 1 页。
  - 空格键提交候选词时，提交当前页的第一个候选词（而非始终是全部候选词列表中的第一个）。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/themes.xml`

### 验证

- 使用项目自带 Gradle Wrapper 的 JDK 21 运行 `compileDebugJavaWithJavac`，编译通过，无错误。
- 未在真机 / 模拟器上进行人工功能测试，建议按 `PROJECT_HANDOFF.md` 的 Testing Checklist 进行验证。

---

## v0.01.0007 及更早

历史版本未建立此变更日志，相关信息参见 `PROJECT_HANDOFF.md` 中的 "Current Completed Features" 与 "Current Work Node" 章节。
