# ChinesePinyinIME Feature Details

This document keeps detailed completed behavior out of `PROJECT_HANDOFF.md` so the handoff stays short.

Ownership:

- This file is maintained by the chief engineer / Codex.
- Other engineers should not edit this file during ordinary feature handoff.
- Implementation handoff belongs in `PROJECT_HANDOFF.md` and `CHANGELOG.md`.
- Test evidence belongs in `tests/` and `tests/README.md`.

## Current Implemented Behavior

- The Android system can recognize ChinesePinyinIME as a third-party input method.
- The app uses a custom `InputMethodService` and XML keyboard view.
- 26-key mode supports Chinese pinyin composing and English direct input.
- The keyboard opens in Chinese mode by default.
- Candidate words appear when the pinyin buffer has content.
- Candidate buttons use measured width instead of fixed equal slots, so longer candidates are not truncated as easily.
- Candidate paging is width-adaptive. `‹` and `›` appear only when previous/next pages exist.
- Space commits the first candidate of the current visible page.
- Tapping a candidate commits that candidate.
- If no dictionary match exists, raw pinyin is used as fallback candidate.
- Delete supports single tap and long-press repeat-delete.
- Enter handles composing text, multi-line fields, and `EditorInfo.imeOptions`.
- EN Shift is one-shot uppercase.
- Symbol keyboards are language-specific: ZH uses Chinese punctuation, EN uses ASCII symbols.
- Chinese punctuation commits directly and clears composing state first.
- Local user-frequency learning records `(pinyin, candidate)` selections.
- Candidate ranking combines dictionary order, word-length preference, manual overrides, and local frequency boost.
- The settings page shows app version, dictionary status, learned-data status, clear learned data, input-method settings shortcut, privacy note, and 26-key/9-key toggle.

## Dictionary

Runtime dictionary:

- `ChinesePinyinIME/app/src/main/assets/pinyin_dict.txt`

Format:

```text
pinyin=candidate1,candidate2,candidate3
```

The current dictionary was converted from jieba's `dict.txt`.

Source and provenance:

- `third_party/jieba/dict.txt`
- `third_party/jieba/LICENSE`
- `scripts/convert_jieba_dict.py`
- `scripts/pinyin_overrides.txt`
- `conversion_report.txt`

Current conversion report summary:

- Parsed entries: `349046`
- Kept Chinese entries: `349040`
- Unique pinyin keys: `268353`
- Total output candidates: `349039`

## 9-Key / T9

9-key is opt-in from the settings page and is still experimental.

Implemented:

- Settings-page 26-key/9-key layout switch through `KeyboardLayoutPreferences`.
- 9-key digit grid:
  - `1` enters symbols
  - `2 ABC` through `9 WXYZ` append digits
  - `0` is space
  - `DEL` deletes one digit
  - `重输` clears the digit buffer
- `PinyinDictionary` builds a digit-to-pinyin reverse index from dictionary pinyin keys.
- `resolveBestPinyinForDigits()` picks a default pinyin for ambiguous digit sequences.
- `getPinyinKeysForDigits()` exposes all matching pinyin keys for the pinyin-choice UI.
- `pinyin_choice_bar` shows ambiguous pinyin labels such as `ni` and `mi` for `64`.
- Tapping a pinyin label changes the active pinyin and refreshes Chinese candidates.
- Candidate ranking, paging, commit, and local learning reuse the 26-key pipeline.

Known 9-key limitation:

- No English input path in 9-key mode. The `ZH`/`EN` button is hidden while 9-key is active.

## Latest Device Verification Summary

- v0.01.0019: full 26-key regression passed.
- v0.01.0020: settings page and learned-data clearing passed.
- v0.01.0021: 9-key stages 1-3 passed; two bugs were found and fixed before shipping.
- v0.01.0022: 9-key pinyin-choice UI passed; empty-buffer `0` space passed; dictionary cold-start load measured at 622-977 ms.

For exact evidence, read the reports under `tests/`.

## Historical Notes

Important historical bugs:

- v0.01.0021 found a static initialization order crash in `PinyinDictionary`; fixed by declaring `LETTER_TO_DIGIT` before `INSTANCE`.
- v0.01.0021 found a dead tie-break in digit-to-pinyin sorting; pinyin keys in the same digit bucket always have the same length, so candidate count replaced length as the useful tie-break.
- v0.01.0022 showed that dictionary loading performance is a real issue, not just a theoretical risk.

## Files That Should Not Be Committed

- `ChinesePinyinIME/.gradle/`
- `ChinesePinyinIME/.gradle-user-home/`
- `ChinesePinyinIME/build/`
- `ChinesePinyinIME/app/build/`
- `ChinesePinyinIME/local.properties`
- `ChinesePinyinIME/gradle/gradle-daemon-jvm.properties`
- Local Android Studio workspace state such as `ChinesePinyinIME/.idea/workspace.xml`
