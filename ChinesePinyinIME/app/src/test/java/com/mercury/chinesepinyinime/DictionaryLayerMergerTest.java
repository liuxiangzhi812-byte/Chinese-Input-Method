package com.mercury.chinesepinyinime;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;

public class DictionaryLayerMergerTest {
    @Test
    public void merge_ordersManualThenLearnedThenBuiltInAndDeduplicates() {
        Map<String, String[]> builtIn = new LinkedHashMap<>();
        builtIn.put("ni", new String[]{"你", "泥", "尼"});
        Map<String, List<String>> learned = new LinkedHashMap<>();
        learned.put("ni", Arrays.asList("泥", "呢"));
        Map<String, List<String>> manual = new LinkedHashMap<>();
        manual.put("ni", Arrays.asList("妮", "你"));

        Map<String, String[]> merged =
                DictionaryLayerMerger.merge(builtIn, learned, manual, 5);

        assertArrayEquals(
                new String[]{"妮", "你", "泥", "呢", "尼"},
                merged.get("ni"));
    }

    @Test
    public void prioritize_restoresStrictSourcePriorityAfterRanking() {
        String[] ranked = new String[]{"你", "泥", "妮", "呢"};
        Map<String, List<String>> learned =
                Collections.singletonMap("ni", Collections.singletonList("呢"));
        Map<String, List<String>> manual =
                Collections.singletonMap("ni", Collections.singletonList("妮"));

        assertArrayEquals(
                new String[]{"妮", "呢", "你", "泥"},
                DictionaryLayerMerger.prioritize("ni", ranked, learned, manual));
    }
}
