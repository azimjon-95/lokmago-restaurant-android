#!/usr/bin/env python3
"""Same 47 icons on iOS and Android: design/icons/*.svg -> ios/LokmaRestaurant/Assets.xcassets/ic_*.imageset (template, vector).
Run from the repo root after tools/icons/icons.py:  python3 tools/design/gen_ios_icons.py"""
import json, pathlib, shutil
root = pathlib.Path(__file__).resolve().parents[2]
src = root / "design/icons"; dst = root / "ios/LokmaRestaurant/Assets.xcassets"
n = 0
for svg in sorted(src.glob("*.svg")):
    if svg.stem.startswith("_") or svg.stem == "logo": continue
    name = f"ic_{svg.stem}"; d = dst / f"{name}.imageset"
    if d.exists(): shutil.rmtree(d)
    d.mkdir(parents=True)
    # Xcode renders SVG without CSS colour keywords; as a TEMPLATE image only the alpha matters, so draw in black.
    (d / f"{svg.stem}.svg").write_text(svg.read_text().replace("currentColor", "#000000"))
    (d / "Contents.json").write_text(json.dumps({
        "images": [{"filename": f"{svg.stem}.svg", "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
        "properties": {"preserves-vector-representation": True, "template-rendering-intent": "template"},
    }, indent=2))
    n += 1
print(f"{n} icon imagesets written")
