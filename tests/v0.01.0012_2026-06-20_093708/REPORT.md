# 测试报告 — v0.01.0012 中文标点

## 元信息

| 项 | 值 |
|----|-----|
| 应用版本 | `v0.01.0012` |
| 测试时间 | 2026-06-20 09:37（本地） |
| 会话目录 | `tests/v0.01.0012_2026-06-20_093708/` |
| 执行者 | Grok (Cursor Agent) |
| 测试类型 | 真机安装 + ADB 坐标半自动验证 |

## 测试设备

| 项 | 值 |
|----|-----|
| 型号 | OnePlus 7 Pro (`GM1910`) |
| 设备 ID | `7fbf2094` |
| Android | `12` |
| 分辨率 | `1440x3120` |

## 用例与结果

| ID | 操作 | 期望 | 实际 | 结论 |
|----|------|------|------|------|
| T00 | 启动页版本 | `v0.01.0012` | 启动页 UI dump 显示 `v0.01.0012` | 通过 |
| T01 | 短信输入框聚焦 | IME 弹出 | `mInputShown=true` | 通过 |
| T02 | 点击 `，` `。` | 直接上屏中文标点 | compose 文本含 `，` `。` | 通过 |
| T03 | 点击 `？` `、` `（` | 继续上屏 | compose 文本含 `？` `、` `（` | 通过 |
| T04 | 输入 `ni` 后点 `。` | 清空组字并上屏句号 | compose 含 `。` 且无 `ni` 残留 | 通过 |
| T05 | 切 `EN` | 标点行隐藏 | 截图 `06_en_mode.png` | 通过 |
| T06 | 切 `123` 符号键盘 | 标点行隐藏 | 截图 `07_symbol_mode.png` | 通过 |
| T07 | `ni` + 空格 + `。` | 上屏 `你。` | ADB 坐标未稳定提交 `你`，仅验证标点/模式切换 | 待人工 |

## 证据

- `screenshots/01_keyboard_zh.png` — ZH 字母键盘含标点两行
- `screenshots/02_comma_period.png` — `，` `。` 上屏
- `screenshots/04_ni_composing.png` — 组字状态
- `screenshots/05_ni_then_period.png` — 组字后句号上屏
- `screenshots/06_en_mode.png` — EN 模式无标点行
- `screenshots/07_symbol_mode.png` — 符号键盘无标点行