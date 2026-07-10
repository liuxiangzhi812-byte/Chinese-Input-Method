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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ManualDictionaryStore {
    private static final String STORE_FILE_NAME = "manual_dictionary.tsv";
    private static final ManualDictionaryStore INSTANCE = new ManualDictionaryStore();

    private final Object lock = new Object();
    private Map<String, Map<String, Integer>> entries = Collections.emptyMap();
    private File storeFile;
    private boolean loaded;

    private ManualDictionaryStore() {
    }

    public static ManualDictionaryStore getInstance() {
        return INSTANCE;
    }

    public void load(Context context) {
        synchronized (lock) {
            if (loaded) {
                return;
            }
            storeFile = new File(context.getApplicationContext().getFilesDir(), STORE_FILE_NAME);
            entries = readEntries(storeFile);
            loaded = true;
        }
    }

    public boolean replaceAll(
            Context context,
            Map<String, Map<String, Integer>> replacement) {
        load(context);
        Map<String, Map<String, Integer>> snapshot = DictionaryTsvCodec.deepCopy(replacement);
        File target;
        synchronized (lock) {
            target = storeFile;
        }
        if (!writeAtomically(target, snapshot)) {
            return false;
        }
        synchronized (lock) {
            entries = snapshot;
        }
        return true;
    }

    public boolean clear(Context context) {
        load(context);
        File target;
        synchronized (lock) {
            target = storeFile;
        }
        try {
            Files.deleteIfExists(target.toPath());
        } catch (IOException ignored) {
            return false;
        }
        synchronized (lock) {
            entries = Collections.emptyMap();
        }
        return true;
    }

    public int getEntryCount(Context context) {
        load(context);
        synchronized (lock) {
            int count = 0;
            for (Map<String, Integer> candidates : entries.values()) {
                count += candidates.size();
            }
            return count;
        }
    }

    public Map<String, List<String>> snapshotOrderedEntries() {
        synchronized (lock) {
            return DictionaryTsvCodec.toOrderedCandidateLists(entries);
        }
    }

    public Map<String, Map<String, Integer>> snapshotWeightedEntries() {
        synchronized (lock) {
            return DictionaryTsvCodec.deepCopy(entries);
        }
    }

    private static Map<String, Map<String, Integer>> readEntries(File file) {
        if (file == null || !file.exists()) {
            return Collections.emptyMap();
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8))) {
            DictionaryTsvCodec.ParseResult result = DictionaryTsvCodec.parse(reader);
            return result.getEntries();
        } catch (IOException ignored) {
            return Collections.emptyMap();
        }
    }

    private static boolean writeAtomically(
            File target,
            Map<String, Map<String, Integer>> snapshot) {
        File parent = target.getParentFile();
        if (parent == null || (!parent.exists() && !parent.mkdirs())) {
            return false;
        }
        File temporary = new File(parent, target.getName() + ".tmp");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(temporary), StandardCharsets.UTF_8))) {
            DictionaryTsvCodec.writeCombined(
                    writer,
                    snapshot,
                    Collections.emptyMap());
        } catch (IOException ignored) {
            return false;
        }

        try {
            try {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException ignored) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }
    }
}
