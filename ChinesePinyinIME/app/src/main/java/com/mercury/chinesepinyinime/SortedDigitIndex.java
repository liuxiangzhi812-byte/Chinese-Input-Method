package com.mercury.chinesepinyinime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Compact digit and digit-prefix lookup: parallel arrays sorted by digit string.
 * Exact digit matches and digit-prefix matches share one representation.
 */
final class SortedDigitIndex {
    private static final SortedDigitIndex EMPTY = new SortedDigitIndex(new String[0], new String[0]);

    private final String[] digits;
    private final String[] pinyinKeys;

    private SortedDigitIndex(String[] digits, String[] pinyinKeys) {
        this.digits = digits;
        this.pinyinKeys = pinyinKeys;
    }

    static SortedDigitIndex empty() {
        return EMPTY;
    }

    static SortedDigitIndex fromWords(
            Map<String, String[]> words,
            DigitEncoder encoder,
            Comparator<String> ranker) {
        if (words == null || words.isEmpty()) {
            return EMPTY;
        }

        List<Entry> entries = new ArrayList<>(words.size());
        for (String pinyin : words.keySet()) {
            String digitString = encoder.toDigits(pinyin);
            if (digitString != null) {
                entries.add(new Entry(digitString, pinyin));
            }
        }
        if (entries.isEmpty()) {
            return EMPTY;
        }

        entries.sort((left, right) -> {
            int digitCompare = left.digits.compareTo(right.digits);
            if (digitCompare != 0) {
                return digitCompare;
            }
            return ranker.compare(left.pinyin, right.pinyin);
        });

        String[] digits = new String[entries.size()];
        String[] pinyinKeys = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            digits[i] = entries.get(i).digits;
            pinyinKeys[i] = entries.get(i).pinyin;
        }
        return new SortedDigitIndex(digits, pinyinKeys);
    }

    SortedDigitIndex withKey(String pinyin, String digitString, Comparator<String> ranker) {
        if (pinyin == null || pinyin.isEmpty() || digitString == null || digitString.isEmpty()) {
            return this;
        }
        for (int i = 0; i < pinyinKeys.length; i++) {
            if (digitString.equals(digits[i]) && pinyin.equals(pinyinKeys[i])) {
                return this;
            }
        }

        Entry[] entries = new Entry[pinyinKeys.length + 1];
        for (int i = 0; i < pinyinKeys.length; i++) {
            entries[i] = new Entry(digits[i], pinyinKeys[i]);
        }
        entries[pinyinKeys.length] = new Entry(digitString, pinyin);
        Arrays.sort(entries, (left, right) -> {
            int digitCompare = left.digits.compareTo(right.digits);
            if (digitCompare != 0) {
                return digitCompare;
            }
            return ranker.compare(left.pinyin, right.pinyin);
        });

        String[] nextDigits = new String[entries.length];
        String[] nextPinyin = new String[entries.length];
        for (int i = 0; i < entries.length; i++) {
            nextDigits[i] = entries[i].digits;
            nextPinyin[i] = entries[i].pinyin;
        }
        return new SortedDigitIndex(nextDigits, nextPinyin);
    }

    List<String> exact(String digitString) {
        if (digitString == null || digitString.isEmpty() || digits.length == 0) {
            return Collections.emptyList();
        }
        int start = lowerBound(digitString);
        List<String> matches = new ArrayList<>();
        for (int i = start; i < digits.length; i++) {
            if (!digitString.equals(digits[i])) {
                break;
            }
            matches.add(pinyinKeys[i]);
        }
        return matches.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(matches);
    }

    List<String> prefix(String digitsPrefix, int limit, Comparator<String> ranker) {
        if (digitsPrefix == null || digitsPrefix.isEmpty() || digits.length == 0 || limit <= 0) {
            return Collections.emptyList();
        }
        int start = lowerBound(digitsPrefix);
        List<String> matches = new ArrayList<>();
        for (int i = start; i < digits.length; i++) {
            if (!digits[i].startsWith(digitsPrefix)) {
                break;
            }
            // Exact full-digit keys are served by exact(); keep prefix matches as longer keys.
            if (digits[i].length() > digitsPrefix.length()) {
                matches.add(pinyinKeys[i]);
            }
        }
        if (matches.isEmpty()) {
            return Collections.emptyList();
        }
        if (ranker != null) {
            matches.sort(ranker);
        }
        if (matches.size() > limit) {
            return Collections.unmodifiableList(new ArrayList<>(matches.subList(0, limit)));
        }
        return Collections.unmodifiableList(matches);
    }

    int size() {
        return pinyinKeys.length;
    }

    private int lowerBound(String target) {
        int low = 0;
        int high = digits.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (digits[mid].compareTo(target) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    interface DigitEncoder {
        String toDigits(String pinyin);
    }

    private static final class Entry {
        private final String digits;
        private final String pinyin;

        private Entry(String digits, String pinyin) {
            this.digits = digits;
            this.pinyin = pinyin;
        }
    }
}
