package com.mercury.chinesepinyinime;

import android.content.Context;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserDictionaryStore {
    private static final String STORE_FILE_NAME = "user_dictionary.tsv";
    private static final UserDictionaryStore INSTANCE = new UserDictionaryStore();

    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Map<String, Integer>> entryCounts = new HashMap<>();
    private File storeFile;
    private boolean loaded;
    private boolean dirty;

    private UserDictionaryStore() {
    }

    public static UserDictionaryStore getInstance() {
        return INSTANCE;
    }

    public void load(Context context) {
        synchronized (lock) {
            if (loaded) {
                return;
            }
            storeFile = new File(context.getApplicationContext().getFilesDir(), STORE_FILE_NAME);
            entryCounts.clear();
            if (storeFile.exists()) {
                readStoreFile();
            }
            loaded = true;
            dirty = false;
        }
    }

    public void recordEntry(String pinyin, String candidate) {
        if (pinyin == null || pinyin.isEmpty() || candidate == null || candidate.isEmpty()) {
            return;
        }

        synchronized (lock) {
            Map<String, Integer> candidates =
                    entryCounts.computeIfAbsent(pinyin, key -> new HashMap<>());
            int nextCount = candidates.getOrDefault(candidate, 0) + 1;
            candidates.put(candidate, nextCount);
            dirty = true;
        }
        schedulePersist();
    }

    public Map<String, List<String>> snapshotOrderedEntries() {
        synchronized (lock) {
            Map<String, List<String>> snapshot = new LinkedHashMap<>();
            List<String> pinyinKeys = new ArrayList<>(entryCounts.keySet());
            Collections.sort(pinyinKeys);
            for (String pinyin : pinyinKeys) {
                List<String> rankedCandidates = sortCandidatesByCount(entryCounts.get(pinyin));
                if (!rankedCandidates.isEmpty()) {
                    snapshot.put(pinyin, rankedCandidates);
                }
            }
            return snapshot;
        }
    }

    public int getLearnedPhraseCount(Context context) {
        load(context);
        synchronized (lock) {
            int total = 0;
            for (Map<String, Integer> candidates : entryCounts.values()) {
                total += candidates.size();
            }
            return total;
        }
    }

    public void clear(Context context) {
        load(context);
        File fileToDelete;
        synchronized (lock) {
            entryCounts.clear();
            dirty = false;
            fileToDelete = storeFile;
        }
        executor.execute(() -> {
            if (fileToDelete != null && fileToDelete.exists()) {
                //noinspection ResultOfMethodCallIgnored
                fileToDelete.delete();
            }
        });
    }

    public void flush() {
        persistIfNeeded();
    }

    private void schedulePersist() {
        executor.execute(this::persistIfNeeded);
    }

    private void persistIfNeeded() {
        Map<String, Map<String, Integer>> snapshot;
        synchronized (lock) {
            if (!dirty || storeFile == null) {
                return;
            }
            snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Integer>> entry : entryCounts.entrySet()) {
                snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            dirty = false;
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(storeFile), StandardCharsets.UTF_8))) {
            List<String> pinyinKeys = new ArrayList<>(snapshot.keySet());
            Collections.sort(pinyinKeys);
            for (String pinyin : pinyinKeys) {
                for (String candidate : sortCandidatesByCount(snapshot.get(pinyin))) {
                    int count = snapshot.get(pinyin).getOrDefault(candidate, 0);
                    if (count <= 0) {
                        continue;
                    }
                    writer.write(pinyin);
                    writer.write('\t');
                    writer.write(candidate);
                    writer.write('\t');
                    writer.write(Integer.toString(count));
                    writer.newLine();
                }
            }
        } catch (IOException ignored) {
            synchronized (lock) {
                dirty = true;
            }
        }
    }

    private void readStoreFile() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(storeFile), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmedLine = line.trim();
                if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                    continue;
                }

                String[] fields = line.split("\t", -1);
                if (fields.length != 3) {
                    continue;
                }

                String pinyin = fields[0].trim();
                String candidate = fields[1].trim();
                if (pinyin.isEmpty() || candidate.isEmpty()) {
                    continue;
                }

                try {
                    int count = Integer.parseInt(fields[2].trim());
                    if (count > 0) {
                        entryCounts
                                .computeIfAbsent(pinyin, key -> new HashMap<>())
                                .put(candidate, count);
                    }
                } catch (NumberFormatException ignored) {
                    // Keep other user-dictionary rows even if one line is malformed.
                }
            }
        } catch (IOException ignored) {
            // Keep the in-memory store empty if the file cannot be read.
        }
    }

    private static List<String> sortCandidatesByCount(Map<String, Integer> counts) {
        if (counts == null || counts.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        Collections.sort(entries, (left, right) -> {
            if (!left.getValue().equals(right.getValue())) {
                return Integer.compare(right.getValue(), left.getValue());
            }
            return left.getKey().compareTo(right.getKey());
        });

        List<String> rankedCandidates = new ArrayList<>(entries.size());
        for (Map.Entry<String, Integer> entry : entries) {
            rankedCandidates.add(entry.getKey());
        }
        return rankedCandidates;
    }
}
