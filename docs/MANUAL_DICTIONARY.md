# Manual Dictionary

ChinesePinyinIME v0.02 uses an importable UTF-8 TSV file for manually curated words.

## Format

Each data row has exactly three tab-separated fields:

```text
pinyin<TAB>candidate<TAB>weight
```

Example:

```text
# Lines beginning with # are comments.
ni\t妮\t100
ceshici\t测试词\t80
```

Rules:

- Save the file as UTF-8 text, normally with a `.tsv` extension.
- Pinyin contains lowercase letters without tones or spaces. Uppercase input is normalized to lowercase during import.
- Candidate text must be non-empty and cannot contain tab/newline control characters.
- Weight is an integer from 1 to 1,000,000. Higher weight comes first within the manual dictionary.
- Blank lines and lines beginning with `#` are ignored.
- Duplicate pinyin/candidate rows keep the highest weight.
- A file can contain up to 50,000 unique entries; extra rows are rejected.

## Import Semantics

- A successful import replaces the current manual dictionary as one atomic operation.
- Valid rows are imported and malformed rows are skipped with a result count.
- If a file contains no valid rows, the existing manual dictionary is preserved.
- Runtime indexes rebuild in the background; the result message appears when the new dictionary is active.

Candidate source priority is strict:

1. Manually imported dictionary
2. Self-learned phrases
3. Built-in base dictionary

## Export Semantics

Export writes both manually imported entries and self-learned phrases to one importable TSV file. If the same pinyin/candidate exists in both layers, the manual weight is kept.

Re-importing an exported file promotes all exported rows into the manually curated layer. This is useful for PC editing and backup.

## Privacy

Import and export use Android's system document picker. The app does not request broad storage permission and does not upload dictionary data.
