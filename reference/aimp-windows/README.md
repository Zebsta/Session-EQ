# Пресеты AIMP для Windows

Извлечены 2026-11-16 из официальной portable-сборки, скачанной по адресу:
https://www.aimp.ru/?do=download.file&id=31

Страница загрузки: https://aimp.ru/?do=download&os=windows

В `history.txt` архива указана версия AIMP v5.40.2731 (12.11.2026).
SHA-256 полученного ZIP:
`46eefcf1aaf62b50aecc9fc31552775897f2b952918d2c13d677ca2d1cd7b681`.
Архив получен по HTTPS с официального сайта. Этот SHA-256 вычислен локально;
он отличается от опубликованного на странице для установщика, поэтому не
используется как подтверждение совпадения с хешем установщика.

В архиве нет отдельного EQPresetsLibrary.ini. Таблица встроенных пресетов
находится открытым текстом внутри `AIMP/AIMP.exe`. EXE не запускался.
Из таблицы извлечены секции `Version=3` с полным набором `Band0`–`Band19`.

- `EQPresetsLibrary.ini` — 24 секции, числовой текст сохранён без округления.
- `presets.json` — те же значения для дальнейшего импорта/анализа.
- `extract_presets.py` — воспроизводимое извлечение из загруженного ZIP.

Набор: Alternative, Ballad, Classic, Club, Dance, Full Bass, Full Bass & Treble,
Full Treble, Headphones, Heavy Metal, Hip-Hop, Industrial, Jazz, Large hall,
Live, Party, Pop, Rap, Reggae, Rock, Ska, Soft, Soft Rock, Techno.

Это пресеты Windows-версии. Они не должны автоматически считаться точными
копиями Android-пресетов. Например:

| Индекс | Windows Rock, dB | Android Rock со скриншота, dB |
|---|---:|---:|
| Band0 | +5.4 | +5.4 |
| Band3 | +3.6 | +1.3 |
| Band5 | −6.3 | −4.8 |
| Band7 | −3.6 | −5.0 |

Официальная история изменений подтверждает приведение количества и набора
полос Android-версии к Windows, но не гарантирует равенства всех пресетов:
https://aimp.ru/?do=changelog&merge=1&os=android&ver=320

Пресеты сохранены как исследовательские данные. В APK Session EQ 0.1.0
включён только Rock, вручную перенесённый с Android-скриншота, и Flat.
