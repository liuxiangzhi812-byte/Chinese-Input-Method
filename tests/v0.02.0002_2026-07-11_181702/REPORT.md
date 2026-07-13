# 测试报告 — v0.02.0002（电脑本地网页词库管理）

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.02.0002`（`versionCode=20002`） |
| 测试时间 | 2026-07-11 18:17 – 18:30（本地） |
| 会话目录 | `tests/v0.02.0002_2026-07-11_181702/` |
| 执行者 | Grok (CLI Agent) |
| 手机 | OnePlus 7 Pro `GM1910` / `7fbf2094`，Android 12 (API 31)，1440×3120 |
| 电脑 | Windows，WLAN `192.168.1.6`；手机 Wi-Fi `192.168.1.2`（同一 `192.168.1.0/24`） |
| 安装 | 使用 Android Studio JBR 强制重打包并 `adb install -r`；`lastUpdateTime=2026-07-11 18:18:14`（含 Android 12 `URLDecoder` 修复） |
| PC 助手 | 因便携包 **缺少 `runtime\bin\java.exe`**，改用 `jbr\bin\java.exe -cp ...\ChinesePinyinIME-PC-Manager.jar com.mercury.cime.manager.PcDictionaryManager --no-browser` |
| 网页 | `http://127.0.0.1:37620/`，版本文案 `v0.02.0002` |
| 范围 | `tests/README.md`「v0.02.0002 电脑管理词库测试重点」+ handoff §8 P1 电脑词库管理 |

## 对应需求

- `PROJECT_HANDOFF.md` §4 / §8「PC Local-Web Dictionary Manager」
- `ChangeLog/v0.02.0002-2026-07-11.md`
- `pc-manager/README.md`

## 结果总表

| 用例 | 结果 | 说明 |
|------|------|------|
| T00 构建 / 单测 / 安装 | **通过** | `testDebugUnitTest` + `assembleDebug` 成功；设备显示 `v0.02.0002` |
| T01 开启电脑管理 + 前台通知 | **通过** | 设置页「开启电脑管理」后 `ComputerDictionaryService` `isForeground=true` |
| T02 UDP 发现 | **通过** | 发现 `紫苏的OnePlus` / `OnePlus GM1910` / `192.168.1.2:37621` / `v0.02.0002` |
| T03 选择设备 + 连接批准 | **通过** | 需展开通知组再点连接条露出「允许」；批准后 `status=approved` |
| T03b 连接轮询（Android 12 修复） | **通过** | 批准前/后多次 `/api/request` 轮询 **无闪退**（原 `URLDecoder.decode(String,Charset)` `NoSuchMethodError`） |
| T04 词库概况 | **通过** | `manualEntries=3, learnedEntries=1, selectionCount=5`（测试时设备既有数据） |
| T05 导入预览 | **通过** | `valid=3, duplicates=1, rejected=2`（mixed.tsv） |
| T06 确认导入 | **通过**（复测） | 首轮曾 `phone_unreachable`；重连后 `IMPORT OK {"valid":3,"duplicates":1,"rejected":2}`，status 仍 3 条 |
| T07 导出仅人工 | **通过** | 文件含 `ni/妮/100`、`ceshici/测试词/80`、`shurufa/输入法/60` |
| T08 导出人工+自学习 | **通过** | 在人工 3 条之外另含学习词 `lalala/啦啦啦/1` |
| T09 清空拒绝 / 确认 | **未闭环** | 后续会话多次因 PC 助手进程退出或 `not_connected` / 通知未展开失败；**未拿到稳定 reject→数据不变 / confirm→分层清空证据** |
| T10 关闭服务会话失效 | **通过**（首轮） | 关闭后 status 不可达，discover 空数组 |
| T11 网页仅本机 | **通过** | `http://192.168.1.6:37620/` 无法连接；仅 `127.0.0.1:37620` 可访问 |
| T12 便携 ZIP 双击 EXE | **失败（打包缺陷）** | `runtime\bin` **无 `java.exe`/`javaw.exe`**，仅有 `java.dll` 等；无法按文档「解压后双击 EXE」验收 |
| T13 IME 输入回归（64426 / 优先妮） | **未在本会话自动化完成** | 本轮焦点在 LAN 管理链路；输入回归依赖 v0.02.0001 已归档结果 + 词库层未改协议 |
| JVM 单测 | **通过** | `ComputerManagerSessionTest` / `DictionaryTsvCodecTest` / `DictionaryLayerMergerTest` |

**总体判定**：**主路径可接受但有条件**——同一 Wi-Fi 下发现、通知批准连接、状态、预览、导入、双导出、本机绑定、Android 12 轮询修复均已真机验证；**便携包缺 `java.exe` 为阻塞交付项**；清空双确认与 IME 手感回归建议补一轮后再 push。

## 详细观察

### Android 12 兼容修复

- 连接请求发出后可持续 poll `/api/request`，status 保持 `pending` 直至通知点「允许」，过程中 **App 未退出**。
- 证据：`artifacts/test_run_log.txt` 中 18:20:00–18:20:53 轮询序列与 `approved=True`。

### 通知交互

- ColorOS 将 ChinesePinyinIME 两条通知折叠为一组。
- 需先点组「展开」，再点「电脑 … 请求管理词库」正文，才会出现 **允许 / 拒绝**。
- 自动化勿假设通知栏一打开就有「允许」。

### 导入 / 导出

夹具 `artifacts/fixtures/mixed.tsv`（自 v0.02.0001）：

| 行 | 预期 |
|----|------|
| `ni / 妮 / 100` | 有效 |
| `ni / 妮 / 50` | 重复 |
| `ni hao / 你好 / 5` | 无效（拼音含空格） |
| `hao / 好 / bad` | 无效（权重） |
| `ceshici / 测试词 / 80` | 有效 |
| `shurufa / 输入法 / 60` | 有效 |

预览与手机导入均报告 **valid=3, duplicates=1, rejected=2**。

导出回读：

- `artifacts/exports/manual.tsv`：3 条人工词
- `artifacts/exports/combined.tsv`：3 条人工 + 至少 1 条自学习

### 首轮 import `phone_unreachable`

- 发生在首次连接后约 18s；logcat **未**见 `FATAL EXCEPTION` / `NoSuchMethodError`。
- 复连后同一 121 字节 TSV **瞬时导入成功**。
- 可能原因：临时网络/服务线程忙碌、PC 助手进程异常退出（stdout 重定向管道也可能加剧）。**不视为确定性崩溃**，但稳定性需继续观察。

### 便携包缺陷（阻塞）

| 检查项 | 结果 |
|--------|------|
| ZIP 内 `ChinesePinyinIME-PC-Manager.exe` | 有 |
| `app\ChinesePinyinIME-PC-Manager.jar` | 有 |
| `runtime\bin\java.exe` | **无** |
| `runtime\bin\java.dll` | 有 |

因此无法按 `pc-manager/README.md` 用完整便携目录双击验收；本会话用系统 JBR 跑 jar 验证协议。

### 安全边界

- 网页 Host 仅 `127.0.0.1:37620`。
- 局域网 IP 访问网页失败（符合设计）。
- 服务默认关闭；开启后前台通知可见。

## 证据索引

| 路径 | 内容 |
|------|------|
| `screenshots/01_baseline/` | 启动 / 电脑管理区 |
| `screenshots/02_discovery_connect/` | 通知折叠与展开、「允许」 |
| `screenshots/03_import/` | 重装与异常弹窗相关截图 |
| `screenshots/05_clear/` | 清空尝试过程截图 |
| `screenshots/06_regression/` | 键盘区截图（未完成点键回归） |
| `ui_dumps/` | 设置页、通知栏 dump |
| `artifacts/fixtures/mixed.tsv` | 导入夹具 |
| `artifacts/exports/manual.tsv` | 仅人工导出 |
| `artifacts/exports/combined.tsv` | 合并导出 |
| `artifacts/test_run_log.txt` | 完整自动化日志 |
| `artifacts/unit_tests/` | JVM 单测 XML（若已复制） |

## 问题与建议

1. **必须修复**：`jpackage` 产物 `runtime\bin` 缺少 `java.exe`，便携版无法独立启动。重新打包并验证双击 EXE 后再标交付完成。
2. **测试体验**：文档注明 ColorOS 通知需「展开组 → 展开请求 → 允许/确认」。
3. **稳定性**：PC 助手在长时间自动化中曾消失；建议避免对 JVM 进程做 stdout 管道阻塞，并给 import 保留足够 read timeout（当前 65s）。
4. **补测**：手机通知确认清空（拒绝保持数据、确认只清一层）；9 键 `64` 人工「妮」优先与 `64426→你好` 冒烟。
5. **非阻塞已知项**：单拼 `ji` 候选不完整（记录在 changelog，本版不修）。

## 结论

| 项 | 判定 |
|----|------|
| 同 Wi-Fi 发现 / 选择 / 通知批准连接 | **通过** |
| Android 12 轮询不再闪退 | **通过** |
| 状态 / 预览 / 导入 / 双导出 | **通过** |
| 网页仅 localhost | **通过** |
| 关闭服务后会话失效 | **通过**（首轮） |
| 便携 ZIP 可双击运行 | **失败**（缺 `java.exe`） |
| 清空双确认 | **未闭环** |
| 是否建议立即 push | **不建议**，至少先修复便携包并补清空确认；协议主路径已基本可用 |

## 附：环境与命令摘要

```text
JAVA_HOME = D:\Software\Android\Android Studio\jbr
gradlew testDebugUnitTest assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
java -cp pc-manager\dist\package\...\ChinesePinyinIME-PC-Manager.jar com.mercury.cime.manager.PcDictionaryManager --no-browser
```

## 工程复核补充（Codex，2026-07-13）

以下补充保留原测试结果，但修正证据解释：

1. **质疑 T12 的失败依据**：`jpackage --type app-image` 的应用启动器直接加载私有运行时中的 `jvm.dll`，运行目录不必提供给用户调用的 `runtime\bin\java.exe`。因此“没有 `java.exe`”本身不能证明便携包损坏。项目已在端口空闲、无旧 Java 服务的条件下直接启动 `ChinesePinyinIME-PC-Manager.exe`，确认新进程监听 `127.0.0.1:37620` 且页面返回 HTTP 200。若仍判失败，应以实际双击错误、启动器退出码和日志为证据，而不是以 `java.exe` 是否存在为标准。
2. **18:22:25 的进程死亡不是自然闪退**：归档 `logcat_full.txt` 明确记录 `Force stopping com.mercury.chinesepinyinime ... from pid 25373`，随后才出现 `app died`。这是测试命令主动停止应用，不能作为产品崩溃证据。
3. **报告遗漏了真正的稳定性问题**：设备日志保存了 2026-07-11 18:21:34 的 `OutOfMemoryError`，栈位于 `PinyinDictionary.buildPinyinPrefixIndex -> buildRuntimeDictionaryState -> reloadUserDictionariesAsync`。2026-07-13 又出现两次同类 OOM，分别由 `addUserCandidate -> mergeUserCandidateIntoRuntime` 触发。它能解释用户反馈的频繁、看似无规律的打字闪退，应将 v0.02.0002 判为存在阻塞性稳定性缺陷，而不是“协议主路径可接受即可推送”。
4. T06 的导入数据正确性可以保留“通过”，但首轮 `phone_unreachable` 不应仅归为临时网络：同时间段存在词库全量重建 OOM 证据，稳定性必须单独判失败。
5. T03 只能证明“按电脑名请求并批准连接”通过；当前通知证据没有显示电脑 IP，不能覆盖 handoff 中“手机显示电脑名/IP”的完整要求。
6. T04 只验证 `manualEntries`、`learnedEntries`、`selectionCount`，未验证冻结规划中的内置词库计数。该用例应描述为“已实现统计字段通过”，而不是完整 dashboard 验收。
7. 报告没有覆盖错误/过期令牌、乱序序号、10MB 超限、并发写入拒绝和完整清空双确认，因此“主路径多数通过”合理，“v0.02.0002 完整验收通过”不合理。

修订后的发布建议：**不推送**。先消除运行时词库/前缀索引全量重建导致的 400MB 堆耗尽，再复测导入、自造词写入和持续输入；清空双确认仍需补测。
