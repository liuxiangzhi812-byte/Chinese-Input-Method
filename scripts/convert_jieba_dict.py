#!/usr/bin/env python3
"""Convert jieba dict.txt into ChinesePinyinIME pinyin_dict.txt."""

from __future__ import annotations

import argparse
import re
import time
from collections import defaultdict
from pathlib import Path

from pypinyin import Style, lazy_pinyin

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_INPUT = ROOT / "third_party" / "jieba" / "dict.txt"
DEFAULT_OUTPUT = ROOT / "ChinesePinyinIME" / "app" / "src" / "main" / "assets" / "pinyin_dict.txt"
DEFAULT_REPORT = ROOT / "conversion_report.txt"
DEFAULT_OVERRIDES = Path(__file__).resolve().parent / "pinyin_overrides.txt"

CJK_RE = re.compile(r"[\u3400-\u9fff]")


def contains_chinese(text: str) -> bool:
    return CJK_RE.search(text) is not None


def word_to_pinyin(word: str) -> str:
    parts = lazy_pinyin(word, style=Style.NORMAL, errors="ignore")
    pinyin = "".join(part.lower() for part in parts if part)
    return re.sub(r"[^a-z]", "", pinyin)


def parse_jieba_line(line: str) -> tuple[str, int] | None:
    parts = line.strip().split()
    if len(parts) < 2:
        return None
    try:
        frequency = int(parts[-2])
    except ValueError:
        return None
    word = " ".join(parts[:-2]).strip()
    if not word:
        return None
    return word, frequency


def load_overrides(path: Path) -> dict[str, list[str]]:
    overrides: dict[str, list[str]] = {}
    if not path.exists():
        return overrides

    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            continue
        pinyin, candidates_text = line.split("=", 1)
        pinyin = pinyin.strip().lower()
        candidates = [item.strip() for item in candidates_text.split(",") if item.strip()]
        if pinyin and candidates:
            overrides[pinyin] = candidates
    return overrides


def merge_candidates(
    grouped: dict[str, dict[str, int]],
    overrides: dict[str, list[str]],
    max_per_key: int,
) -> dict[str, list[str]]:
    merged: dict[str, list[str]] = {}

    for pinyin, candidate_freqs in grouped.items():
        ranked = sorted(candidate_freqs.items(), key=lambda item: (-item[1], item[0]))
        ordered: list[str] = []
        seen: set[str] = set()

        for candidate, _ in ranked:
            if candidate in seen:
                continue
            seen.add(candidate)
            ordered.append(candidate)
            if len(ordered) >= max_per_key:
                break

        override_list = overrides.get(pinyin)
        if override_list:
            final: list[str] = []
            seen.clear()
            for candidate in override_list + ordered:
                if candidate in seen:
                    continue
                seen.add(candidate)
                final.append(candidate)
                if len(final) >= max_per_key:
                    break
            ordered = final

        merged[pinyin] = ordered

    for pinyin, override_only in overrides.items():
        if pinyin not in merged:
            merged[pinyin] = override_only[:max_per_key]

    return dict(sorted(merged.items(), key=lambda item: item[0]))


def write_dictionary(path: Path, merged: dict[str, list[str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        handle.write("# ChinesePinyinIME local dictionary converted from jieba dict.txt\n")
        handle.write("# Format:\n")
        handle.write("# pinyin=candidate1,candidate2,candidate3\n")
        handle.write("#\n")
        handle.write("# Rules:\n")
        handle.write("# 1. Pinyin keys are lowercase, without tone marks and spaces.\n")
        handle.write("# 2. Candidates are separated by English commas.\n")
        handle.write("# 3. Candidates under the same pinyin are sorted mainly by jieba frequency, high to low.\n")
        handle.write("# 4. Lines starting with # are comments.\n")
        handle.write("#\n")
        for pinyin, candidates in merged.items():
            handle.write(f"{pinyin}={','.join(candidates)}\n")


def write_report(
    path: Path,
    *,
    input_path: Path,
    output_path: Path,
    parsed_entries: int,
    kept_entries: int,
    skipped_without_chinese: int,
    skipped_bad_entries: int,
    unique_pinyin_keys: int,
    total_output_candidates: int,
    max_candidates_key: str,
    max_candidates_count: int,
    elapsed_seconds: float,
    sample_candidates: dict[str, list[str]],
) -> None:
    lines = [
        f"input_path: {input_path}",
        f"output_path: {output_path}",
        f"parsed_entries: {parsed_entries}",
        f"kept_entries_with_chinese: {kept_entries}",
        f"skipped_entries_without_chinese: {skipped_without_chinese}",
        f"skipped_bad_entries: {skipped_bad_entries}",
        f"unique_pinyin_keys: {unique_pinyin_keys}",
        f"total_output_candidates: {total_output_candidates}",
        f"max_candidates_key: {max_candidates_key}",
        f"max_candidates_count: {max_candidates_count}",
        f"elapsed_seconds: {elapsed_seconds:.2f}",
        f"sample_candidates: {sample_candidates}",
        "",
    ]
    path.write_text("\n".join(lines), encoding="utf-8")


def convert(
    input_path: Path,
    output_path: Path,
    report_path: Path,
    overrides_path: Path,
    max_per_key: int,
) -> None:
    start = time.time()
    grouped: dict[str, dict[str, int]] = defaultdict(dict)
    overrides = load_overrides(overrides_path)

    parsed_entries = 0
    kept_entries = 0
    skipped_without_chinese = 0
    skipped_bad_entries = 0

    with input_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            parsed = parse_jieba_line(line)
            if parsed is None:
                skipped_bad_entries += 1
                continue

            word, frequency = parsed
            parsed_entries += 1
            if not contains_chinese(word):
                skipped_without_chinese += 1
                continue

            pinyin = word_to_pinyin(word)
            if not pinyin:
                skipped_bad_entries += 1
                continue

            current = grouped[pinyin].get(word, 0)
            if frequency > current:
                grouped[pinyin][word] = frequency
            kept_entries += 1

    merged = merge_candidates(grouped, overrides, max_per_key)
    write_dictionary(output_path, merged)

    max_candidates_key = ""
    max_candidates_count = 0
    total_output_candidates = 0
    for pinyin, candidates in merged.items():
        total_output_candidates += len(candidates)
        if len(candidates) > max_candidates_count:
            max_candidates_count = len(candidates)
            max_candidates_key = pinyin

    sample_keys = ["ni", "hao", "nihao", "zhongguo", "shurufa", "yi", "shi", "de", "wo"]
    sample_candidates = {
        key: merged[key]
        for key in sample_keys
        if key in merged
    }

    write_report(
        report_path,
        input_path=input_path,
        output_path=output_path,
        parsed_entries=parsed_entries,
        kept_entries=kept_entries,
        skipped_without_chinese=skipped_without_chinese,
        skipped_bad_entries=skipped_bad_entries,
        unique_pinyin_keys=len(merged),
        total_output_candidates=total_output_candidates,
        max_candidates_key=max_candidates_key,
        max_candidates_count=max_candidates_count,
        elapsed_seconds=time.time() - start,
        sample_candidates=sample_candidates,
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--overrides", type=Path, default=DEFAULT_OVERRIDES)
    parser.add_argument("--max-per-key", type=int, default=20)
    args = parser.parse_args()

    convert(
        input_path=args.input,
        output_path=args.output,
        report_path=args.report,
        overrides_path=args.overrides,
        max_per_key=args.max_per_key,
    )
    print(f"Wrote dictionary: {args.output}")
    print(f"Wrote report: {args.report}")


if __name__ == "__main__":
    main()