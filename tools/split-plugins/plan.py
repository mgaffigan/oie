"""Turns the analysis into a concrete plan: which file goes where, which classes
must stay in core (and why), and which project depends on which."""
import json
import os
import re
import tomllib
import zipfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

import analysis
import model

# Deleted outright: it enumerates ~20 extension classes, which no core code may do.
DELETED = ['server/src/main/java/com/mirth/connect/server/servlets/SwaggerExamplesServlet.java']


@dataclass
class Plan:
    exts: list
    sources: list
    moves: dict = field(default_factory=dict)         # repo-relative src -> dst
    kept: dict = field(default_factory=dict)          # path -> reason it stays in core
    promoted: dict = field(default_factory=dict)      # path -> reason it moved to common
    cycles: list = field(default_factory=list)        # [[node, ...]]: tiers that must compile together; first is the host
    deps: dict = field(default_factory=dict)          # (ext, tier) -> {'main': set(nodes), 'test': set(nodes)}
    core_test_deps: set = field(default_factory=set)  # nodes core tests need
    libs: dict = field(default_factory=dict)          # ext -> [catalog alias]
    vendored_libs: dict = field(default_factory=dict)  # ext -> [jar paths] that move into the plugin
    core_libs: dict = field(default_factory=dict)      # catalog alias or vendored jar path -> core classes using it
    kept_vendored: dict = field(default_factory=dict)  # ext -> lib dir whose jars stay in server/lib/extensions
    userutil_dirs: dict = field(default_factory=dict)  # ext -> repo-relative userutil source dir
    violations: list = field(default_factory=list)
    problems: list = field(default_factory=list)


def node_name(n):
    return f'{n[0]}:{n[1]}'


def _tarjan(nodes, edges):
    index, low, stack, on, out, counter = {}, {}, [], set(), [], [0]

    def visit(v):
        index[v] = low[v] = counter[0]
        counter[0] += 1
        stack.append(v)
        on.add(v)
        for w in sorted(edges.get(v, ())):
            if w not in index:
                visit(w)
                low[v] = min(low[v], low[w])
            elif w in on:
                low[v] = min(low[v], index[w])
        if low[v] == index[v]:
            comp = []
            while True:
                w = stack.pop()
                on.discard(w)
                comp.append(w)
                if w == v:
                    break
            out.append(comp)

    for v in sorted(nodes):
        if v not in index:
            visit(v)
    return out


def make_plan(repo):
    repo = Path(repo)
    exts = model.load_extensions(repo)
    sources, problems = model.classify(repo, exts)
    plan = Plan(exts, sources, problems=problems)
    by_path = {s.path: s for s in sources}
    index = analysis.build_class_index(sources)
    refs = [r for r in analysis.references(repo, sources, index) if r[0] not in DELETED]
    out = defaultdict(set)                 # consumer path -> provider paths (main code only)
    for path, owner, tier, kind, prov, fq in refs:
        if kind == 'main':
            out[path].add(prov.path)

    # 1. a shared class may only use shared classes: promote violating providers
    changed = True
    while changed:
        changed = False
        for path, owner, tier, kind, prov, fq in refs:
            if kind == 'main' and tier == 'common' and prov.owner != 'core' and prov.tier not in ('common', None):
                plan.promoted[prov.path] = f'used by shared class {Path(path).name}'
                prov.tier = 'common'
                changed = True

    # 2. whatever core (or donkey/command/...) uses stays in core, with its closure
    def close(seed):
        keep, todo = set(), list(seed)
        while todo:
            p = todo.pop()
            if p in keep or by_path[p].owner == 'core':
                continue
            keep.add(p)
            todo.extend(out[p])
        return keep

    keep = {}
    for path, owner, tier, kind, prov, fq in refs:
        if kind == 'main' and owner in ('core', 'other') and prov.owner != 'core':
            for p in close([prov.path]):
                keep.setdefault(p, f'referenced by core: {Path(path).name}'
                                if p == prov.path else f'needed by kept {Path(prov.path).name}')

    # 3. plugin tiers that depend on each other cannot be separate Gradle projects: they compile
    #    together in the first member's project and keep separate jars
    node_of = lambda p: (by_path[p].owner, by_path[p].tier)
    graph = defaultdict(set)
    for c, provs in out.items():
        if c in keep or c not in by_path or by_path[c].owner == 'core':
            continue
        for p in provs:
            if p not in keep and by_path[p].owner not in ('core', by_path[c].owner):
                graph[node_of(c)].add(node_of(p))
    plan.kept = keep
    plan.cycles = [sorted(c) for c in _tarjan(list(graph), graph) if len(c) > 1]

    # 4. dependency edges between the remaining plugin projects
    deps = defaultdict(lambda: {'main': set(), 'test': set()})
    for path, owner, tier, kind, prov, fq in refs:
        if owner in ('core', 'other') or prov.owner == 'core' or prov.path in keep or path in keep:
            continue
        me, them = (owner, by_path[path].tier), (prov.owner, prov.tier)
        if me != them:
            deps[me]['main' if kind == 'main' else 'test'].add(them)
        if prov.tier not in analysis.ALLOWED[by_path[path].tier]:
            plan.violations.append((path, me, them, fq))
        elif kind == 'main':
            pass
    for d in deps.values():
        d['test'] -= d['main']
    for comp in plan.cycles:
        host = comp[0]
        for n in comp:
            for kind in ('main', 'test'):
                deps[n][kind] -= set(comp)
                if n != host:
                    deps[host][kind] |= deps[n][kind]
    plan.deps = dict(deps)
    for path, owner, tier, kind, prov, fq in refs:
        if owner in ('core', 'other') and kind == 'test' and prov.owner != 'core' and prov.path not in keep:
            plan.core_test_deps.add((prov.owner, prov.tier))

    _plan_libs(repo, plan, by_path, keep)
    _plan_moves(repo, plan, by_path, keep)
    _add_common_deps(plan)
    return plan


def _add_common_deps(plan):
    """Project dependencies are not transitive, and a tier's classes extend the plugin's common ones
    (the plugin loads both jars), so depending on a tier means depending on its plugin's common too."""
    tiers = tiers_of(plan)
    withcommon = lambda nodes: nodes | {(p, 'common') for p, t in nodes if t != 'common' and 'common' in tiers[p]}
    for d in plan.deps.values():
        d['main'] = withcommon(d['main'])
        d['test'] = withcommon(d['test']) - d['main']
    plan.core_test_deps = withcommon(plan.core_test_deps)


def tiers_of(plan):
    """ext -> tiers that exist: those with sources, plus every tier the old build made a jar for
    (plugin.xml names the jar, so it must still be built when its classes all stay in core)."""
    out = defaultdict(set)
    for e in plan.exts:
        for tier, present in (('common', e.shared), ('server', e.server), ('client', e.client_pkg)):
            if present:
                out[e.name].add(tier)
    for dst in plan.moves.values():
        m = re.match(r'plugins/([^/]+)/(common|server|client)/', dst)
        if m:
            out[m.group(1)].add(m.group(2))
    return out


def lib_tier(tiers):
    for t in ('server', 'client', 'common'):
        if t in tiers:
            return t




def _plan_moves(repo, plan, by_path, keep):
    for s in plan.sources:
        if s.owner == 'core' or s.path in keep or s.path in DELETED:
            continue
        rel = re.sub(r'^(server|client)/src/(main|test)/(java|resources)/', '', s.path)
        ext = s.owner
        if s.kind == 'resource' and s.tier != 'common' and s.module == 'server':
            e = next(e for e in plan.exts if e.name == ext)
            if rel == f'{e.pkg}/{Path(rel).name}' and rel.endswith('.xml'):
                plan.moves[s.path] = f'plugins/{ext}/{Path(rel).name}'      # descriptor
                continue
        tier = s.tier
        sub = {'main': 'src/main/java', 'test': 'src/test/java',
               'resource': 'src/main/resources', 'testresource': 'src/test/resources'}[s.kind]
        plan.moves[s.path] = f'plugins/{ext}/{tier}/{sub}/{rel}'
        if s.kind == 'main' and tier == 'server' and '/userutil/' in rel:
            plan.userutil_dirs[ext] = f'plugins/{ext}/server/src/main/java/' + rel.split('/userutil/')[0] + '/userutil'
    tiers = tiers_of(plan)
    for e in plan.exts:
        d = repo / 'server/lib/extensions' / e.lib
        if d.is_dir() and e.name not in plan.kept_vendored:
            for jar in sorted(d.glob('*')):
                if jar.is_file():
                    src = jar.relative_to(repo).as_posix()
                    plan.moves[src] = f'plugins/{e.name}/{lib_tier(tiers[e.name])}/lib/{jar.name}'
                    plan.vendored_libs.setdefault(e.name, []).append(src)


def _imported_prefixes(repo, sources, keep):
    """Every package prefix imported by code that stays in core (and the core tests)."""
    prefixes = {}
    imp = re.compile(r'^import\s+(?:static\s+)?([\w.]+?)(?:\.\*)?;', re.M)
    for s in sources:
        if not s.path.endswith('.java') or not (s.owner == 'core' or s.path in keep):
            continue
        for m in imp.finditer(Path(repo, s.path).read_text(errors='replace')):
            parts = m.group(1).split('.')
            for i in range(1, len(parts) + 1):
                prefixes.setdefault('.'.join(parts[:i]), set()).add(Path(s.path).stem)
    return prefixes


def _jar_packages(jar):
    with zipfile.ZipFile(jar) as z:
        return {n.rsplit('/', 1)[0].replace('/', '.') for n in z.namelist() if n.endswith('.class') and '/' in n}


def _cached_jar(module, version):
    group, artifact = module.split(':')
    home = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle'))
    hits = sorted((home / 'caches/modules-2/files-2.1' / group / artifact / version).glob(f'*/{artifact}-{version}*.jar'))
    return hits[0] if hits else None


def _plan_libs(repo, plan, by_path, keep):
    """Maven artifacts staged under extensions/<lib>/ belong to that extension, and so do the
    vendored jars in server/lib/extensions/<lib>, unless core code compiles against them."""
    imported = _imported_prefixes(repo, plan.sources, keep)

    def used_by_core(jar):
        hit = set()
        for pkg in _jar_packages(jar):
            hit |= imported.get(pkg, set())
        return sorted(hit)

    layout = json.loads((repo / 'gradle/vendored-layout.json').read_text())['server-extensions']
    catalog = tomllib.loads((repo / 'gradle/libs.versions.toml').read_text())
    alias_of, version_of = {}, {}
    for alias, lib in catalog['libraries'].items():
        module = lib['module'] if 'module' in lib else f"{lib['group']}:{lib['name']}"
        version = lib.get('version')
        if isinstance(version, dict):
            version = version.get('ref') and catalog['versions'][version['ref']] or version.get('require')
        alias_of[module], version_of[module] = alias, version
    ext_of_lib = {e.lib: e.name for e in plan.exts}
    for ga, placement in layout.items():
        d = placement['dir'] if isinstance(placement, dict) else placement
        parts = d.split('/')
        if parts[0] != 'extensions' or parts[1] not in ext_of_lib:
            raise SystemExit(f'cannot attribute {ga} ({d}) to an extension')
        plan.libs.setdefault(ext_of_lib[parts[1]], []).append(alias_of[ga])
        jar = _cached_jar(ga, version_of[ga])
        users = used_by_core(jar) if jar else ['(jar not in the Gradle cache; assumed)']
        if users:
            plan.core_libs[alias_of[ga]] = users
    for e in plan.exts:
        d = repo / 'server/lib/extensions' / e.lib
        if d.is_dir():
            users = sorted({u for jar in d.glob('*.jar') for u in used_by_core(jar)})
            if users:
                plan.kept_vendored[e.name] = e.lib
                plan.core_libs[f'server/lib/extensions/{e.lib}'] = users
