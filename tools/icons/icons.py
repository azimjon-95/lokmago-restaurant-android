#!/usr/bin/env python3
"""
LokmaGo icon system — single source of truth.
24x24 grid, 1.75 stroke, round caps/joins, 2px optical padding.
Generates: design/icons/*.svg, Android VectorDrawables, preview sheet.
Run:  python3 tools/icons/icons.py
"""
import os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SVG_DIR = os.path.join(ROOT, "design", "icons")
DRAWABLE_DIR = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
STROKE = 1.75

def circle(cx, cy, r):
    return f"M{cx-r} {cy}a{r} {r} 0 1 0 {2*r} 0 {r} {r} 0 1 0 {-2*r} 0z"

def dot(x, y):
    return f"M{x} {y}h.01"

ICONS = {
  # navigation
  "home": "M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4v-5.5H9V20H5a1 1 0 0 1-1-1z",
  "orders": "M9 4h6a1 1 0 0 1 1 1v1H8V5a1 1 0 0 1 1-1zM8 6H6.5A1.5 1.5 0 0 0 5 7.5v11A1.5 1.5 0 0 0 6.5 20h11a1.5 1.5 0 0 0 1.5-1.5v-11A1.5 1.5 0 0 0 17.5 6H16M9 11h6M9 15h4",
  "report": "M4 20h16M7 20v-6M12 20V5M17 20v-9",
  "profile": circle(12, 8, 4) + "M4.5 20a7.5 7.5 0 0 1 15 0",
  "settings": "M4 7h9M17 7h3M15 9.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5zM4 17h3M11 17h9M9 19.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z",
  # alerts
  "bell": "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2.5h-15zM10 21a2 2 0 0 0 4 0",
  "bell_ring": "M6 16v-5a6 6 0 0 1 12 0v5l1.5 2.5h-15zM10 21a2 2 0 0 0 4 0M2.5 9A9.5 9.5 0 0 1 5 4M21.5 9A9.5 9.5 0 0 0 19 4",
  "volume": "M4 9.5v5h3.5l4.5 4v-13l-4.5 4zM15.5 9a4 4 0 0 1 0 6M18.2 6.3a8 8 0 0 1 0 11.4",
  "volume_off": "M4 9.5v5h3.5l4.5 4v-13l-4.5 4zM16 9.5l5 5M21 9.5l-5 5",
  "vibrate": "M9 4h6a1.5 1.5 0 0 1 1.5 1.5v13A1.5 1.5 0 0 1 15 20H9a1.5 1.5 0 0 1-1.5-1.5v-13A1.5 1.5 0 0 1 9 4zM4.5 9v6M19.5 9v6M2 10.5v3M22 10.5v3",
  "queue": "M12 4 20.5 8.5 12 13 3.5 8.5zM3.5 12.5 12 17l8.5-4.5M3.5 16.5 12 21l8.5-4.5",
  "wifi": "M3 9.5a13 13 0 0 1 18 0M6 13a8.5 8.5 0 0 1 12 0M9 16.5a4 4 0 0 1 6 0" + dot(12, 20),
  "wifi_off": "M3 9.5a13 13 0 0 1 3-2.2M18 13a8.5 8.5 0 0 0-2-1.4M9 16.5a4 4 0 0 1 6 0" + dot(12, 20) + "M4 4l16 16",
  "warning": "M12 4 21 19.5H3zM12 10v4" + dot(12, 17),
  # actions
  "check": "M5 12.5 9.5 17 19 7.5",
  "check_circle": circle(12, 12, 9) + "M8 12.5l3 3 5-6",
  "close": "M6 6l12 12M18 6 6 18",
  "close_circle": circle(12, 12, 9) + "M9 9l6 6M15 9l-6 6",
  "chevron_right": "M9.5 6l6 6-6 6",
  "chevron_left": "M14.5 6l-6 6 6 6",
  "chevron_down": "M6 9.5l6 6 6-6",
  "arrow_left": "M19 12H5M11 6l-6 6 6 6",
  "arrow_right": "M5 12h14M13 6l6 6-6 6",
  "refresh": "M20 12a8 8 0 1 1-2.6-5.9M20 4v4.5h-4.5",
  "download": "M12 4v11M7.5 11 12 15.5 16.5 11M5 20h14",
  "logout": "M10 4.5H6.5A1.5 1.5 0 0 0 5 6v12a1.5 1.5 0 0 0 1.5 1.5H10M15 8l4 4-4 4M19 12H9.5",
  # data
  "clock": circle(12, 12, 9) + "M12 7v5l3 2",
  "calendar": "M6.5 5h11a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2h-11a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2zM3.5 10h17M8 3v4M16 3v4",
  "trend_up": "M3.5 16.5l6-6 4 4 7-7M15 7.5h5.5V13",
  "wallet": "M4 7.5A2.5 2.5 0 0 1 6.5 5H17v3M4 7.5V17a2 2 0 0 0 2 2h13a1 1 0 0 0 1-1V9.5a1 1 0 0 0-1-1H6.5A2.5 2.5 0 0 1 4 7.5z" + dot(16.5, 14),
  "cash": "M5 6.5h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z" + circle(12, 12, 2.75) + dot(6.5, 12) + dot(17.5, 12),
  "card": "M5 5.5h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-9a2 2 0 0 1 2-2zM3 10h18M7 14.5h3",
  # order flow
  "cloche": "M4 17a8 8 0 0 1 16 0zM2.5 20h19M12 9V7M10.5 7h3",
  "pot": "M4 11h16v5a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4zM2.5 11h19M9 4c0 1.2 1 1.2 1 2.5M14 4c0 1.2 1 1.2 1 2.5",
  "bag": "M5.5 8h13l-1 11.2a1 1 0 0 1-1 .8h-9a1 1 0 0 1-1-.8zM9 8V7a3 3 0 0 1 6 0v1",
  "delivery": circle(6, 17, 3) + circle(18, 17, 3) + "M6 17l3.2-6.5h6.3l2.5 6.5M9.2 10.5 8.2 8H6.5M15.5 10.5 14.3 6.5H12",
  "utensils": "M7 4v5.5a2 2 0 0 0 4 0V4M9 4v16M17 20V4c-2.2 1.4-3 4-3 7h3",
  "store": "M4 9.5 5.5 4h13L20 9.5a2.7 2.7 0 0 1-5.3 0 2.7 2.7 0 0 1-5.4 0A2.7 2.7 0 0 1 4 9.5zM5.5 12.5V20h13v-7.5M10 20v-4.5h4V20",
  "chef_hat": "M7.5 14.5A4 4 0 0 1 8 6.6a4.5 4.5 0 0 1 8 0 4 4 0 0 1 .5 7.9M7.5 14.5V19a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1v-4.5M7.5 17h9",
  # people & place
  "user": circle(12, 8, 4) + "M4.5 20a7.5 7.5 0 0 1 15 0",
  "phone": "M5 4h3.5L10 8 8 9.5a11 11 0 0 0 6.5 6.5L16 14l4 1.5V19a1.5 1.5 0 0 1-1.5 1.5A15.5 15.5 0 0 1 3.5 5.5 1.5 1.5 0 0 1 5 4z",
  "pin": "M12 21s-6.5-5.6-6.5-11a6.5 6.5 0 0 1 13 0c0 5.4-6.5 11-6.5 11z" + circle(12, 10, 2.5),
  "map": "M3.5 6.5 9 4l6 2.5L20.5 4v13.5L15 20l-6-2.5L3.5 20zM9 4v13.5M15 6.5V20",
  # info
  "help": circle(12, 12, 9) + "M9.6 9.6a2.5 2.5 0 1 1 3.5 2.3c-.7.4-1.1.9-1.1 1.6" + dot(12, 17),
  "shield": "M12 3.5 19 6v5.5c0 4.2-2.9 7.4-7 9-4.1-1.6-7-4.8-7-9V6zM8.8 12l2.4 2.4 4-4.4",
  "document": "M7 3.5h6.5l5 5V19a1.5 1.5 0 0 1-1.5 1.5H7A1.5 1.5 0 0 1 5.5 19V5A1.5 1.5 0 0 1 7 3.5zM13.5 3.5v5h5M9 13h6M9 16.5h4",
  "info": circle(12, 12, 9) + "M12 11v5" + dot(12, 8),
}

def svg(name, d, size=24, color="currentColor"):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 24 24" '
            f'fill="none" stroke="{color}" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round">'
            f'<title>{name}</title><path d="{d}"/></svg>\n')

def vector(d):
    return f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:pathData="{d}"
        android:strokeColor="#FFFFFF"
        android:strokeWidth="{STROKE}"
        android:strokeLineCap="round"
        android:strokeLineJoin="round" />
</vector>
'''

# ---- Brand logo (color): chef hat over a serving plate, orange gradient ----
LOGO = '''<svg xmlns="http://www.w3.org/2000/svg" width="256" height="256" viewBox="0 0 96 96">
<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFB020"/><stop offset="1" stop-color="#FF6A00"/></linearGradient></defs>
<g fill="none" stroke="url(#g)" stroke-width="5.5" stroke-linecap="round" stroke-linejoin="round">
<path d="M30 56a15 15 0 0 1 1.5-29.4 17 17 0 0 1 33 0A15 15 0 0 1 66 56"/>
<path d="M30 56v17a4 4 0 0 0 4 4h28a4 4 0 0 0 4-4V56"/>
<path d="M30 66h36"/></g></svg>
'''
# Launcher foreground (108dp adaptive, safe zone 66dp) — same mark, scaled
LAUNCHER_FG = '''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <group android:scaleX="0.62" android:scaleY="0.62" android:pivotX="54" android:pivotY="54" android:translateX="0" android:translateY="1">
        <path android:pathData="M36 62a15 15 0 0 1 1.5-29.4 17 17 0 0 1 33 0A15 15 0 0 1 72 62M36 62v17a4 4 0 0 0 4 4h28a4 4 0 0 0 4-4V62M36 72h36"
            android:strokeWidth="6" android:strokeLineCap="round" android:strokeLineJoin="round">
            <aapt:attr name="android:strokeColor">
                <gradient android:startY="20" android:endY="88" android:startX="54" android:endX="54"
                    android:startColor="#FFB020" android:endColor="#FF6A00" />
            </aapt:attr>
        </path>
    </group>
</vector>
'''

def main():
    os.makedirs(SVG_DIR, exist_ok=True); os.makedirs(DRAWABLE_DIR, exist_ok=True)
    for name, d in ICONS.items():
        open(os.path.join(SVG_DIR, f"{name}.svg"), "w").write(svg(name, d))
        open(os.path.join(DRAWABLE_DIR, f"ic_{name}.xml"), "w").write(vector(d))
    open(os.path.join(SVG_DIR, "logo.svg"), "w").write(LOGO)
    open(os.path.join(DRAWABLE_DIR, "ic_launcher_foreground.xml"), "w").write(LAUNCHER_FG)
    # preview sheet
    cell, cols = 96, 8
    rows = (len(ICONS) + cols - 1) // cols
    W, H = cols * cell, rows * cell + 8
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="#0B1220"/>']
    for i, (name, d) in enumerate(ICONS.items()):
        x, y = (i % cols) * cell, (i // cols) * cell
        parts.append(f'<g transform="translate({x+24} {y+14}) scale(2)" fill="none" stroke="#22D3A0" stroke-width="{STROKE}" stroke-linecap="round" stroke-linejoin="round"><path d="{d}"/></g>')
        parts.append(f'<text x="{x+cell/2}" y="{y+84}" font-family="sans-serif" font-size="10" fill="#8A97AD" text-anchor="middle">{name}</text>')
    parts.append("</svg>")
    open(os.path.join(SVG_DIR, "_preview.svg"), "w").write("".join(parts))
    print(f"{len(ICONS)} icons generated")

if __name__ == "__main__":
    main()
