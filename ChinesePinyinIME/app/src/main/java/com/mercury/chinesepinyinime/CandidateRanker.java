package com.mercury.chinesepinyinime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CandidateRanker {
    private static final Map<String, String[]> MANUAL_OVERRIDES = createManualOverrides();

    private CandidateRanker() {
    }

    public static String[] rank(String pinyin, String[] candidates) {
        if (candidates == null || candidates.length <= 1) {
            return candidates;
        }

        UserFrequencyStore frequencyStore = UserFrequencyStore.getInstance();
        String[] overrideOrder = MANUAL_OVERRIDES.get(pinyin);
        List<ScoredCandidate> scoredCandidates = new ArrayList<>(candidates.length);

        for (int index = 0; index < candidates.length; index++) {
            String candidate = candidates[index];
            int score = (candidates.length - index) * 10;
            score += lengthBonus(candidate);
            score += overrideBonus(candidate, overrideOrder);
            score += frequencyStore.getBoost(pinyin, candidate);
            scoredCandidates.add(new ScoredCandidate(candidate, score, index));
        }

        Collections.sort(scoredCandidates, (left, right) -> {
            if (left.score != right.score) {
                return Integer.compare(right.score, left.score);
            }
            return Integer.compare(left.originalIndex, right.originalIndex);
        });

        String[] ranked = new String[candidates.length];
        for (int i = 0; i < scoredCandidates.size(); i++) {
            ranked[i] = scoredCandidates.get(i).candidate;
        }
        return ranked;
    }

    private static int lengthBonus(String candidate) {
        int length = candidate.length();
        if (length == 1 || length == 2) {
            return 5;
        }
        if (length == 3) {
            return 2;
        }
        return 0;
    }

    private static int overrideBonus(String candidate, String[] overrideOrder) {
        if (overrideOrder == null) {
            return 0;
        }
        for (int i = 0; i < overrideOrder.length; i++) {
            if (candidate.equals(overrideOrder[i])) {
                return 120 - (i * 8);
            }
        }
        return 0;
    }

    private static Map<String, String[]> createManualOverrides() {
        Map<String, String[]> overrides = new HashMap<>();
        overrides.put("ni", arr("你", "泥", "尼", "拟", "逆"));
        overrides.put("hao", arr("好", "号", "浩", "毫"));
        overrides.put("shi", arr("是", "时", "事", "十", "实"));
        overrides.put("yi", arr("一", "以", "已", "意", "易"));
        overrides.put("de", arr("的", "得", "地"));
        overrides.put("wo", arr("我", "握", "窝"));
        overrides.put("ta", arr("他", "她", "它", "塔"));
        overrides.put("ma", arr("吗", "妈", "马", "嘛"));
        overrides.put("le", arr("了", "乐"));
        overrides.put("bu", arr("不", "部", "步", "布"));
        overrides.put("zai", arr("在", "再", "载"));
        overrides.put("you", arr("有", "又", "右", "由"));
        overrides.put("shuo", arr("说", "硕"));
        overrides.put("ren", arr("人", "认", "任"));
        overrides.put("dou", arr("都", "斗", "豆"));
        overrides.put("hen", arr("很", "狠"));
        overrides.put("mao", arr("吗", "毛", "猫"));
        overrides.put("ne", arr("呢", "讷"));
        overrides.put("ba", arr("吧", "把", "爸", "八"));
        overrides.put("ge", arr("个", "各", "哥", "歌"));
        return Collections.unmodifiableMap(overrides);
    }

    private static String[] arr(String... values) {
        return Arrays.copyOf(values, values.length);
    }

    private static final class ScoredCandidate {
        private final String candidate;
        private final int score;
        private final int originalIndex;

        private ScoredCandidate(String candidate, int score, int originalIndex) {
            this.candidate = candidate;
            this.score = score;
            this.originalIndex = originalIndex;
        }
    }
}
