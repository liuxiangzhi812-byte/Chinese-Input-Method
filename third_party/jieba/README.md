# jieba dictionary

Source: https://github.com/fxsjy/jieba

Downloaded files:
- `dict.txt`: jieba default dictionary, containing word, frequency, and part-of-speech data.
- `LICENSE`: MIT license from the jieba repository.

Current use in ChinesePinyinIME:
- This file is source data for common Chinese words and frequencies.
- It is not a pinyin dictionary by itself.
- A conversion step is still needed to produce `pinyin=candidate1,candidate2,...` data for the IME.
