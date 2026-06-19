# Changelog

All notable changes to ChinesePinyinIME are recorded here.

Version format: `vMAJOR.MINOR.DEBUG` (e.g. `v0.01.0008`).

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
