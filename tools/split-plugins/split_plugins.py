#!/usr/bin/env python3
"""Partitions the connectors/datatypes/plugins out of the server and client
projects into self-contained plugins/<name>/{common,server,client} builds.

  split_plugins.py analyze [--repo DIR]   report only; changes nothing
  split_plugins.py apply   [--repo DIR]   perform the split (needs a clean tree)
  split_plugins.py emit    [--repo DIR] --out DIR
                                          write the reviewable script that performs the same split, and check
                                          that it does (DIR becomes tools/split-plugins of the pre-split tree)

See tools/split-plugins/README.md.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import analysis
import model
import plan as plan_mod
import apply as apply_mod
import emit as emit_mod


def analyze(repo):
    plan = plan_mod.make_plan(repo)
    owned = [s for s in plan.sources if s.owner != 'core']
    print(f'{len(plan.exts)} extensions; {len(owned)} source files belong to one, '
          f'{len(plan.moves)} paths move, {len(plan.kept)} stay in core')
    print('\nunclassified (placed in server tier):', *plan.problems, sep='\n  ' if plan.problems else ' none')
    print('\npromoted to common (a shared class depends on them):')
    for p, why in sorted(plan.promoted.items()):
        print(f'  {p}  [{why}]')
    print('\nstay in core (gaps.md):')
    for p, why in sorted(plan.kept.items()):
        print(f'  {p}  [{why}]')
    print('\nco-compiled cycles:', [[plan_mod.node_name(n) for n in c] for c in plan.cycles] or 'none')
    print('\nproject dependencies:')
    for (e, t), d in sorted(plan.deps.items()):
        print(f'  {e}:{t}  main -> {sorted(map(plan_mod.node_name, d["main"]))}  test -> {sorted(map(plan_mod.node_name, d["test"]))}')
    print('\ncore tests need:', sorted(map(plan_mod.node_name, plan.core_test_deps)))
    print('\ntier violations left:', *[f'{p} {plan_mod.node_name(a)} -> {plan_mod.node_name(b)} {fq}'
                                       for p, a, b, fq in plan.violations], sep='\n  ')
    print('\nlibs:', {k: v for k, v in plan.libs.items()})
    print('vendored:', plan.vendored_libs)
    print('userutil:', plan.userutil)
    print('libraries from other plugins:', {plan_mod.node_name(k): {a: sorted(b) for a, b in v.items() if b} for k, v in plan.foreign_libs.items()})


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument('command', choices=['analyze', 'apply', 'emit'])
    ap.add_argument('--repo', default='.', type=Path)
    ap.add_argument('--out', type=Path)
    args = ap.parse_args()
    repo = args.repo.resolve()
    if args.command == 'analyze':
        analyze(repo)
    elif args.command == 'emit':
        if not args.out:
            ap.error('emit needs --out')
        emit_mod.emit(repo, args.out.resolve())
    else:
        apply_mod.check_clean(repo)
        apply_mod.apply(repo, plan_mod.make_plan(repo))


if __name__ == '__main__':
    main()
