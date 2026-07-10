package com.mercury.chinesepinyinime;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DictionaryTsvCodecTest {
    @Test
    public void parse_keepsValidRowsAndHighestDuplicateWeight() throws Exception {
        String source = "# comment\n"
                + "ni\t你\t3\n"
                + "NI\t你\t8\n"
                + "ni hao\t你好\t5\n"
                + "hao\t好\tbad\n"
                + "hao\t好\t2\n";

        DictionaryTsvCodec.ParseResult result =
                DictionaryTsvCodec.parse(new StringReader(source));

        assertEquals(2, result.getValidEntries());
        assertEquals(5, result.getDataRows());
        assertEquals(2, result.getRejectedRows());
        assertEquals(1, result.getDuplicateRows());
        assertEquals(Integer.valueOf(8), result.getEntries().get("ni").get("你"));
    }

    @Test
    public void writeCombined_manualEntryWinsAndOutputCanBeParsed() throws Exception {
        Map<String, Map<String, Integer>> manual = weighted("ni", "妮", 100);
        Map<String, Map<String, Integer>> learned = weighted("ni", "妮", 1);
        learned.get("ni").put("你", 9);
        StringWriter writer = new StringWriter();

        int written = DictionaryTsvCodec.writeCombined(writer, manual, learned);
        DictionaryTsvCodec.ParseResult reparsed =
                DictionaryTsvCodec.parse(new StringReader(writer.toString()));

        assertEquals(2, written);
        assertEquals(2, reparsed.getValidEntries());
        assertEquals(Integer.valueOf(100), reparsed.getEntries().get("ni").get("妮"));
        assertEquals(Integer.valueOf(9), reparsed.getEntries().get("ni").get("你"));
    }

    private static Map<String, Map<String, Integer>> weighted(
            String pinyin,
            String candidate,
            int weight) {
        Map<String, Integer> candidates = new LinkedHashMap<>();
        candidates.put(candidate, weight);
        Map<String, Map<String, Integer>> entries = new LinkedHashMap<>();
        entries.put(pinyin, candidates);
        return entries;
    }
}
