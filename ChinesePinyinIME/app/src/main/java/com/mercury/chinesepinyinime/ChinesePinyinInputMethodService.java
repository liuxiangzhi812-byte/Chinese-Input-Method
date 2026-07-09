package com.mercury.chinesepinyinime;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ChinesePinyinInputMethodService extends InputMethodService {
    private static final int DELETE_LONG_PRESS_DELAY_MS = 500;
    private static final int DELETE_REPEAT_INTERVAL_MS = 80;
    private static final String SHIFT_LABEL_OFF = "⇧";
    private static final String SHIFT_LABEL_ON = "▲";

    private enum KeyboardLayoutMode { QWERTY_26, T9_9 }

    private final StringBuilder composingPinyin = new StringBuilder();
    private final StringBuilder composingDigits = new StringBuilder();
    private final List<T9CommittedSegment> t9CommittedSegments = new ArrayList<>();
    private final Handler deleteRepeatHandler = new Handler(Looper.getMainLooper());
    private final PinyinDictionary pinyinDictionary = PinyinDictionary.getInstance();
    private final CandidatePager candidatePager = new CandidatePager();
    private KeyboardLayoutMode keyboardLayoutMode = KeyboardLayoutMode.QWERTY_26;
    private boolean chineseMode = true;
    private boolean symbolMode = false;
    private boolean shiftOneShot = false;
    private boolean candidatePanelExpanded = false;
    private int candidatePageIndex = 0;
    private String t9ActivePinyin;
    private EditorInfo currentEditorInfo;
    private int candidateListContainerWidthPx = 0;
    private int candidateExpandedContainerWidthPx = 0;
    private boolean deleteRepeatStarted;
    private Runnable deleteRepeatRunnable;
    private LinearLayout candidateBar;
    private LinearLayout candidateListContainer;
    private LinearLayout candidateExpandedList;
    private LinearLayout t9PinyinChoiceList;
    private TextView candidateExpandToggle;
    private TextView candidatePagePrev;
    private TextView candidatePageNext;
    private TextView keyboardStatus;
    private Button modeButton;
    private Button shiftButton;
    private Button symbolToggleButton;
    private LinearLayout letterKeyboardSection;
    private LinearLayout t9KeyboardSection;
    private LinearLayout symbolKeyboardSection;
    private LinearLayout symbolKeyboardEnSection;
    private LinearLayout symbolKeyboardZhSection;
    private View candidateExpandedPanel;
    private View keyboardRootView;
    private ViewTreeObserver.OnGlobalLayoutListener candidateWidthLayoutListener;
    private View.OnAttachStateChangeListener candidateWidthAttachListener;
    private String t9SelectedSyllablePinyin;
    private String t9DeferredDigitsAfterSelection = "";
    private int t9SelectedSyllableDigitCount;

    @Override
    public View onCreateInputView() {
        View keyboardView = getLayoutInflater().inflate(R.layout.keyboard_view, null);
        candidateBar = keyboardView.findViewById(R.id.candidate_bar);
        candidateListContainer = keyboardView.findViewById(R.id.candidate_list_container);
        candidateExpandedList = keyboardView.findViewById(R.id.candidate_expanded_list);
        t9PinyinChoiceList = keyboardView.findViewById(R.id.t9_pinyin_choice_list);
        candidateExpandToggle = keyboardView.findViewById(R.id.candidate_expand_toggle);
        candidatePagePrev = keyboardView.findViewById(R.id.candidate_page_prev);
        candidatePageNext = keyboardView.findViewById(R.id.candidate_page_next);
        keyboardStatus = keyboardView.findViewById(R.id.keyboard_status);
        modeButton = keyboardView.findViewById(R.id.key_mode);
        shiftButton = keyboardView.findViewById(R.id.key_shift);
        symbolToggleButton = keyboardView.findViewById(R.id.key_symbol_toggle);
        letterKeyboardSection = keyboardView.findViewById(R.id.letter_keyboard_section);
        t9KeyboardSection = keyboardView.findViewById(R.id.t9_keyboard_section);
        symbolKeyboardSection = keyboardView.findViewById(R.id.symbol_keyboard_section);
        symbolKeyboardEnSection = keyboardView.findViewById(R.id.symbol_keyboard_en_section);
        symbolKeyboardZhSection = keyboardView.findViewById(R.id.symbol_keyboard_zh_section);
        candidateExpandedPanel = keyboardView.findViewById(R.id.candidate_expanded_panel);
        bindKeyboardButtons(keyboardView);
        bindCandidatePageButtons();
        bindCandidateListContainerWidthListener(keyboardView);
        refreshKeyboardLayoutModeFromPreferences();
        updateKeyboardLayout();
        updateKeyboardStatus();
        pinyinDictionary.loadAsync(getApplicationContext(), this::handleDictionaryLoaded);
        return keyboardView;
    }

    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info, restarting);
        refreshKeyboardLayoutModeFromPreferences();
    }

    private void refreshKeyboardLayoutModeFromPreferences() {
        boolean wantsT9 = KeyboardLayoutPreferences.isT9Enabled(getApplicationContext());
        KeyboardLayoutMode desiredMode = wantsT9 ? KeyboardLayoutMode.T9_9 : KeyboardLayoutMode.QWERTY_26;
        if (desiredMode == keyboardLayoutMode) {
            return;
        }
        keyboardLayoutMode = desiredMode;
        symbolMode = false;
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9) {
            chineseMode = true;
        }
        clearComposingState();
        updateKeyboardLayout();
    }

    @Override
    public void onDestroy() {
        removeCandidateListContainerWidthListener();
        stopDeleteRepeat();
        UserFrequencyStore.getInstance().flush();
        UserDictionaryStore.getInstance().flush();
        super.onDestroy();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        currentEditorInfo = attribute;
        clearComposingState();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        stopDeleteRepeat();
        clearComposingState();
        UserFrequencyStore.getInstance().flush();
        UserDictionaryStore.getInstance().flush();
    }

    private void bindKeyboardButtons(View view) {
        if ("action:delete".equals(view.getTag())) {
            bindDeleteKeyLongPress(view);
            return;
        }

        if (view instanceof Button) {
            view.setOnClickListener(this::handleKeyClick);
            return;
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                bindKeyboardButtons(group.getChildAt(i));
            }
        }
    }

    private void bindDeleteKeyLongPress(View deleteView) {
        deleteView.setOnTouchListener((view, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startDeleteRepeatCountdown();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    boolean wasRepeating = deleteRepeatStarted;
                    stopDeleteRepeat();
                    if (!wasRepeating) {
                        InputConnection inputConnection = getCurrentInputConnection();
                        if (inputConnection != null) {
                            handleDelete(inputConnection);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        });
    }

    private void startDeleteRepeatCountdown() {
        deleteRepeatStarted = false;
        deleteRepeatRunnable = new Runnable() {
            @Override
            public void run() {
                deleteRepeatStarted = true;
                InputConnection inputConnection = getCurrentInputConnection();
                if (inputConnection != null) {
                    handleDelete(inputConnection);
                }
                deleteRepeatHandler.postDelayed(this, DELETE_REPEAT_INTERVAL_MS);
            }
        };
        deleteRepeatHandler.postDelayed(deleteRepeatRunnable, DELETE_LONG_PRESS_DELAY_MS);
    }

    private void stopDeleteRepeat() {
        if (deleteRepeatRunnable != null) {
            deleteRepeatHandler.removeCallbacks(deleteRepeatRunnable);
            deleteRepeatRunnable = null;
        }
        deleteRepeatStarted = false;
    }

    private void handleKeyClick(View view) {
        Object tagObject = view.getTag();
        if (!(tagObject instanceof String)) {
            return;
        }

        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null) {
            return;
        }

        String tag = (String) tagObject;
        if (tag.startsWith("key:")) {
            handleLetterKey(inputConnection, tag.substring(4));
            return;
        }

        if (tag.startsWith("sym:")) {
            handleSymbolKey(inputConnection, tag.substring(4));
            return;
        }

        if (tag.startsWith("punc:")) {
            handlePunctuationKey(inputConnection, tag.substring(5));
            return;
        }

        if (tag.startsWith("t9:")) {
            handleT9DigitKey(tag.substring(3));
            return;
        }

        switch (tag) {
            case "action:space":
                handleSpace(inputConnection);
                break;
            case "action:delete":
                handleDelete(inputConnection);
                break;
            case "action:enter":
                handleEnter(inputConnection);
                break;
            case "action:mode":
                toggleInputMode();
                break;
            case "action:shift":
                toggleShiftOneShot();
                break;
            case "action:symbol":
                toggleSymbolMode();
                break;
            case "action:retype":
                clearComposingState();
                break;
            default:
                break;
        }
    }

    private void handleT9DigitKey(String digit) {
        if (isT9SyllableSelectionActive()) {
            t9DeferredDigitsAfterSelection += digit;
        } else {
            composingDigits.append(digit);
        }
        t9ActivePinyin = null;
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void handleSymbolKey(InputConnection inputConnection, String halfWidthSymbol) {
        clearComposingState();
        inputConnection.commitText(halfWidthSymbol, 1);
    }

    private void handlePunctuationKey(InputConnection inputConnection, String punctuation) {
        clearComposingState();
        inputConnection.commitText(punctuation, 1);
    }

    private void toggleSymbolMode() {
        symbolMode = !symbolMode;
        resetShiftOneShot();
        clearComposingState();
        updateKeyboardLayout();
    }

    private void updateKeyboardLayout() {
        boolean isT9 = keyboardLayoutMode == KeyboardLayoutMode.T9_9;
        boolean showExpandedCandidatePanel =
                candidatePanelExpanded && isComposingActive() && !symbolMode;
        if (letterKeyboardSection != null) {
            letterKeyboardSection.setVisibility(!isT9 && !symbolMode && !showExpandedCandidatePanel
                    ? View.VISIBLE
                    : View.GONE);
        }
        if (t9KeyboardSection != null) {
            t9KeyboardSection.setVisibility(isT9 && !symbolMode && !showExpandedCandidatePanel
                    ? View.VISIBLE
                    : View.GONE);
        }
        if (symbolKeyboardSection != null) {
            symbolKeyboardSection.setVisibility(symbolMode ? View.VISIBLE : View.GONE);
        }
        if (candidateExpandedPanel != null) {
            candidateExpandedPanel.setVisibility(showExpandedCandidatePanel ? View.VISIBLE : View.GONE);
        }
        if (symbolKeyboardEnSection != null) {
            symbolKeyboardEnSection.setVisibility(symbolMode && !chineseMode
                    ? View.VISIBLE
                    : View.GONE);
        }
        if (symbolKeyboardZhSection != null) {
            symbolKeyboardZhSection.setVisibility(symbolMode && chineseMode
                    ? View.VISIBLE
                    : View.GONE);
        }
        if (symbolToggleButton != null) {
            symbolToggleButton.setText(symbolMode ? (isT9 ? "9键" : "ABC") : "123");
        }
        if (modeButton != null) {
            modeButton.setVisibility(isT9 ? View.GONE : View.VISIBLE);
        }
        if (shiftButton != null) {
            shiftButton.setVisibility(chineseMode || symbolMode ? View.GONE : View.VISIBLE);
        }
        updateShiftKeyAppearance();
    }

    private void toggleShiftOneShot() {
        if (chineseMode || symbolMode) {
            return;
        }
        shiftOneShot = !shiftOneShot;
        updateShiftKeyAppearance();
    }

    private void updateShiftKeyAppearance() {
        if (shiftButton != null) {
            shiftButton.setText(shiftOneShot ? SHIFT_LABEL_ON : SHIFT_LABEL_OFF);
            shiftButton.setBackgroundResource(shiftOneShot
                    ? R.drawable.keyboard_shift_active_background
                    : R.drawable.keyboard_key_background);
        }
        updateLetterKeyLabels();
    }

    private void updateLetterKeyLabels() {
        if (letterKeyboardSection == null) {
            return;
        }
        boolean uppercase = !chineseMode && shiftOneShot;
        updateLetterKeyLabelsInGroup(letterKeyboardSection, uppercase);
    }

    private void updateLetterKeyLabelsInGroup(ViewGroup group, boolean uppercase) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof ViewGroup) {
                updateLetterKeyLabelsInGroup((ViewGroup) child, uppercase);
            } else if (child instanceof Button) {
                Object tag = child.getTag();
                if (tag instanceof String) {
                    String tagString = (String) tag;
                    if (tagString.startsWith("key:")) {
                        String letter = tagString.substring(4);
                        ((Button) child).setText(uppercase
                                ? letter.toUpperCase(Locale.ROOT)
                                : letter);
                    }
                }
            }
        }
    }

    private void resetShiftOneShot() {
        shiftOneShot = false;
        updateShiftKeyAppearance();
    }

    private void handleLetterKey(InputConnection inputConnection, String letter) {
        if (chineseMode) {
            composingPinyin.append(letter);
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        String output = letter;
        if (shiftOneShot && letter.length() == 1 && Character.isLetter(letter.charAt(0))) {
            output = letter.toUpperCase(Locale.ROOT);
            resetShiftOneShot();
        }
        inputConnection.commitText(output, 1);
    }

    private void handleSpace(InputConnection inputConnection) {
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9
                && hasCommittedT9Segments()
                && composingDigits.length() == 0
                && !isT9SyllableSelectionActive()) {
            commitT9ComposedText(inputConnection);
            return;
        }

        if (isComposingActive()) {
            commitFirstCandidate(inputConnection);
            return;
        }

        inputConnection.commitText(" ", 1);
    }

    private void handleDelete(InputConnection inputConnection) {
        if (candidatePanelExpanded) {
            candidatePanelExpanded = false;
        }

        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9 && isT9SyllableSelectionActive()) {
            restoreFullDigitsFromSelectedSyllable();
            if (composingDigits.length() > 0) {
                composingDigits.deleteCharAt(composingDigits.length() - 1);
            }
            t9ActivePinyin = null;
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9 && composingDigits.length() > 0) {
            composingDigits.deleteCharAt(composingDigits.length() - 1);
            t9ActivePinyin = null;
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9 && hasCommittedT9Segments()) {
            t9CommittedSegments.remove(t9CommittedSegments.size() - 1);
            t9ActivePinyin = null;
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        if (chineseMode && composingPinyin.length() > 0) {
            composingPinyin.deleteCharAt(composingPinyin.length() - 1);
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        inputConnection.deleteSurroundingText(1, 0);
    }

    private void handleEnter(InputConnection inputConnection) {
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9
                && hasCommittedT9Segments()
                && composingDigits.length() == 0
                && !isT9SyllableSelectionActive()) {
            commitT9ComposedText(inputConnection);
            return;
        }

        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9 && composingDigits.length() > 0) {
            commitFirstCandidate(inputConnection);
            return;
        }

        if (chineseMode && composingPinyin.length() > 0) {
            commitComposingPinyin(inputConnection);
            return;
        }

        if (isMultiLineField()) {
            inputConnection.commitText("\n", 1);
            return;
        }

        int imeAction = getImeAction();
        if (shouldPerformEditorAction(imeAction)) {
            inputConnection.performEditorAction(imeAction);
            return;
        }

        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private int getImeAction() {
        if (currentEditorInfo == null) {
            return EditorInfo.IME_ACTION_UNSPECIFIED;
        }
        return currentEditorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
    }

    private boolean isMultiLineField() {
        if (currentEditorInfo == null) {
            return false;
        }
        return (currentEditorInfo.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    private boolean shouldPerformEditorAction(int imeAction) {
        return imeAction != EditorInfo.IME_ACTION_NONE
                && imeAction != EditorInfo.IME_ACTION_UNSPECIFIED;
    }

    private void toggleInputMode() {
        chineseMode = !chineseMode;
        resetShiftOneShot();
        clearComposingState();
        updateKeyboardLayout();
    }

    private void commitComposingPinyin(InputConnection inputConnection) {
        inputConnection.commitText(composingPinyin.toString(), 1);
        composingPinyin.setLength(0);
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void commitFirstCandidate(InputConnection inputConnection) {
        String[] pageCandidates = getCandidatesForCurrentPage();
        if (pageCandidates.length == 0) {
            return;
        }
        commitCandidateInternal(inputConnection, pageCandidates[0]);
    }

    private void commitCandidate(String candidate) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null || candidate.isEmpty()) {
            return;
        }
        commitCandidateInternal(inputConnection, candidate);
    }

    private void commitCandidateInternal(InputConnection inputConnection, String candidate) {
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9
                && hasCommittedT9Segments()
                && composingDigits.length() == 0
                && !isT9SyllableSelectionActive()) {
            commitT9ComposedText(inputConnection);
            return;
        }

        if (shouldAppendT9CandidateToComposition()) {
            appendT9CandidateToComposition(candidate);
            return;
        }

        String pinyin = resolvePinyinForLearning();
        inputConnection.commitText(candidate, 1);
        if (pinyin != null && !pinyin.isEmpty()) {
            UserFrequencyStore.getInstance().recordSelection(pinyin, candidate);
        }
        clearComposingState();
    }

    private String resolvePinyinForLearning() {
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9) {
            return getActiveResolvedPinyinForDigits();
        }
        return getResolvedPinyinForCurrentInput();
    }

    /**
     * The pinyin currently driving 9-key candidates: the user's explicit choice
     * from the pinyin-choice bar if they tapped one, otherwise the dictionary's
     * default pick for the current digit buffer.
     */
    private String getActiveResolvedPinyinForDigits() {
        if (isT9SyllableSelectionActive()) {
            return t9SelectedSyllablePinyin;
        }
        if (t9ActivePinyin != null && !isT9ManualCompositionMode()) {
            return t9ActivePinyin;
        }
        String digits = composingDigits.toString();
        String exactPinyin = pinyinDictionary.resolveBestPinyinForDigits(digits);
        if (exactPinyin != null) {
            return exactPinyin;
        }
        if (hasCommittedT9Segments() || shouldUseLeadingSyllableFallback(digits)) {
            return pinyinDictionary.resolveBestLeadingSingleSyllableForDigits(digits);
        }
        return pinyinDictionary.resolveBestPinyinForDigitPrefix(digits);
    }

    private String getResolvedPinyinForCurrentInput() {
        String pinyinInput = composingPinyin.toString();
        String[] exactCandidates = pinyinDictionary.getCandidates(pinyinInput);
        if (exactCandidates != null) {
            return pinyinInput;
        }
        return pinyinDictionary.resolveBestPinyinForPrefix(pinyinInput);
    }

    private void selectPinyinChoice(T9PinyinChoice choice) {
        if (choice == null) {
            return;
        }
        if (choice.wholeBufferMatch && !isT9ManualCompositionMode()) {
            t9ActivePinyin = choice.pinyin;
            clearT9SyllableSelection();
        } else {
            String fullDigits = getVisibleDigitsForT9Choices();
            t9SelectedSyllablePinyin = choice.pinyin;
            t9SelectedSyllableDigitCount = Math.min(choice.consumedDigitsCount, fullDigits.length());
            t9DeferredDigitsAfterSelection = fullDigits.substring(t9SelectedSyllableDigitCount);
            composingDigits.setLength(0);
            composingDigits.append(fullDigits, 0, t9SelectedSyllableDigitCount);
            t9ActivePinyin = null;
        }
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private boolean isComposingActive() {
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9) {
            return composingDigits.length() > 0 || hasCommittedT9Segments();
        }
        return chineseMode && composingPinyin.length() > 0;
    }

    private void clearComposingState() {
        composingPinyin.setLength(0);
        composingDigits.setLength(0);
        t9CommittedSegments.clear();
        t9ActivePinyin = null;
        clearT9SyllableSelection();
        candidatePanelExpanded = false;
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void goToPreviousCandidatePage() {
        if (candidatePageIndex > 0) {
            candidatePageIndex--;
            updateKeyboardStatus();
        }
    }

    private void goToNextCandidatePage() {
        if (candidatePager.hasNextPage(candidatePageIndex)) {
            candidatePageIndex++;
            updateKeyboardStatus();
        }
    }

    private void updateKeyboardStatus() {
        if (keyboardStatus != null) {
            if (keyboardLayoutMode == KeyboardLayoutMode.T9_9) {
                keyboardStatus.setText(buildT9KeyboardStatusText());
            } else if (chineseMode) {
                String pinyin = composingPinyin.length() > 0 ? composingPinyin.toString() : "";
                keyboardStatus.setText("中文 " + pinyin);
            } else {
                keyboardStatus.setText("English");
            }
        }

        if (modeButton != null) {
            modeButton.setText(chineseMode ? "ZH" : "EN");
        }

        if (!isComposingActive()) {
            candidatePanelExpanded = false;
        }

        updateKeyboardLayout();
        updateT9PinyinChoiceList();
        updateCandidateBar();
    }

    /**
     * Populates the fixed-width vertical pinyin-choice column to the left of the
     * 9-key digit grid. The column itself is never hidden/shown (see
     * keyboard_view.xml comment) - only its contents change, so the digit grid
     * next to it never resizes. Empty (no children) whenever 9-key isn't active,
     * the digit buffer is empty, or the digit sequence is unambiguous.
     */
    private void updateT9PinyinChoiceList() {
        if (t9PinyinChoiceList == null) {
            return;
        }

        if (keyboardLayoutMode != KeyboardLayoutMode.T9_9 || getVisibleDigitsForT9Choices().isEmpty()) {
            t9PinyinChoiceList.removeAllViews();
            return;
        }

        List<T9PinyinChoice> choices = getMatchingPinyinChoicesForDigits();
        if (choices.size() <= 1) {
            t9PinyinChoiceList.removeAllViews();
            return;
        }

        T9PinyinChoice activeChoice = getActiveT9PinyinChoice();
        t9PinyinChoiceList.removeAllViews();
        for (T9PinyinChoice choice : choices) {
            boolean active = activeChoice != null
                    && choice.pinyin.equals(activeChoice.pinyin)
                    && choice.consumedDigitsCount == activeChoice.consumedDigitsCount
                    && choice.wholeBufferMatch == activeChoice.wholeBufferMatch;
            t9PinyinChoiceList.addView(createPinyinChoiceListItem(choice, active));
        }
    }

    private List<T9PinyinChoice> getMatchingPinyinChoicesForDigits() {
        String digits = getVisibleDigitsForT9Choices();
        if (digits.isEmpty()) {
            return new ArrayList<>();
        }

        List<T9PinyinChoice> choices = new ArrayList<>();
        if (isT9ManualCompositionMode()) {
            addLeadingSyllableChoices(choices, digits);
            return choices;
        }

        List<String> exactMatches = pinyinDictionary.getPinyinKeysForDigits(digits);
        if (!exactMatches.isEmpty()) {
            addChoicesForPinyinKeys(choices, exactMatches, digits.length(), true);
            return choices;
        }

        addLeadingSyllableChoices(choices, digits);
        if (!choices.isEmpty()) {
            return choices;
        }

        List<String> prefixMatches = pinyinDictionary.getPinyinKeysForDigitPrefix(digits);
        if (!prefixMatches.isEmpty()) {
            addChoicesForPinyinKeys(choices, prefixMatches, digits.length(), true);
        }
        return choices;
    }

    private TextView createPinyinChoiceListItem(T9PinyinChoice choice, boolean active) {
        TextView choiceView = new TextView(this);
        choiceView.setText(choice.pinyin);
        choiceView.setGravity(Gravity.CENTER);
        choiceView.setMaxLines(1);
        choiceView.setEllipsize(TextUtils.TruncateAt.END);
        choiceView.setTextColor(0xFF20242C);
        choiceView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        choiceView.setBackgroundResource(active
                ? R.drawable.keyboard_shift_active_background
                : R.drawable.keyboard_key_background);

        float density = getResources().getDisplayMetrics().density;
        int marginPx = (int) (3 * density);
        int heightPx = (int) (40 * density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, heightPx);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        choiceView.setLayoutParams(params);
        choiceView.setOnClickListener(view -> selectPinyinChoice(choice));
        return choiceView;
    }

    private void updateCandidateBar() {
        if (candidateBar == null || candidateListContainer == null) {
            return;
        }

        boolean showCandidates = isComposingActive();
        candidateBar.setVisibility(showCandidates ? View.VISIBLE : View.GONE);
        if (!showCandidates) {
            candidatePanelExpanded = false;
            candidateListContainer.removeAllViews();
            clearExpandedCandidatePanel();
            updateCandidatePageButtons(false, false);
            updateCandidateExpandToggle();
            return;
        }

        String[] allCandidates = getCandidatesForComposing();
        recomputeCandidatePages(allCandidates);
        candidatePageIndex = candidatePager.clampPageIndex(candidatePageIndex);

        int pageStart = candidatePager.getPageStart(candidatePageIndex);
        int pageEnd = candidatePager.getPageEnd(candidatePageIndex, allCandidates.length);

        candidateListContainer.removeAllViews();
        for (int i = pageStart; i < pageEnd; i++) {
            candidateListContainer.addView(createCandidateView(allCandidates[i]));
        }

        updateCandidatePageButtons(
                candidatePager.hasPreviousPage(candidatePageIndex),
                candidatePager.hasNextPage(candidatePageIndex));
        updateCandidateExpandToggle();
        updateExpandedCandidatePanel(allCandidates);
    }

    private void recomputeCandidatePages(String[] allCandidates) {
        int availableWidth = candidateListContainerWidthPx > 0
                ? candidateListContainerWidthPx
                : getResources().getDisplayMetrics().widthPixels;
        candidatePager.recompute(allCandidates, availableWidth, this::measureCandidateViewWidth);
    }

    private int measureCandidateViewWidth(String text) {
        TextView measuringView = createCandidateView(text);
        measuringView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        LinearLayout.MarginLayoutParams params =
                (LinearLayout.MarginLayoutParams) measuringView.getLayoutParams();
        return measuringView.getMeasuredWidth() + params.leftMargin + params.rightMargin;
    }

    private int measureExpandedCandidateViewWidth(String text) {
        TextView measuringView = createExpandedCandidateView(text);
        measuringView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        LinearLayout.MarginLayoutParams params =
                (LinearLayout.MarginLayoutParams) measuringView.getLayoutParams();
        return measuringView.getMeasuredWidth() + params.leftMargin + params.rightMargin;
    }

    private TextView createCandidateView(String text) {
        TextView candidateView = new TextView(this, null, 0, R.style.CandidateText);
        candidateView.setText(text);
        int marginPx = (int) (3 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        candidateView.setLayoutParams(params);
        candidateView.setOnClickListener(view -> commitCandidate(text));
        return candidateView;
    }

    private TextView createExpandedCandidateView(String text) {
        TextView candidateView = new TextView(this, null, 0, R.style.CandidatePanelText);
        candidateView.setText(text);
        int marginPx = (int) (3 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(marginPx, marginPx, marginPx, marginPx);
        candidateView.setLayoutParams(params);
        candidateView.setOnClickListener(view -> commitCandidate(text));
        return candidateView;
    }

    private void updateCandidateExpandToggle() {
        if (candidateExpandToggle == null) {
            return;
        }
        if (!isComposingActive()) {
            candidateExpandToggle.setVisibility(View.INVISIBLE);
            return;
        }
        candidateExpandToggle.setVisibility(View.VISIBLE);
        candidateExpandToggle.setText(candidatePanelExpanded ? "▾" : "▴");
    }

    private void updateExpandedCandidatePanel(String[] allCandidates) {
        if (candidateExpandedList == null) {
            return;
        }

        if (!candidatePanelExpanded || symbolMode || !isComposingActive()) {
            clearExpandedCandidatePanel();
            return;
        }

        candidateExpandedList.removeAllViews();
        int availableWidth = candidateExpandedContainerWidthPx > 0
                ? candidateExpandedContainerWidthPx
                : getResources().getDisplayMetrics().widthPixels;
        LinearLayout currentRow = createExpandedCandidateRow();
        int usedWidth = 0;

        for (String candidate : allCandidates) {
            int candidateWidth = measureExpandedCandidateViewWidth(candidate);
            if (usedWidth + candidateWidth > availableWidth && currentRow.getChildCount() > 0) {
                candidateExpandedList.addView(currentRow);
                currentRow = createExpandedCandidateRow();
                usedWidth = 0;
            }
            currentRow.addView(createExpandedCandidateView(candidate));
            usedWidth += candidateWidth;
        }

        if (currentRow.getChildCount() > 0) {
            candidateExpandedList.addView(currentRow);
        }
    }

    private LinearLayout createExpandedCandidateRow() {
        LinearLayout row = new LinearLayout(this);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        row.setOrientation(LinearLayout.HORIZONTAL);
        return row;
    }

    private void clearExpandedCandidatePanel() {
        if (candidateExpandedList != null) {
            candidateExpandedList.removeAllViews();
        }
    }

    private void updateCandidatePageButtons(boolean hasPrevPage, boolean hasNextPage) {
        if (candidatePagePrev != null) {
            candidatePagePrev.setVisibility(hasPrevPage ? View.VISIBLE : View.INVISIBLE);
        }
        if (candidatePageNext != null) {
            candidatePageNext.setVisibility(hasNextPage ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void bindCandidatePageButtons() {
        if (candidatePagePrev != null) {
            candidatePagePrev.setOnClickListener(view -> goToPreviousCandidatePage());
        }
        if (candidatePageNext != null) {
            candidatePageNext.setOnClickListener(view -> goToNextCandidatePage());
        }
        if (candidateExpandToggle != null) {
            candidateExpandToggle.setOnClickListener(view -> toggleCandidatePanel());
        }
    }

    private void toggleCandidatePanel() {
        if (!isComposingActive()) {
            return;
        }
        candidatePanelExpanded = !candidatePanelExpanded;
        updateKeyboardStatus();
    }

    private void bindCandidateListContainerWidthListener(View keyboardView) {
        removeCandidateListContainerWidthListener();
        keyboardRootView = keyboardView;
        candidateWidthLayoutListener = () -> {
            if (candidateListContainer == null) {
                return;
            }
            boolean widthChanged = false;
            int newWidth = candidateListContainer.getWidth();
            if (newWidth > 0 && newWidth != candidateListContainerWidthPx) {
                candidateListContainerWidthPx = newWidth;
                widthChanged = true;
            }
            int expandedWidth = candidateExpandedPanel != null ? candidateExpandedPanel.getWidth() : 0;
            if (expandedWidth > 0 && expandedWidth != candidateExpandedContainerWidthPx) {
                candidateExpandedContainerWidthPx = expandedWidth;
                widthChanged = true;
            }
            if (widthChanged) {
                updateCandidateBar();
            }
        };
        keyboardView.getViewTreeObserver().addOnGlobalLayoutListener(candidateWidthLayoutListener);
        candidateWidthAttachListener = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View view) {
            }

            @Override
            public void onViewDetachedFromWindow(View view) {
                removeCandidateListContainerWidthListener();
            }
        };
        keyboardView.addOnAttachStateChangeListener(candidateWidthAttachListener);
    }

    private void removeCandidateListContainerWidthListener() {
        if (keyboardRootView != null && candidateWidthLayoutListener != null) {
            ViewTreeObserver viewTreeObserver = keyboardRootView.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.removeOnGlobalLayoutListener(candidateWidthLayoutListener);
            }
        }
        if (keyboardRootView != null && candidateWidthAttachListener != null) {
            keyboardRootView.removeOnAttachStateChangeListener(candidateWidthAttachListener);
        }
        keyboardRootView = null;
        candidateWidthLayoutListener = null;
        candidateWidthAttachListener = null;
    }

    private String[] getCandidatesForComposing() {
        if (keyboardLayoutMode == KeyboardLayoutMode.T9_9) {
            if (composingDigits.length() == 0 && hasCommittedT9Segments()) {
                return new String[]{buildT9ComposedText()};
            }
            return getCandidatesForCurrentDigits();
        }
        String pinyin = composingPinyin.toString();
        String[] candidates = pinyinDictionary.getCandidates(pinyin);
        if (candidates != null) {
            return candidates;
        }
        candidates = pinyinDictionary.getCandidatesForPinyinPrefix(pinyin);
        if (candidates == null) {
            return new String[]{pinyin};
        }
        return candidates;
    }

    private String[] getCandidatesForCurrentDigits() {
        String digits = composingDigits.toString();
        String resolvedPinyin = getActiveResolvedPinyinForDigits();
        if (resolvedPinyin == null) {
            return new String[]{digits};
        }

        if (shouldAppendT9CandidateToComposition()) {
            String[] syllableCandidates = pinyinDictionary.getCandidates(resolvedPinyin);
            if (syllableCandidates == null) {
                return new String[]{digits};
            }
            return syllableCandidates;
        }

        String[] exactMatches = pinyinDictionary.getPinyinKeysForDigits(digits).isEmpty()
                ? null
                : pinyinDictionary.getCandidates(resolvedPinyin);
        String[] candidates = exactMatches;
        if (candidates == null && resolvedPinyin != null) {
            candidates = pinyinDictionary.getCandidatesForDigitPrefix(digits);
        }
        if (candidates == null) {
            return new String[]{digits};
        }
        return candidates;
    }

    private String[] getCandidatesForCurrentPage() {
        String[] allCandidates = getCandidatesForComposing();
        recomputeCandidatePages(allCandidates);
        candidatePageIndex = candidatePager.clampPageIndex(candidatePageIndex);
        return candidatePager.getCandidatesForPage(allCandidates, candidatePageIndex);
    }

    private String buildT9KeyboardStatusText() {
        StringBuilder status = new StringBuilder("中文");
        String composedText = buildT9ComposedText();
        String visibleDigits = getVisibleDigitsForT9Choices();
        if (!composedText.isEmpty()) {
            status.append(' ').append(composedText);
        }
        if (!visibleDigits.isEmpty()) {
            status.append(' ').append(visibleDigits);
        }
        return status.toString();
    }

    private boolean shouldUseLeadingSyllableFallback(String digits) {
        if (digits == null || digits.isEmpty()) {
            return false;
        }
        if (!pinyinDictionary.getPinyinKeysForDigits(digits).isEmpty()) {
            return false;
        }
        return !pinyinDictionary.getLeadingSingleSyllablePinyinKeys(digits).isEmpty();
    }

    private boolean shouldAppendT9CandidateToComposition() {
        if (keyboardLayoutMode != KeyboardLayoutMode.T9_9 || composingDigits.length() == 0) {
            return false;
        }
        if (isT9SyllableSelectionActive() || hasCommittedT9Segments()) {
            return true;
        }
        return shouldUseLeadingSyllableFallback(composingDigits.toString());
    }

    private void appendT9CandidateToComposition(String candidate) {
        T9PinyinChoice activeChoice = getActiveT9PinyinChoice();
        if (activeChoice == null || candidate == null || candidate.isEmpty()) {
            return;
        }

        t9CommittedSegments.add(new T9CommittedSegment(activeChoice.pinyin, candidate));
        String remainingDigits = getRemainingDigitsAfterActiveChoice(activeChoice);
        clearT9SyllableSelection();
        composingDigits.setLength(0);
        composingDigits.append(remainingDigits);
        t9ActivePinyin = null;
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void commitT9ComposedText(InputConnection inputConnection) {
        String candidate = buildT9ComposedText();
        String pinyin = buildT9ComposedPinyin();
        if (candidate.isEmpty() || pinyin.isEmpty()) {
            return;
        }

        inputConnection.commitText(candidate, 1);
        pinyinDictionary.addUserCandidate(pinyin, candidate);
        UserFrequencyStore.getInstance().recordSelection(pinyin, candidate);
        clearComposingState();
    }

    private String buildT9ComposedText() {
        StringBuilder builder = new StringBuilder();
        for (T9CommittedSegment segment : t9CommittedSegments) {
            builder.append(segment.candidate);
        }
        return builder.toString();
    }

    private String buildT9ComposedPinyin() {
        StringBuilder builder = new StringBuilder();
        for (T9CommittedSegment segment : t9CommittedSegments) {
            builder.append(segment.pinyin);
        }
        return builder.toString();
    }

    private boolean hasCommittedT9Segments() {
        return !t9CommittedSegments.isEmpty();
    }

    private boolean isT9ManualCompositionMode() {
        return hasCommittedT9Segments() || isT9SyllableSelectionActive();
    }

    private boolean isT9SyllableSelectionActive() {
        return t9SelectedSyllablePinyin != null && t9SelectedSyllableDigitCount > 0;
    }

    private void clearT9SyllableSelection() {
        t9SelectedSyllablePinyin = null;
        t9SelectedSyllableDigitCount = 0;
        t9DeferredDigitsAfterSelection = "";
    }

    private void restoreFullDigitsFromSelectedSyllable() {
        if (!isT9SyllableSelectionActive()) {
            return;
        }
        String restoredDigits = getVisibleDigitsForT9Choices();
        clearT9SyllableSelection();
        composingDigits.setLength(0);
        composingDigits.append(restoredDigits);
    }

    private String getVisibleDigitsForT9Choices() {
        if (isT9SyllableSelectionActive()) {
            return composingDigits.toString() + t9DeferredDigitsAfterSelection;
        }
        return composingDigits.toString();
    }

    private T9PinyinChoice getActiveT9PinyinChoice() {
        if (isT9SyllableSelectionActive()) {
            return new T9PinyinChoice(
                    t9SelectedSyllablePinyin,
                    t9SelectedSyllableDigitCount,
                    false);
        }

        List<T9PinyinChoice> choices = getMatchingPinyinChoicesForDigits();
        return choices.isEmpty() ? null : choices.get(0);
    }

    private void addChoicesForPinyinKeys(
            List<T9PinyinChoice> target,
            List<String> pinyinKeys,
            int consumedDigitsCount,
            boolean wholeBufferMatch) {
        for (String pinyin : pinyinKeys) {
            target.add(new T9PinyinChoice(pinyin, consumedDigitsCount, wholeBufferMatch));
        }
    }

    private void addLeadingSyllableChoices(List<T9PinyinChoice> target, String digits) {
        for (String pinyin : pinyinDictionary.getLeadingSingleSyllablePinyinKeys(digits)) {
            String pinyinDigits = pinyinDictionary.getDigitsForPinyin(pinyin);
            if (pinyinDigits == null || pinyinDigits.isEmpty()) {
                continue;
            }
            target.add(new T9PinyinChoice(
                    pinyin,
                    Math.min(pinyinDigits.length(), digits.length()),
                    false));
        }
    }

    private String getRemainingDigitsAfterActiveChoice(T9PinyinChoice activeChoice) {
        if (isT9SyllableSelectionActive()) {
            return t9DeferredDigitsAfterSelection;
        }

        String digits = composingDigits.toString();
        int consumedCount = Math.min(activeChoice.consumedDigitsCount, digits.length());
        return digits.substring(consumedCount);
    }

    private void handleDictionaryLoaded() {
        if (keyboardStatus != null) {
            updateKeyboardStatus();
        }
    }

    private static final class T9CommittedSegment {
        private final String pinyin;
        private final String candidate;

        private T9CommittedSegment(String pinyin, String candidate) {
            this.pinyin = pinyin;
            this.candidate = candidate;
        }
    }

    private static final class T9PinyinChoice {
        private final String pinyin;
        private final int consumedDigitsCount;
        private final boolean wholeBufferMatch;

        private T9PinyinChoice(String pinyin, int consumedDigitsCount, boolean wholeBufferMatch) {
            this.pinyin = pinyin;
            this.consumedDigitsCount = consumedDigitsCount;
            this.wholeBufferMatch = wholeBufferMatch;
        }
    }
}
