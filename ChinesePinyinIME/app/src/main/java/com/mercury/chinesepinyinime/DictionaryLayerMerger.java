package com.mercury.chinesepinyinime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class DictionaryLayerMerger {
    private DictionaryLayerMerger() {
    }

    static Map<String, String[]> merge(
            Map<String, String[]> builtInWords,
            Map<String, List<String>> learnedWords,
            Map<String, List<String>> manualWords,
            int maxCandidatesPerPinyin) {
        Set<String> allPinyin = new LinkedHashSet<>(builtInWords.keySet());
        allPinyin.addAll(learnedWords.keySet());
        allPinyin.addAll(manualWords.keySet());

        Map<String, String[]> merged = new HashMap<>();
        for (String pinyin : allPinyin) {
            List<String> candidates = new ArrayList<>(maxCandidatesPerPinyin);
            Set<String> seen = new LinkedHashSet<>();
            append(candidates, seen, manualWords.get(pinyin), maxCandidatesPerPinyin);
            append(candidates, seen, learnedWords.get(pinyin), maxCandidatesPerPinyin);
            String[] builtInCandidates = builtInWords.get(pinyin);
            if (builtInCandidates != null) {
                List<String> builtInList = new ArrayList<>(builtInCandidates.length);
                Collections.addAll(builtInList, builtInCandidates);
                append(candidates, seen, builtInList, maxCandidatesPerPinyin);
            }
            if (!candidates.isEmpty()) {
                merged.put(pinyin, candidates.toArray(new String[0]));
            }
        }
        return Collections.unmodifiableMap(merged);
    }

    static String[] prioritize(
            String pinyin,
            String[] rankedCandidates,
            Map<String, List<String>> learnedWords,
            Map<String, List<String>> manualWords) {
        List<String> prioritized = new ArrayList<>(rankedCandidates.length);
        Set<String> available = new LinkedHashSet<>();
        Collections.addAll(available, rankedCandidates);
        Set<String> added = new LinkedHashSet<>();
        appendAvailable(prioritized, added, available, manualWords.get(pinyin));
        appendAvailable(prioritized, added, available, learnedWords.get(pinyin));
        for (String candidate : rankedCandidates) {
            if (added.add(candidate)) {
                prioritized.add(candidate);
            }
        }
        return prioritized.toArray(new String[0]);
    }

    private static void append(
            List<String> target,
            Set<String> seen,
            List<String> source,
            int limit) {
        if (source == null || target.size() >= limit) {
            return;
        }
        for (String candidate : source) {
            if (candidate == null || candidate.isEmpty() || !seen.add(candidate)) {
                continue;
            }
            target.add(candidate);
            if (target.size() >= limit) {
                return;
            }
        }
    }

    private static void appendAvailable(
            List<String> target,
            Set<String> added,
            Set<String> available,
            List<String> source) {
        if (source == null) {
            return;
        }
        for (String candidate : source) {
            if (available.contains(candidate) && added.add(candidate)) {
                target.add(candidate);
            }
        }
    }
}
