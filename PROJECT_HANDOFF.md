# ChinesePinyinIME Project Handoff

## 1. Project Snapshot

ChinesePinyinIME is a lightweight local-first Android Chinese Pinyin input method.

Current technical choices:

- Android native app
- Java
- XML View UI
- `InputMethodService`
- Minimum SDK: Android 12 / API 31
- Package / namespace: `com.mercury.chinesepinyinime`
- Current display version: `v0.01.0024`

The project is still in early on-device testing. The immediate goal is not performance perfection; it is a simple, reliable, personally usable Chinese Pinyin IME. Current priority is input smoothness: 9-key pinyin selection, candidate expansion, and incomplete-pinyin lookup.

Detailed implemented behavior has been moved out of this handoff to keep handoff reading short:

- `docs/FEATURE_DETAILS.md`
- `CHANGELOG.md`
- `tests/README.md`

Ownership rule:

- `docs/FEATURE_DETAILS.md` is maintained only by the chief engineer / Codex.
- Other implementation engineers should use `PROJECT_HANDOFF.md` and `CHANGELOG.md` for handoff notes.
- Test engineers should update `tests/` reports and `tests/README.md`.

## 2. Important Paths

- `ChinesePinyinIME/`: Android Studio project
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`: IME service, keyboard state, input interaction
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/PinyinDictionary.java`: dictionary loading, candidate lookup, 9-key digit index
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidateRanker.java`: candidate ranking and manual overrides
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/UserFrequencyStore.java`: local user selection frequency
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidatePager.java`: width-adaptive candidate paging
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`: keyboard UI
- `ChinesePinyinIME/app/src/main/assets/pinyin_dict.txt`: runtime dictionary
- `scripts/convert_jieba_dict.py`: reproducible jieba-to-pinyin dictionary conversion
- `tests/`: real-device test archives
- `ENVIRONMENT_SETUP.md`: local build/test environment notes

## 3. Current Completed Scope

High-level completed features:

- Android system recognizes the app as a third-party IME.
- 26-key Chinese/English input is functional.
- Chinese pinyin composing, candidate display, candidate paging, tap/space commit, delete, long-press delete, symbol keyboards, punctuation, Shift, and Enter behavior are implemented.
- Large local dictionary loads from `assets/pinyin_dict.txt`; fallback dictionary is available while loading.
- Local candidate ranking and user-frequency learning exist.
- Basic settings page exists: version, dictionary status, learned-data status/clear action, input-method settings shortcut, 26-key/9-key layout toggle.
- 9-key/T9 prototype is implemented through pinyin-choice UI: digit grid, digit-to-pinyin index, ambiguous pinyin choice bar, shared ranking/paging/learning pipeline.

Detailed behavior and historical notes are in `docs/FEATURE_DETAILS.md`.

## 4. Current Work Node

Latest functional node: **v0.01.0024 — 9-key vertical pinyin selection list**

Implementation (Claude Code, committed locally on top of `main`/`origin/main` — **not based on `codex-experiment-dict-load-v0.01.0023`**, not pushed, no device test run per task instruction — testing assigned to Grok):

- Replaced the old horizontal `pinyin_choice_bar` (a row above the candidate bar) with a **fixed-width (64dp) vertical scrollable list** to the left of the 9-key digit grid, inside `t9_keyboard_section`. New view id: `t9_pinyin_choice_list`, wrapped in a plain `ScrollView`.
- The list shows every pinyin key the current digit buffer matches (e.g. `64 -> ni / mi`), highlights the active one (reuses `keyboard_shift_active_background`), and tapping an entry calls the existing `selectPinyinChoice()` — no change to how the active pinyin or candidate refresh logic works, only how the choices are presented.
- **Layout stability decision**: the 64dp column is never hidden or resized — only its contents change (empty when unambiguous or no digits yet). This was chosen over "hide the column when there's only one match" specifically to stop the 9-key digit grid's button width from jumping every time the digit buffer edges in/out of an ambiguous match. The whole 9-key row (list column + digit grid) is a fixed 144dp tall, matching the previous 3×48dp total exactly, so the keyboard's overall height is unchanged.
- Explicit-choice clearing on digit-buffer edits (append/delete/`重输`) reuses the existing `t9ActivePinyin = null` reset points already in `handleT9DigitKey`, the T9 branch of `handleDelete`, and `clearComposingState()` — no new clearing logic was needed, since those reset points already existed from the v0.01.0022 horizontal-bar version.
- 26-key (`letter_keyboard_section`) was not touched at all. Candidate ranking (`CandidateRanker`, `buildDigitIndex`'s tie-break order) was not touched — this is purely a presentation change (horizontal row -> vertical scrollable column) for the same underlying pinyin-choice data.
- No prefix pinyin matching, no fuzzy pinyin added (out of scope, confirmed not implemented).

Deferred to v0.01.0025 (see P0 task below): the **expandable Chinese candidate panel**. Reasoning: it's an independently-sized chunk of new UI work (expand/collapse state, a panel view covering the 9-key area, and close-triggers at ~6 different call sites) that would have added real risk if rushed alongside the vertical pinyin list in the same version. Not implemented this round; full brief is in section 8.

Previous shipped node on `main`: **v0.01.0022 — 9-key pinyin-choice UI + dictionary loading performance measurement**

- Claude Code implemented the (now-replaced) horizontal pinyin-choice bar; Grok ran the real-device pass and it passed. Report: `tests/v0.01.0022_2026-06-20_145553/REPORT.md`.
- Dictionary cold-start performance baseline measured: **622-977 ms** (OnePlus 7 Pro). The v0.01.0023 optimization attempt on top of this did not help and lives on `codex-experiment-dict-load-v0.01.0023`, not merged into `main`. Dictionary loading is **not** the current priority — see P2 task in section 8.

Next step: Grok device-tests v0.01.0024 (see the P0 task's testing brief in section 8) before anyone pushes.

## 5. Current Repository State Notes

At the time of this handoff update:

- The failed v0.01.0023 dictionary-loading experiment is preserved on branch `codex-experiment-dict-load-v0.01.0023` and should not be merged into `main`.
- `.idea/` is local IDE state and should not be committed.
- v0.01.0024 (vertical pinyin chooser) was built directly on top of `main`/`origin/main`, confirmed not based on the experiment branch — it does not contain any `PinyinDictPerf` logging or HashMap pre-sizing changes.
- `main` is now ahead of `origin/main` by the v0.01.0024 commit; it has **not** been pushed and has **not** been device-tested — both intentional per this round's task instructions.

Recommended immediate repository action:

1. Grok device-tests v0.01.0024 (see the P0 task's testing brief in section 8) and archives a report under `tests/`.
2. Once that test passes and is committed, push `main` to `origin/main`.
3. Keep `ChinesePinyinIME/.idea/` uncommitted.

## 6. Collaboration Workflow

The user wants development and testing separated:

1. **Planning / review**: Codex acts as chief engineer. Codex writes plans, clarifies scope, reviews structure, and checks handoff quality.
2. **Implementation**: Claude Code is recommended for focused code implementation. Claude should write code, compile if practical, update changelog/handoff, commit locally, and **not immediately run full real-device testing or push** unless explicitly asked.
3. **Testing**: Grok is recommended for device testing because it has high quota and has already built useful ADB/Edge coordinate workflows for this project. Grok must receive very explicit test instructions and archive evidence under `tests/`.
4. **Push / next step**: Only after testing passes and reports are archived should Codex or the designated engineer push to GitHub and move to the next feature.

Documentation permissions:

- Codex/chief engineer may update `docs/FEATURE_DETAILS.md` when architecture or long-form feature details need consolidation.
- Claude Code and other coding engineers should update only `PROJECT_HANDOFF.md` and `CHANGELOG.md` for ordinary handoff.
- Grok should update `tests/` and `tests/README.md`; if a test changes project state, summarize it in `PROJECT_HANDOFF.md` and `CHANGELOG.md` as needed.

Default rule for new development:

- Code-complete commit first.
- No immediate full test requirement for the coding agent.
- Separate test assignment next.
- Push only after test pass and test record are committed.

## 7. Engineer Profiles And Assignment Guidance

### Codex

Best for:

- Chief-engineer planning
- Handoff cleanup
- Code review
- Test-plan writing
- Repository hygiene
- Breaking large ideas into safe implementation tasks

Watch-outs:

- Codex may over-document if not constrained. Keep handoff short and move details to separate docs.

### Claude Code

Best for:

- Focused implementation
- Java/XML feature work
- Small, testable commits
- Updating changelog during implementation

Watch-outs:

- Do not require Claude to run the full real-device pass immediately after coding.
- Ask Claude to commit but not push unless the feature has already passed a separate test cycle.
- Give Claude a narrow implementation brief and explicit "do not expand scope" instruction.

### Grok / Cursor Agent

Best for:

- Real-device testing
- ADB scripts
- Screenshot evidence
- UI coordinate calibration
- Repeated verification passes

Watch-outs:

- Grok needs detailed test instructions: target app, coordinates if known, expected results, screenshot names, and report structure.
- Grok may add temporary instrumentation for measurement; require cleanup or explicit note before final commit.

## 8. Near-Term Development Plan

### P0 — 9-Key Vertical Pinyin Selection List

Target version: `v0.01.0024`
Status: **code-complete, not yet device-tested.** Implemented on top of `main`/`origin/main` (confirmed not based on `codex-experiment-dict-load-v0.01.0023`); compiled and packaged locally (`compileDebugJavaWithJavac` + `assembleDebug`), committed locally, not pushed. This task is not closed until Grok reports a passing device test.
Difficulty: Medium
Depth: Medium
Recommended implementation engineer: Claude Code
Recommended tester: Grok

Problem:

- Current 9-key pinyin-choice UI is a horizontal top row.
- When a digit sequence maps to more than a few pinyin keys, the pinyin labels cannot all be displayed or selected smoothly.
- User wants a Sogou-like 9-key interaction: pinyin choices appear on the left side of the 9-key digit area and can scroll vertically.

Implementation brief:

- Only change 9-key mode in this version. Keep 26-key behavior unchanged.
- Replace the current horizontal `pinyin_choice_bar` behavior for 9-key with a **left-side vertical pinyin selection list** beside the 9-key digit grid.
- The list should:
  - appear only in 9-key mode when the current digit buffer maps to multiple pinyin keys;
  - support vertical scrolling when pinyin choices exceed visible space;
  - highlight the active pinyin;
  - switch active pinyin on tap and refresh Chinese candidates immediately;
  - clear explicit selection when the digit buffer changes, preserving current behavior.
- Layout goal:
  - left column: vertical pinyin choices;
  - right area: 9-key digit grid;
  - do not make the keyboard taller than necessary;
  - keep key sizes stable and text readable on the OnePlus 7 Pro test device.
- Do **not** implement prefix pinyin matching in this version.
- Do **not** implement fuzzy pinyin in this version.

Testing brief for Grok:

- Enable 9-key mode from settings.
- In Edge search/address bar, type `64`; confirm a vertical list appears in a ~64dp-wide column to the **left** of the digit grid (not a horizontal row above the candidates anymore), showing `ni` and `mi` with `ni` highlighted (blue background), and tapping `mi` refreshes the Chinese candidates below.
- Type a single-digit or otherwise unambiguous sequence (e.g. `9` `3` `6` for `wen`); confirm the left column area is present but **empty/blank** (this is intentional — the column is always reserved, never hidden, see Current Work Node's layout-stability note; it should look like blank keyboard background, not an error or a stray empty button).
- Find or create a digit sequence with more than ~3 pinyin matches (enough to overflow the 144dp-tall column); confirm the list scrolls vertically and every visible entry is tappable, including ones reached only by scrolling.
- Confirm digit append/delete and `重输` clear the explicit pinyin choice and recompute the list for the new digit sequence.
- Confirm `DEL` (short tap and long-press), `重输`, `0` space, and the symbol-keyboard round trip (digit `1` in, `9键` back) still work exactly as before.
- Confirm the keyboard's overall height looks the same as the previous (v0.01.0022) 9-key build — no visible growth/shrink when switching between ambiguous and unambiguous digit sequences.
- Smoke-test 26-key `ni -> 你`, candidate paging, and symbol keyboard to confirm 26-key was not disturbed (no code in `letter_keyboard_section` changed, but worth a real confirmation).
- On OnePlus 7 Pro 1440x3120 specifically: check for any text overlap, squeezed/unreadable digit-key labels, or unclickable list entries — these were the explicit risks called out for this device.
- Archive screenshots and report under `tests/v0.01.0024_{date}_{time}/`.

Acceptance:

- 9-key pinyin selection is no longer limited by one horizontal row.
- Pinyin choices beyond the visible area can be reached by vertical scrolling.
- Active pinyin highlight and candidate refresh are reliable.
- Digit grid button size/position never visibly changes regardless of how many pinyin matches exist.
- 26-key behavior is unchanged.
- Claude committed locally but did not push; Grok tests before any push.

### P0 — Expandable Chinese Candidate Panel

Target version: `v0.01.0025` (**deferred from v0.01.0024** — see Current Work Node for reasoning: implementing this alongside the vertical pinyin list in the same version was judged too much independent new-UI surface for one safe round)
Difficulty: Medium
Depth: Medium
Recommended implementation engineer: Claude Code
Recommended tester: Grok

Problem:

- The Chinese candidate row can show only a limited number of words.
- Paging arrows work but are not as fluid as opening a larger candidate panel.
- User wants the main row to keep compact candidates, with a small arrow on the far right that opens a larger candidate menu covering roughly the 9-key keyboard area.

Implementation brief:

- Add a small expand arrow at the far right of the Chinese candidate row.
- When tapped, show an expanded candidate panel that overlays or replaces the 9-key digit grid area.
- The expanded panel should show more Chinese candidates than the compact row and allow scrolling if needed.
- Tapping a candidate commits it through the existing candidate commit path and closes the panel.
- Tapping the arrow again, pressing `DEL`, committing a candidate, clearing composition, or switching modes should close the panel.
- Keep the compact candidate row usable when the panel is closed.
- Avoid changing candidate ranking in this version.

Testing brief for Grok:

- Type a pinyin/digit sequence with many Chinese candidates, such as `yi`, `shi`, or T9 `94/94664` depending on available candidates.
- Confirm the compact row shows candidates and a far-right expand arrow.
- Tap the arrow; confirm the expanded candidate panel appears over the keyboard area and shows more candidates.
- Tap a candidate from the expanded panel; confirm it commits and panel closes.
- Confirm `DEL`, `重输`, mode/symbol toggles, and composition clearing close the panel cleanly.
- Confirm 26-key still works; if the panel is shared across 26-key and 9-key, test both.

Acceptance:

- User can access more than the compact candidate row without repeated page-arrow tapping.
- Expanded candidate panel does not permanently cover the keyboard or leave stale state.
- Candidate commit behavior and learning path remain unchanged.
- If this becomes too large while implementing v0.01.0024, split it into the next version rather than rushing.

### P0 — Prefix Pinyin Matching

Target version: `v0.01.0025`
Difficulty: Medium-High
Depth: Deep
Recommended implementation engineer: Claude Code
Recommended tester: Grok

Problem:

- Current lookup is mostly exact-key based.
- 26-key and 9-key are not yet comfortable because the user often must type complete pinyin before useful Chinese candidates appear.
- The user can spell pinyin and is building this IME for personal use; fuzzy pinyin is not urgent, but incomplete pinyin should work.

Implementation brief:

- Add prefix matching for both 26-key and 9-key.
- 26-key examples:
  - `zho` should be able to suggest `zhong` candidates such as `中`;
  - `sh` should surface useful pinyin/candidate options such as `shi`, `shang`, `shen`, `shuo`;
  - exact matches must still rank first.
- 9-key examples:
  - short digit prefixes should show possible pinyin choices instead of requiring the full digit sequence;
  - the vertical pinyin selection list from v0.01.0024 should be reused for prefix pinyin choices.
- Add or derive prefix indexes:
  - `pinyinPrefix -> pinyinKeys`;
  - `digitPrefix -> pinyinKeys`;
  - cap result counts so UI stays responsive.
- Ranking order should prefer:
  - exact pinyin/digit match;
  - common/manual-override pinyin;
  - pinyin with more/high-quality candidates;
  - stable alphabetical fallback.
- Keep true fuzzy pinyin out of this version.

Testing brief:

- 26-key: type partial pinyin like `zho`, `sh`, `zhongg`; confirm useful candidates or pinyin choices appear before the full spelling is complete.
- 9-key: type partial digit sequences and confirm pinyin choices appear in the vertical list.
- Confirm exact full pinyin behavior remains unchanged.
- Confirm candidate commit still records learning under the resolved pinyin, not raw prefix text.
- Confirm performance is acceptable during typing; no noticeable stall per keypress.

Acceptance:

- User can get useful candidates before typing complete pinyin.
- Exact matches remain stronger than prefix matches.
- 9-key vertical pinyin list and expanded candidate panel still work.

### P2 — Fuzzy Pinyin

Difficulty: High
Depth: Deep
Recommended engineer: Not scheduled yet

Current decision:

- Fuzzy pinyin is useful in general, but the user can spell pinyin and this IME is being customized for personal use.
- Do not implement fuzzy pinyin in the near term.
- Keep it as a future possible feature after 9-key selection and prefix matching are comfortable.

Possible future fuzzy groups:

- `z/zh`
- `c/ch`
- `s/sh`
- `n/l`
- `en/eng`
- `in/ing`
- `an/ang`

Important future rule:

- Exact matches must rank before fuzzy matches to avoid disturbing correct pinyin input.

### P2 — Dictionary Loading Optimization

Difficulty: High
Depth: Deep
Recommended implementation engineer: Senior Android/Java engineer
Recommended tester: Grok

Current decision:

- Not the next priority.
- The user has manually tried the app and considers the keyboard-open stutter acceptable for now.
- v0.01.0023 showed that a naive HashMap pre-sizing / two-pass read experiment did not improve load time; that experiment lives on branch `codex-experiment-dict-load-v0.01.0023`.
- Revisit only after basic typing is comfortable.

Future direction when revisited:

- Avoid two-pass raw-line buffering.
- Prioritize reducing or delaying `buildDigitIndex`.
- Consider build-time pre-indexing rather than runtime full parsing.

### P1 — Automated Core Tests

Difficulty: Medium
Depth: Medium
Recommended engineer: Codex or Claude Code

Implementation brief:

- Replace template tests with meaningful JVM tests.
- Cover `CandidatePager`, `CandidateRanker`, digit mapping/index ordering, and `UserFrequencyStore` bad-row tolerance.

Acceptance:

- `testDebugUnitTest` or equivalent local unit test task catches core ranking/paging/storage regressions.
- Tests do not require a real device.

### P2 — Handoff And File Structure Standardization

Difficulty: Low-Medium
Depth: Shallow-Medium
Recommended engineer: Codex

Tasks:

- Keep root handoff short.
- Move deep notes into `docs/`.
- Consider renaming `dict(1).txt` to `converted_pinyin_dict.txt` or moving generated dictionary artifacts under `data/`.
- Consider ignoring all `ChinesePinyinIME/.idea/` unless the team intentionally wants shared IDE project files.

Acceptance:

- New engineer can understand current state from handoff in 5-10 minutes.
- Deep details remain available but do not block onboarding.

## 9. Required Test Flow

For every testable feature:

1. Implementation engineer updates version/changelog and commits locally.
2. Implementation engineer does not need to run a full real-device pass unless specifically asked.
3. Tester creates a new `tests/v{version}_{YYYY-MM-DD}_{HHMMSS}/` folder.
4. Tester records `REPORT.md`, screenshots, UI dumps if useful, and command logs.
5. Tester updates `tests/README.md` index.
6. If test passes, commit the test archive.
7. Push to GitHub only after source + test report are both committed.

Minimum regression set:

- Launch/settings page version and dictionary status.
- 26-key: `ni -> 你`, candidate paging, tap/space commit, delete, long-press delete, symbols, punctuation.
- 9-key: layout toggle, `64 -> ni/mi`, tap `mi`, append digit resets choice, `94664 -> zhong`, `DEL`, `重输`, `0` empty-buffer space.
- After v0.01.0024: verify the left-side vertical pinyin list scrolls, highlights the active pinyin, and the digit grid's size never changes regardless of match count. (The candidate expand panel was deferred to v0.01.0025, not part of this version.)
- After v0.01.0025: verify the candidate expand panel opens/closes cleanly, and/or partial pinyin / digit-prefix matching before full spelling (whichever lands in that version).
- Performance-sensitive changes only: repeat cold-process dictionary load measurement.

## 10. Known Limitations

- Dictionary cold-start loading is not ideal, but not the current priority; typing usability comes first.
- 9-key mode currently has no English input path.
- No prefix pinyin matching yet; this is the next major usability feature after the 9-key UI work.
- No fuzzy pinyin; intentionally deferred because the user can spell pinyin and wants exact/prefix behavior first.
- No mistyped-pinyin correction.
- No tone support.
- No custom user dictionary UI.
- No import/export for learned data.
- No detailed privacy page beyond the local-only note.
- No cloud sync, networking, handwriting, speech input, or AI prediction planned for early versions.

## 11. Useful References

- Latest changelog: `CHANGELOG.md`
- Detailed feature behavior: `docs/FEATURE_DETAILS.md`
- Test archive rules: `tests/README.md`
- Environment setup: `ENVIRONMENT_SETUP.md`
- Latest device report: `tests/v0.01.0022_2026-06-20_145553/REPORT.md`
- Failed dictionary-loading experiment branch: `codex-experiment-dict-load-v0.01.0023`
