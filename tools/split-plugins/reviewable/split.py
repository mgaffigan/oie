#!/usr/bin/env python3
"""Splits the connectors, datatypes and plugins out of server/ and client/ into plugins/<name>/.

Run from the root of a clean checkout:  tools/split-plugins/split.py

The intent is all in manifest.py. This script only carries it out, in this order, leaving
everything unstaged:

  1. moves      MOVES and PLUGINS[name]['moves']: files change directory, nothing is edited
  2. copies     COPIES
  3. projects   PLUGINS[name]['projects']: plugins/<name>/<tier>/build.gradle from templates/
  4. files/     added to the tree as they are
  5. patches/   edits to the files that were already there, applied with `git apply`
"""
import argparse
import re
import shutil
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import manifest

TIERS = ('common', 'server', 'client')
TOKEN = re.compile(r'\$\{(\w+)\}')


def plan_moves(repo):
    """Yields (source file, destination file) for every rule, rejecting overlaps and rules that match nothing."""
    rules = [(None, *r) for r in manifest.MOVES]
    for name, plugin in manifest.PLUGINS.items():
        rules += [(name, *r) for r in plugin['moves']]
    claimed = {}
    for plugin, src, dest, *names in rules:
        if dest in TIERS:
            dest = f'plugins/{plugin}/{dest}/{src.split("/", 1)[1]}'
        if names:
            files = [(repo / src / n, n) for n in names[0]]
        else:
            files = [(f, f.relative_to(repo / src).as_posix()) for f in sorted((repo / src).rglob('*')) if f.is_file()]
        if not files:
            sys.exit(f'{src}: nothing to move')
        for f, rel in files:
            if not f.is_file():
                sys.exit(f'{f.relative_to(repo)}: no such file')
            if f in claimed:
                sys.exit(f'{f.relative_to(repo)}: moved by two rules')
            claimed[f] = repo / dest / rel
    return claimed


def move(repo, moves):
    for src, dst in moves.items():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(src, dst)
    for src in moves:
        for d in src.parents:
            if d == repo or (d.exists() and any(d.iterdir())):
                break
            if d.exists():
                d.rmdir()


def expand(template, params):
    """${name} is replaced by the parameter; a list repeats the line for each element, and a line
    naming a parameter that was not given is left out."""
    used, out = set(), []
    for line in template.read_text().splitlines(keepends=True):
        names = TOKEN.findall(line)
        if any(n not in params for n in names):
            continue
        used.update(names)
        repeated = next((n for n in names if isinstance(params[n], list)), None)
        for value in params[repeated] if repeated else [None]:
            out.append(TOKEN.sub(lambda m: value if m.group(1) == repeated else params[m.group(1)], line))
    unused = set(params) - used
    if unused:
        sys.exit(f'{template.name}: unused parameter(s) {", ".join(sorted(unused))}')
    return ''.join(out)


def write_projects(repo):
    for name, plugin in manifest.PLUGINS.items():
        for tier, fragments in plugin['projects'].items():
            text = '\n'.join(expand(HERE / 'templates' / template, params) for template, params in fragments)
            target = repo / 'plugins' / name / tier / 'build.gradle'
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(text)


def add_files(repo):
    for f in sorted(p for p in (HERE / 'files').rglob('*') if p.is_file()):
        target = repo / f.relative_to(HERE / 'files')
        if target.exists():
            sys.exit(f'{target.relative_to(repo)}: already exists')
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(f, target)


def apply_patches(repo):
    for patch in sorted((HERE / 'patches').rglob('*.patch')):
        subprocess.run(['git', 'apply', '--whitespace=nowarn', patch], cwd=repo, check=True)


def check_clean(repo):
    if (repo / 'plugins').exists():
        sys.exit('plugins/ exists; the split has been applied')
    status = subprocess.run(['git', 'status', '--porcelain', '--untracked-files=no'], cwd=repo,
                            capture_output=True, text=True, check=True).stdout
    dirty = [l for l in status.splitlines() if not l[3:].startswith('tools/split-plugins/')]
    if dirty:
        sys.exit('working tree has uncommitted changes:\n' + '\n'.join(dirty[:10]))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('--repo', default='.', type=Path)
    repo = ap.parse_args().repo.resolve()
    check_clean(repo)
    moves = plan_moves(repo)
    move(repo, moves)
    for src, dst in manifest.COPIES:
        (repo / dst).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(repo / src, repo / dst)
    write_projects(repo)
    add_files(repo)
    apply_patches(repo)
    print(f'moved {len(moves)} files')


if __name__ == '__main__':
    main()
