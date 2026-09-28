# -*- coding: utf-8 -*-
"""
Fix YAML scalar trap: 'prefix: -' will be parsed as a YAML sequence entry by SnakeYAML.
Wrap @prefix@ with single quotes so after Maven resource filtering the value becomes
'prefix: '-' which is a valid string scalar.

Scans src/main/resources/config/application.yml under three project roots, replaces:
    prefix: @prefix@     ->     prefix: '@prefix@'
Only touches the 'prefix:' line; leaves env: @envName@ untouched (env values like
dev/test/prod are not YAML reserved tokens, no quoting needed).
"""
import os
import re

PROJECTS = [
    r"D:\Project\Ahwx\trunk\Wms\bcd-service\bcd-service-api",
    r"D:\Project\Ahwx\trunk\Yms\bcd-yms-new\bcd-service-api",
    r"D:\Project\Ahwx\trunk\Oms\bcd-service\bcd-service-api",
]

# Match '    prefix: @prefix@' (with leading whitespace) that is NOT already quoted.
# Captures leading whitespace + 'prefix:' so we can wrap the value with single quotes.
PATTERN = re.compile(r'^(\s*prefix:\s*)@prefix@(\s*)$', re.MULTILINE)
REPLACEMENT = r"\1'@prefix@'\2"

total_files = 0
total_replacements = 0

for project_root in PROJECTS:
    if not os.path.isdir(project_root):
        print(f"[SKIP] project root not found: {project_root}")
        continue
    print(f"\n=== Scanning: {project_root} ===")
    for dirpath, dirnames, filenames in os.walk(project_root):
        # Skip build outputs / IDE mirrors / cache to avoid touching stale copies.
        # Only edit source yml files under src/main/resources/config/.
        if "src" not in dirpath.replace("\\", "/").split("/"):
            continue
        if "main" not in dirpath.replace("\\", "/").split("/"):
            continue
        if "resources" not in dirpath.replace("\\", "/").split("/"):
            continue
        if "config" not in dirpath.replace("\\", "/").split("/"):
            continue
        for fn in filenames:
            if fn != "application.yml":
                continue
            fpath = os.path.join(dirpath, fn)
            try:
                with open(fpath, "r", encoding="utf-8") as f:
                    content = f.read()
            except Exception as e:
                print(f"  [READ ERROR] {fpath}: {e}")
                continue
            new_content, n = PATTERN.subn(REPLACEMENT, content)
            if n == 0:
                continue
            try:
                with open(fpath, "w", encoding="utf-8") as f:
                    f.write(new_content)
            except Exception as e:
                print(f"  [WRITE ERROR] {fpath}: {e}")
                continue
            total_files += 1
            total_replacements += n
            print(f"  [OK] {fpath}  ({n} replacement)")

print(f"\nDone. Files modified: {total_files}, total replacements: {total_replacements}")
