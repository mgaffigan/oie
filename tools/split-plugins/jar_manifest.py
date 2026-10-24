#!/usr/bin/env python3
"""Snapshot and compare the contents of every jar in a distribution tree.

  jar_manifest.py snapshot server/setup > before.json
  jar_manifest.py compare before.json after.json

A snapshot maps "<path relative to the root>" -> {entry name: crc}. The compare
step reports jars added/removed and, per jar, entries added/removed/changed.
Entries differing only by directory markers or META-INF/MANIFEST.MF are ignored.
"""
import json
import sys
import zipfile
from pathlib import Path


def snapshot(root):
    out = {}
    for jar in sorted(Path(root).rglob('*.jar')):
        with zipfile.ZipFile(jar) as z:
            out[str(jar.relative_to(root))] = {
                i.filename: i.CRC for i in z.infolist()
                if not i.is_dir() and i.filename != 'META-INF/MANIFEST.MF'}
    return out


def compare(before, after):
    problems = 0
    for jar in sorted(set(before) | set(after)):
        if jar not in after:
            print(f'REMOVED jar {jar}')
            problems += 1
        elif jar not in before:
            print(f'ADDED   jar {jar}')
            problems += 1
        else:
            b, a = before[jar], after[jar]
            for name in sorted(set(b) | set(a)):
                if name not in a:
                    print(f'{jar}: - {name}')
                elif name not in b:
                    print(f'{jar}: + {name}')
                elif a[name] != b[name]:
                    print(f'{jar}: ~ {name}')
                else:
                    continue
                problems += 1
    print(f'{problems} difference(s)')
    return problems


if __name__ == '__main__':
    if sys.argv[1] == 'snapshot':
        json.dump(snapshot(sys.argv[2]), sys.stdout, indent=0, sort_keys=True)
    elif sys.argv[1] == 'compare':
        sys.exit(1 if compare(*(json.load(open(p)) for p in sys.argv[2:4])) else 0)
    else:
        sys.exit(__doc__)
