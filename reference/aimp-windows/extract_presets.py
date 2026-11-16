"""Extract the numeric preset table without executing AIMP.exe."""
from pathlib import Path
from zipfile import ZipFile
import hashlib
import json
import re

root = Path(__file__).resolve().parent
archive_path = root / 'aimp-windows-official.zip'
with ZipFile(archive_path) as archive:
    binary = archive.read('AIMP/AIMP.exe')

anchor = binary.find(b'[Rock]\r\nVersion=3')
if anchor < 0:
    raise ValueError('Expected preset table was not found')
start = end = anchor

def printable(byte):
    return 32 <= byte <= 126 or byte in (9, 10, 13)

while start and printable(binary[start - 1]):
    start -= 1
while end < len(binary) and printable(binary[end]):
    end += 1

text = binary[start:end].decode('ascii')
sections = list(re.finditer(r'^\[([^\]\r\n]+)\]\r?$', text, re.M))
presets = {}
ini_sections = []
for index, match in enumerate(sections):
    block_end = sections[index + 1].start() if index + 1 < len(sections) else len(text)
    block = text[match.end():block_end]
    raw = dict(re.findall(r'^Band(\d+)=([-+\d.eE]+)\r?$', block, re.M))
    if set(raw) != {str(i) for i in range(20)} or not re.search(r'^Version=3\r?$', block, re.M):
        continue
    name = match[1]
    presets[name] = [float(raw[str(i)]) for i in range(20)]
    ini_sections.append('\n'.join([f'[{name}]', 'Version=3'] + [f'Band{i}={raw[str(i)]}' for i in range(20)]))

if len(presets) != 24:
    raise ValueError(f'Expected 24 presets from this version, got {len(presets)}')
(root / 'EQPresetsLibrary.ini').write_text('\n\n'.join(ini_sections) + '\n')
(root / 'presets.json').write_text(json.dumps({
    'source': 'https://www.aimp.ru/?do=download.file&id=31',
    'archive_sha256': hashlib.sha256(archive_path.read_bytes()).hexdigest(),
    'presets': presets,
}, ensure_ascii=False, indent=2) + '\n')
print(f'Extracted {len(presets)} presets with 20 bands each')
