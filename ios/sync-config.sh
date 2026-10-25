#!/bin/sh
# env.properties dagi API_URL + API_PATH_PREFIX dan iOS uchun API_BASE_URL ni yozadi (ios/Secrets.xcconfig, git-ignored).
# Bo'sh qolsa ilova standart serverga ulanadi (Config.swift). Gateway sozlamalari olib tashlangan.
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
python3 - "$ROOT/env.properties" "$ROOT/ios/Secrets.xcconfig" <<'PYCODE'
from pathlib import Path
import os, re, sys
source, target = map(Path, sys.argv[1:])
values = {}
if source.exists():
    for line in source.read_text().splitlines():
        item = line.strip()
        if item and not item.startswith('#') and '=' in item:
            key, value = item.split('=', 1)
            values[key.strip()] = value.strip()
origin = values.get('API_URL', '').rstrip('/')
prefix = values.get('API_PATH_PREFIX', 'api/').strip('/')
if origin and not re.fullmatch(r'https://[A-Za-z0-9.-]+(:\d+)?', origin):
    raise SystemExit('API_URL must be a bare https origin, e.g. https://apilokma.poppolizol.uz')
# xcconfig'da "//" izoh boshlanishi — shuning uchun $() bilan ajratiladi
base = f"{origin}/{prefix}/".replace('//', '/$()/') if origin else ''
target.write_text(f'API_BASE_URL = {base}\n')
os.chmod(target, 0o600)
print('iOS API_BASE_URL synced from env.properties:', base.replace('/$()/', '//') or '(default)')
PYCODE
