package com.mercury.chinesepinyinime;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Compact pinyin-prefix lookup backed by a single sorted key array.
 * Avoids materializing every prefix as a {@code HashMap} bucket of full keys.
 */
final class SortedPrefixIndex {
    private static final SortedPrefixIndex EMPTY = new SortedPrefixIndex(new String[0]);

    private final String[] sortedKeys;

    private SortedPrefixIndex(String[] sortedKeys) {
        this.sortedKeys = sortedKeys;
    }

    static SortedPrefixIndex empty() {
        return EMPTY;
    }

    static SortedPrefixIndex fromKeys(Collection<String> keys) {
        if (keys == null || keys.isEmpty()) {
            return EMPTY;
        }
        String[] sorted = keys.toArray(new String[0]);
        Arrays.sort(sorted);
        return new SortedPrefixIndex(sorted);
    }

    SortedPrefixIndex withKey(String key) {
        if (key == null || key.isEmpty()) {
            return this;
        }
        int index = Arrays.binarySearch(sortedKeys, key);
        if (index >= 0) {
            return this;
        }
        String[] next = new String[sortedKeys.length + 1];
        System.arraycopy(sortedKeys, 0, next, 0, sortedKeys.length);
        next[sortedKeys.length] = key;
        Arrays.sort(next);
        return new SortedPrefixIndex(next);
    }

    List<String> lookup(String prefix, int limit, Comparator<String> ranker) {
        if (prefix == null || prefix.isEmpty() || sortedKeys.length == 0 || limit <= 0) {
            return Collections.emptyList();
        }

        int start = lowerBound(prefix);
        List<String> matches = new ArrayList<>();
        for (int i = start; i < sortedKeys.length; i++) {
            String key = sortedKeys[i];
            if (!key.startsWith(prefix)) {
                break;
            }
            matches.add(key);
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
        return sortedKeys.length;
    }

    private int lowerBound(String prefix) {
        int low = 0;
        int high = sortedKeys.length;
        while (low < high) {
            int mid = (low + high) >>> 1;
            if (sortedKeys[mid].compareTo(prefix) < 0) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }
}
