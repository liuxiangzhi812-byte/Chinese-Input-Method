# Changelog

All notable changes to ChinesePinyinIME are recorded here.

Version format: `vMAJOR.MINOR.DEBUG` (e.g. `v0.01.0008`).

## v0.01.0020 — 2026-06-20

执行者: Codex

### 新增

- **基础设置页**：
  - 启动页从单纯名称/版本展示改为可滚动设置页。
  - 显示当前版本号。
  - 后台读取 `assets/pinyin_dict.txt`，显示拼音条目数和候选词总数。
  - 显示本地已学习词频状态：候选组数和累计选择次数。
  - 新增“清除已学习词频”按钮，调用 `UserFrequencyStore.clear()` 清空内存记录并删除应用私有目录中的 `user_frequency.tsv`。
  - 新增“打开系统输入法设置”按钮，跳转 Android 系统输入法设置页。
  - 增加本地隐私说明：当前不联网、不同步、不做云端预测。

### 修改

- 修正 `PROJECT_HANDOFF.md` 中当前版本为 `v0.01.0019` 时 debug version 仍写成 `0018` 的错误，并推进到 `v0.01.0020`。
- 更新启动页版本号为 `v0.01.0020`。
- 更新 `ENVIRONMENT_SETUP.md` 中的当前版本示例。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/MainActivity.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/UserFrequencyStore.java`
- `ChinesePinyinIME/app/src/main/res/layout/activity_main.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `ChinesePinyinIME/app/src/main/res/values/themes.xml`
- `CHANGELOG.md`
- `PROJECT_HANDOFF.md`
- `ENVIRONMENT_SETUP.md`

### 验证

- 使用项目内完整 JDK 21（`.gradle-user-home/jdks/`）离线运行 `compileDebugJavaWithJavac`，编译通过。
- 使用同一环境运行 `assembleDebug`，打包通过。
- 真机测试通过，详见 `tests/v0.01.0020_2026-06-20_114254/REPORT.md`。
- 已验证：启动设置页、词库状态显示、学习数据状态显示、清除学习数据、跳转系统输入法设置。

---

## v0.01.0019 — 2026-06-20

执行者: Grok (Cursor Agent)

### 修改

- **Shift 键图标化**：
  - 将文字 `shift`/`SHIFT` 改为 `⇧` 上箭头符号，缩小按键宽度（`layout_weight` 0.65）。
  - 按下后图标变为实心 `▲`，并高亮蓝色背景；松开后单次大写逻辑不变。
  - Shift 激活时，26 个字母键面同步显示大写；恢复小写后键面回到小写。
- **语言切换键位置调整**：
  - `ZH/EN` 从第三行移至底栏，顺序为 `123` | `ZH/EN` | `space` | `enter`。
  - 第三行仅保留 `⇧`、字母区与 `DEL`，字母键获得更多宽度。
- 切换符号键盘时自动重置 Shift 状态。
- 更新启动页版本号为 `v0.01.0019`。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/themes.xml`
- `ChinesePinyinIME/app/src/main/res/drawable/keyboard_shift_active_background.xml`（新增）
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

### 验证

- 真机测试通过，详见 `tests/v0.01.0019_2026-06-20_105830/REPORT.md`。
- 本机编译安装通过；Shift 图标切换、全键面大写显示、底栏 `ZH/EN` 位置、符号模式内 `ZH/EN` 切换均通过。

---

## v0.01.0018 — 2026-06-20

执行者: Codex

### 修改

- 优化本地用户词频存储：
  - `user_frequency.tsv` 明确使用三列格式：`pinyin<TAB>candidate<TAB>count`。
  - 读取时遇到坏行只跳过该行，不再清空全部学习记录。
  - 内部 map key 改用不可见分隔符，避免和文件中的 tab 列格式混在一起。
  - 输入结束和服务销毁时主动 flush 一次，降低刚选词后退出导致学习记录尚未写入的风险。
- 优化候选排序权重：
  - 降低手动覆盖表的权重，使其仍能保护常用词，但不再完全压制本地用户选择。
  - 提高本地词频最大加分，让用户多次选择的候选能逐步前移；单次误触影响仍然较小。
- 更新启动页版本号为 `v0.01.0018`。
- 新增 `ENVIRONMENT_SETUP.md`，记录本机 SDK/JDK/Gradle/测试目录等环境信息，避免后续重复排查。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidateRanker.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/UserFrequencyStore.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `ENVIRONMENT_SETUP.md`
- `CHANGELOG.md`
- `PROJECT_HANDOFF.md`

### 验证

- `git diff --check` 通过（仅有 Windows 换行提示）。
- 已尝试运行 `compileDebugJavaWithJavac`：
  - 普通权限下可访问 SDK 受限，并提示找不到 Build Tools 36.0.0。
  - 授权后 SDK 访问问题解除，但当前可用 JetBrains JBR 缺少 `jlink.exe`，Android Gradle 的 JDK image transform 无法完成，因此本机命令行编译未完成。
- 待真机测试：重点验证候选学习是否能稳定前移，以及坏数据不会清空已有学习记录。

---

## v0.01.0017 — 2026-06-20

执行者: Grok (Cursor Agent)

### 新增

- **EN 模式 Shift / 大小写**：
  - 字母键盘第三行新增 `shift` 键（仅 `EN` 字母键盘显示）。
  - 单次大写：点 `shift` 后下一字母大写，随后自动恢复小写；按钮文案在 `shift` / `SHIFT` 间切换。
  - `ZH` 模式与符号键盘下不显示 Shift，不影响拼音输入。
- **Enter 行为细化**：
  - `ZH` 组字中按 Enter 仍上屏原始拼音。
  - 多行输入框插入换行 `\n`。
  - 单行字段按 `EditorInfo.imeOptions` 执行 `performEditorAction`（如发送、搜索、完成）。
  - 无特殊 action 时保持发送 Enter 键事件。
- **候选排序优化**：
  - 新增 `CandidateRanker`：综合词库顺序、词长偏好、手动覆盖表（`ni`/`shi`/`yi`/`de` 等）与本地词频加分重排候选。
- **本地用户词频学习**：
  - 新增 `UserFrequencyStore`：候选提交时记录 `(pinyin, candidate)` 选择次数，持久化至应用私有目录 `user_frequency.tsv`。
  - 本地加分有上限，避免误触永久置顶。
- **词库转换脚本**：
  - 新增 `scripts/convert_jieba_dict.py`：从 `third_party/jieba/dict.txt` 可重复生成 `assets/pinyin_dict.txt` 与 `conversion_report.txt`。
  - 新增 `scripts/pinyin_overrides.txt` 手动排序覆盖表；`scripts/requirements.txt` 声明 `pypinyin` 依赖。

### 修改

- `PinyinDictionary.getCandidates()` 返回经 `CandidateRanker` 重排后的候选列表。
- 空格/点击候选提交时写入本地词频记录。
- 更新启动页版本号为 `v0.01.0017`。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/PinyinDictionary.java`
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidateRanker.java`（新增）
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/UserFrequencyStore.java`（新增）
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `scripts/convert_jieba_dict.py`（新增）
- `scripts/pinyin_overrides.txt`（新增）
- `scripts/requirements.txt`（新增）
- `CHANGELOG.md`
- `PROJECT_HANDOFF.md`

### 验证

- 使用项目 Gradle Wrapper + JDK 21 运行 `compileDebugJavaWithJavac`，编译通过。
- 未进行真机测试。

---

## v0.01.0016 — 2026-06-20

执行者: Grok (Cursor Agent)

### 修改

- `ZH` 符号键盘数字键改为输出半角（`1` `2` …），不再转换为全角（`１` `２` …）。
- 符号键（`sym:`）统一按键面半角字符直接上屏；移除已不再使用的全角符号转换逻辑。
- 更新启动页版本号为 `v0.01.0016`。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

---

## v0.01.0015 — 2026-06-20

执行者: Grok (Cursor Agent)

### 修改

- `ZH` + `123` 符号键盘顶部恢复 `1`–`0` 数字行；`ZH` 模式下仍输出全角数字（如 `１` `２`）。
- `ZH` 符号页现为三行：数字 / 中文标点两行 + `DEL`；仍不显示英文符号行。
- 更新启动页版本号为 `v0.01.0015`。

### 修改文件

- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

---

## v0.01.0014 — 2026-06-20

执行者: Grok (Cursor Agent)

### 修改

- 符号键盘按语言模式完全分离，避免键位拥挤：
  - `ZH` + `123`：仅显示两行中文标点（`， 。 ？ ！ ： ；` / `、 “ ” （ ）` + `DEL`），不再显示数字与英文符号。
  - `EN` + `123`：保留完整三行半角数字、符号与英文标点。
- 中文标点仍通过 `punc:` 直接上屏；组字时点击会先清空拼音缓冲。
- 更新启动页版本号为 `v0.01.0014`。

### 修改文件

- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

---

## v0.01.0013 — 2026-06-20

执行者: Grok (Cursor Agent)

### 修改

- 移除 `ZH` 字母键盘下方的中文标点行，恢复紧凑字母布局。
- 改造 `123` 符号键盘：`ZH` 与 `EN` 共享数字与常用符号前两行；第三行按语言模式切换：
  - `ZH`：显示 `， 。 ？ ！ ： ； 、 “ ” （ ）` 中文标点（`punc:` 标签，直接上屏，组字时先清空拼音缓冲）。
  - `EN`：保留原有半角英文标点第三行。
- 切换 `ZH`/`EN` 时若已在符号键盘，会同步刷新第三行布局。
- 更新启动页版本号为 `v0.01.0013`。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

---

## v0.01.0012 — 2026-06-20

执行者: Grok (Cursor Agent)

### 新增

- 中文标点键盘行（仅 `ZH` 字母键盘下显示）：
  - 第一行：`，` `。` `？` `！` `：` `；`
  - 第二行：`、` `“` `”` `（` `）`
  - 标点键使用 `punc:` 标签，点击后直接上屏，不经过拼音缓冲；若正在组字会先清空拼音缓冲。
  - `EN` 模式与 `123` 符号键盘下不显示标点行（需先返回字母键盘）。
- 新增 `PunctuationKey` 样式，略缩小标点键字号以便两行容纳 11 个常用中文标点。

### 修改

- 更新启动页版本号为 `v0.01.0012`。
- `toggleInputMode()` 切换 `ZH`/`EN` 时同步刷新键盘布局，以显示/隐藏标点行。

### 修改文件

- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`
- `ChinesePinyinIME/app/src/main/res/values/themes.xml`
- `ChinesePinyinIME/app/src/main/res/values/strings.xml`
- `CHANGELOG.md`

### 验证

- 使用项目 Gradle Wrapper + 工作区 JDK 21 运行 `assembleDebug`，编译/打包成功。
- 真机（OnePlus 7 Pro，`7fbf2094`）：`adb install -r` 安装成功；启动页版本 `v0.01.0012` 正确。
- 真机 ADB 半自动验证（详见 `tests/v0.01.0012_2026-06-20_093708/REPORT.md` 与 `screenshots/`）：
  - **通过**：`ZH` 字母键盘显示两行中文标点；`，` `。` `？` `、` `（` 等直接上屏。
  - **通过**：组字中（`ni`）点击 `。` 会先清空拼音缓冲并上屏句号，而非把标点当作拼音。
  - **通过**：切至 `EN` 或 `123` 符号键盘后标点行隐藏（截图 `06_en_mode.png`、`07_symbol_mode.png`）。
  - **待人工**：`ni` + 空格提交 `你` 后再输入 `。` 组成 `你。`（ADB 字母键坐标与 v0.01.0010 同类问题，未在本次脚本中稳定复现）。

---

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
