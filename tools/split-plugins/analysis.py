"""Dependency analysis between core and the extensions' common/server/client tiers."""
import re
from collections import defaultdict
from pathlib import Path

# Anything that looks like a fully qualified class name in code. Imports and inline
# FQCNs match; string literals (XStream aliases, reflection) are not compile-time uses.
_FQCN = re.compile(r'\b((?:com|org|net|de)(?:\.[a-z][\w]*)+\.[A-Z]\w*)')

# Trees outside server/client that may also reference extension code.
OTHER_JAVA_ROOTS = ['donkey', 'command', 'generator', 'smoketest']


def build_class_index(sources):
    """fqcn -> Source for every java file in the server/client trees."""
    return {s.fqcn: s for s in sources if s.fqcn and s.kind in ('main', 'test')}


def _java_files_outside(root):
    for top in OTHER_JAVA_ROOTS:
        for p in sorted(Path(root, top).rglob('*.java')):
            if 'build' not in p.relative_to(root).parts[:3]:
                yield p.relative_to(root).as_posix(), p


_PACKAGE = re.compile(r'^package\s+([\w.]+);', re.M)
_WILDCARD = re.compile(r'^import\s+([\w.]+)\.\*;', re.M)
_SIMPLE = re.compile(r'\b[A-Z]\w*')


_COMMENTS = re.compile(r'/\*.*?\*/|//[^\n]*', re.S)
_STRINGS = re.compile(r'"(?:\\.|[^"\\\n])*"')


def references(root, sources, index):
    """Yields (consumer_path, consumer_owner, consumer_tier, consumer_kind, provider Source, fqcn)."""
    def visible(module):
        # the server tree cannot see client classes; the client tree sees both
        return lambda fq: module != 'server' or index[fq].module == 'server'

    def targets(text, module='client'):
        seen = set()
        text = _COMMENTS.sub('', text)
        # simple names resolve against the file's own package and its wildcard imports
        pkgs = set(_WILDCARD.findall(text)) | set(_PACKAGE.findall(text))
        code = _STRINGS.sub('""', text)
        for simple in set(_SIMPLE.findall(code)):
            for pkg in pkgs:
                if f'{pkg}.{simple}' in index and visible(module)(f'{pkg}.{simple}'):
                    seen.add(f'{pkg}.{simple}')
        for m in _FQCN.finditer(code):
            name = m.group(1)
            while name:
                if name in index:
                    if visible(module)(name):
                        seen.add(name)
                    break
                # nested class reference: strip trailing segment
                name = name.rsplit('.', 1)[0] if '.' in name else ''
        return seen

    for s in sources:
        if not s.path.endswith('.java'):
            continue
        text = Path(root, s.path).read_text(errors='replace')
        for fq in targets(text, s.module):
            p = index[fq]
            if p.path != s.path:
                yield s.path, s.owner, s.tier, s.kind, p, fq
    for path, p in _java_files_outside(root):
        for fq in targets(p.read_text(errors='replace')):
            yield path, 'other', None, 'main', index[fq], fq


def find_core_leaks(refs):
    """core (or other modules) -> extension code, in main or test sources."""
    leaks = defaultdict(set)
    for path, owner, tier, kind, prov, fq in refs:
        if owner in ('core', 'other') and prov.owner != 'core':
            leaks[(path, kind)].add((prov.owner, prov.tier, fq))
    return leaks


ALLOWED = {'common': {'common'}, 'server': {'common', 'server'}, 'client': {'common', 'client'}}


def find_extension_edges(refs):
    """extension tier -> extension tier edges, plus references a tier may not make.

    A tier may use its own plugin's and other plugins' classes only in the
    tiers listed in ALLOWED: common is a leaf, server and client never see each
    other (the admin client only ever loads the shared jar of a server class).
    """
    edges = defaultdict(set)
    violations = []
    for path, owner, tier, kind, prov, fq in refs:
        if owner in ('core', 'other') or prov.owner == 'core':
            continue
        if owner != prov.owner:
            edges[(owner, tier)].add((prov.owner, prov.tier))
        if prov.tier not in ALLOWED[tier]:
            violations.append((path, owner, tier, prov.owner, prov.tier, fq))
    return edges, violations
