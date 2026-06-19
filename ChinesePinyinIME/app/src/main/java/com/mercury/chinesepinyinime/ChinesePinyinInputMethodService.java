package com.mercury.chinesepinyinime;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChinesePinyinInputMethodService extends InputMethodService {
    private static final int DELETE_LONG_PRESS_DELAY_MS = 500;
    private static final int DELETE_REPEAT_INTERVAL_MS = 80;

    private final StringBuilder composingPinyin = new StringBuilder();
    private final Handler deleteRepeatHandler = new Handler(Looper.getMainLooper());
    private final PinyinDictionary pinyinDictionary = PinyinDictionary.getInstance();
    private final CandidatePager candidatePager = new CandidatePager();
    private boolean chineseMode = true;
    private boolean symbolMode = false;
    private int candidatePageIndex = 0;
    private int candidateListContainerWidthPx = 0;
    private boolean deleteRepeatStarted;
    private Runnable deleteRepeatRunnable;
    private LinearLayout candidateBar;
    private LinearLayout candidateListContainer;
    private TextView candidatePagePrev;
    private TextView candidatePageNext;
    private TextView keyboardStatus;
    private Button modeButton;
    private Button symbolToggleButton;
    private LinearLayout letterKeyboardSection;
    private LinearLayout symbolKeyboardSection;
    private View keyboardRootView;
    private ViewTreeObserver.OnGlobalLayoutListener candidateWidthLayoutListener;
    private View.OnAttachStateChangeListener candidateWidthAttachListener;

    @Override
    public View onCreateInputView() {
        View keyboardView = getLayoutInflater().inflate(R.layout.keyboard_view, null);
        candidateBar = keyboardView.findViewById(R.id.candidate_bar);
        candidateListContainer = keyboardView.findViewById(R.id.candidate_list_container);
        candidatePagePrev = keyboardView.findViewById(R.id.candidate_page_prev);
        candidatePageNext = keyboardView.findViewById(R.id.candidate_page_next);
        keyboardStatus = keyboardView.findViewById(R.id.keyboard_status);
        modeButton = keyboardView.findViewById(R.id.key_mode);
        symbolToggleButton = keyboardView.findViewById(R.id.key_symbol_toggle);
        letterKeyboardSection = keyboardView.findViewById(R.id.letter_keyboard_section);
        symbolKeyboardSection = keyboardView.findViewById(R.id.symbol_keyboard_section);
        bindKeyboardButtons(keyboardView);
        bindCandidatePageButtons();
        bindCandidateListContainerWidthListener(keyboardView);
        updateKeyboardLayout();
        updateKeyboardStatus();
        pinyinDictionary.loadAsync(getApplicationContext(), this::handleDictionaryLoaded);
        return keyboardView;
    }

    @Override
    public void onDestroy() {
        removeCandidateListContainerWidthListener();
        stopDeleteRepeat();
        super.onDestroy();
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        clearComposingPinyin();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        stopDeleteRepeat();
        clearComposingPinyin();
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
            case "action:symbol":
                toggleSymbolMode();
                break;
            default:
                break;
        }
    }

    private void handleSymbolKey(InputConnection inputConnection, String halfWidthSymbol) {
        clearComposingPinyin();
        inputConnection.commitText(toSymbolForCurrentMode(halfWidthSymbol), 1);
    }

    private void toggleSymbolMode() {
        symbolMode = !symbolMode;
        clearComposingPinyin();
        updateKeyboardLayout();
    }

    private void updateKeyboardLayout() {
        if (letterKeyboardSection != null) {
            letterKeyboardSection.setVisibility(symbolMode ? View.GONE : View.VISIBLE);
        }
        if (symbolKeyboardSection != null) {
            symbolKeyboardSection.setVisibility(symbolMode ? View.VISIBLE : View.GONE);
        }
        if (symbolToggleButton != null) {
            symbolToggleButton.setText(symbolMode ? "ABC" : "123");
        }
    }

    private String toSymbolForCurrentMode(String halfWidthSymbol) {
        if (!chineseMode) {
            return halfWidthSymbol;
        }
        return convertToFullWidth(halfWidthSymbol);
    }

    private String convertToFullWidth(String halfWidthSymbol) {
        if (halfWidthSymbol.isEmpty()) {
            return halfWidthSymbol;
        }

        StringBuilder fullWidth = new StringBuilder(halfWidthSymbol.length());
        for (int i = 0; i < halfWidthSymbol.length(); i++) {
            char character = halfWidthSymbol.charAt(i);
            if (character >= '0' && character <= '9') {
                fullWidth.append((char) ('\uFF10' + (character - '0')));
                continue;
            }

            Character mapped = FULL_WIDTH_SYMBOL_MAP.get(character);
            fullWidth.append(mapped != null ? mapped : character);
        }
        return fullWidth.toString();
    }

    private static final Map<Character, Character> FULL_WIDTH_SYMBOL_MAP = createFullWidthSymbolMap();

    private static Map<Character, Character> createFullWidthSymbolMap() {
        Map<Character, Character> map = new HashMap<>();
        map.put('@', '\uFF20');
        map.put('#', '\uFF03');
        map.put('$', '\uFF04');
        map.put('%', '\uFF05');
        map.put('&', '\uFF06');
        map.put('*', '\uFF0A');
        map.put('-', '\uFF0D');
        map.put('+', '\uFF0B');
        map.put('=', '\uFF1D');
        map.put('/', '\uFF0F');
        map.put('(', '\uFF08');
        map.put(')', '\uFF09');
        map.put(':', '\uFF1A');
        map.put(';', '\uFF1B');
        map.put('\'', '\uFF07');
        map.put('"', '\uFF02');
        map.put('?', '\uFF1F');
        map.put('!', '\uFF01');
        map.put('.', '\uFF0E');
        map.put(',', '\uFF0C');
        return Collections.unmodifiableMap(map);
    }

    private void handleLetterKey(InputConnection inputConnection, String letter) {
        if (chineseMode) {
            composingPinyin.append(letter);
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        inputConnection.commitText(letter, 1);
    }

    private void handleSpace(InputConnection inputConnection) {
        if (chineseMode && composingPinyin.length() > 0) {
            commitFirstCandidate(inputConnection);
            return;
        }

        inputConnection.commitText(" ", 1);
    }

    private void handleDelete(InputConnection inputConnection) {
        if (chineseMode && composingPinyin.length() > 0) {
            composingPinyin.deleteCharAt(composingPinyin.length() - 1);
            candidatePageIndex = 0;
            updateKeyboardStatus();
            return;
        }

        inputConnection.deleteSurroundingText(1, 0);
    }

    private void handleEnter(InputConnection inputConnection) {
        if (chineseMode && composingPinyin.length() > 0) {
            commitComposingPinyin(inputConnection);
            return;
        }

        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
        inputConnection.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
    }

    private void toggleInputMode() {
        chineseMode = !chineseMode;
        clearComposingPinyin();
    }

    private void commitComposingPinyin(InputConnection inputConnection) {
        inputConnection.commitText(composingPinyin.toString(), 1);
        composingPinyin.setLength(0);
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void commitFirstCandidate(InputConnection inputConnection) {
        inputConnection.commitText(getCandidatesForCurrentPage()[0], 1);
        composingPinyin.setLength(0);
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void commitCandidate(String candidate) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null || candidate.isEmpty()) {
            return;
        }

        inputConnection.commitText(candidate, 1);
        composingPinyin.setLength(0);
        candidatePageIndex = 0;
        updateKeyboardStatus();
    }

    private void clearComposingPinyin() {
        composingPinyin.setLength(0);
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
            if (chineseMode) {
                String pinyin = composingPinyin.length() > 0 ? composingPinyin.toString() : "";
                keyboardStatus.setText("中文 " + pinyin);
            } else {
                keyboardStatus.setText("English");
            }
        }

        if (modeButton != null) {
            modeButton.setText(chineseMode ? "ZH" : "EN");
        }

        updateCandidateBar();
    }

    private void updateCandidateBar() {
        if (candidateBar == null || candidateListContainer == null) {
            return;
        }

        boolean showCandidates = chineseMode && composingPinyin.length() > 0;
        candidateBar.setVisibility(showCandidates ? View.VISIBLE : View.GONE);
        if (!showCandidates) {
            candidateListContainer.removeAllViews();
            updateCandidatePageButtons(false, false);
            return;
        }

        String[] allCandidates = getCandidatesForCurrentPinyin();
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
    }

    private void bindCandidateListContainerWidthListener(View keyboardView) {
        removeCandidateListContainerWidthListener();
        keyboardRootView = keyboardView;
        candidateWidthLayoutListener = () -> {
            if (candidateListContainer == null) {
                return;
            }
            int newWidth = candidateListContainer.getWidth();
            if (newWidth > 0 && newWidth != candidateListContainerWidthPx) {
                candidateListContainerWidthPx = newWidth;
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

    private String[] getCandidatesForCurrentPinyin() {
        String pinyin = composingPinyin.toString();
        String[] candidates = pinyinDictionary.getCandidates(pinyin);
        if (candidates == null) {
            return new String[]{pinyin};
        }
        return candidates;
    }

    private String[] getCandidatesForCurrentPage() {
        String[] allCandidates = getCandidatesForCurrentPinyin();
        recomputeCandidatePages(allCandidates);
        candidatePageIndex = candidatePager.clampPageIndex(candidatePageIndex);
        return candidatePager.getCandidatesForPage(allCandidates, candidatePageIndex);
    }

    private void handleDictionaryLoaded() {
        if (keyboardStatus != null) {
            updateKeyboardStatus();
        }
    }
}
