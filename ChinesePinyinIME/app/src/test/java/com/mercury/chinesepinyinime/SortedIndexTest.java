package com.mercury.chinesepinyinime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SortedIndexTest {
    @Test
    public void prefixIndex_returnsKeysStartingWithPrefixInRankOrder() {
        SortedPrefixIndex index = SortedPrefixIndex.fromKeys(
                Arrays.asList("shi", "shang", "shen", "si", "nihao"));

        List<String> matches = index.lookup("sh", 10, String::compareTo);

        assertEquals(Arrays.asList("shang", "shen", "shi"), matches);
        assertFalse(matches.contains("si"));
        assertFalse(matches.contains("nihao"));
    }

    @Test
    public void prefixIndex_withKey_addsWithoutDuplicates() {
        SortedPrefixIndex index = SortedPrefixIndex.fromKeys(Collections.singletonList("ni"));
        SortedPrefixIndex next = index.withKey("nihao").withKey("ni");

        List<String> matches = next.lookup("ni", 10, String::compareTo);
        assertEquals(Arrays.asList("ni", "nihao"), matches);
        assertEquals(2, next.size());
    }

    @Test
    public void digitIndex_exactAndPrefix() {
        Map<String, String[]> words = new HashMap<>();
        words.put("ni", new String[]{"你"});
        words.put("mi", new String[]{"米"});
        words.put("nihao", new String[]{"你好"});

        SortedDigitIndex index = SortedDigitIndex.fromWords(
                words,
                pinyin -> {
                    if ("ni".equals(pinyin)) {
                        return "64";
                    }
                    if ("mi".equals(pinyin)) {
                        return "64";
                    }
                    if ("nihao".equals(pinyin)) {
                        return "64426";
                    }
                    return null;
                },
                String::compareTo);

        List<String> exact = index.exact("64");
        assertEquals(2, exact.size());
        assertTrue(exact.contains("mi"));
        assertTrue(exact.contains("ni"));

        List<String> prefix = index.prefix("64", 10, String::compareTo);
        assertEquals(Collections.singletonList("nihao"), prefix);
    }

    @Test
    public void digitIndex_withKey_addsNewPinyin() {
        Map<String, String[]> words = new HashMap<>();
        words.put("ni", new String[]{"你"});
        SortedDigitIndex index = SortedDigitIndex.fromWords(
                words,
                pinyin -> "ni".equals(pinyin) ? "64" : null,
                String::compareTo);

        SortedDigitIndex next = index.withKey("mi", "64", String::compareTo);
        List<String> exact = next.exact("64");
        assertEquals(2, exact.size());
        assertTrue(exact.contains("mi"));
        assertTrue(exact.contains("ni"));
    }
}
