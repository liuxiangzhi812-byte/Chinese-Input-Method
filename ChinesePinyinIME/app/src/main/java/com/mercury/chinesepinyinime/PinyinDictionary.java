package com.mercury.chinesepinyinime;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PinyinDictionary {
    private static final String TAG = "PinyinDict";
    private static final String DICTIONARY_ASSET_NAME = "pinyin_dict.txt";
    private static final String SYLLABLE_DICTIONARY_ASSET_NAME = "pinyin_syllable_dict.txt";
    /** High enough for complete single-syllable browsing (e.g. ji has ~174 entries). */
    private static final int MAX_CANDIDATES_PER_PINYIN = 512;
    private static final int MAX_PREFIX_PYNYIN_KEYS = 24;
    private static final int MAX_PREFIX_CANDIDATES = 40;
    private static final Map<Character, Character> LETTER_TO_DIGIT = createLetterToDigitMap();
    private static final PinyinDictionary INSTANCE = new PinyinDictionary();

    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingCallbacks = new ArrayList<>();
    private volatile Map<String, String[]> builtInCandidateWords = createFallbackCandidateWords();
    private volatile RuntimeDictionaryState runtimeState =
            buildRuntimeDictionaryState(
                    builtInCandidateWords,
                    Collections.emptyMap(),
                    Collections.emptyMap());
    private boolean loadingStarted;
    private boolean loadFinished;
    private boolean syllableFallbackLoaded;

    private PinyinDictionary() {
    }

    public static PinyinDictionary getInstance() {
        return INSTANCE;
    }

    public void loadAsync(Context context, Runnable onLoaded) {
        Context appContext = context.getApplicationContext();
        UserFrequencyStore.getInstance().load(appContext);
        UserDictionaryStore.getInstance().load(appContext);
        ensureSyllableFallbackLoaded(appContext);
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
            executor.execute(() -> {
                ManualDictionaryStore.getInstance().load(appContext);
                finishLoad(loadCandidateWordsFromAssets(appContext));
            });
        }
    }

    public String[] getCandidates(String pinyin) {
        RuntimeDictionaryState state = runtimeState;
        return getCandidates(pinyin, state);
    }

    private static String[] getCandidates(String pinyin, RuntimeDictionaryState state) {
        String[] candidates = state.candidateWords.get(pinyin);
        if (candidates == null) {
            return null;
        }
        return DictionaryLayerMerger.prioritize(
                pinyin,
                CandidateRanker.rank(pinyin, candidates),
                state.learnedCandidateWords,
                state.manualCandidateWords);
    }

    public String[] getCandidatesForPinyinPrefix(String prefix) {
        return mergeCandidatesForPinyinKeys(getPinyinKeysForPrefix(prefix));
    }

    public String[] getCandidatesForDigitPrefix(String digitsPrefix) {
        return mergeCandidatesForPinyinKeys(getPinyinKeysForDigitPrefix(digitsPrefix));
    }

    /**
     * Returns candidates for exact 9-key input that represents a complete
     * multi-syllable word. Single-syllable keys are deliberately excluded so
     * the caller can fall back to the per-syllable composition flow.
     */
    public String[] getMultiSyllableCandidatesForDigits(String digits) {
        return mergeCandidatesForPinyinKeys(getMultiSyllablePinyinKeysForDigits(digits));
    }

    public String resolveMultiSyllablePinyinForDigits(String digits) {
        List<String> matches = getMultiSyllablePinyinKeysForDigits(digits);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public String resolveBestPinyinForDigits(String digits) {
        List<String> matches = getPinyinKeysForDigits(digits);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<String> getPinyinKeysForDigits(String digits) {
        if (digits == null || digits.isEmpty()) {
            return Collections.emptyList();
        }
        return runtimeState.digitIndex.exact(digits);
    }

    private List<String> getMultiSyllablePinyinKeysForDigits(String digits) {
        List<String> matches = getPinyinKeysForDigits(digits);
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> multiSyllableMatches = new ArrayList<>();
        Map<String, String[]> singleSyllableWords = runtimeState.singleSyllableCandidateWords;
        for (String pinyin : matches) {
            if (!singleSyllableWords.containsKey(pinyin)) {
                multiSyllableMatches.add(pinyin);
            }
        }
        return multiSyllableMatches.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(multiSyllableMatches);
    }

    public String resolveBestPinyinForPrefix(String prefix) {
        List<String> matches = getPinyinKeysForPrefix(prefix);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<String> getPinyinKeysForPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return Collections.emptyList();
        }
        RuntimeDictionaryState state = runtimeState;
        return state.pinyinPrefixIndex.lookup(
                prefix,
                MAX_PREFIX_PYNYIN_KEYS,
                rankingComparator(state.candidateWords));
    }

    public String resolveBestPinyinForDigitPrefix(String digitsPrefix) {
        List<String> matches = getPinyinKeysForDigitPrefix(digitsPrefix);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<String> getPinyinKeysForDigitPrefix(String digitsPrefix) {
        if (digitsPrefix == null || digitsPrefix.isEmpty()) {
            return Collections.emptyList();
        }
        RuntimeDictionaryState state = runtimeState;
        return state.digitIndex.prefix(
                digitsPrefix,
                MAX_PREFIX_PYNYIN_KEYS,
                rankingComparator(state.candidateWords));
    }

    public String resolveBestLeadingSingleSyllableForDigits(String digits) {
        List<String> matches = getLeadingSingleSyllablePinyinKeys(digits);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<String> getLeadingSingleSyllablePinyinKeys(String digits) {
        if (digits == null || digits.isEmpty()) {
            return Collections.emptyList();
        }

        RuntimeDictionaryState state = runtimeState;
        List<String> dictionaryAlignedMatches =
                getDictionaryAlignedLeadingSingleSyllableMatches(state, digits);

        List<String> exactMatches = state.singleSyllableDigitIndex.exact(digits);
        if (!exactMatches.isEmpty()) {
            return prependDistinctMatches(dictionaryAlignedMatches, exactMatches);
        }

        List<String> prefixMatches = state.singleSyllableDigitIndex.prefix(
                digits,
                MAX_PREFIX_PYNYIN_KEYS,
                rankingComparator(state.singleSyllableCandidateWords));
        if (!prefixMatches.isEmpty()) {
            return prependDistinctMatches(dictionaryAlignedMatches, prefixMatches);
        }

        return prependDistinctMatches(
                dictionaryAlignedMatches,
                getLeadingConsumedSyllableMatches(state, digits));
    }

    public String getDigitsForPinyin(String pinyin) {
        if (pinyin == null || pinyin.isEmpty()) {
            return null;
        }
        return toDigits(pinyin);
    }

    public void addUserCandidate(String pinyin, String candidate) {
        if (pinyin == null || pinyin.isEmpty() || candidate == null || candidate.isEmpty()) {
            return;
        }

        if (containsCandidate(builtInCandidateWords.get(pinyin), candidate)) {
            return;
        }

        UserDictionaryStore.getInstance().recordEntry(pinyin, candidate);
        if (containsCandidate(runtimeState.candidateWords.get(pinyin), candidate)) {
            return;
        }

        executor.execute(() -> mergeUserCandidateIntoRuntime(pinyin, candidate));
    }

    public void reloadUserDictionariesAsync(Context context, Runnable onReloaded) {
        Context appContext = context.getApplicationContext();
        executor.execute(() -> {
            UserDictionaryStore.getInstance().load(appContext);
            ManualDictionaryStore.getInstance().load(appContext);
            synchronized (lock) {
                applyDictionaryLayers(builtInCandidateWords, "reload_user_layers");
            }
            postCallback(onReloaded);
        });
    }

    private void finishLoad(Map<String, String[]> loadedWords) {
        List<Runnable> callbacks;
        synchronized (lock) {
            builtInCandidateWords = loadedWords.isEmpty()
                    ? createFallbackCandidateWords()
                    : loadedWords;
            applyDictionaryLayers(builtInCandidateWords, "full_asset_load");
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

    private void ensureSyllableFallbackLoaded(Context context) {
        synchronized (lock) {
            if (syllableFallbackLoaded) {
                return;
            }
            Map<String, String[]> syllableWords = loadCandidateWordsFromAsset(
                    context,
                    SYLLABLE_DICTIONARY_ASSET_NAME);
            if (!syllableWords.isEmpty()) {
                Map<String, String[]> fallbackWords = new HashMap<>(builtInCandidateWords);
                fallbackWords.putAll(syllableWords);
                builtInCandidateWords = Collections.unmodifiableMap(fallbackWords);
                applyDictionaryLayers(builtInCandidateWords, "syllable_fallback");
            }
            syllableFallbackLoaded = true;
        }
    }

    private Map<String, String[]> loadCandidateWordsFromAssets(Context context) {
        return loadCandidateWordsFromAsset(context, DICTIONARY_ASSET_NAME);
    }

    private Map<String, String[]> loadCandidateWordsFromAsset(
            Context context,
            String assetName) {
        Map<String, String[]> words = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open(assetName), StandardCharsets.UTF_8))) {
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

    /**
     * Incrementally merge one learned candidate without rebuilding every index
     * for the full dictionary.
     */
    private void mergeUserCandidateIntoRuntime(String pinyin, String candidate) {
        synchronized (lock) {
            RuntimeDictionaryState old = runtimeState;
            if (containsCandidate(old.candidateWords.get(pinyin), candidate)) {
                return;
            }

            long startedAt = System.nanoTime();
            long heapBefore = usedHeapBytes();

            Map<String, List<String>> learnedWords =
                    UserDictionaryStore.getInstance().snapshotOrderedEntries();
            Map<String, List<String>> manualWords = old.manualCandidateWords;

            boolean keyExisted = old.candidateWords.containsKey(pinyin);
            String[] existing = old.candidateWords.get(pinyin);
            String[] updated = insertCandidate(existing, candidate);

            Map<String, String[]> newCandidateWords = new HashMap<>(old.candidateWords);
            newCandidateWords.put(pinyin, updated);

            RuntimeDictionaryState next;
            if (keyExisted) {
                Map<String, String[]> newSingleSyllable =
                        refreshSingleSyllableEntry(
                                old.singleSyllableCandidateWords, pinyin, updated);
                SortedDigitIndex singleDigitIndex = old.singleSyllableDigitIndex;
                if (newSingleSyllable != old.singleSyllableCandidateWords) {
                    singleDigitIndex = SortedDigitIndex.fromWords(
                            newSingleSyllable,
                            PinyinDictionary::toDigits,
                            rankingComparator(newSingleSyllable));
                }
                next = new RuntimeDictionaryState(
                        Collections.unmodifiableMap(newCandidateWords),
                        old.digitIndex,
                        old.pinyinPrefixIndex,
                        newSingleSyllable,
                        singleDigitIndex,
                        learnedWords,
                        manualWords);
            } else {
                String digitString = toDigits(pinyin);
                Comparator<String> ranker = rankingComparator(newCandidateWords);
                SortedDigitIndex newDigitIndex = digitString == null
                        ? old.digitIndex
                        : old.digitIndex.withKey(pinyin, digitString, ranker);
                SortedPrefixIndex newPrefixIndex = old.pinyinPrefixIndex.withKey(pinyin);

                Map<String, String[]> newSingleSyllable =
                        refreshSingleSyllableEntry(
                                old.singleSyllableCandidateWords, pinyin, updated);
                SortedDigitIndex singleDigitIndex = old.singleSyllableDigitIndex;
                if (newSingleSyllable != old.singleSyllableCandidateWords
                        && digitString != null
                        && newSingleSyllable.containsKey(pinyin)) {
                    singleDigitIndex = old.singleSyllableDigitIndex.withKey(
                            pinyin, digitString, rankingComparator(newSingleSyllable));
                } else if (newSingleSyllable != old.singleSyllableCandidateWords) {
                    singleDigitIndex = SortedDigitIndex.fromWords(
                            newSingleSyllable,
                            PinyinDictionary::toDigits,
                            rankingComparator(newSingleSyllable));
                }

                next = new RuntimeDictionaryState(
                        Collections.unmodifiableMap(newCandidateWords),
                        newDigitIndex,
                        newPrefixIndex,
                        newSingleSyllable,
                        singleDigitIndex,
                        learnedWords,
                        manualWords);
            }

            runtimeState = next;
            logRebuild(
                    keyExisted ? "learn_existing_pinyin" : "learn_new_pinyin",
                    startedAt,
                    heapBefore,
                    next.candidateWords.size(),
                    next.pinyinPrefixIndex.size());
        }
    }

    private void applyDictionaryLayers(Map<String, String[]> builtInWords, String reason) {
        long startedAt = System.nanoTime();
        long heapBefore = usedHeapBytes();

        Map<String, List<String>> learnedWords =
                UserDictionaryStore.getInstance().snapshotOrderedEntries();
        Map<String, List<String>> manualWords =
                ManualDictionaryStore.getInstance().snapshotOrderedEntries();
        Map<String, String[]> mergedWords = DictionaryLayerMerger.merge(
                builtInWords,
                learnedWords,
                manualWords,
                MAX_CANDIDATES_PER_PINYIN);
        RuntimeDictionaryState next =
                buildRuntimeDictionaryState(mergedWords, learnedWords, manualWords);
        runtimeState = next;

        logRebuild(
                reason,
                startedAt,
                heapBefore,
                next.candidateWords.size(),
                next.pinyinPrefixIndex.size());
    }

    private static RuntimeDictionaryState buildRuntimeDictionaryState(
            Map<String, String[]> candidateWords,
            Map<String, List<String>> learnedWords,
            Map<String, List<String>> manualWords) {
        Map<String, String[]> singleSyllableWords =
                buildSingleSyllableCandidateWords(candidateWords);
        Comparator<String> fullRanker = rankingComparator(candidateWords);
        Comparator<String> singleRanker = rankingComparator(singleSyllableWords);
        return new RuntimeDictionaryState(
                candidateWords,
                SortedDigitIndex.fromWords(candidateWords, PinyinDictionary::toDigits, fullRanker),
                SortedPrefixIndex.fromKeys(candidateWords.keySet()),
                singleSyllableWords,
                SortedDigitIndex.fromWords(
                        singleSyllableWords, PinyinDictionary::toDigits, singleRanker),
                learnedWords,
                manualWords);
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

    private static Comparator<String> rankingComparator(Map<String, String[]> words) {
        return (left, right) -> comparePinyinKeys(left, right, words);
    }

    private static int comparePinyinKeys(String left, String right, Map<String, String[]> words) {
        boolean leftOverride = CandidateRanker.hasManualOverride(left);
        boolean rightOverride = CandidateRanker.hasManualOverride(right);
        if (leftOverride != rightOverride) {
            return leftOverride ? -1 : 1;
        }
        String[] leftCandidates = words.get(left);
        String[] rightCandidates = words.get(right);
        int leftCount = leftCandidates == null ? 0 : leftCandidates.length;
        int rightCount = rightCandidates == null ? 0 : rightCandidates.length;
        if (leftCount != rightCount) {
            return Integer.compare(rightCount, leftCount);
        }
        return left.compareTo(right);
    }

    private List<String> getLeadingConsumedSyllableMatches(
            RuntimeDictionaryState state,
            String digits) {
        List<String> matches = new ArrayList<>();
        Set<String> seenMatches = new LinkedHashSet<>();
        int maxPrefixLength = Math.min(6, digits.length() - 1);
        for (int prefixLength = maxPrefixLength; prefixLength >= 1; prefixLength--) {
            List<String> prefixMatches =
                    state.singleSyllableDigitIndex.exact(digits.substring(0, prefixLength));
            for (String match : prefixMatches) {
                if (seenMatches.add(match)) {
                    matches.add(match);
                    if (matches.size() >= MAX_PREFIX_PYNYIN_KEYS) {
                        return Collections.unmodifiableList(matches);
                    }
                }
            }
        }
        return matches.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(matches);
    }

    private List<String> getDictionaryAlignedLeadingSingleSyllableMatches(
            RuntimeDictionaryState state,
            String digits) {
        List<String> fullPinyinMatches = state.digitIndex.exact(digits);
        if (fullPinyinMatches.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> alignedMatches = new LinkedHashSet<>();
        for (String fullPinyin : fullPinyinMatches) {
            int maxLeadingLength = Math.min(6, fullPinyin.length() - 1);
            for (int leadingLength = 1; leadingLength <= maxLeadingLength; leadingLength++) {
                String syllable = fullPinyin.substring(0, leadingLength);
                if (!state.singleSyllableCandidateWords.containsKey(syllable)) {
                    continue;
                }
                String remainingPinyin = fullPinyin.substring(syllable.length());
                if (canSegmentIntoSingleSyllables(state, remainingPinyin)) {
                    alignedMatches.add(syllable);
                }
            }
        }

        if (alignedMatches.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> orderedMatches = new ArrayList<>(alignedMatches);
        orderedMatches.sort(rankingComparator(state.singleSyllableCandidateWords));
        return Collections.unmodifiableList(orderedMatches);
    }

    private boolean canSegmentIntoSingleSyllables(
            RuntimeDictionaryState state,
            String pinyin) {
        if (pinyin.isEmpty()) {
            return true;
        }
        boolean[] reachable = new boolean[pinyin.length() + 1];
        reachable[0] = true;
        for (int start = 0; start < pinyin.length(); start++) {
            if (!reachable[start]) {
                continue;
            }
            int maxEnd = Math.min(pinyin.length(), start + 6);
            for (int end = start + 1; end <= maxEnd; end++) {
                if (state.singleSyllableCandidateWords.containsKey(pinyin.substring(start, end))) {
                    reachable[end] = true;
                }
            }
        }
        return reachable[pinyin.length()];
    }

    private static List<String> prependDistinctMatches(
            List<String> preferredMatches,
            List<String> fallbackMatches) {
        if (preferredMatches.isEmpty()) {
            return fallbackMatches;
        }
        Set<String> mergedMatches = new LinkedHashSet<>(preferredMatches);
        mergedMatches.addAll(fallbackMatches);
        return Collections.unmodifiableList(new ArrayList<>(mergedMatches));
    }

    private static final class RuntimeDictionaryState {
        private final Map<String, String[]> candidateWords;
        private final SortedDigitIndex digitIndex;
        private final SortedPrefixIndex pinyinPrefixIndex;
        private final Map<String, String[]> singleSyllableCandidateWords;
        private final SortedDigitIndex singleSyllableDigitIndex;
        private final Map<String, List<String>> learnedCandidateWords;
        private final Map<String, List<String>> manualCandidateWords;

        private RuntimeDictionaryState(
                Map<String, String[]> candidateWords,
                SortedDigitIndex digitIndex,
                SortedPrefixIndex pinyinPrefixIndex,
                Map<String, String[]> singleSyllableCandidateWords,
                SortedDigitIndex singleSyllableDigitIndex,
                Map<String, List<String>> learnedCandidateWords,
                Map<String, List<String>> manualCandidateWords) {
            this.candidateWords = candidateWords;
            this.digitIndex = digitIndex;
            this.pinyinPrefixIndex = pinyinPrefixIndex;
            this.singleSyllableCandidateWords = singleSyllableCandidateWords;
            this.singleSyllableDigitIndex = singleSyllableDigitIndex;
            this.learnedCandidateWords = learnedCandidateWords;
            this.manualCandidateWords = manualCandidateWords;
        }
    }

    private String[] mergeCandidatesForPinyinKeys(List<String> pinyinKeys) {
        if (pinyinKeys.isEmpty()) {
            return null;
        }

        RuntimeDictionaryState state = runtimeState;
        List<String> mergedCandidates = new ArrayList<>();
        Set<String> seenCandidates = new LinkedHashSet<>();
        appendSourceCandidatesForPinyinKeys(
                mergedCandidates,
                seenCandidates,
                pinyinKeys,
                state.manualCandidateWords,
                state.candidateWords);
        if (mergedCandidates.size() < MAX_PREFIX_CANDIDATES) {
            appendSourceCandidatesForPinyinKeys(
                    mergedCandidates,
                    seenCandidates,
                    pinyinKeys,
                    state.learnedCandidateWords,
                    state.candidateWords);
        }
        for (String pinyin : pinyinKeys) {
            String[] rankedCandidates = getCandidates(pinyin, state);
            if (rankedCandidates == null) {
                continue;
            }
            for (String candidate : rankedCandidates) {
                if (seenCandidates.add(candidate)) {
                    mergedCandidates.add(candidate);
                    if (mergedCandidates.size() >= MAX_PREFIX_CANDIDATES) {
                        return mergedCandidates.toArray(new String[0]);
                    }
                }
            }
        }
        return mergedCandidates.isEmpty() ? null : mergedCandidates.toArray(new String[0]);
    }

    private static void appendSourceCandidatesForPinyinKeys(
            List<String> target,
            Set<String> seen,
            List<String> pinyinKeys,
            Map<String, List<String>> sourceWords,
            Map<String, String[]> availableWords) {
        for (String pinyin : pinyinKeys) {
            List<String> candidates = sourceWords.get(pinyin);
            if (candidates == null) {
                continue;
            }
            for (String candidate : candidates) {
                if (containsCandidate(availableWords.get(pinyin), candidate)
                        && seen.add(candidate)) {
                    target.add(candidate);
                    if (target.size() >= MAX_PREFIX_CANDIDATES) {
                        return;
                    }
                }
            }
        }
    }

    private static Map<String, String[]> buildSingleSyllableCandidateWords(Map<String, String[]> words) {
        Map<String, String[]> syllableWords = new HashMap<>();
        for (Map.Entry<String, String[]> entry : words.entrySet()) {
            if (isProbableSingleSyllableKey(entry.getKey(), entry.getValue())) {
                syllableWords.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(syllableWords);
    }

    private static Map<String, String[]> refreshSingleSyllableEntry(
            Map<String, String[]> previous,
            String pinyin,
            String[] candidates) {
        boolean shouldInclude = isProbableSingleSyllableKey(pinyin, candidates);
        boolean previouslyIncluded = previous.containsKey(pinyin);
        if (shouldInclude == previouslyIncluded
                && (!shouldInclude || containsSameCandidates(previous.get(pinyin), candidates))) {
            return previous;
        }
        Map<String, String[]> next = new HashMap<>(previous);
        if (shouldInclude) {
            next.put(pinyin, candidates);
        } else {
            next.remove(pinyin);
        }
        return Collections.unmodifiableMap(next);
    }

    private static boolean containsSameCandidates(String[] left, String[] right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null || left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!left[i].equals(right[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isProbableSingleSyllableKey(String pinyin, String[] candidates) {
        if (pinyin == null || candidates == null || pinyin.isEmpty() || pinyin.length() > 6) {
            return false;
        }
        for (int i = 0; i < pinyin.length(); i++) {
            char current = pinyin.charAt(i);
            if (current < 'a' || current > 'z') {
                return false;
            }
        }
        for (String candidate : candidates) {
            if (candidate != null && candidate.length() == 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCandidate(String[] candidates, String candidate) {
        if (candidates == null || candidate == null || candidate.isEmpty()) {
            return false;
        }
        for (String existing : candidates) {
            if (candidate.equals(existing)) {
                return true;
            }
        }
        return false;
    }

    private static String[] insertCandidate(String[] existing, String candidate) {
        if (existing == null || existing.length == 0) {
            return new String[]{candidate};
        }
        if (containsCandidate(existing, candidate)) {
            return existing;
        }
        int nextLength = Math.min(existing.length + 1, MAX_CANDIDATES_PER_PINYIN);
        String[] updated = new String[nextLength];
        updated[0] = candidate;
        System.arraycopy(existing, 0, updated, 1, nextLength - 1);
        return updated;
    }

    private static String toDigits(String pinyin) {
        StringBuilder digits = new StringBuilder(pinyin.length());
        for (int i = 0; i < pinyin.length(); i++) {
            Character digit = LETTER_TO_DIGIT.get(pinyin.charAt(i));
            if (digit == null) {
                return null;
            }
            digits.append(digit.charValue());
        }
        return digits.toString();
    }

    private static Map<Character, Character> createLetterToDigitMap() {
        Map<Character, Character> map = new HashMap<>();
        String[] groups = {"abc", "def", "ghi", "jkl", "mno", "pqrs", "tuv", "wxyz"};
        for (int i = 0; i < groups.length; i++) {
            char digit = (char) ('2' + i);
            for (char letter : groups[i].toCharArray()) {
                map.put(letter, digit);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void logRebuild(
            String reason,
            long startedAtNanos,
            long heapBefore,
            int keyCount,
            int prefixIndexSize) {
        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        long heapAfter = usedHeapBytes();
        Log.i(
                TAG,
                "rebuild reason=" + reason
                        + " durationMs=" + durationMs
                        + " keys=" + keyCount
                        + " prefixIndexSize=" + prefixIndexSize
                        + " heapBefore=" + heapBefore
                        + " heapAfter=" + heapAfter);
    }
}
