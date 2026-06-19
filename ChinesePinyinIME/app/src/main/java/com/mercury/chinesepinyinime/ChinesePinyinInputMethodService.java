package com.mercury.chinesepinyinime;

import android.inputmethodservice.InputMethodService;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ChinesePinyinInputMethodService extends InputMethodService {
    private static final Map<String, String[]> CANDIDATE_WORDS = createCandidateWords();

    private final StringBuilder composingPinyin = new StringBuilder();
    private boolean chineseMode = false;
    private LinearLayout candidateBar;
    private TextView[] candidateViews;
    private TextView keyboardStatus;
    private Button modeButton;

    @Override
    public View onCreateInputView() {
        View keyboardView = getLayoutInflater().inflate(R.layout.keyboard_view, null);
        candidateBar = keyboardView.findViewById(R.id.candidate_bar);
        candidateViews = new TextView[]{
                keyboardView.findViewById(R.id.candidate_1),
                keyboardView.findViewById(R.id.candidate_2),
                keyboardView.findViewById(R.id.candidate_3),
                keyboardView.findViewById(R.id.candidate_4),
                keyboardView.findViewById(R.id.candidate_5)
        };
        keyboardStatus = keyboardView.findViewById(R.id.keyboard_status);
        modeButton = keyboardView.findViewById(R.id.key_mode);
        bindKeyboardButtons(keyboardView);
        bindCandidateViews();
        updateKeyboardStatus();
        return keyboardView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        clearComposingPinyin();
    }

    @Override
    public void onFinishInput() {
        super.onFinishInput();
        clearComposingPinyin();
    }

    private void bindKeyboardButtons(View view) {
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
                break;
            default:
                break;
        }
    }

    private void handleLetterKey(InputConnection inputConnection, String letter) {
        if (chineseMode) {
            composingPinyin.append(letter);
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
        updateKeyboardStatus();
    }

    private void commitFirstCandidate(InputConnection inputConnection) {
        inputConnection.commitText(getCandidatesForCurrentPinyin()[0], 1);
        composingPinyin.setLength(0);
        updateKeyboardStatus();
    }

    private void commitCandidate(String candidate) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection == null || candidate.isEmpty()) {
            return;
        }

        inputConnection.commitText(candidate, 1);
        composingPinyin.setLength(0);
        updateKeyboardStatus();
    }

    private void clearComposingPinyin() {
        composingPinyin.setLength(0);
        updateKeyboardStatus();
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
        if (candidateBar == null || candidateViews == null) {
            return;
        }

        boolean showCandidates = chineseMode && composingPinyin.length() > 0;
        candidateBar.setVisibility(showCandidates ? View.VISIBLE : View.GONE);
        if (!showCandidates) {
            clearCandidateTexts();
            return;
        }

        String[] candidates = getCandidatesForCurrentPinyin();

        for (int i = 0; i < candidateViews.length; i++) {
            if (i < candidates.length) {
                candidateViews[i].setText(candidates[i]);
                candidateViews[i].setVisibility(View.VISIBLE);
            } else {
                candidateViews[i].setText("");
                candidateViews[i].setVisibility(View.INVISIBLE);
            }
        }
    }

    private void clearCandidateTexts() {
        for (TextView candidateView : candidateViews) {
            candidateView.setText("");
            candidateView.setVisibility(View.INVISIBLE);
        }
    }

    private void bindCandidateViews() {
        if (candidateViews == null) {
            return;
        }

        for (TextView candidateView : candidateViews) {
            candidateView.setOnClickListener(view -> {
                CharSequence text = ((TextView) view).getText();
                commitCandidate(text == null ? "" : text.toString());
            });
        }
    }

    private String[] getCandidatesForCurrentPinyin() {
        String pinyin = composingPinyin.toString();
        String[] candidates = CANDIDATE_WORDS.get(pinyin);
        if (candidates == null) {
            return new String[]{pinyin};
        }
        return candidates;
    }

    private static Map<String, String[]> createCandidateWords() {
        Map<String, String[]> words = new HashMap<>();
        words.put("a", new String[]{"啊", "阿"});
        words.put("ai", new String[]{"爱", "哎", "唉"});
        words.put("an", new String[]{"安", "按", "案"});
        words.put("ba", new String[]{"吧", "把", "爸"});
        words.put("baba", new String[]{"爸爸"});
        words.put("ban", new String[]{"办", "半", "班"});
        words.put("bang", new String[]{"帮", "棒", "榜"});
        words.put("bei", new String[]{"被", "北", "杯"});
        words.put("bu", new String[]{"不", "部", "步"});
        words.put("cai", new String[]{"才", "菜", "猜"});
        words.put("chang", new String[]{"长", "常", "场"});
        words.put("chi", new String[]{"吃", "迟", "持"});
        words.put("chu", new String[]{"出", "处", "初"});
        words.put("da", new String[]{"大", "打", "达"});
        words.put("dao", new String[]{"到", "道", "倒"});
        words.put("di", new String[]{"地", "第", "低"});
        words.put("dian", new String[]{"点", "电", "店"});
        words.put("dong", new String[]{"动", "东", "懂"});
        words.put("dui", new String[]{"对", "队"});
        words.put("duo", new String[]{"多", "朵"});
        words.put("er", new String[]{"而", "二", "儿"});
        words.put("fan", new String[]{"饭", "反", "烦"});
        words.put("fang", new String[]{"方", "放", "房"});
        words.put("fei", new String[]{"非", "飞", "费"});
        words.put("fen", new String[]{"分", "份", "粉"});
        words.put("gao", new String[]{"高", "搞", "告"});
        words.put("ge", new String[]{"个", "各", "哥"});
        words.put("gei", new String[]{"给"});
        words.put("gong", new String[]{"工", "公", "功"});
        words.put("gongzuo", new String[]{"工作"});
        words.put("hai", new String[]{"还", "海", "孩"});
        words.put("ni", new String[]{"你", "呢", "尼"});
        words.put("hao", new String[]{"好", "号", "浩"});
        words.put("nihao", new String[]{"你好", "你号"});
        words.put("hen", new String[]{"很", "狠"});
        words.put("hui", new String[]{"会", "回", "灰"});
        words.put("jia", new String[]{"家", "加", "假"});
        words.put("jian", new String[]{"见", "件", "间"});
        words.put("jiao", new String[]{"叫", "交", "教"});
        words.put("jin", new String[]{"进", "今", "近"});
        words.put("jintian", new String[]{"今天"});
        words.put("kan", new String[]{"看", "砍"});
        words.put("ke", new String[]{"可", "课", "科"});
        words.put("lai", new String[]{"来", "赖"});
        words.put("laoshi", new String[]{"老师"});
        words.put("le", new String[]{"了", "乐"});
        words.put("li", new String[]{"里", "理", "力"});
        words.put("ma", new String[]{"吗", "妈", "马"});
        words.put("mama", new String[]{"妈妈"});
        words.put("mei", new String[]{"没", "美", "每"});
        words.put("men", new String[]{"们", "门"});
        words.put("ming", new String[]{"名", "明", "命"});
        words.put("mingtian", new String[]{"明天"});
        words.put("na", new String[]{"那", "拿", "哪"});
        words.put("nali", new String[]{"哪里", "那里"});
        words.put("ne", new String[]{"呢"});
        words.put("neng", new String[]{"能"});
        words.put("pengyou", new String[]{"朋友"});
        words.put("qu", new String[]{"去", "取", "区"});
        words.put("ren", new String[]{"人", "认", "任"});
        words.put("renshi", new String[]{"认识"});
        words.put("ri", new String[]{"日"});
        words.put("san", new String[]{"三", "散"});
        words.put("shang", new String[]{"上", "商", "伤"});
        words.put("shangxue", new String[]{"上学"});
        words.put("shangwu", new String[]{"上午"});
        words.put("wo", new String[]{"我", "握", "窝"});
        words.put("women", new String[]{"我们"});
        words.put("shi", new String[]{"是", "时", "事", "十"});
        words.put("de", new String[]{"的", "得", "地"});
        words.put("shei", new String[]{"谁"});
        words.put("shenme", new String[]{"什么"});
        words.put("sheng", new String[]{"生", "声", "省"});
        words.put("shijian", new String[]{"时间"});
        words.put("shouji", new String[]{"手机"});
        words.put("shu", new String[]{"书", "数", "树"});
        words.put("shuo", new String[]{"说"});
        words.put("ta", new String[]{"他", "她", "它"});
        words.put("tamen", new String[]{"他们", "她们"});
        words.put("tian", new String[]{"天", "田"});
        words.put("ting", new String[]{"听", "停", "挺"});
        words.put("wan", new String[]{"完", "晚", "万"});
        words.put("wang", new String[]{"网", "往", "王"});
        words.put("wei", new String[]{"为", "位", "喂"});
        words.put("wenti", new String[]{"问题"});
        words.put("xi", new String[]{"西", "系", "洗"});
        words.put("xia", new String[]{"下", "夏"});
        words.put("xian", new String[]{"先", "现", "线"});
        words.put("xiang", new String[]{"想", "向", "像"});
        words.put("xiawu", new String[]{"下午"});
        words.put("xie", new String[]{"写", "谢", "些"});
        words.put("xiexie", new String[]{"谢谢"});
        words.put("xihuan", new String[]{"喜欢"});
        words.put("xue", new String[]{"学", "雪"});
        words.put("xuesheng", new String[]{"学生"});
        words.put("yao", new String[]{"要", "药", "摇"});
        words.put("ye", new String[]{"也", "夜", "业"});
        words.put("yi", new String[]{"一", "以", "已"});
        words.put("you", new String[]{"有", "又", "右"});
        words.put("zaijian", new String[]{"再见"});
        words.put("zai", new String[]{"在", "再", "载"});
        words.put("zenme", new String[]{"怎么"});
        words.put("zhe", new String[]{"这", "着"});
        words.put("zheli", new String[]{"这里"});
        words.put("zhong", new String[]{"中", "种", "重"});
        words.put("guo", new String[]{"国", "过", "果"});
        words.put("zhongguo", new String[]{"中国"});
        words.put("zi", new String[]{"字", "子", "自"});
        words.put("zou", new String[]{"走"});
        words.put("zuo", new String[]{"做", "坐", "作"});
        return Collections.unmodifiableMap(words);
    }
}
