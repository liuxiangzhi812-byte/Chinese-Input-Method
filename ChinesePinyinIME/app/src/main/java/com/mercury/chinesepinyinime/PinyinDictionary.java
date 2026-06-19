package com.mercury.chinesepinyinime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PinyinDictionary {
    private static final String DICTIONARY_ASSET_NAME = "pinyin_dict.txt";
    private static final int MAX_CANDIDATES_PER_PINYIN = 20;
    private static final PinyinDictionary INSTANCE = new PinyinDictionary();

    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingCallbacks = new ArrayList<>();
    private volatile Map<String, String[]> candidateWords = createFallbackCandidateWords();
    private boolean loadingStarted;
    private boolean loadFinished;

    private PinyinDictionary() {
    }

    public static PinyinDictionary getInstance() {
        return INSTANCE;
    }

    public void loadAsync(Context context, Runnable onLoaded) {
        Context appContext = context.getApplicationContext();
        boolean shouldStartLoad = false;
        synchronized (lock) {
            if (loadFinished) {
                postCallback(onLoaded);
                return;
            }
            if (onLoaded != null) {
                pendingCallbacks.add(onLoaded);
            }
            if (!loadingStarted) {
                loadingStarted = true;
                shouldStartLoad = true;
            }
        }

        if (shouldStartLoad) {
            executor.execute(() -> finishLoad(loadCandidateWordsFromAssets(appContext)));
        }
    }

    public String[] getCandidates(String pinyin) {
        return candidateWords.get(pinyin);
    }

    private void finishLoad(Map<String, String[]> loadedWords) {
        List<Runnable> callbacks;
        synchronized (lock) {
            if (!loadedWords.isEmpty()) {
                candidateWords = loadedWords;
            }
            loadFinished = true;
            loadingStarted = false;
            callbacks = new ArrayList<>(pendingCallbacks);
            pendingCallbacks.clear();
        }
        for (Runnable callback : callbacks) {
            postCallback(callback);
        }
    }

    private void postCallback(Runnable callback) {
        if (callback != null) {
            mainHandler.post(callback);
        }
    }

    private Map<String, String[]> loadCandidateWordsFromAssets(Context context) {
        Map<String, String[]> words = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(DICTIONARY_ASSET_NAME), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0 || separatorIndex == line.length() - 1) {
                    continue;
                }

                String pinyin = line.substring(0, separatorIndex).trim();
                String candidateText = line.substring(separatorIndex + 1).trim();
                if (pinyin.isEmpty() || candidateText.isEmpty()) {
                    continue;
                }

                String[] rawCandidates = candidateText.split(",");
                int candidateCount = Math.min(rawCandidates.length, MAX_CANDIDATES_PER_PINYIN);
                String[] candidates = new String[candidateCount];
                for (int i = 0; i < candidateCount; i++) {
                    candidates[i] = rawCandidates[i].trim();
                }
                words.put(pinyin, candidates);
            }
        } catch (IOException ignored) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(words);
    }

    private static Map<String, String[]> createFallbackCandidateWords() {
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
