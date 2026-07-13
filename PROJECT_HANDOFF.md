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
- Current display version: `v0.02.0002` (development snapshot pushed 2026-07-13; PC LAN main path mostly verified, but stable acceptance is blocked by repeatable dictionary-index OOM crashes and incomplete clear-confirm testing)

Version-stage decision:

- The `v0.01` series is now closed as the **basic-function foundation**. It delivered the usable IME shell, 26-key/9-key input, pinyin and prefix lookup, candidate display/expansion, symbol and editing controls, large local dictionary loading, user-frequency learning, syllable-by-syllable 9-key composition, and basic self-learned words.
- `v0.01.0032` is the final `v0.01` foundation release and has been pushed to `origin/main`.
- The `v0.02` series starts a **major dictionary-system upgrade**. The first target is maintainable manual dictionary import/export and explicit merge priority; ranking optimization follows after real imported data is available.

The immediate goal is now to turn the existing large but mostly static dictionary into a maintainable three-layer system: manually curated dictionary, self-learned dictionary, and built-in base dictionary.

Detailed implemented behavior has been moved out of this handoff to keep handoff reading short:

- `docs/FEATURE_DETAILS.md`
- `ChangeLog/` update logs folder
- `tests/README.md`

Ownership rule:

- `docs/FEATURE_DETAILS.md` is maintained only by the chief engineer / Codex.
- Other implementation engineers should use `PROJECT_HANDOFF.md` and `ChangeLog/` for handoff notes.
- New update-log files should live under `ChangeLog/` and use `v{version}-{YYYY-MM-DD}.md` naming. The existing `ChangeLog/CHANGELOG.md` is now a legacy summary file and may remain until history is fully split out.
- Test engineers should update `tests/` reports and `tests/README.md`.

## 2. Important Paths

- `ChinesePinyinIME/`: Android Studio project
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ChinesePinyinInputMethodService.java`: IME service, keyboard state, input interaction
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/PinyinDictionary.java`: dictionary loading, candidate lookup, 9-key digit index
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidateRanker.java`: candidate ranking and manual overrides
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/UserFrequencyStore.java`: local user selection frequency
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/UserDictionaryStore.java`: local custom-word dictionary learned from user-composed phrases
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/ManualDictionaryStore.java`: persisted manually imported dictionary
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/DictionaryTsvCodec.java`: TSV parsing, validation, and export
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/DictionaryLayerMerger.java`: strict manual > learned > built-in merge policy
- `ChinesePinyinIME/app/src/main/java/com/mercury/chinesepinyinime/CandidatePager.java`: width-adaptive candidate paging
- `ChinesePinyinIME/app/src/main/res/layout/keyboard_view.xml`: keyboard UI
- `ChinesePinyinIME/app/src/main/assets/pinyin_dict.txt`: runtime dictionary
- `ChinesePinyinIME/app/src/main/assets/pinyin_syllable_dict.txt`: generated compact single-syllable dictionary loaded before the large runtime dictionary
- `ChangeLog/`: update-log folder; new files should use `v{version}-{YYYY-MM-DD}.md`
- `scripts/convert_jieba_dict.py`: reproducible jieba-to-pinyin dictionary conversion
- `scripts/build_syllable_dict.py`: regenerates the compact single-syllable dictionary from `pinyin_dict.txt`
- `docs/MANUAL_DICTIONARY.md`: manual dictionary format and usage
- `tests/`: real-device test archives
- `ENVIRONMENT_SETUP.md`: local build/test environment notes

## 3. Current Completed Scope

High-level completed features:

- Android system recognizes the app as a third-party IME.
- 26-key Chinese/English input is functional.
- Chinese pinyin composing, candidate display, candidate paging, tap/space commit, delete, long-press delete, symbol keyboards, punctuation, Shift, and Enter behavior are implemented.
- Large local dictionary loads from `assets/pinyin_dict.txt`; fallback dictionary is available while loading.
- Local candidate ranking and user-frequency learning exist.
- Basic settings page exists: version, built-in IME test input box, dictionary status, learned-data status/clear action, input-method settings shortcut, 26-key/9-key layout toggle.
- Expandable Chinese candidate panel is implemented locally: compact candidate row now has a far-right expand arrow that can replace the main keyboard area with a scrollable multi-row candidate panel.
- 9-key/T9 prototype is implemented through pinyin-choice UI: digit grid, digit-to-pinyin index, ambiguous pinyin choice bar, shared ranking/paging/learning pipeline.
- Settings now provide local manual dictionary import, combined manual/self-learned export, manual dictionary clear, and entry-count/result status.

Detailed behavior and historical notes are in `docs/FEATURE_DETAILS.md`.

## 4. Current Work Node

Latest development node: **v0.02.0002 — PC local-web dictionary manager**

Current status:

- Android settings now starts/stops a visible temporary foreground management service; it is off by default.
- Windows helper serves its UI only on `127.0.0.1:37620`, discovers enabled phones over UDP `37622`, and sends dictionary commands to phone TCP `37621`.
- Windows test artifact is `pc-manager/dist/ChinesePinyinIME-PC-Manager-v0.02.0002-portable.zip`. Extract the entire archive and run `ChinesePinyinIME-PC-Manager/ChinesePinyinIME-PC-Manager.exe`; never test a copied standalone EXE because its adjacent `app` and `runtime` directories are required. The package and launcher names intentionally use ASCII for Windows JVM-launcher compatibility.
- Device selection uses phone name/model. Connection requires phone notification approval and returns a temporary random token; every command also requires an increasing sequence number.
- PC page implements dictionary counts, TSV preview, authoritative phone import, manual-only export, combined export, and separate manual/learned clear requests.
- Clear requests do not execute until separately confirmed from the phone notification. Service stop invalidates the active session.
- Phone retains the v0.02.0001 file-picker workflow and reuses the same validation, atomic replacement, three-layer priority, and background index publication.
- Android `testDebugUnitTest` and `assembleDebug` pass. The PC helper compiles, packages with a private runtime, and its localhost/origin checks pass locally.
- OnePlus 7 Pro exposed an Android 12 compatibility crash during PC request polling: `URLDecoder.decode(String, Charset)` raised `NoSuchMethodError`. The phone parser now uses the older UTF-8 overload; rebuild/install and repeat the complete confirmation flow before accepting v0.02.0002.

Device/LAN test result (v0.02.0002) — archive: `tests/v0.02.0002_2026-07-11_181702/REPORT.md`:

- **Passed**: same-LAN discovery, device select, notification approve connect, status counts, TSV preview (`valid=3/dup=1/rejected=2`), import replace, manual export, combined export, webpage localhost-only, first-round session stop invalidation, Android 12 poll without crash.
- **Test-report claim disputed**: missing `runtime\bin\java.exe` is not a valid `jpackage` app-image failure criterion; the packaged EXE was independently started with a clean port and served HTTP 200. Retest actual Explorer launch if needed, but do not require a standalone `java.exe` inside the private runtime.
- **Not closed**: clear-manual / clear-learned reject+confirm notification path; IME `64→妮` / `64426→你好` smoke not re-run in this session.
- **Blocking stability defect found during engineering review**: repeated `OutOfMemoryError` at the 402,653,184-byte heap limit while `PinyinDictionary.buildPinyinPrefixIndex` rebuilds the complete runtime state. It occurs both after dictionary reload/import and after `addUserCandidate`, matching the user's frequent apparently random typing crashes.
- Tester used `java -cp ...PcDictionaryManager.jar` with Android Studio JBR after treating the absent `java.exe` as incomplete packaging; engineering review does not accept that criterion because the packaged launcher was independently verified to run.

Recommended next step:

1. Treat the runtime dictionary OOM as P0: eliminate full prefix/digit index reconstruction on every learned candidate and reduce peak index memory before further feature work.
2. Add a 10-minute management idle timeout, then complete clear reject/confirm and short 9-key priority regression.
3. Retest the actual packaged EXE launch rather than checking for `runtime\bin\java.exe`; only then mark the already-pushed v0.02.0002 snapshot as accepted/stable.

Final v0.01 foundation node: **v0.01.0032 — atomic dictionary readiness + cold-start syllable coverage**

Implementation (Codex; code-complete locally, not yet manually/device-tested):

- Diagnosed the delayed 9-key pinyin choices shown in the user screenshot. The full candidate map was published before its digit, prefix, and single-syllable indexes finished building, so the UI could combine full-dictionary words with fallback-only pinyin choices for roughly 25 seconds.
- Replaced the separately published runtime maps with one immutable runtime dictionary state. Candidate words and every derived index are now built in the background and published together through one atomic state switch.
- Added a generated compact dictionary containing all 405 single syllables found in the complete base dictionary. It is loaded before the keyboard is returned, so cold-start pinyin choices are no longer limited to a hand-maintained subset. `744824` is the regression example, not a special-case rule.
- Added `scripts/build_syllable_dict.py`; whenever the base dictionary changes, this script regenerates `pinyin_syllable_dict.txt` from all valid single-syllable entries and their single-character candidates.
- During full loading, the UI should now show one internally consistent state: complete fallback data first, then the complete large dictionary. It must never show full words paired with stale fallback syllables.
- Local `assembleDebug` passed after this change.

Manual/device test procedure (v0.01.0032):

1. Force-stop the app/IME process, reopen the app, focus the test field, and enter `744824` immediately without waiting for the large dictionary readiness message.
2. Expected during cold start: the left pinyin list includes the `74` choices `qi`, `pi`, `ri`, and `si` (scroll if needed), with `shi` retained as the `744` ambiguity. It must not remain limited to only `shi/ri`.
3. During the same cold-start window, tap `qi`, select `奇`, continue with `guai`, and select `怪`. Expected: `奇怪` can be composed without waiting roughly 25 seconds.
4. Repeat `744824` while the full dictionary finishes loading. Expected: candidate words and pinyin choices refresh together; no phase may show full-dictionary words with a stale two-item pinyin list.
5. After the full dictionary is ready, repeat `744824`, `744`, `64`, `726`, and `64426`; verify the established full-dictionary candidates and pinyin choices still work.
6. Re-run the v0.01.0031 rapid-input checks (`644` and top-row digits) because v0.01.0032 includes that still-unpushed keyboard-position fix.

Previous functional node: **v0.01.0031 — stable keyboard position during candidate updates**

Implementation (Codex; code-complete locally, not yet manually/device-tested):

- Diagnosed the repeated fast-typing mis-taps as layout movement rather than oversized key hitboxes. Previously the 40dp candidate bar changed from `GONE` to `VISIBLE` after the first composing key, shifting the complete keyboard downward between taps.
- The candidate row now always reserves its 40dp height and switches between `INVISIBLE` and `VISIBLE`. Keys should remain at identical screen coordinates before the first digit, while composing, and immediately after commit/clear.
- This explains both observed symptoms: a fast intended `4` could hit the previous position of `1/符号`, while a top-row digit such as `3` could land on the newly inserted candidate row.
- Local `assembleDebug` passed after the layout-stability change.

Manual/device test procedure (v0.01.0031):

1. Open an empty input field in 9-key mode and note the numeric-key positions before composing. Tap the first digit slowly; expected: the candidate row appears without any numeric key moving vertically.
2. Clear composition and rapidly enter `644` at least 20 times. Expected: every run records `644`; it must not open the `1/符号` keyboard.
3. Clear composition and rapidly tap `3` followed by other top-row digits at least 20 times. Expected: no candidate is accidentally committed and the input remains in digit composition.
4. Repeat the rapid-input checks immediately after committing a candidate, pressing DEL until empty, closing/reopening the IME, and expanding/collapsing the candidate panel.
5. Regression: verify candidate row contents, expand arrow, `64426 -> 你好`, and 26-key first-letter input remain functional.

Previous functional node: **v0.01.0030 — dictionary-aligned leading syllables + simplified candidate bar**

Implementation (Codex; code-complete locally, not yet manually/device-tested):

- When a 9-key digit sequence has an exact whole-word pinyin match, the left pinyin column now prioritizes leading syllables that can validly segment that matched pinyin. Example: `744824` matching `qiguai` should put `qi` before the longer raw digit-prefix ambiguity `shi`.
- Raw digit ambiguities are still appended after dictionary-aligned choices, so valid alternatives are not discarded.
- Removed the compact candidate bar's left/right page buttons. The compact row keeps its first-page candidates, and the existing expand control is now the single way to browse the full candidate list.
- Local `assembleDebug` passed after this change.

Manual/device test procedure (v0.01.0030):

1. In 9-key mode, enter `744824` (`qiguai`). Expected: whole-word candidates include `奇怪`, and the visible left pinyin choices include `qi` in the first visible position; `shi` may remain as a later ambiguity but must not hide `qi`.
2. Tap `qi`, select `奇`, and confirm the remaining `4824` can continue as `guai`; select `怪` and confirm `奇怪` commits without leftover digits or a crash.
3. Enter `744` alone. Expected: `shi` remains available for the genuine single-syllable input and its candidates remain usable.
4. Confirm the compact candidate bar no longer displays left/right paging arrows. Tap the expand arrow and verify all candidates remain scrollable and selectable in the expanded panel.
5. Regression: repeat `64426 -> 你好`, `336726 -> fen` syllable selection, DEL while composing, and expanded-panel close/reopen.

Device test result (v0.01.0030):

- Passed: compact candidate arrows removed, expanded candidate panel, `744 -> shi`, `744824 -> 奇怪` direct commit, `64426 -> 你好`, DEL, and `fen` syllable-path regression.
- Known limitation accepted for this push: on `744824`, `qi` is visible but remains second after `shi` because the same digits also exactly match `shitai`; automated tapping did not close the full `qi -> 奇 -> guai -> 怪` path.
- `726` now visibly includes `pan`, although its default highlighted ambiguity may still be another syllable.

Previous functional node: **v0.01.0029 — 9-key whole-word candidates + syllable fallback**

Implementation (Codex; device-tested by Grok on 2026-07-10):

- When a complete 9-key digit sequence exactly matches a multi-syllable dictionary or learned-word entry, the candidate bar now prioritizes whole-word candidates. This makes the dictionary and learned custom words directly usable again.
- The left-side pinyin chooser remains a single-syllable chooser. Tapping a pinyin entry explicitly enters per-syllable mode and shows candidates for that syllable, then continues with the remaining digits as before.
- If the complete digit sequence has no multi-syllable match, candidate display automatically falls back to the existing single-syllable composition flow.

Device test result (v0.01.0029) — archive: `tests/v0.01.0029_2026-07-10_162115/REPORT.md`:

- **Case A pass**: `64426` shows whole-word candidates such as `你好`; one-tap commit works; DEL while composing is safe.
- **Case B pass**: tapping left-side pinyin leaves whole-word mode, shows single-syllable candidates, then `你` + remaining `426`/`hao` + `好` commits cleanly without freeze.
- **Case C partial**: tapping `fen` on `336726` correctly enters syllable mode and can select `分`. However `336726` is **not** a no-whole-word case (built-in `fenran` → `愤然/奋然/忿然`). Second syllable for remaining `726` still does not expose a usable `pan` choice (same class of issue as v0.01.0028); `分盘` learn/recall was **not** completed.
- Commit path freeze from early v0.01.0028 was **not** reproduced.

Next step after this test:

1. The user accepted the v0.01.0029 release with the `726` limitation recorded; commit the test archive and push source together.
2. Keep ambiguous leading-syllable choices for keys such as `726` (`pan`/`san`/`ran`) as a non-blocking follow-up. Retest Case C after that fix.

Previous functional node: **v0.01.0028 — 9-key syllable-by-syllable composition + self-learning custom words**

Implementation (Codex; completed and pushed in `66776eb`; device test passed without an archived test report):

- Changed 9-key composition to always advance syllable by syllable: regardless of whether the full digit buffer already has a whole-word hit, the left-side pinyin list now stays on the current syllable and then advances to the remaining digits after that syllable is chosen.
- In that syllable-by-syllable path, the IME consumes the current digit buffer one syllable at a time, appends selected Chinese candidates into an internal phrase buffer, and then continues with the remaining digits.
- After the assembled phrase is committed, the IME now writes the combined `pinyin -> candidate` mapping into a local custom-word dictionary as a foundation for later recall/ranking.
- After Grok reported a `commitT9ComposedText -> addUserCandidate` freeze in `tests/v0.01.0028_2026-07-09_155530/REPORT.md`, the user-dictionary merge path was adjusted so the IME no longer rebuilds the whole runtime dictionary on the UI thread during commit.
- The settings page learned-data area now covers both learned frequency data and learned custom words; clearing learned data clears both stores.
- Local `assembleDebug` passed after this `v0.01.0028` round.

Previous pushed node: **v0.01.0027 — prefix pinyin matching (26-key + 9-key)**

- Pushed to `origin/main`.
- User manually tested `v0.01.0027` and confirmed it can be pushed, but no formal `tests/` archive was created for it.

Previous local node: **v0.01.0026 — expandable Chinese candidate panel**

- User manually tested the expandable candidate panel and reported it works, but no formal `tests/` archive was created for `v0.01.0026`.
- Last archived report still remains `tests/v0.01.0025_2026-07-09_133826/REPORT.md`.

Next step:

1. Commit and push `tests/v0.01.0029_2026-07-10_162115/` with the accepted v0.01.0029 source.
2. Schedule the non-blocking `726` ambiguous-syllable fix, then retest Case C (`fen`→`分`→`pan`→`盘` learn/recall).

Planned follow-up after `v0.01.0027` acceptance:

- The next feature is no longer just "optimize dictionary" in the abstract.
- The concrete target is to let 9-key users always advance by syllable, instead of letting full-word dictionary hits bypass the syllable-selection flow.
- Example requirement:
  - If the user wants `fenpan`, 9-key input should let the user walk through `fen` + `pan`, assemble the word, and commit it.
  - Even if the digit string already has some whole-word hit elsewhere in the dictionary, the left-side pinyin flow should still stay on the current single syllable first.
  - After commit, the IME should record the new mapping into self-learning storage.

## 5. Current Repository State Notes

At the time of this handoff update:

- The failed v0.01.0023 dictionary-loading experiment is preserved on branch `codex-experiment-dict-load-v0.01.0023` and should not be merged into `main`.
- `.idea/` is local IDE state and should not be committed.
- v0.01.0024 (vertical pinyin chooser) already has an archived device-test report under `tests/`, but its commit and report have not been pushed to `origin/main`.
- v0.01.0025 (in-app IME test input box) is already on `origin/main` with its archived device-test report.
- v0.01.0026 (expandable Chinese candidate panel) was manually verified by the user but has no archived device-test report.
- v0.01.0027 (prefix pinyin matching) has already been pushed to `origin/main`.
- v0.01.0028 (9-key syllable-by-syllable composition + self-learning custom words) was tested by the user and pushed to `origin/main` in `66776eb`; its earlier Grok failure report remains archived for traceability.
- v0.01.0029 (9-key whole-word candidates + syllable fallback) has an archived device report: Cases A/B passed; Case C remains incomplete because `726` does not expose `pan`. The user accepted this as a non-blocking follow-up for the release.
- v0.01.0030 (dictionary-aligned leading syllables + simplified candidate bar) was device-tested and pushed with its archived report.
- v0.01.0031 (stable keyboard position) and v0.01.0032 (atomic dictionary readiness + generated single-syllable base) have been pushed to `origin/main`; they close the v0.01 basic-function stage.
- v0.02.0001 passed device testing and is pushed. The v0.02.0002 development snapshot was pushed on 2026-07-13 by user request; engineering review found blocking dictionary-index OOM crashes and clear-confirm remains incomplete, so push status must not be read as stable acceptance.

Recommended immediate repository action:

1. Fix and stress-test the P0 runtime dictionary/index OOM before candidate UI or ranking work.
2. Add the 10-minute inactive management shutdown, then finish clear reject/confirm + short IME regression.
3. After the fixes, archive a new stability/device result and explicitly mark the already-pushed v0.02.0002 snapshot accepted or superseded.

## 6. Collaboration Workflow

The user wants development and testing separated:

1. **Planning / review**: Codex acts as chief engineer. Codex writes plans, clarifies scope, reviews structure, and checks handoff quality.
2. **Implementation**: Claude Code is recommended for focused code implementation. Claude should write code, compile if practical, update the relevant `ChangeLog/` entry and handoff, commit locally, and **not immediately run full real-device testing or push** unless explicitly asked.
3. **Testing**: Grok is recommended for device testing because it has high quota and has already built useful ADB/Edge coordinate workflows for this project. Grok must receive very explicit test instructions and archive evidence under `tests/`.
4. **Push / next step**: Only after testing passes and reports are archived should Codex or the designated engineer push to GitHub and move to the next feature.

Documentation permissions:

- Codex/chief engineer may update `docs/FEATURE_DETAILS.md` when architecture or long-form feature details need consolidation.
- Claude Code and other coding engineers should update only `PROJECT_HANDOFF.md` and the relevant `ChangeLog/` entry for ordinary handoff.
- Grok should update `tests/` and `tests/README.md`; if a test changes project state, summarize it in `PROJECT_HANDOFF.md` and the relevant `ChangeLog/` entry as needed.

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
- Updating the relevant `ChangeLog/` entry during implementation

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

### Current Engineer Assignment Queue (2026-07-13)

This is the authoritative pickup order. Historical task sections below remain for traceability; an old `P0` label attached to a completed v0.01 feature does not make it active again.

1. **P0 — Runtime Dictionary Index OOM Stability**: assign immediately to one senior Android/Java engineer. This is the only feature-code task that may change `PinyinDictionary` indexing and runtime publication until its stress acceptance passes.
2. **P1 — PC Manager Lifecycle Completion**: after the P0 data-structure contract is settled, implement the 10-minute valid-action idle timeout and close clear reject/confirm plus packaged-EXE regression gaps.
3. **P1 — Complete Scrollable Candidate Dropdown**: start after the P0 lookup behavior is stable. It must expose the authoritative complete result set and must not special-case `ji` or `激`.
4. **P1 — Candidate Ranking And Dictionary Expansion**: start after candidate completeness is proven, so missing data is not confused with UI truncation or ranking.
5. **P1 — Automated Core Tests**: may proceed in parallel when tests do not freeze or duplicate the changing P0 implementation details.
6. **P2 backlog**: leading-syllable single-character selection, fuzzy pinyin, PC-enter remote input, manual hitbox/DEL confirmation, in-app manual dictionary row editor, loading-speed optimization, and handoff/file cleanup.

Parallel-work rules:

- Do not assign OOM repair, candidate retrieval internals, and ranking changes to separate engineers at the same time; they overlap in `PinyinDictionary`, `CandidateRanker`, and runtime-state publication.
- PC helper UI/packaging checks and documentation-only work may run in parallel. Phone service lifecycle work should wait until the P0 runtime ownership design is fixed.
- Every assignee must preserve generic behavior, add no word-specific patches, update `PROJECT_HANDOFF.md` plus the versioned `ChangeLog/` file, and leave a local commit for separate testing. Push only after the required test record passes unless the user explicitly directs otherwise.
- Engineers should implement only the assigned active section. They must not revive completed historical v0.01 sections or expand a P2 item without a new assignment.

### P0 — Expandable Chinese Candidate Panel

Target version: `v0.01.0026`
Status: **code-complete, not yet device-tested.** Implemented locally in the worktree; not committed or pushed for this version yet.
Difficulty: Medium
Depth: Medium
Recommended implementation engineer: Codex or Claude Code
Recommended tester: Grok

Problem:

- The Chinese candidate row can show only a limited number of words.
- Paging arrows work but are not as fluid as opening a larger candidate panel.
- User wants the main row to keep compact candidates, with a small arrow on the far right that opens a larger candidate menu covering roughly the keyboard area.

Implementation brief:

- Add a far-right expand arrow to the compact candidate row.
- When tapped, show an expanded candidate panel that replaces the main keyboard section area.
- The expanded panel should show more Chinese candidates than the compact row and allow scrolling if needed.
- Tapping a candidate commits it through the existing candidate commit path and closes the panel.
- Tapping the arrow again, pressing `DEL`, committing a candidate, clearing composition, or switching modes should close the panel.
- Keep the compact candidate row usable when the panel is closed.
- Avoid changing candidate ranking in this version.

Testing brief for Grok:

- In both 26-key and 9-key Chinese composing states, confirm the compact candidate row shows a far-right expand arrow.
- Tap the arrow and confirm the expanded candidate panel appears in place of the main keyboard area while the compact candidate row remains visible.
- Confirm the expanded panel shows more candidates than the compact row and can scroll when needed.
- Tap a candidate from the expanded panel; confirm it commits and the panel closes.
- Confirm `DEL`, `重输`, mode/symbol toggles, and composition clearing close the panel cleanly.
- Recheck `64 -> ni/mi`, `94664 -> zhong`, and 26-key `ni -> 你` to make sure the panel did not disturb the existing candidate flow.
- Archive screenshots and report under `tests/v0.01.0026_{date}_{time}/`.

Acceptance:

- User can access more than the compact candidate row without repeated page-arrow tapping.
- Expanded candidate panel does not permanently cover the keyboard or leave stale state.
- Candidate commit behavior and learning path remain unchanged.
- 26-key and 9-key candidate flows still work.

### P0 — In-App IME Test Input Box

Target version: `v0.01.0025`
Status: **device-tested (pass).** Report: `tests/v0.01.0025_2026-07-09_133826/REPORT.md`. Already pushed.
Difficulty: Low-Medium
Depth: Small
Recommended implementation engineer: Codex
Recommended tester: Grok

Problem:

- Manual verification currently has to start in another app such as Edge, which adds setup friction before every smoke test.
- The project needs a stable built-in input target so testers can open the app and immediately try the IME.

Implementation brief:

- Add a dedicated multiline input box near the top of `MainActivity`.
- Keep the existing settings page content intact below it.
- Request focus for the field on launch so the cursor is ready immediately.
- Make the field suitable for basic candidate, delete, paging, Enter, and 26-key/9-key layout checks.
- Do **not** change IME candidate ranking or keyboard behavior in this version; this is a testability improvement only.

Tester note (moved out of the app UI):

- The section is now titled **"输入测试"** (previously "快速测试"); the on-screen explanatory paragraph under it was removed to keep the settings page clean.
- What that paragraph told testers, kept here for reference: open the app and type directly in this field to immediately check candidates, delete key, paging, Enter, and 26-key/9-key switching; this field is the default first test target, so there is no need to switch to Edge first.

Testing brief for Grok:

- Open the app and confirm the "输入测试" section is visible near the top.
- Confirm the test input box already has focus or can be focused with one tap, then switch to ChinesePinyinIME and type directly inside the app.
- Run the basic smoke path in this field first:
  - 26-key `ni -> 你`
  - candidate tap / space commit
  - `DEL` short tap and long-press
  - symbol keyboard round trip
  - 26/9 key layout toggle after reopening the keyboard
- In 9-key mode, use the same in-app field to verify `64 -> ni/mi`, tap `mi`, `94664 -> zhong`, `重输`, and empty-buffer `0` space.
- After the in-app field passes, optionally rerun a lighter Edge cross-app check for regression confidence.
- Archive screenshots and report under `tests/v0.01.0025_{date}_{time}/`.

Acceptance:

- App opens with a visible built-in test field.
- Testers can start IME smoke testing inside the app without first navigating to Edge.
- Existing settings-page controls still work.
- No push happens before the test archive is committed.

### P0 — 9-Key Vertical Pinyin Selection List

Target version: `v0.01.0024`
Status: **implemented, with archived device test evidence.** Grok archived `tests/v0.01.0024_2026-06-20_171120/REPORT.md`; the core behavior passed, while the long-list scroll case remained not covered.
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

### P0 — Prefix Pinyin Matching

Target version: `v0.01.0027`
Status: **code-complete, not yet device-tested.** Implemented locally in the worktree; not committed or pushed for this version yet.
Difficulty: Medium-High
Depth: Deep
Recommended implementation engineer: Codex or Claude Code
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

### P0 — 9-Key Syllable-By-Syllable Composition And Self-Learning Foundation

Target version: `v0.01.0028`
Status: **code-complete, not yet device-tested.** Implemented locally in the worktree; local `assembleDebug` passed.
Difficulty: High
Depth: Deep
Recommended implementation engineer: Codex or Claude Code
Recommended tester: Grok

Problem:

- Current 9-key flow should not let whole-word dictionary hits bypass syllable-by-syllable input.
- The user wants to decide the current syllable first, then continue to the remaining syllables, even when the full digit string already has some whole-word match in the dictionary.
- This blocks the desired self-learning behavior, because the IME cannot learn a new word that the user was never able to type successfully in the first place.
- Example:
  - The user wants to type `fenpan`.
  - In 9-key mode, the IME should first present single-syllable pinyin choices for the current front segment of the digit string.
  - After the user chooses `fen`, the pinyin choices should immediately switch to the single-syllable choices for the remaining digits, such as `pan`.
  - This should remain true even if the full digit string already has some whole-word hit elsewhere in the dictionary.
  - The user must be able to select `fen` and `pan` step by step, assemble the target text, and commit it.
  - Only then can the IME store that result into a self-learning dictionary for future direct recall.

Implementation brief:

- Make the 9-key pinyin-choice column always operate at the current single-syllable level, instead of switching to whole-word pinyin matches for the full digit buffer.
- After one syllable is chosen, advance the pinyin-choice column to the remaining digits and keep showing single-syllable choices there.
- Introduce or derive a pinyin-syllable validity source so the IME can recognize legal syllables even when the final whole word is not already in the dictionary.
- Allow the user to build a phrase from multiple selected syllables in sequence, such as `fen` then `pan`.
- Keep the interaction compatible with the current 9-key pinyin-choice UI as much as possible; avoid redesigning the whole keyboard in this version.
- After a user commits a word/phrase that was assembled through this syllable-by-syllable path, store:
  - committed Chinese text;
  - resolved full pinyin sequence;
  - enough local data to support future learned recall/ranking.
- Reuse existing local-only storage patterns where practical, but do not block this version on a polished settings UI.
- Do not bundle PC-side manual dictionary editing into the first implementation if that slows delivery; the foundation is:
  - user can type missing words first;
  - IME can learn them locally afterward.

Suggested delivery split:

1. Make 9-key missing-word composition possible.
2. Persist successful manual compositions into a basic user dictionary.
3. Only after the above is stable, add PC-side manual dictionary editing/import workflow.

Testing brief:

- In 9-key mode, verify the left-side pinyin list stays on the current single syllable even when the full digit string already has a whole-word dictionary hit.
- Use the `fenpan`-style scenario as an explicit regression case.
- Confirm the user can select the first syllable, continue typing/selecting the next syllable, and finally commit the combined result.
- After the first successful commit, confirm the assembled phrase is written into local learned data without breaking the always-syllable flow.
- Confirm the commit path no longer freezes or kills the app when a newly assembled custom phrase is written to learned data.
- Confirm existing exact-hit words such as `94664 -> zhong` still behave normally.
- Confirm prefix matching from `v0.01.0027` still works and does not conflict with the new 9-key composition state machine.
- Confirm delete, `重输`, candidate expand panel, and mode switching all clear intermediate composition state correctly.

Acceptance:

- 9-key users can always advance one syllable at a time instead of being forced into whole-word resolution for the full digit string.
- 9-key users can type target words even when the base dictionary lacks a direct full-word pronunciation entry.
- The IME can store those successfully committed words into local learned data.
- Existing 26-key and non-9-key flows remain stable.
- Manual PC-side dictionary editing is allowed to remain a later follow-up.

### P2 — Manual Interaction Confirmation Cleanup

Difficulty: Low
Depth: Small
Recommended implementation engineer: Not required unless a real bug is confirmed
Recommended tester: Grok or human manual tester

Current decision:

- The v0.01.0025 smoke pass already verified the new in-app test box and the main 26-key / 9-key flows.
- Two items remain better suited to manual confirmation than automation in the current setup:
  - candidate-row tap accuracy in the app test field;
  - `DEL` short-tap / long-press feel in the same field.
- Edge full regression was not rerun after the in-app field pass; this is useful but not a blocker.
- Treat these as **low-priority follow-up checks**, not as blockers for pushing v0.01.0025 unless a human finds a real regression.

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

### P1 — Manual/User Dictionary Management

Difficulty: Medium-High
Depth: Deep
Recommended implementation engineer: Codex or Claude Code
Recommended tester: Grok

Current decision:

- Implemented, device-tested, and pushed in v0.02.0001: UTF-8 TSV import/export, atomic replacement, combined manual/self-learned export, and strict manual > learned > built-in priority.
- v0.02.0002 adds the PC local-web management path. Its stable acceptance remains blocked by the separate P0 OOM defect and incomplete clear-confirm regression.

Planned scope when revisited:

- Define a stable custom-dictionary file format for manually maintained entries.
- Let the user update that file from a PC and have the app ingest it locally.
- Decide merge rules between:
  - built-in base dictionary;
  - user self-learning dictionary;
  - manually maintained custom dictionary.
- Add conflict rules so manually curated entries are not accidentally drowned out by noisy learned data.

Acceptance:

- User can maintain a small hand-edited dictionary on PC without rebuilding the entire app workflow each time.
- Manual entries and learned entries have predictable merge priority.
- This feature does not block `v0.01.0028`.

### P0 — Runtime Dictionary Index OOM Stability

Status: **repeatable production crash confirmed from device logcat; highest priority before new features**

Evidence and cause:

- OnePlus 7 Pro / Android 12 has a 402,653,184-byte app heap limit. Confirmed OOM timestamps include 2026-07-11 18:21:34 and 2026-07-13 09:56:22 / 09:57:58.
- Crashes allocate inside `PinyinDictionary.buildPinyinPrefixIndex`, reached from both `reloadUserDictionariesAsync` after dictionary operations and `addUserCandidate -> mergeUserCandidateIntoRuntime` during normal typing.
- Every new learned candidate currently calls `applyDictionaryLayers`, rebuilding candidate words, digit indexes, pinyin-prefix indexes, digit-prefix indexes, and syllable indexes for the complete 268k-key dictionary while the previous immutable state is still live. Peak duplication exhausts the heap.
- A later main-thread OOM inside ColorOS UI code is secondary heap exhaustion, not evidence that ColorOS caused the leak.

Frozen repair target:

- Do not rebuild the complete runtime state for every learned candidate. Existing-pinyin candidate additions must update only candidate data; genuinely new pinyin keys must use a small delta index or a bounded deferred batch.
- Replace prefix indexes that duplicate every full pinyin key across many `HashMap<String,List<String>>` buckets with a compact representation, such as sorted key arrays plus prefix-range lookup, while preserving exact current lookup behavior.
- Full import/reload must keep peak memory below the device heap limit. Build compact replacement structures in the background and publish atomically without retaining multiple expanded index copies longer than necessary.
- Do not treat `OutOfMemoryError` catching, `largeHeap`, forced GC, or reducing the dictionary as the primary fix. Memory growth must be structurally removed.
- Add lightweight diagnostic logging for rebuild reason, duration, key counts, and approximate heap before/after; never log dictionary contents.

Required tests:

- Record at least 100 distinct self-created words during continuous 26-key and 9-key use; no process restart, OOM, ANR, or missing learned recall.
- Repeat manual import, combined export/re-import, manual clear, and learned clear at least 10 cycles while typing between operations.
- Measure heap before and after each cycle and after idle GC. Memory must stabilize rather than grow toward 400MB.
- Verify prefix results, 9-key digit choices, cold-start `744824`, `64426 -> 你好`, manual > learned > built-in priority, and atomic publication remain unchanged.
- Stress while the 10-minute PC management service is active and after it auto-stops, proving network lifecycle does not retain old runtime dictionary states.

Acceptance:

- No dictionary/index OOM in the stress sequence on the OnePlus 7 Pro.
- Adding a learned candidate does not trigger a complete runtime index rebuild.
- Full reload peak memory stays safely below the 402MB device limit with repeatable headroom.

### P1 — PC Local-Web Dictionary Manager

Target version: `v0.02.0002`
Status: **development snapshot pushed; LAN main path device-tested with gaps; not accepted as stable** — discovery/connect/import/export/localhost OK; repeated dictionary-index OOM is blocking; clear-confirm not closed. The report's `java.exe` packaging criterion is disputed. Report: `tests/v0.02.0002_2026-07-11_181702/REPORT.md`.
Difficulty: High
Depth: Deep
Recommended implementation engineer: Codex
Recommended tester: Grok plus human cross-device testing

Frozen scope:

- Windows-only PC helper packaged with its runtime; double-click starts a localhost graphical webpage.
- Same-LAN wireless transport only. No internet, VPN, cloud relay, wired transport, ADB, public endpoint, account, or automatic sync.
- Web UI binds only to `127.0.0.1:37620`; phone protocol uses fixed TCP port `37621` and UDP discovery port `37622`.
- Phone management is off by default. The user starts a visible temporary foreground-service session; stopping it immediately disables discovery and commands.
- Communication content is plaintext by accepted product decision for trusted home networks.
- No manually entered pairing secret. PC discovers active phones by model/name; the user selects one; the phone confirms the incoming computer connection.
- Phone returns an automatically generated temporary session token after confirmation. Commands require that token plus increasing sequence numbers. Session stop invalidates it.
- Clear-manual and clear-learned commands require a second phone-side confirmation. Import requires PC preview/confirmation and phone-side authoritative validation.
- Reuse v0.02.0001 TSV codec, stores, strict source priority, atomic writes, and background atomic index publication.

PC web UI:

- Device discovery/selection with phone model, name, IP, app version, and connection state.
- Dashboard with manual, self-learned, and built-in counts plus latest operation state.
- TSV choose/drop, local preview, valid/duplicate/rejected counts, first 20 valid rows, first 20 errors, explicit confirm, upload, phone validation, rebuild progress, and final result.
- Separate downloads for manual-only export and combined manual + self-learned export.
- Separate manual-clear and learned-clear controls. Each requires typing `CLEAR`, PC confirmation, then phone confirmation.
- Local operation history contains only timestamp, command, counts, success, and error category; never dictionary contents or tokens; retain at most 100 records.

Phone UI and lifecycle:

- Settings section shows inactive, waiting, pending confirmation, connected, processing, and stopped states.
- Start session, stop session, accept connection, reject connection, and revoke current session controls.
- Foreground notification remains visible during the session and provides stop plus pending request actions.
- App/process restart returns to inactive state; no permanent background connection.
- After 10 minutes without a valid management action, automatically stop the complete phone management service, close discovery/TCP sockets, invalidate the token, remove its foreground notification, and update the settings state to inactive. Discovery packets and rejected/unauthorized traffic must not reset the timer; successful authenticated status/import/export/clear requests and connection approval do reset it.

Protocol and limits:

- Plain HTTP-style phone protocol over the LAN, with a 128-bit random temporary token and increasing sequence header.
- UTF-8 TSV only; 10MB upload limit; 50,000 unique-entry limit; existing line, pinyin, candidate, and weight limits remain authoritative.
- Interrupted import never replaces existing data. Incomplete export is discarded on the PC.
- Import/export/clear operations are serialized; concurrent state-changing commands are rejected.
- No dictionary contents or tokens in logs.

Required operations:

- Status, connect, disconnect, import manual dictionary, export manual dictionary, export combined manual/self-learned dictionary, request clear manual, request clear learned.
- Export filenames include scope plus local timestamp.
- v0.02.0001 phone file-picker import/export remains available independently.

Operation flow:

1. User starts the PC helper; it opens the local webpage.
2. User enables a temporary “computer dictionary management” session on the phone.
3. PC discovers active phones and user selects the target model/name.
4. Phone displays computer name/IP and user accepts this temporary connection.
5. PC receives the automatic temporary token and initiates status/import/export/clear commands.
6. Phone validates, writes atomically, rebuilds in the background, and returns final counts/state.
7. Session stop, disconnect timeout, app restart, and explicit revoke invalidate the token.

Testing brief:

- Same-LAN Windows PC + phone: discovery, device selection, phone accept/reject, status, import variants, both exports, both clear flows, stop/restart, timeout, and app/IME responsiveness.
- Attempt missing/wrong/expired token, repeated/out-of-order sequence, stopped session, oversized body, concurrent write command, and unconfirmed clear; every case must be rejected.
- Confirm the webpage is reachable from PC localhost and is not served to LAN clients.
- Regression: phone-side file-picker import/export from v0.02.0001 remains usable without the PC helper.
- Idle timeout: after the last valid action, verify the service remains active before 10 minutes and shuts down at 10 minutes; discovery, old tokens, and commands must then fail until the user explicitly starts a new phone session.

Acceptance:

- A non-technical user can start the Windows helper, discover the active phone, approve the connection on the phone, and manage dictionaries from the local browser.
- Same-LAN wireless import/export/clear works without external services, accounts, manually entered secrets, ADB, and wired transport.
- Plaintext transport is accepted for trusted home LAN use and documented clearly.
- All phone-side validation, layer priority, atomic replacement, and background rebuild behavior remains identical to v0.02.0001.
- The management session is explicit, visible, stoppable, and off by default.

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

### P2 — Candidate Bar Leading-Syllable Single Character

Difficulty: Low-Medium
Depth: Small
Recommended implementation engineer: Claude Code
Recommended tester: Grok

Problem:

- When the user types a multi-syllable pinyin, the candidate bar shows whole-word matches for the full pinyin.
- There is no quick way to commit only the first character when the user actually wants just the leading syllable, without deleting back or paging.

Implementation brief:

- After the existing whole-word / full-pinyin candidates in the compact candidate row, append the single character for the **first syllable** of the current input as an extra trailing candidate.
- Resolve the first syllable from the current composing pinyin (26-key) or the current leading single-syllable choice (9-key), then pull its top single-character candidate.
- Tapping that trailing single character commits only that character and leaves the remaining pinyin/digits composing, so the user can continue with the rest.
- Do not disturb the existing whole-word candidate ordering; this is an additive trailing entry only.
- Only add the trailing single character when the input actually spans more than one syllable and the whole-word candidates are not already just that single character.
- Keep it consistent between 26-key and 9-key composing states as far as practical.

Testing brief for Grok:

- 26-key: type a two-syllable pinyin such as `nihao`; confirm whole-word candidates (你好…) appear, and a trailing single character for the first syllable (你) is shown after them.
- Tap the trailing single character; confirm only that character commits and the remaining pinyin keeps composing.
- Confirm single-syllable input (e.g. `ni`) does not show a redundant duplicate trailing character.
- 9-key: repeat with a multi-syllable digit sequence and confirm the trailing first-syllable character behaves the same.
- Confirm candidate paging, space/tap commit, delete, and the expand panel still work.

Acceptance:

- User can commit just the leading-syllable character directly from the candidate bar in a multi-syllable input.
- Whole-word candidate ranking is unchanged; the single character is only an additive trailing option.
- 26-key and 9-key composing both behave consistently.

### P1 — Complete Scrollable Candidate Dropdown

Status: **reported by user; record only, do not implement in v0.02.0002**

Observed problem:

- In 26-key Chinese input, typing single-syllable `ji` does not expose every available `ji` character in the candidate UI; `激` is not selectable.
- The same built-in dictionary can produce the whole-word candidate `激动`, proving `激` exists in dictionary data. This should be investigated as candidate extraction, truncation, paging, or display coverage rather than fixed by adding one special `ji -> 激` entry.

Frozen next-optimization target:

- Replace/extend the limited candidate browsing experience with a dropdown candidate panel that exposes the complete candidate result set for the current pinyin.
- The dropdown must support vertical scrolling and direct tap selection of both single characters and words.
- Compact first-row candidates may remain for fast selection, but opening the dropdown must not reuse the compact row's truncated subset.
- Apply the behavior consistently to 26-key and 9-key candidate results without changing dictionary source priority.
- Do not special-case `ji`, `激`, or `激动`; correct the shared candidate retrieval and display path for all pinyin.

Required regression checks:

- Type `ji`, open the dropdown, scroll vertically, and confirm `激` is selectable.
- Type the full pinyin for `激动` and confirm the existing whole-word candidate remains available.
- Use other high-candidate syllables such as `shi`, `yi`, and `li` to confirm later candidates are reachable rather than silently dropped.
- Verify selecting a scrolled candidate commits the correct text and closes or refreshes the panel consistently.
- Recheck candidate ranking, manual > learned > built-in priority, DEL, candidate expansion, and 26-key/9-key switching.

Acceptance:

- Every candidate returned by the authoritative lookup for the active pinyin is reachable through vertical scrolling.
- A candidate is not hidden merely because it falls outside the compact row or an internal first-page limit.
- The solution is generic and requires no per-word dictionary patch.

### P1 — Candidate Ranking And Dictionary Expansion

Status: **engineer-ready after P0 OOM repair and complete-candidate dropdown acceptance**

Problem:

- The current dictionary is large, but candidate order and corpus coverage have not been evaluated with a reproducible benchmark.
- Candidate omission, compact-row truncation, low ranking, and genuinely absent dictionary data must be measured separately before changing weights or adding entries.

Frozen scope:

- Keep the existing source priority: manual dictionary > self-learned dictionary > built-in dictionary. Ranking changes may reorder candidates only within the same source layer.
- Within one layer, rank exact full-pinyin matches before prefix matches, then use explicit weight/user frequency, then a stable deterministic tie-breaker.
- Build a checked-in benchmark containing representative single syllables, common words, multi-syllable words, 26-key prefixes, and 9-key ambiguities. Record expected presence separately from expected top-candidate order.
- Expand the built-in corpus only through a documented source/conversion pipeline. Normalize pinyin and candidate text, reject invalid rows, deduplicate identical pinyin/candidate pairs deterministically, and regenerate `pinyin_syllable_dict.txt` whenever the base dictionary changes.
- Produce before/after counts for source rows, accepted unique entries, rejected rows, duplicate rows, generated syllables, dictionary size, load time, and peak heap.
- Do not add hardcoded lookup exceptions for reported examples. A word is added only through the same corpus/import path used for all entries.
- Do not add cloud ranking, network prediction, AI prediction, tone input, or fuzzy pinyin in this task.

Required tests:

- Verify manual > learned > built-in priority and existing user-frequency behavior remain unchanged.
- Verify every benchmark presence case is reachable in the complete dropdown; verify every ranking case has deterministic ordering across process restarts.
- Recheck `ji` including `激`, `激动`, high-volume syllables `shi/yi/li`, cold-start `744824`, `64426 -> 你好`, prefix lookup, and 9-key whole-word plus per-syllable composition.
- Run the P0 memory stress sequence after rebuilding the corpus. Dictionary expansion must not restore full-index OOM or materially remove the accepted heap headroom.

Acceptance:

- The team can explain whether each benchmark failure is missing data, lookup behavior, or ranking, with no UI truncation mistaken for a dictionary defect.
- Import/conversion is reproducible from checked-in scripts and documented inputs; two runs produce identical runtime dictionaries.
- Candidate order is deterministic, source priority is preserved, and no per-word code patches are introduced.
- The expanded dictionary passes the established device memory and typing regression thresholds.

### P2 — PC-Enter To Phone Remote Input Bridge

Difficulty: Medium-High
Depth: Deep
Recommended implementation engineer: Codex or Claude Code
Recommended tester: Grok

Problem:

- The user wants to type text on the PC, press Enter, and have the same text appear in the phone's current input field through this IME.
- This is a personal-use convenience bridge for pushing PC-composed text onto the phone, not a general networking feature.

Implementation brief:

- Provide a way for the IME to receive a text payload from the PC and commit it into the phone's currently focused input field via the existing commit path.
- Build only after the v0.02.0002 PC local-web dictionary bridge is stable. Reuse its same-LAN plaintext temporary-session transport, phone approval, random token, and increasing sequence number instead of creating an ADB-specific second channel.
- Trigger semantics: one Enter on the PC side sends one text payload, and the phone commits it as a single insertion at the current cursor.
- Keep this strictly opt-in and separate from normal typing so it never interferes with on-device composing.
- This must reuse the PC-side dictionary transport rather than creating two unrelated connection systems.

Open question to settle before implementation:

- Whether the phone must be focused on a text field, and fallback behavior when it is not.

Acceptance:

- Pressing Enter on the PC reliably inserts the composed text into the phone's focused input field through the IME.
- The bridge does not disturb normal on-device typing when unused.
- Transport choice is documented and consistent with the local-first / low-permission direction.

### P2 — In-App Manual Dictionary Row Editor

Status: **planned only; assign after OOM stability, candidate completeness, and ranking work**

Frozen scope:

- Add a settings-page editor for manual-dictionary rows only. Self-learned and built-in rows remain read-only in this screen.
- Support search, add, edit, and delete for pinyin, candidate text, and weight. Show validation errors before applying changes.
- Reuse `DictionaryTsvCodec` validation and `ManualDictionaryStore`; the editor must produce data identical to importing an equivalent valid TSV file.
- Stage edits in the UI and apply them as one atomic batch. Do not rebuild the complete runtime dictionary after each keystroke or individual row edit.
- Preserve PC/file import and export. The row editor is an additional maintenance path, not a replacement.
- Require confirmation before discarding unsaved edits or deleting all visible matching rows. Do not add cloud sync or collaborative editing.

Required tests:

- Add, edit, delete, search, cancel, invalid-row rejection, duplicate handling, and atomic apply.
- Export after editing, clear manual data, then re-import the export; rows and weights must round-trip exactly.
- Verify manual > learned > built-in priority, IME responsiveness during apply, and no OOM during repeated edit/apply cycles.

Acceptance:

- A user can maintain individual manual entries entirely on the phone without editing a TSV file.
- Invalid or cancelled changes never partially replace the persisted dictionary.
- Applying a batch publishes one consistent runtime state and preserves all existing import/export behavior.

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

1. Implementation engineer updates the display version and the relevant `ChangeLog/` entry, then commits locally.
2. Implementation engineer does not need to run a full real-device pass unless specifically asked.
3. Tester creates a new `tests/v{version}_{YYYY-MM-DD}_{HHMMSS}/` folder.
4. Tester records `REPORT.md`, screenshots, UI dumps if useful, and command logs.
5. Tester updates `tests/README.md` index.
6. If test passes, commit the test archive.
7. Push to GitHub only after source + test report are both committed.

Minimum regression set:

- Launch/settings page version and dictionary status.
- Built-in app test box: cursor appears on launch, text can be entered directly inside the app, and this field is used as the default first test target before cross-app checks.
- 26-key: `ni -> 你`, candidate paging, tap/space commit, delete, long-press delete, symbols, punctuation.
- 9-key: layout toggle, `64 -> ni/mi`, tap `mi`, append digit resets choice, `94664 -> zhong`, `DEL`, `重输`, `0` empty-buffer space.
- After v0.01.0024: verify the left-side vertical pinyin list scrolls, highlights the active pinyin, and the digit grid's size never changes regardless of match count. (The candidate expand panel was deferred beyond this version and is currently planned for v0.01.0026.)
- After v0.01.0025: confirm the new in-app test box covers the main smoke path, then optionally rerun a lighter Edge pass for cross-app behavior.
- Low-priority manual follow-up only: candidate-row tap hitbox and `DEL` feel in the in-app test field, if automation remains noisy.
- After v0.01.0026: verify the candidate expand panel opens/closes cleanly and the compact candidate row still behaves normally.
- After v0.01.0027: verify partial pinyin / digit-prefix matching before full spelling (when that version lands).
- Performance-sensitive changes only: repeat cold-process dictionary load measurement.
- After v0.02.0001: verify valid/invalid/duplicate import, strict manual priority, combined export/re-import, layer-specific clear behavior, and responsiveness during background index rebuild.

## 10. Known Limitations

- Frequent typing crashes are now attributed to repeatable runtime dictionary-index OOM, not an unknown random trigger. Full prefix-index reconstruction after self-learning/import can exhaust the 402MB heap; this is the P0 blocker above.
- Single-syllable candidate display can currently omit later dictionary characters. Confirmed example: `ji` does not expose `激`, although the dictionary can produce `激动`. This is recorded for the complete scrollable candidate dropdown optimization and must not be patched as a one-word exception.
- Dictionary cold-start loading is not ideal, but not the current priority; typing usability comes first.
- 9-key mode currently has no English input path.
- Prefix pinyin matching is already on `origin/main` as `v0.01.0027`; it was manually verified by the user, but no formal `tests/` archive exists for that version.
- `v0.01.0028` established the always-syllable composition path and is already pushed; `v0.01.0029` adds direct whole-word recall while preserving that explicit per-syllable path.
- No fuzzy pinyin; intentionally deferred because the user can spell pinyin and wants exact/prefix behavior first.
- No mistyped-pinyin correction.
- No tone support.
- Manual dictionary import/export exists in v0.02.0001; there is no in-app row-by-row dictionary editor yet.
- No detailed privacy page beyond the local-only note.
- No cloud sync, networking, handwriting, speech input, or AI prediction planned for early versions.

## 11. Useful References

- Update logs: `ChangeLog/` (new files should use `v{version}-{YYYY-MM-DD}.md`; legacy summary file is `ChangeLog/CHANGELOG.md`; current latest file is `ChangeLog/v0.02.0002-2026-07-11.md`)
- Detailed feature behavior: `docs/FEATURE_DETAILS.md`
- Test archive rules: `tests/README.md`
- Environment setup: `ENVIRONMENT_SETUP.md`
- Latest archived device report: `tests/v0.02.0002_2026-07-11_181702/REPORT.md`
- Failed dictionary-loading experiment branch: `codex-experiment-dict-load-v0.01.0023`
