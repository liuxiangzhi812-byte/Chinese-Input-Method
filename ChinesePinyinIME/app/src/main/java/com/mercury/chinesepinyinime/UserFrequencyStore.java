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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserFrequencyStore {
    private static final String STORE_FILE_NAME = "user_frequency.tsv";
    private static final String KEY_SEPARATOR = "\u001F";
    private static final int MAX_BOOST = 360;
    private static final int BOOST_PER_SELECTION = 18;
    private static final UserFrequencyStore INSTANCE = new UserFrequencyStore();

    private final Object lock = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Map<String, Integer> selectionCounts = new HashMap<>();
    private File storeFile;
    private boolean loaded;
    private boolean dirty;

    private UserFrequencyStore() {
    }

    public static UserFrequencyStore getInstance() {
        return INSTANCE;
    }

    public void load(Context context) {
        synchronized (lock) {
            if (loaded) {
                return;
            }
            storeFile = new File(context.getApplicationContext().getFilesDir(), STORE_FILE_NAME);
            selectionCounts.clear();
            if (storeFile.exists()) {
                readStoreFile();
            }
            loaded = true;
            dirty = false;
        }
    }

    public void recordSelection(String pinyin, String candidate) {
        if (pinyin == null || pinyin.isEmpty() || candidate == null || candidate.isEmpty()) {
            return;
        }

        String key = buildKey(pinyin, candidate);
        synchronized (lock) {
            int nextCount = selectionCounts.getOrDefault(key, 0) + 1;
            selectionCounts.put(key, nextCount);
            dirty = true;
        }
        schedulePersist();
    }

    public int getBoost(String pinyin, String candidate) {
        if (pinyin == null || candidate == null) {
            return 0;
        }
        synchronized (lock) {
            int count = selectionCounts.getOrDefault(buildKey(pinyin, candidate), 0);
            return Math.min(count * BOOST_PER_SELECTION, MAX_BOOST);
        }
    }

    public int getLearnedEntryCount(Context context) {
        load(context);
        synchronized (lock) {
            return selectionCounts.size();
        }
    }

    public int getTotalSelectionCount(Context context) {
        load(context);
        synchronized (lock) {
            int total = 0;
            for (int count : selectionCounts.values()) {
                total += count;
            }
            return total;
        }
    }

    public void clear(Context context) {
        load(context);
        File fileToDelete;
        synchronized (lock) {
            selectionCounts.clear();
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
        Map<String, Integer> snapshot;
        synchronized (lock) {
            if (!dirty || storeFile == null) {
                return;
            }
            snapshot = new HashMap<>(selectionCounts);
            dirty = false;
        }

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(storeFile), StandardCharsets.UTF_8))) {
            for (Map.Entry<String, Integer> entry : snapshot.entrySet()) {
                String[] keyParts = splitKey(entry.getKey());
                if (keyParts == null) {
                    continue;
                }
                writer.write(keyParts[0]);
                writer.write('\t');
                writer.write(keyParts[1]);
                writer.write('\t');
                writer.write(Integer.toString(entry.getValue()));
                writer.newLine();
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
                        selectionCounts.put(buildKey(pinyin, candidate), count);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore only the malformed row; keep other learned entries.
                }
            }
        } catch (IOException ignored) {
            // Keep the in-memory map empty if the store cannot be read.
        }
    }

    private static String buildKey(String pinyin, String candidate) {
        return pinyin + KEY_SEPARATOR + candidate;
    }

    private static String[] splitKey(String key) {
        int separatorIndex = key.indexOf(KEY_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex == key.length() - KEY_SEPARATOR.length()) {
            return null;
        }
        return new String[]{
                key.substring(0, separatorIndex),
                key.substring(separatorIndex + KEY_SEPARATOR.length())
        };
    }
}
