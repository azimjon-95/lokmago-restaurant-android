#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
python3 - "$ROOT/env.properties" "$ROOT/ios/Secrets.xcconfig" <<'PYCODE'
from pathlib import Path
import os, re, sys
source, target = map(Path, sys.argv[1:])
values = {}
for line in source.read_text().splitlines():
    item = line.strip()
    if item and not item.startswith('#') and '=' in item:
        key, value = item.split('=', 1)
        values[key.strip()] = value.strip()
header = values.get('API_GATEWAY_HEADER', '')
password = values.get('API_GATEWAY_PASSWORD', '')
if not re.fullmatch(r'[A-Za-z0-9._-]{1,128}', header) or not re.fullmatch(r'[A-Za-z0-9._-]{1,256}', password):
    raise SystemExit('API gateway header/password is missing or unsupported in env.properties')
target.write_text(f'API_GATEWAY_HEADER = {header}\nAPI_GATEWAY_PASSWORD = {password}\n')
os.chmod(target, 0o600)
print('iOS gateway build settings synced from env.properties.')
PYCODE
