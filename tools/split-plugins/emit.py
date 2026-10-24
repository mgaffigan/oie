"""Writes the reviewable form of the split.

`apply` decides what moves where and how each Gradle script is parameterised, but that decision is
spread over a few hundred lines of analysis. `emit` records the decision as data instead:

  manifest.py   what moves where (directory rules) and, per plugin project, which templates with which parameters
  split.py      carries the manifest out; has no knowledge of the plugins
  README.md     what the script does and how to check it (reviewable/README.md)
  templates/    the fragments a plugin project's build.gradle is made from
  files/        new files that need no parameters
  patches/      edits to the files that already exist, one unified diff per file

The result is checked: it is run on a fresh clone, and the tree must equal what `apply` produces.
"""
import filecmp
import os
import shutil
import subprocess
import tempfile
from collections import defaultdict
from pathlib import Path

import apply as apply_mod
import plan as plan_mod

HERE = Path(__file__).parent
SKIP = ('.git', 'tools/split-plugins')


def clone(repo, dest):
    subprocess.run(['git', 'clone', '-q', '--local', str(repo), str(dest)], check=True)
    shutil.rmtree(dest / 'tools/split-plugins')
    return dest


def tree(root):
    files, dirs = {}, set()
    for dirpath, dirnames, filenames in os.walk(root):
        rel = Path(dirpath).relative_to(root).as_posix()
        join = lambda name: name if rel == '.' else f'{rel}/{name}'
        dirnames[:] = [d for d in dirnames if join(d) not in SKIP]
        dirs.add(rel)
        files.update({join(f): Path(dirpath, f) for f in filenames})
    return files, dirs


def compare(a, b):
    """Differences between two trees, as a list of strings; empty when they are the same."""
    fa, da = tree(a)
    fb, db = tree(b)
    out = [f'only in {a}: {p}' for p in sorted(fa.keys() - fb.keys())]
    out += [f'only in {b}: {p}' for p in sorted(fb.keys() - fa.keys())]
    out += [f'differs: {p}' for p in sorted(fa.keys() & fb.keys()) if not filecmp.cmp(fa[p], fb[p], shallow=False)]
    out += [f'directory only in {a}: {p}' for p in sorted(da - db)] + [f'directory only in {b}: {p}' for p in sorted(db - da)]
    return out


# ------------------------------------------------------------------ moves

def _rules(repo, plan):
    """[(plugin or None, source dir, destination dir, names or None)]: a rule moves a whole tree, or just `names`."""
    tracked = subprocess.run(['git', 'ls-files'], cwd=repo, capture_output=True, text=True, check=True).stdout.splitlines()
    moves = plan.moves

    def whole_tree(d):
        """The destination directory if every file under d moves there with its relative path, else None."""
        under = [f for f in tracked if f.startswith(d + '/')]
        if not under or any(f not in moves for f in under):
            return None
        q = moves[under[0]][:-(len(under[0]) - len(d))]
        return q if all(moves[f] == q + f[len(d):] for f in under) else None

    rules, covered = [], []
    inside = lambda d: any(d == c or d.startswith(c + '/') for c in covered)
    for d in sorted({f.rsplit('/', 1)[0] for f in moves}, key=lambda d: (d.count('/'), d)):
        if not inside(d) and (q := whole_tree(d)):
            covered.append(d)
            rules.append((d, q, None))
    flat = defaultdict(list)
    for src, dst in moves.items():
        d, name = src.rsplit('/', 1)
        if not inside(d):
            assert dst.endswith('/' + name), f'{src} is renamed'
            flat[(d, dst.rsplit('/', 1)[0])].append(name)
    rules += [(d, q, sorted(names)) for (d, q), names in flat.items()]
    return [(q.split('/')[1] if q.startswith('plugins/') else None, d, q, names)
            for d, q, names in sorted(rules, key=lambda r: r[:2])]


def _wrap(items, indent, width=110):
    lines, line = [], ''
    for item in map(repr, items):
        if line and len(indent + line) + len(item) + 2 > width:
            lines.append(line.rstrip())
            line = ''
        line += item + ', '
    lines.append(line.rstrip().rstrip(','))
    return ('\n' + indent).join(lines)


def _rule_text(plugin, d, q, names, indent):
    tier = next((t for t in ('common', 'server', 'client') if plugin and q == f'plugins/{plugin}/{t}/{d.split("/", 1)[1]}'), None)
    dest = repr(tier or q)
    if names is None:
        return f'{indent}({d!r}, {dest}),\n'
    return f'{indent}({d!r}, {dest}, [\n{indent}    {_wrap(names, indent + "    ")}]),\n'


# ------------------------------------------------------------------ projects

def _projects(plan):
    """plugin -> tier -> [(template, parameters)]: what apply.write_plugin_builds writes, as data."""
    tiers = plan_mod.tiers_of(plan)
    if plan.kept_vendored:
        raise SystemExit('kept vendored libraries have no template')
    out = {}
    for ext in plan.exts:
        carrier = plan_mod.lib_tier(tiers[ext.name])
        aliases = [a.replace('-', '.') for a in plan.libs.get(ext.name, [])]
        for tier in sorted(tiers[ext.name], key=('common', 'server', 'client').index):
            deps = plan.deps.get((ext.name, tier), {'main': set(), 'test': set()})
            foreign = plan.foreign_libs.get((ext.name, tier), {'maven': set(), 'vendored': set()})
            params = {'compile': [f'{p}:{t}' for p, t in sorted(deps['main'])],
                      'test': [f'{p}:{t}' for p, t in sorted(deps['test'])],
                      'implementation': aliases if tier == carrier else [],
                      'compile_only': (aliases if tier != carrier else []) + [a.replace('-', '.') for a in sorted(foreign['maven'])],
                      'vendored': sorted(foreign['vendored'])}
            params = {k: v for k, v in params.items() if v}
            fragments = [('tier.gradle', {'tier': tier})]
            if params:
                fragments.append(('dependencies.gradle', params))
            pkgdirs = [pkgdir for e, _, pkgdir in plan.userutil if e == ext.name]
            if tier == 'server' and pkgdirs:
                fragments.append(('userutil-sources.gradle', {'plugin': ext.name, 'packages': pkgdirs}))
            if not {'common': ext.shared, 'server': ext.server, 'client': ext.client_pkg}[tier]:
                fragments.append(('no-jar.gradle', {'tier': tier}))
            fragments += _cycle(plan, ext, tier)
            out.setdefault(ext.name, {})[tier] = fragments
    return out


def _cycle(plan, ext, tier):
    node = (ext.name, tier)
    for comp in plan.cycles:
        if node not in comp:
            continue
        host, guests = comp[0], comp[1:]
        pkgs = lambda n: [e.pkg for e in plan.exts if e.name == n[0]] + \
            [x for e in plan.exts if e.name == n[0] for x in e.extra_server_pkgs]
        if node == host:
            return [('cycle-host.gradle', {'members': ', '.join(plan_mod.node_name(n) for n in comp),
                                           'guest': [f'{g[0]}/{g[1]}' for g in guests],
                                           'excluded': [p for g in guests for p in pkgs(g)]})]
        return [('cycle-guest.gradle', {'host': plan_mod.node_name(host), 'packages': pkgs(node)})]
    return []


def _fragment_text(fragments, indent):
    lines = []
    for template, params in fragments:
        items = [f'{k!r}: {v!r}' for k, v in params.items()]
        line = f'{indent}({template!r}, {{{", ".join(items)}}}),\n'
        if len(line) > 120:
            line = f'{indent}({template!r}, {{\n' + ''.join(f'{indent}    {i},\n' for i in items) + f'{indent}}}),\n'
        lines.append(line)
    return ''.join(lines)


# ------------------------------------------------------------------ manifest

def manifest_text(repo, plan):
    rules = _rules(repo, plan)
    projects = _projects(plan)
    out = ['"""What the split moves and generates. split.py carries it out; nothing here is computed.\n\n'
           'A move is (source directory, destination directory[, names]): the whole tree under the source, or only the\n'
           'named files directly in it, keep their path relative to the source. Inside a plugin the destination may be\n'
           "'common', 'server' or 'client': plugins/<plugin>/<tier>/ followed by the source path without its first\n"
           'segment (server/ or client/). No file may be moved twice.\n\n'
           'A project is plugins/<plugin>/<tier>/build.gradle: the templates, in order, each with its parameters.\n'
           'A parameter that is a list repeats the template line that names it; see templates/.\n"""\n\n']
    signing = (None, 'server', 'distribution', ['custom_manifest.mf', 'keystore.jks', 'keystore.properties'])
    out.append('MOVES = [\n' + ''.join(_rule_text(*r, '    ') for r in [signing] + [r for r in rules if r[0] is None]) + ']\n\n')
    out.append("COPIES = [\n    ('server/build.gradle', 'distribution/build.gradle'),\n]\n\n")
    out.append('PLUGINS = {\n')
    for ext in sorted(e.name for e in plan.exts):
        out.append(f"    {ext!r}: {{\n        'moves': [\n")
        out.append(''.join(_rule_text(*r, ' ' * 12) for r in rules if r[0] == ext))
        out.append("        ],\n        'projects': {\n")
        for tier, fragments in projects[ext].items():
            if len(fragments) == 1:
                out.append(f'            {tier!r}: [{_fragment_text(fragments, "").strip().rstrip(",")}],\n')
            else:
                out.append(f'            {tier!r}: [\n{_fragment_text(fragments, " " * 16)}            ],\n')
        out.append('        },\n    },\n')
    out.append('}\n')
    return ''.join(out)


# ------------------------------------------------------------------ emit

def _run(repo, out, dest):
    """Runs the emitted script on a fresh clone of repo."""
    clone(repo, dest)
    shutil.copytree(out, dest / 'tools/split-plugins')
    run = subprocess.run(['python3', 'tools/split-plugins/split.py'], cwd=dest, capture_output=True, text=True)
    if run.returncode:
        raise SystemExit(f'split.py failed:\n{run.stdout}{run.stderr}')
    shutil.rmtree(dest / 'tools/split-plugins')
    return dest


def _add(out, rel, data):
    p = out / 'files' / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_bytes(data)


def emit(repo, out):
    out = Path(out)
    plan = plan_mod.make_plan(repo)
    work = Path(tempfile.mkdtemp(prefix='split-plugins-'))
    try:
        reference = clone(repo, work / 'reference')
        apply_mod.apply(reference, plan)

        if out.exists():
            shutil.rmtree(out)
        shutil.copytree(HERE / 'reviewable', out, ignore=shutil.ignore_patterns('__pycache__'))
        shutil.copy(HERE / 'jar_manifest.py', out)
        (out / 'manifest.py').write_text(manifest_text(repo, plan))
        for t in ('plugin-base', 'plugin-common', 'plugin-server', 'plugin-client'):
            _add(out, f'gradle/{t}.gradle', (apply_mod.TEMPLATES / f'{t}.gradle').read_bytes())
        _add(out, 'plugins/build.gradle', (apply_mod.TEMPLATES / 'plugins.build.gradle').read_bytes())
        _add(out, 'plugins/gaps.md', (reference / 'plugins/gaps.md').read_bytes())

        # what the manifest, templates and files produce on their own; the patches are the rest
        (out / 'patches').mkdir()
        preview = _run(repo, out, work / 'preview')
        rf, _ = tree(reference)
        pf, _ = tree(preview)
        if pf.keys() != rf.keys():
            raise SystemExit('the manifest produces a different set of files:\n' + '\n'.join(compare(preview, reference)[:20]))
        for rel in sorted(rf):
            if not filecmp.cmp(pf[rel], rf[rel], shallow=False):
                patch = out / 'patches' / (rel + '.patch')
                patch.parent.mkdir(parents=True, exist_ok=True)
                patch.write_bytes(subprocess.run(['diff', '-u', '--label', f'a/{rel}', '--label', f'b/{rel}', str(pf[rel]), str(rf[rel])],
                                                 capture_output=True).stdout)

        tests = subprocess.run(['python3', '-m', 'unittest', '-q'], cwd=out, capture_output=True, text=True,
                               env={**os.environ, 'PYTHONDONTWRITEBYTECODE': '1'})
        if tests.returncode:
            raise SystemExit(f"the emitted script's tests fail:\n{tests.stderr}")
        problems = compare(_run(repo, out, work / 'result'), reference)
        if problems:
            raise SystemExit('the emitted script does not reproduce `apply`:\n' + '\n'.join(problems[:20]))
        print(f'{out}: reproduces apply exactly ({len(list((out / "patches").rglob("*.patch")))} patches)')
    finally:
        shutil.rmtree(work, ignore_errors=True)
