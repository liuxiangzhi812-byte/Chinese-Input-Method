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
- Current display version: `v0.01.0022`

The project is still in early on-device testing. The goal is a simple, reliable, local Chinese Pinyin IME before advanced features such as fuzzy pinyin, cloud sync, skins, or AI prediction.

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

Latest functional node: **v0.01.0022 — 9-key pinyin-choice UI + dictionary loading performance measurement**

Implementation:

- Claude Code implemented 9-key pinyin-choice UI and committed locally.
- Per user instruction, Claude did not run real-device tests immediately.
- `pinyin_choice_bar` appears above the Chinese candidate bar when a digit sequence maps to multiple pinyin keys, for example `64 -> ni / mi`.
- Tapping a pinyin label changes active pinyin and refreshes Chinese candidates.
- Editing the digit buffer clears the explicit pinyin choice.

Testing:

- Grok (Cursor Agent) later ran the real-device pass.
- Report: `tests/v0.01.0022_2026-06-20_145553/REPORT.md`
- Device: OnePlus 7 Pro (`7fbf2094`), 1440 x 3120
- Result: v0.01.0022 pinyin-choice UI passed.
- Also passed: v0.01.0021 deferred empty-buffer `0` key space insertion.
- Dictionary cold-start performance was measured: **622-977 ms** across 5 cold-process runs in Edge search/address bar.

Important follow-up:

- The temporary `PinyinDictPerf` logging used for measurement should not remain in production source.
- Dictionary loading performance is now a confirmed optimization target, not an unmeasured risk.

## 5. Current Repository State Notes

At the time of this handoff update:

- `main` is ahead of `origin/main`.
- v0.01.0022 test archive should be committed and pushed.
- `.idea/` is local IDE state and should not be committed.
- `tests/README.md` should include the v0.01.0022 test archive entry.

Recommended immediate repository action:

1. Ensure temporary performance logging is removed.
2. Commit `PROJECT_HANDOFF.md`, `CHANGELOG.md`, `tests/README.md`, and `tests/v0.01.0022_2026-06-20_145553/`.
3. Do not commit `ChinesePinyinIME/.idea/`.
4. Push after the test record and cleanup are committed.

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

### P0 — Repository And Documentation Closure

Difficulty: Low
Depth: Shallow
Recommended engineer: Codex

Tasks:

- Ensure v0.01.0022 test archive is recorded.
- Keep `PROJECT_HANDOFF.md` concise.
- Move detailed completed behavior into `docs/FEATURE_DETAILS.md`.
- Mark `docs/FEATURE_DETAILS.md` as chief-engineer-maintained; other engineers should not edit it during ordinary implementation handoff.
- Ensure `CHANGELOG.md` no longer says v0.01.0022 is untested.
- Ensure temporary performance logging is removed before push.

Acceptance:

- GitHub has clean v0.01.0022 source + tests.
- No `.idea/` local state committed.
- Handoff is short enough for the next engineer to read quickly.

### P0 — Dictionary Loading Optimization

Difficulty: High
Depth: Deep
Recommended implementation engineer: Claude Code or another senior Android/Java engineer
Recommended tester: Grok

Problem:

- Current dictionary load + 9-key digit-index build measured **622-977 ms** on OnePlus 7 Pro.
- A fast user can type during the fallback-dictionary window.

Implementation brief:

- First split performance timing into phases: asset read, line parsing, candidate map construction, `buildDigitIndex()`.
- Do not jump directly to SQLite or binary format before phase timing.
- Candidate options, in increasing effort:
  - improve fallback coverage and show loading state if needed
  - reduce parsed dictionary size
  - generate a pre-indexed/loading-friendly asset at build/conversion time
  - consider SQLite only if profiling supports it
  - consider compact binary format only if text parsing is proven to dominate

Testing brief for Grok:

- Repeat v0.01.0022 Edge search/address-bar cold-process method.
- Run at least 5 cold-process runs.
- Record raw log lines, screenshots, device info, and report range/mean.
- If possible, repeat on a slower device or throttled emulator.

Acceptance:

- OnePlus 7 Pro cold load target: ideally under 300 ms.
- If not under 300 ms, report bottleneck and next optimization recommendation.
- No temporary measurement logging left in production source unless deliberately designed as diagnostics.

### P1 — 9-Key Ranking And Learning Polish

Difficulty: Medium-High
Depth: Medium
Recommended implementation engineer: Claude Code
Recommended tester: Grok

Implementation brief:

- Verify repeated 9-key selections can move candidates forward.
- Decide whether digit-to-pinyin ordering should learn from user choices, not only candidate ordering after pinyin is resolved.
- Keep 26-key learning behavior unchanged.

Testing brief:

- In 9-key, repeatedly choose a non-default candidate for `64 -> ni/mi`.
- Reopen keyboard and app process; confirm learning survives.
- Confirm 26-key lookup for the same pinyin shares learning where intended.
- Regress 26-key `ni`, candidate paging, symbols, delete, and settings layout toggle.

Acceptance:

- User learning is visible, bounded, and restart-safe.
- No regression to existing 26-key behavior.

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
- Performance-sensitive changes: repeat cold-process dictionary load measurement.

## 10. Known Limitations

- Dictionary cold-start loading is too slow for a polished prototype.
- 9-key mode currently has no English input path.
- No fuzzy pinyin.
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
