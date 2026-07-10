package com.mercury.chinesepinyinime;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class DictionaryTsvCodec {
    private static final int MAX_ENTRIES = 50_000;
    private static final int MAX_LINE_LENGTH = 4_096;
    private static final int MAX_PINYIN_LENGTH = 64;
    private static final int MAX_CANDIDATE_LENGTH = 64;
    private static final int MAX_WEIGHT = 1_000_000;
    private static final Pattern PINYIN_PATTERN = Pattern.compile("[a-z]+");

    private DictionaryTsvCodec() {
    }

    static ParseResult parse(Reader source) throws IOException {
        Map<String, Map<String, Integer>> entries = new LinkedHashMap<>();
        int dataRows = 0;
        int rejectedRows = 0;
        int duplicateRows = 0;
        int uniqueEntries = 0;

        try (BufferedReader reader = new BufferedReader(source)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                dataRows++;
                if (line.length() > MAX_LINE_LENGTH) {
                    rejectedRows++;
                    continue;
                }

                String[] fields = line.split("\t", -1);
                if (fields.length != 3) {
                    rejectedRows++;
                    continue;
                }
                String pinyin = stripBom(fields[0].trim()).toLowerCase(Locale.ROOT);
                String candidate = fields[1].trim();
                Integer weight = parseWeight(fields[2].trim());
                if (!isValidPinyin(pinyin) || !isValidCandidate(candidate) || weight == null) {
                    rejectedRows++;
                    continue;
                }

                Map<String, Integer> candidates =
                        entries.computeIfAbsent(pinyin, key -> new LinkedHashMap<>());
                Integer previousWeight = candidates.get(candidate);
                if (previousWeight != null) {
                    duplicateRows++;
                    if (weight > previousWeight) {
                        candidates.put(candidate, weight);
                    }
                    continue;
                }
                if (uniqueEntries >= MAX_ENTRIES) {
                    rejectedRows++;
                    continue;
                }
                candidates.put(candidate, weight);
                uniqueEntries++;
            }
        }

        Map<String, Map<String, Integer>> orderedEntries = orderEntries(entries);
        return new ParseResult(
                orderedEntries,
                countEntries(orderedEntries),
                dataRows,
                rejectedRows,
                duplicateRows);
    }

    static int writeCombined(
            Writer writer,
            Map<String, Map<String, Integer>> manualEntries,
            Map<String, Map<String, Integer>> learnedEntries) throws IOException {
        Map<String, Map<String, Integer>> combined = deepCopy(learnedEntries);
        for (Map.Entry<String, Map<String, Integer>> entry : manualEntries.entrySet()) {
            Map<String, Integer> candidates =
                    combined.computeIfAbsent(entry.getKey(), key -> new LinkedHashMap<>());
            candidates.putAll(entry.getValue());
        }
        Map<String, Map<String, Integer>> ordered = orderEntries(combined);

        writer.write("# ChinesePinyinIME custom dictionary\n");
        writer.write("# UTF-8 TSV format: pinyin<TAB>candidate<TAB>weight\n");
        writer.write("# Import replaces the current manual dictionary. Manual entries outrank learned and built-in entries.\n");
        int written = 0;
        for (Map.Entry<String, Map<String, Integer>> entry : ordered.entrySet()) {
            for (Map.Entry<String, Integer> candidate : entry.getValue().entrySet()) {
                if (!isValidPinyin(entry.getKey())
                        || !isValidCandidate(candidate.getKey())
                        || candidate.getValue() == null
                        || candidate.getValue() <= 0) {
                    continue;
                }
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(candidate.getKey());
                writer.write('\t');
                writer.write(Integer.toString(Math.min(candidate.getValue(), MAX_WEIGHT)));
                writer.write('\n');
                written++;
            }
        }
        writer.flush();
        return written;
    }

    static Map<String, List<String>> toOrderedCandidateLists(
            Map<String, Map<String, Integer>> weightedEntries) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : orderEntries(weightedEntries).entrySet()) {
            result.put(entry.getKey(), new ArrayList<>(entry.getValue().keySet()));
        }
        return Collections.unmodifiableMap(result);
    }

    static Map<String, Map<String, Integer>> deepCopy(
            Map<String, Map<String, Integer>> source) {
        Map<String, Map<String, Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : source.entrySet()) {
            copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
        }
        return copy;
    }

    private static Map<String, Map<String, Integer>> orderEntries(
            Map<String, Map<String, Integer>> source) {
        List<String> pinyinKeys = new ArrayList<>(source.keySet());
        Collections.sort(pinyinKeys);
        Map<String, Map<String, Integer>> ordered = new LinkedHashMap<>();
        Comparator<Map.Entry<String, Integer>> candidateOrder = Comparator
                .<Map.Entry<String, Integer>>comparingInt(entry -> entry.getValue())
                .reversed()
                .thenComparing(Map.Entry::getKey);
        for (String pinyin : pinyinKeys) {
            List<Map.Entry<String, Integer>> candidates =
                    new ArrayList<>(source.get(pinyin).entrySet());
            candidates.sort(candidateOrder);
            Map<String, Integer> orderedCandidates = new LinkedHashMap<>();
            for (Map.Entry<String, Integer> candidate : candidates) {
                orderedCandidates.put(candidate.getKey(), candidate.getValue());
            }
            if (!orderedCandidates.isEmpty()) {
                ordered.put(pinyin, orderedCandidates);
            }
        }
        return ordered;
    }

    private static Integer parseWeight(String text) {
        try {
            int weight = Integer.parseInt(text);
            return weight > 0 && weight <= MAX_WEIGHT ? weight : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean isValidPinyin(String pinyin) {
        return !pinyin.isEmpty()
                && pinyin.length() <= MAX_PINYIN_LENGTH
                && PINYIN_PATTERN.matcher(pinyin).matches();
    }

    private static boolean isValidCandidate(String candidate) {
        if (candidate.isEmpty() || candidate.length() > MAX_CANDIDATE_LENGTH) {
            return false;
        }
        for (int i = 0; i < candidate.length(); i++) {
            if (Character.isISOControl(candidate.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static String stripBom(String text) {
        return !text.isEmpty() && text.charAt(0) == '\uFEFF' ? text.substring(1) : text;
    }

    private static int countEntries(Map<String, Map<String, Integer>> entries) {
        int count = 0;
        for (Map<String, Integer> candidates : entries.values()) {
            count += candidates.size();
        }
        return count;
    }

    static final class ParseResult {
        private final Map<String, Map<String, Integer>> entries;
        private final int validEntries;
        private final int dataRows;
        private final int rejectedRows;
        private final int duplicateRows;

        private ParseResult(
                Map<String, Map<String, Integer>> entries,
                int validEntries,
                int dataRows,
                int rejectedRows,
                int duplicateRows) {
            this.entries = entries;
            this.validEntries = validEntries;
            this.dataRows = dataRows;
            this.rejectedRows = rejectedRows;
            this.duplicateRows = duplicateRows;
        }

        Map<String, Map<String, Integer>> getEntries() {
            return deepCopy(entries);
        }

        int getValidEntries() {
            return validEntries;
        }

        int getDataRows() {
            return dataRows;
        }

        int getRejectedRows() {
            return rejectedRows;
        }

        int getDuplicateRows() {
            return duplicateRows;
        }
    }
}
