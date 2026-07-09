package com.mercury.chinesepinyinime;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String DICTIONARY_ASSET_NAME = "pinyin_dict.txt";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView dictionaryStatus;
    private TextView learnedDataStatus;
    private TextView keyboardLayoutStatus;
    private Button keyboardLayoutToggleButton;
    private EditText imeTestInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        dictionaryStatus = findViewById(R.id.dictionary_status);
        learnedDataStatus = findViewById(R.id.learned_data_status);
        keyboardLayoutStatus = findViewById(R.id.keyboard_layout_status);
        keyboardLayoutToggleButton = findViewById(R.id.keyboard_layout_toggle_button);
        imeTestInput = findViewById(R.id.ime_test_input);
        Button clearLearnedDataButton = findViewById(R.id.clear_learned_data_button);
        Button openInputSettingsButton = findViewById(R.id.open_input_settings_button);

        clearLearnedDataButton.setOnClickListener(view -> clearLearnedData());
        openInputSettingsButton.setOnClickListener(view ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        keyboardLayoutToggleButton.setOnClickListener(view -> toggleKeyboardLayoutPreference());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        updateDictionaryStatus();
        updateLearnedDataStatus();
        updateKeyboardLayoutStatus();
        mainHandler.post(() -> imeTestInput.requestFocus());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLearnedDataStatus();
    }

    @Override
    protected void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    private void updateDictionaryStatus() {
        executor.execute(() -> {
            DictionaryStats stats = readDictionaryStats();
            mainHandler.post(() -> {
                if (stats.loaded) {
                    dictionaryStatus.setText(getString(
                            R.string.dictionary_status_ready,
                            stats.pinyinKeyCount,
                            stats.candidateCount));
                } else {
                    dictionaryStatus.setText(R.string.dictionary_status_failed);
                }
            });
        });
    }

    private DictionaryStats readDictionaryStats() {
        int pinyinKeyCount = 0;
        int candidateCount = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open(DICTIONARY_ASSET_NAME), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0 || separatorIndex == line.length() - 1) {
                    continue;
                }
                pinyinKeyCount++;
                candidateCount += line.substring(separatorIndex + 1).split(",").length;
            }
            return new DictionaryStats(true, pinyinKeyCount, candidateCount);
        } catch (IOException ignored) {
            return new DictionaryStats(false, 0, 0);
        }
    }

    private void updateLearnedDataStatus() {
        UserFrequencyStore frequencyStore = UserFrequencyStore.getInstance();
        int entryCount = frequencyStore.getLearnedEntryCount(getApplicationContext());
        int selectionCount = frequencyStore.getTotalSelectionCount(getApplicationContext());
        int customPhraseCount = UserDictionaryStore.getInstance()
                .getLearnedPhraseCount(getApplicationContext());
        learnedDataStatus.setText(getString(
                R.string.learned_data_status,
                entryCount,
                selectionCount,
                customPhraseCount));
    }

    private void clearLearnedData() {
        UserFrequencyStore.getInstance().clear(getApplicationContext());
        UserDictionaryStore.getInstance().clear(getApplicationContext());
        updateLearnedDataStatus();
        Toast.makeText(this, R.string.learned_data_cleared, Toast.LENGTH_SHORT).show();
    }

    private void updateKeyboardLayoutStatus() {
        boolean isT9 = KeyboardLayoutPreferences.isT9Enabled(getApplicationContext());
        keyboardLayoutStatus.setText(isT9
                ? R.string.keyboard_layout_status_9
                : R.string.keyboard_layout_status_26);
        keyboardLayoutToggleButton.setText(isT9
                ? R.string.switch_to_qwerty_layout
                : R.string.switch_to_t9_layout);
    }

    private void toggleKeyboardLayoutPreference() {
        boolean isCurrentlyT9 = KeyboardLayoutPreferences.isT9Enabled(getApplicationContext());
        KeyboardLayoutPreferences.setT9Enabled(getApplicationContext(), !isCurrentlyT9);
        updateKeyboardLayoutStatus();
    }

    private static final class DictionaryStats {
        private final boolean loaded;
        private final int pinyinKeyCount;
        private final int candidateCount;

        private DictionaryStats(boolean loaded, int pinyinKeyCount, int candidateCount) {
            this.loaded = loaded;
            this.pinyinKeyCount = pinyinKeyCount;
            this.candidateCount = candidateCount;
        }
    }
}
