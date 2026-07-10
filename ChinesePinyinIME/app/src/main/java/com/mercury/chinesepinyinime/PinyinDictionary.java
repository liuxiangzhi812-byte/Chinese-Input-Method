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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PinyinDictionary {
    private static final String DICTIONARY_ASSET_NAME = "pinyin_dict.txt";
    private static final int MAX_CANDIDATES_PER_PINYIN = 20;
    private static final int MAX_PREFIX_PYNYIN_KEYS = 24;
    private static final int MAX_PREFIX_CANDIDATES = 40;
    private static final Map<Character, Character> LETTER_TO_DIGIT = createLetterToDigitMap();
    private static final PinyinDictionary INSTANCE = new PinyinDictionary();

    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<Runnable> pendingCallbacks = new ArrayList<>();
    private volatile Map<String, String[]> builtInCandidateWords = createFallbackCandidateWords();
    private volatile Map<String, String[]> candidateWords = builtInCandidateWords;
    private volatile Map<String, List<String>> digitToPinyinIndex = buildDigitIndex(candidateWords);
    private volatile Map<String, List<String>> pinyinPrefixIndex = buildPinyinPrefixIndex(candidateWords);
    private volatile Map<String, List<String>> digitPrefixIndex = buildDigitPrefixIndex(candidateWords);
    private volatile Map<String, String[]> singleSyllableCandidateWords =
            buildSingleSyllableCandidateWords(candidateWords);
    private volatile Map<String, List<String>> singleSyllableDigitIndex =
            buildDigitIndex(singleSyllableCandidateWords);
    private volatile Map<String, List<String>> singleSyllableDigitPrefixIndex =
            buildDigitPrefixIndex(singleSyllableCandidateWords);
    private boolean loadingStarted;
    private boolean loadFinished;

    private PinyinDictionary() {
    }

    public static PinyinDictionary getInstance() {
        return INSTANCE;
    }

    public void loadAsync(Context context, Runnable onLoaded) {
        Context appContext = context.getApplicationContext();
        UserFrequencyStore.getInstance().load(appContext);
        UserDictionaryStore.getInstance().load(appContext);
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
        String[] candidates = candidateWords.get(pinyin);
        if (candidates == null) {
            return null;
        }
        return CandidateRanker.rank(pinyin, candidates);
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

    /**
     * Resolves a 9-key digit sequence (e.g. "64") to the best matching dictionary
     * pinyin key (e.g. "ni"), or null if no dictionary key maps to these digits.
     * When a digit sequence is ambiguous, the first entry of
     * {@link #getPinyinKeysForDigits} wins; see {@link #buildDigitIndex} for the
     * tie-break order.
     */
    public String resolveBestPinyinForDigits(String digits) {
        List<String> matches = getPinyinKeysForDigits(digits);
        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Returns every dictionary pinyin key that maps to {@code digits}, ordered by
     * the same tie-break {@link #resolveBestPinyinForDigits} uses to pick its
     * default. Used by the 9-key pinyin-choice UI to let the user pick a
     * different pinyin than the default when a digit sequence is ambiguous
     * (e.g. "64" matches both "ni" and "mi"). Empty if there is no match.
     */
    public List<String> getPinyinKeysForDigits(String digits) {
        if (digits == null || digits.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> matches = digitToPinyinIndex.get(digits);
        return matches == null ? Collections.emptyList() : Collections.unmodifiableList(matches);
    }

    private List<String> getMultiSyllablePinyinKeysForDigits(String digits) {
        List<String> matches = getPinyinKeysForDigits(digits);
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> multiSyllableMatches = new ArrayList<>();
        for (String pinyin : matches) {
            if (!singleSyllableCandidateWords.containsKey(pinyin)) {
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
        return getPrefixMatches(pinyinPrefixIndex, prefix);
    }

    public String resolveBestPinyinForDigitPrefix(String digitsPrefix) {
        List<String> matches = getPinyinKeysForDigitPrefix(digitsPrefix);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<String> getPinyinKeysForDigitPrefix(String digitsPrefix) {
        if (digitsPrefix == null || digitsPrefix.isEmpty()) {
            return Collections.emptyList();
        }
        return getPrefixMatches(digitPrefixIndex, digitsPrefix);
    }

    public String resolveBestLeadingSingleSyllableForDigits(String digits) {
        List<String> matches = getLeadingSingleSyllablePinyinKeys(digits);
        return matches.isEmpty() ? null : matches.get(0);
    }

    public List<String> getLeadingSingleSyllablePinyinKeys(String digits) {
        if (digits == null || digits.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> dictionaryAlignedMatches =
                getDictionaryAlignedLeadingSingleSyllableMatches(digits);

        List<String> exactMatches = getPinyinKeysForDigits(singleSyllableDigitIndex, digits);
        if (!exactMatches.isEmpty()) {
            return prependDistinctMatches(dictionaryAlignedMatches, exactMatches);
        }

        List<String> prefixMatches = getPrefixMatches(singleSyllableDigitPrefixIndex, digits);
        if (!prefixMatches.isEmpty()) {
            return prependDistinctMatches(dictionaryAlignedMatches, prefixMatches);
        }

        return prependDistinctMatches(
                dictionaryAlignedMatches,
                getLeadingConsumedSyllableMatches(digits));
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
        if (containsCandidate(candidateWords.get(pinyin), candidate)) {
            return;
        }

        executor.execute(() -> mergeUserCandidateIntoRuntime(pinyin, candidate));
    }

    private void finishLoad(Map<String, String[]> loadedWords) {
        List<Runnable> callbacks;
        synchronized (lock) {
            builtInCandidateWords = loadedWords.isEmpty()
                    ? createFallbackCandidateWords()
                    : loadedWords;
            applyCandidateWords(mergeUserDictionaryWords(
                    builtInCandidateWords,
                    UserDictionaryStore.getInstance().snapshotOrderedEntries()));
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

    private void mergeUserCandidateIntoRuntime(String pinyin, String candidate) {
        synchronized (lock) {
            if (containsCandidate(candidateWords.get(pinyin), candidate)) {
                return;
            }
            Map<String, String[]> updatedWords = new HashMap<>(candidateWords);
            updatedWords.put(pinyin, prependCandidate(updatedWords.get(pinyin), candidate));
            applyCandidateWords(Collections.unmodifiableMap(updatedWords));
        }
    }

    private void applyCandidateWords(Map<String, String[]> words) {
        candidateWords = words;
        digitToPinyinIndex = buildDigitIndex(words);
        pinyinPrefixIndex = buildPinyinPrefixIndex(words);
        digitPrefixIndex = buildDigitPrefixIndex(words);
        singleSyllableCandidateWords = buildSingleSyllableCandidateWords(words);
        singleSyllableDigitIndex = buildDigitIndex(singleSyllableCandidateWords);
        singleSyllableDigitPrefixIndex = buildDigitPrefixIndex(singleSyllableCandidateWords);
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

    private static Map<String, String[]> mergeUserDictionaryWords(
            Map<String, String[]> builtInWords,
            Map<String, List<String>> userWords) {
        if (userWords.isEmpty()) {
            return builtInWords;
        }

        Map<String, String[]> mergedWords = new HashMap<>(builtInWords);
        for (Map.Entry<String, List<String>> entry : userWords.entrySet()) {
            String pinyin = entry.getKey();
            List<String> userCandidates = entry.getValue();
            if (pinyin == null || pinyin.isEmpty() || userCandidates == null || userCandidates.isEmpty()) {
                continue;
            }

            List<String> mergedCandidates = new ArrayList<>(MAX_CANDIDATES_PER_PINYIN);
            Set<String> seenCandidates = new LinkedHashSet<>();
            for (String candidate : userCandidates) {
                if (candidate == null || candidate.isEmpty()) {
                    continue;
                }
                if (seenCandidates.add(candidate)) {
                    mergedCandidates.add(candidate);
                    if (mergedCandidates.size() >= MAX_CANDIDATES_PER_PINYIN) {
                        break;
                    }
                }
            }

            if (mergedCandidates.size() < MAX_CANDIDATES_PER_PINYIN) {
                String[] builtInCandidates = builtInWords.get(pinyin);
                if (builtInCandidates != null) {
                    for (String candidate : builtInCandidates) {
                        if (seenCandidates.add(candidate)) {
                            mergedCandidates.add(candidate);
                            if (mergedCandidates.size() >= MAX_CANDIDATES_PER_PINYIN) {
                                break;
                            }
                        }
                    }
                }
            }

            if (!mergedCandidates.isEmpty()) {
                mergedWords.put(pinyin, mergedCandidates.toArray(new String[0]));
            }
        }
        return Collections.unmodifiableMap(mergedWords);
    }

    private static Map<String, List<String>> buildDigitIndex(Map<String, String[]> words) {
        Map<String, List<String>> index = new HashMap<>();
        for (String pinyin : words.keySet()) {
            String digits = toDigits(pinyin);
            if (digits == null) {
                continue;
            }
            index.computeIfAbsent(digits, key -> new ArrayList<>()).add(pinyin);
        }
        sortPinyinKeyBuckets(index, words);
        return index;
    }

    private static Map<String, List<String>> buildPinyinPrefixIndex(Map<String, String[]> words) {
        Map<String, List<String>> index = new HashMap<>();
        for (String pinyin : words.keySet()) {
            for (int prefixLength = 1; prefixLength < pinyin.length(); prefixLength++) {
                String prefix = pinyin.substring(0, prefixLength);
                index.computeIfAbsent(prefix, key -> new ArrayList<>()).add(pinyin);
            }
        }
        sortPinyinKeyBuckets(index, words);
        return index;
    }

    private static Map<String, List<String>> buildDigitPrefixIndex(Map<String, String[]> words) {
        Map<String, List<String>> index = new HashMap<>();
        for (String pinyin : words.keySet()) {
            String digits = toDigits(pinyin);
            if (digits == null) {
                continue;
            }
            for (int prefixLength = 1; prefixLength < digits.length(); prefixLength++) {
                String prefix = digits.substring(0, prefixLength);
                index.computeIfAbsent(prefix, key -> new ArrayList<>()).add(pinyin);
            }
        }
        sortPinyinKeyBuckets(index, words);
        return index;
    }

    private static void sortPinyinKeyBuckets(Map<String, List<String>> index, Map<String, String[]> words) {
        for (List<String> pinyinKeys : index.values()) {
            Collections.sort(pinyinKeys, (left, right) -> comparePinyinKeys(left, right, words));
        }
    }

    private static int comparePinyinKeys(String left, String right, Map<String, String[]> words) {
        // Candidate count is a reasonable proxy for how common/productive a syllable is
        // after the manual-override signal.
        boolean leftOverride = CandidateRanker.hasManualOverride(left);
        boolean rightOverride = CandidateRanker.hasManualOverride(right);
        if (leftOverride != rightOverride) {
            return leftOverride ? -1 : 1;
        }
        int leftCount = words.get(left).length;
        int rightCount = words.get(right).length;
        if (leftCount != rightCount) {
            return Integer.compare(rightCount, leftCount);
        }
        return left.compareTo(right);
    }

    private List<String> getPrefixMatches(Map<String, List<String>> index, String prefix) {
        List<String> matches = index.get(prefix);
        if (matches == null || matches.isEmpty()) {
            return Collections.emptyList();
        }
        int resultSize = Math.min(matches.size(), MAX_PREFIX_PYNYIN_KEYS);
        return Collections.unmodifiableList(new ArrayList<>(matches.subList(0, resultSize)));
    }

    private static List<String> getPinyinKeysForDigits(Map<String, List<String>> index, String digits) {
        if (digits == null || digits.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> matches = index.get(digits);
        return matches == null ? Collections.emptyList() : Collections.unmodifiableList(matches);
    }

    private List<String> getLeadingConsumedSyllableMatches(String digits) {
        List<String> matches = new ArrayList<>();
        Set<String> seenMatches = new LinkedHashSet<>();
        int maxPrefixLength = Math.min(6, digits.length() - 1);
        for (int prefixLength = maxPrefixLength; prefixLength >= 1; prefixLength--) {
            List<String> prefixMatches = getPinyinKeysForDigits(
                    singleSyllableDigitIndex,
                    digits.substring(0, prefixLength));
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

    private List<String> getDictionaryAlignedLeadingSingleSyllableMatches(String digits) {
        List<String> fullPinyinMatches = getPinyinKeysForDigits(digitToPinyinIndex, digits);
        if (fullPinyinMatches.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> alignedMatches = new LinkedHashSet<>();
        for (String fullPinyin : fullPinyinMatches) {
            int maxLeadingLength = Math.min(6, fullPinyin.length() - 1);
            for (int leadingLength = 1; leadingLength <= maxLeadingLength; leadingLength++) {
                String syllable = fullPinyin.substring(0, leadingLength);
                if (!singleSyllableCandidateWords.containsKey(syllable)) {
                    continue;
                }
                String remainingPinyin = fullPinyin.substring(syllable.length());
                if (canSegmentIntoSingleSyllables(remainingPinyin)) {
                    alignedMatches.add(syllable);
                }
            }
        }

        if (alignedMatches.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> orderedMatches = new ArrayList<>(alignedMatches);
        Collections.sort(orderedMatches,
                (left, right) -> comparePinyinKeys(left, right, singleSyllableCandidateWords));
        return Collections.unmodifiableList(orderedMatches);
    }

    private boolean canSegmentIntoSingleSyllables(String pinyin) {
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
                if (singleSyllableCandidateWords.containsKey(pinyin.substring(start, end))) {
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

    private String[] mergeCandidatesForPinyinKeys(List<String> pinyinKeys) {
        if (pinyinKeys.isEmpty()) {
            return null;
        }

        List<String> mergedCandidates = new ArrayList<>();
        Set<String> seenCandidates = new LinkedHashSet<>();
        for (String pinyin : pinyinKeys) {
            String[] rankedCandidates = getCandidates(pinyin);
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

    private static Map<String, String[]> buildSingleSyllableCandidateWords(Map<String, String[]> words) {
        Map<String, String[]> syllableWords = new HashMap<>();
        for (Map.Entry<String, String[]> entry : words.entrySet()) {
            if (isProbableSingleSyllableKey(entry.getKey(), entry.getValue())) {
                syllableWords.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(syllableWords);
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

    private static String[] prependCandidate(String[] existingCandidates, String candidate) {
        List<String> mergedCandidates = new ArrayList<>(MAX_CANDIDATES_PER_PINYIN);
        mergedCandidates.add(candidate);
        if (existingCandidates != null) {
            for (String existing : existingCandidates) {
                if (!candidate.equals(existing)) {
                    mergedCandidates.add(existing);
                    if (mergedCandidates.size() >= MAX_CANDIDATES_PER_PINYIN) {
                        break;
                    }
                }
            }
        }
        return mergedCandidates.toArray(new String[0]);
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
}
