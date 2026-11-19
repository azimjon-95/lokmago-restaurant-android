"""Kotlin and Swift both NEST block comments: a "/*" inside a comment (e.g. a path like `/api/courier-app/*`) opens a second level, the
writer's "*/" only closes that one, and the rest of the file silently becomes a comment -> compile error far from the cause.
This lexer finds every block comment still open at end of file, and every nested "/*" inside a comment (the usual cause).
Exit code 1 if anything is found."""
import glob, os, sys
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))

def scan(path):
    s = open(path, encoding="utf-8").read(); i = 0; n = len(s); line = 1
    depth = 0; opened_at = []; nested = []
    while i < n:
        c = s[i]
        if c == "\n": line += 1
        if depth == 0:
            if s.startswith('"""', i):
                j = s.find('"""', i + 3); j = n if j < 0 else j + 3; line += s.count("\n", i, j); i = j; continue
            if c == '"':
                j = i + 1
                while j < n and s[j] != '"' and s[j] != "\n":
                    j += 2 if s[j] == "\\" else 1
                i = j + 1; continue
            if s.startswith("//", i):
                j = s.find("\n", i); i = n if j < 0 else j; continue
            if s.startswith("/*", i):
                depth = 1; opened_at = [line]; i += 2; continue
        else:
            if s.startswith("/*", i):
                depth += 1; opened_at.append(line); nested.append(line); i += 2; continue
            if s.startswith("*/", i):
                depth -= 1; opened_at.pop(); i += 2; continue
        i += 1
    return depth, opened_at, nested

bad = 0
files = glob.glob(os.path.join(REPO, "app/src/**/*.kt"), recursive=True) + glob.glob(os.path.join(REPO, "ios/**/*.swift"), recursive=True)
for f in sorted(files):
    depth, opened, nested = scan(f)
    rel = os.path.relpath(f, REPO)
    if depth > 0:
        bad += 1; print(f"ERROR {rel}: comment opened on line {opened[0]} is never closed (nested '/*' on line(s) {nested}) - the rest of the file is a comment")
    elif nested:
        bad += 1; print(f"ERROR {rel}: nested '/*' inside a comment on line(s) {nested} - write it without '/*' (e.g. '/…')")
print(f"files: {len(files)}  problems: {bad}")
sys.exit(1 if bad else 0)
