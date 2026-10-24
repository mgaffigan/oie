"""Reads the current extension layout out of the Gradle build and classifies sources.

Source of truth for what an extension contains is the existing build:
  * server/build.gradle  `serverExtensions`  - name, package, lib dir, and the
    include/exclude class globs of the -shared (common) and -server jars
  * client/build.gradle  `clientExtensions`  - name -> client package
Everything is derived from those, so the split reproduces today's jars.
"""
import fnmatch
import re
from dataclasses import dataclass, field
from pathlib import Path

TIERS = ('common', 'server', 'client')


# ---------------------------------------------------------------- groovy literals

class _Groovy:
    """Just enough of a Groovy literal parser: [..] lists/maps, 'strings', null."""

    def __init__(self, text):
        self.s = re.sub(r'//[^\n]*', '', text)
        self.i = 0

    def ws(self):
        while self.i < len(self.s) and self.s[self.i] in ' \t\r\n,':
            self.i += 1

    def value(self):
        self.ws()
        c = self.s[self.i]
        if c == "'":
            j = self.s.index("'", self.i + 1)
            v, self.i = self.s[self.i + 1:j], j + 1
            return v
        if c == '[':
            return self.collection()
        m = re.compile(r'\w+').match(self.s, self.i)
        self.i = m.end()
        return {'null': None, 'true': True, 'false': False}[m.group()]

    def collection(self):
        self.i += 1
        items, mapping = [], None
        while True:
            self.ws()
            if self.s[self.i] == ']':
                self.i += 1
                return mapping if mapping is not None else items
            key = re.compile(r"(\w+|'[^']*')\s*:").match(self.s, self.i)
            if key:
                mapping = {} if mapping is None else mapping
                self.i = key.end()
                mapping[key.group(1).strip("'")] = self.value()
            else:
                items.append(self.value())


def groovy_literal(text, var):
    """Parses `def <var> = [...]` out of a Gradle script."""
    g = _Groovy(text)
    m = re.search(r'def\s+' + re.escape(var) + r'\s*=\s*\[', g.s)
    if not m:
        raise SystemExit(f'cannot find `def {var} = [` in build script')
    g.i = m.end() - 1
    return g.value()


# ---------------------------------------------------------------- extensions

@dataclass
class Extension:
    name: str
    pkg: str                       # slash form, e.g. com/mirth/connect/connectors/http
    lib: str
    shared: dict | None
    server: dict | None
    client_pkg: str | None
    extra_server_pkgs: list = field(default_factory=list)   # e.g. org/dcm4che2

    @property
    def fqpkg(self):
        return self.pkg.replace('/', '.')


def _globs(spec):
    return (spec or {}).get('includes', []), (spec or {}).get('excludes', [])


def glob_match(path, includes, excludes):
    def rx(g):
        return re.compile('^' + re.escape(g).replace(r'\*\*', '.*').replace(r'\*', '[^/]*') + '$')
    return any(rx(g).match(path) for g in includes) and not any(rx(g).match(path) for g in excludes)


def load_extensions(root):
    root = Path(root)
    server_text = (root / 'server/build.gradle').read_text()
    client_text = (root / 'client/build.gradle').read_text()
    client = {}
    m = re.search(r'def clientExtensions = \[(.*?)\n\]', client_text, re.S)
    for name, pkg in re.findall(r"'([\w-]+)'\s*:\s*'([\w/]+)'", m.group(1)):
        client[name] = pkg
    exts = []
    for e in groovy_literal(server_text, 'serverExtensions'):
        ext = Extension(e['name'], e['pkg'], e['lib'], e['shared'], e['server'],
                        client.pop(e['name'], None))
        # packages named only by an include glob (dicom ships org/dcm4che2/**)
        for g in _globs(e['server'])[0]:
            top = g.rsplit('/**', 1)[0]
            if g.endswith('/**') and not top.startswith(ext.pkg):
                ext.extra_server_pkgs.append(top)
        exts.append(ext)
    # client-only extensions (dicomviewer): no server jar entry
    for name, pkg in client.items():
        exts.append(Extension(name, pkg, name, None, None, pkg))
    return exts


# ---------------------------------------------------------------- source files

# Classes that belong to a plugin although their package says otherwise. Core no longer
# references them (see the commit that removes core references to plugins); they ship in
# the plugin's jars, under the same package names so scripts and serialized data are unaffected.
OWNERSHIP_OVERRIDES = {
    'com/mirth/connect/server/attachments/dicom/DICOMAttachmentHandler': ('dicom', 'server'),
    'com/mirth/connect/server/attachments/dicom/DICOMAttachmentHandlerProvider': ('dicom', 'server'),
    'com/mirth/connect/server/userutil/DICOMUtil': ('dicom', 'server'),
    'com/mirth/connect/server/util/DICOMMessageUtil': ('dicom', 'server'),
    'com/mirth/connect/model/converters/DICOMConverter': ('dicom', 'common'),
    'com/mirth/connect/server/userutil/HTTPUtil': ('http', 'server'),
    'com/mirth/connect/server/userutil/DatabaseConnectionFactory': ('jdbc', 'server'),
}

@dataclass
class Source:
    path: str            # repo-relative
    kind: str            # main | test | resource | testresource
    module: str          # server | client
    owner: str           # extension name or 'core'
    tier: str | None     # common | server | client (None for core)
    fqcn: str | None = None


def _top_class_key(rel_to_java_root):
    return re.sub(r'\.java$', '.class', rel_to_java_root)


def classify(root, exts):
    """Returns every Source under server/ and client/ src trees with its owner/tier."""
    root = Path(root)
    by_pkg = sorted(exts, key=lambda e: -len(e.pkg))
    sources, problems = [], []

    def owner_of(rel, java_root_rel):
        for e in by_pkg:
            for pkg in [e.pkg] + e.extra_server_pkgs:
                if rel.startswith(pkg + '/'):
                    return e, pkg
        return None, None

    for module in ('server', 'client'):
        for kind, sub in (('main', 'src/main/java'), ('test', 'src/test/java'),
                          ('resource', 'src/main/resources'), ('testresource', 'src/test/resources')):
            base = root / module / sub
            if not base.exists():
                continue
            for f in sorted(p for p in base.rglob('*') if p.is_file()):
                rel = f.relative_to(base).as_posix()
                e, _ = owner_of(rel, sub)
                repo_rel = f.relative_to(root).as_posix()
                override = OWNERSHIP_OVERRIDES.get(rel[:-5]) if module == 'server' and kind == 'main' else None
                if override:
                    sources.append(Source(repo_rel, kind, module, override[0], override[1], _fqcn(rel)))
                    continue
                if e is None:
                    sources.append(Source(repo_rel, kind, module, 'core', None, _fqcn(rel)))
                    continue
                tier = _tier(e, module, kind, rel)
                if tier is None:
                    problems.append(f'unclassified (in no jar rule): {repo_rel} [{e.name}]')
                    tier = 'server'
                sources.append(Source(repo_rel, kind, module, e.name, tier, _fqcn(rel)))
    return sources, problems


def _fqcn(rel):
    return rel[:-5].replace('/', '.') if rel.endswith('.java') else None


def _tier(e, module, kind, rel):
    if module == 'client':
        return 'client'
    if kind in ('test', 'testresource'):
        return 'server'
    if kind == 'resource':
        # shared-jar resources (the edi xml defs) ride with common, see build.gradle
        if rel.startswith('com/mirth/connect/plugins/datatypes/edi/xml/'):
            return 'common'
        return 'server'
    key = _top_class_key(rel)
    if e.shared and glob_match(key, *_globs(e.shared)):
        return 'common'
    if e.server and glob_match(key, *_globs(e.server)):
        return 'server'
    return None
