# 测试报告 — v0.02.0001（人工词库导入/导出 + 三层优先级）

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.02.0001` |
| 测试时间 | 2026-07-11 00:35 – 00:51（本地） |
| 会话目录 | `tests/v0.02.0001_2026-07-11_003542/` |
| 执行者 | Grok (CLI Agent) |
| 设备 | OnePlus 7 Pro `7fbf2094`，1440×3120 |
| 安装 | **未重新编译**；设备 `lastUpdateTime=2026-07-11 00:21:22`，与 `app/build/intermediates/apk/debug/app-debug.apk` 一致 |
| 默认 IME | `com.mercury.chinesepinyinime/.ChinesePinyinInputMethodService` |
| 范围 | Handoff §4 v0.02.0001 手测 1–8：导入/优先级/坏文件/导出/分层清空 + 0031/0032 与输入回归 |

## 对应需求

`PROJECT_HANDOFF.md` §4 / `ChangeLog/v0.02.0001-2026-07-11.md` / `docs/MANUAL_DICTIONARY.md`

## 结果总表

| 用例 | 结果 | 说明 |
|------|------|------|
| T01 基线版本与设置区 | **通过** | 设置页显示 `v0.02.0001`；人工词库三按钮可见 |
| T02 混合 TSV 导入（有效+重复+坏行） | **通过** | SAF 选中 `mixed.tsv` 后 `manual_dictionary.tsv` 含 3 条：`ni/妮/100`、`ceshici/测试词/80`、`shurufa/输入法/60`；状态「人工词库：3 条」 |
| T03 人工优先：9 键 `64` → `妮` 在 `你` 前 | **通过** | 候选栏 **`妮 你 泥 尼 拟 逆`** |
| T04 人工优先：26 键 `ni` | **未有效覆盖** | 坐标点键未稳定进入 26 键组字；逻辑与 9 键共用合并管线，以 T03 为准 |
| T05 仅坏行导入不覆盖 | **通过** | 导入 `malformed_only.tsv` 后 store **仍保留** 原 3 条；`64` 仍首候选「妮」 |
| T06 导出含人工+自学习 | **通过** | Toast「导出完成：12 条」；`/sdcard/Download/ChinesePinyinIME_dictionary.tsv` 含 `ni/妮/100` 与学习词（如 `shitai/是太` 等） |
| T07 只清空人工词库 | **通过** | 清空后 `manual_dictionary.tsv` 消失；`user_dictionary.tsv` 仍在；`64` 首候选回到「你」 |
| T08 只清除学习数据 | **通过** | 状态变为 0 组词频 / 0 自造词；学习文件删除（测试序列中其后又重导人工库） |
| T09 `64426→你好` | **通过** | 整词候选与一点上屏 |
| T10 组字 DEL | **通过** | `64426`→DEL 无崩溃 |
| T11 候选展开 | **通过** | 展开面板可用 |
| T12 冷启动 `744824`（0032） | **通过（冒烟）** | force-stop 后立即输入；左侧出现 **`shi/pi/qi`** 等多音节，非长时间仅 fallback 两项 |
| T13 快速 `644`（0031） | **通过（5 轮冒烟）** | 连续 5 次得到 `中文 644`，未误进符号键盘 |
| 导入计数文案（有效/重复/无效） | **部分** | 结果条在滚动后未稳定截到；由 store 内容与 JVM 单测佐证解析逻辑 |

**总体**：v0.02.0001 **主路径可接受**——导入生效、人工严格优先、坏文件不覆盖、导出合并、分层清空互不误伤；输入与 0031/0032 冒烟无阻塞问题。

## 详细观察

### 导入与存储

夹具 `artifacts/fixtures/mixed.tsv`：

- 合法：`ni/妮/100`、`ceshici/测试词/80`、`shurufa/输入法/60`
- 重复更低权重：`ni/妮/50`（应丢弃）
- 坏行：`ni hao`（含空格）、`hao/好/bad`（权重非法）

设备 `run-as` 读到的最终人工库仅 3 条有效，符合解析语义。

**SAF 注意**：

- 首次 push 到 `Download/ime_test_v020001/` 后，DocumentsUI 在未索引时显示「无任何文件」。
- 经 `MEDIA_SCANNER_SCAN_FILE` 后出现在「最近」；`.tsv` 可选。
- 建议文档写明：导入前可用「最近」或系统文件应用打开一次以触发索引。

### 优先级（核心）

导入后 9 键 `64`：

| 期望 | 实际 | 证据 |
|------|------|------|
| `妮` 先于内置 `你` | **符合** | `screenshots/03_priority_input/fx04_64_ni_priority.png` |
| 清空人工后恢复内置序 | **符合**（你/泥/尼…） | `screenshots/06_clear_layers/c03_64_after_clear_manual.png` |

### 坏文件不覆盖

| 步骤 | 结果 | 证据 |
|------|------|------|
| 已有 3 条人工时导入 `malformed_only.tsv` | store 仍为 3 条含 `妮` | `run-as` 日志 |
| 再输 `64` | 仍「妮」第一 | `fx07_64_still_ni.png` |

UI 结果文案「没有有效词条」因页面滚动未截稳，**行为以 store + 候选为准**。

### 导出

| 项 | 结果 |
|----|------|
| Toast | 导出完成：12 条 |
| 文件 | `artifacts/exported_ChinesePinyinIME_dictionary.tsv` |
| 内容 | 人工 `妮/测试词/输入法` + 当时自学习词；UTF-8 TSV 可回读 |

### 回归（v0.01 嵌套）

| 项 | 结果 | 证据 |
|----|------|------|
| `64426` / 你好 | 通过 | `r02` / `r03` |
| DEL | 通过 | `r04` |
| 展开 | 通过 | `r05` |
| 冷启动音节 | `qi`/`pi`/`shi` 可见 | `r06`/`r07` |
| 快打 `644` | 5/5 为 `644` | `r08_rapid644_*.png` |

## 证据索引

| 路径 | 内容 |
|------|------|
| `screenshots/01_baseline/` | 版本与设置 |
| `screenshots/02_import_valid/` | SAF 导航、导入后状态「3 条」 |
| `screenshots/03_priority_input/` | **`fx04_64_ni_priority`：妮优先** |
| `screenshots/04_import_malformed/` | 坏文件后仍妮优先 |
| `screenshots/05_export/` | 导出 Toast 12 条 |
| `screenshots/06_clear_layers/` | 清人工 / 清学习 |
| `screenshots/07_regression/` | 你好 / DEL / 展开 / 冷启动 / 快打 |
| `artifacts/fixtures/` | mixed / example / malformed 夹具 |
| `artifacts/exported_ChinesePinyinIME_dictionary.tsv` | 导出回读 |
| `artifacts/run_remaining_tests.ps1` | 主自动化脚本 |
| `artifacts/adb_log.txt` / `remaining_log.txt` | 日志 |

## 问题与建议

1. **文档**：OpenDocument 的 `text/*` 在部分目录视图下对新 push 文件不即时显示；写入手册「用最近文件或先索引」。
2. **产品**：导入成功/失败结果 TextView 在键盘顶起时易被滚出视野；可考虑 Toast 已有、或导入后自动滚到结果区（非阻塞）。
3. **测试债**：26 键 `ni` 点键坐标未在本会话校准通过；若需 26 键专门证据可再补一轮。
4. **自动化坑**：PowerShell 中 `$t` 与 `$T` 同名（大小写不敏感）会破坏路径变量；脚本应避免单字母大写路径变量。

## 结论

| 项 | 判定 |
|----|------|
| 人工词库导入/生效 | **通过** |
| 严格人工优先（9 键） | **通过** |
| 坏文件不覆盖 | **通过** |
| 导出人工+学习 | **通过** |
| 分层清空 | **通过** |
| 输入与 0031/0032 冒烟 | **通过** |
| 是否建议 push | **建议**：主交付已真机验证；可将本归档一并提交后 push |

## 附：JVM 单测

本会话未重跑 Gradle（按用户「不用编译下载」）；handoff 记 `testDebugUnitTest` 已本地通过，覆盖 TSV 解析与 `DictionaryLayerMerger` 三层顺序。
