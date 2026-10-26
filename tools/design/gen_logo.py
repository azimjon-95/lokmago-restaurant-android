#!/usr/bin/env python3
"""LGO Business logo -> every icon the two apps need. Source: design/logo/lgo-business.png (square, plate radius ~18.4 %).
Run from the repo root:  python3 tools/design/gen_logo.py
  Android  adaptive icon foreground (5 densities) + gradient background, in-app logo
  iOS      AppIcon 1024 (opaque, the system rounds it), in-app logo
  Play     docs/store/play-icon-512.png (full-bleed, Google rounds it)
"""
import json, pathlib
from PIL import Image, ImageDraw
root = pathlib.Path(__file__).resolve().parents[2]
src = Image.open(root / "design/logo/lgo-business.png").convert("RGB")
R = 0.184                                                    # measured corner radius of the plate

def rounded_alpha(size, ss=4):
    m = Image.new("L", (size * ss,) * 2, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, size * ss - 1, size * ss - 1), radius=int(size * ss * R), fill=255)
    return m.resize((size, size), Image.LANCZOS)

def plate(size):                                             # the logo with transparent corners
    im = src.resize((size, size), Image.LANCZOS).convert("RGBA"); im.putalpha(rounded_alpha(size)); return im

# ---------- Android: adaptive icon ----------
res = root / "app/src/main/res"
master = Image.new("RGBA", (432, 432), (0, 0, 0, 0))        # 108dp @ xxxhdpi
inner = plate(259)                                           # 60 % keeps "Business" inside a circular mask
master.paste(inner, ((432 - 259) // 2, (432 - 259) // 2), inner)
for d, px in {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}.items():
    out = res / f"mipmap-{d}"; out.mkdir(parents=True, exist_ok=True)
    master.resize((px, px), Image.LANCZOS).save(out / "ic_launcher_fg.png", optimize=True)
(res / "drawable/ic_launcher_bg.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<!-- Matches the logo plate: deep blue at the top fading to near-black navy -->
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <gradient android:type="linear" android:angle="270" android:startColor="#0A2C70" android:endColor="#050D20" />
</shape>
''')
(res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi-v26/ic_launcher.xml").write_text('''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_fg" />
</adaptive-icon>
''')
(res / "drawable-nodpi").mkdir(exist_ok=True)
plate(512).save(res / "drawable-nodpi/logo_lgo.png", optimize=True)

# ---------- iOS ----------
ios = root / "ios/LokmaRestaurant/Assets.xcassets"
src.resize((1024, 1024), Image.LANCZOS).save(ios / "AppIcon.appiconset/AppIcon.png", optimize=True)   # opaque; iOS mask (22 %) is wider than the plate (18 %)
d = ios / "logo_lgo.imageset"; d.mkdir(exist_ok=True)
plate(512).save(d / "logo_lgo.png", optimize=True)
(d / "Contents.json").write_text(json.dumps({"images": [{"filename": "logo_lgo.png", "idiom": "universal"}], "info": {"author": "xcode", "version": 1}}, indent=2))

# ---------- Google Play (512, full-bleed: crop the outer 6 % so no black corner survives) ----------
c = int(src.width * 0.06)
src.crop((c, c, src.width - c, src.height - c)).resize((512, 512), Image.LANCZOS).save(root / "docs/store/play-icon-512.png", optimize=True)
print("logo assets written")
