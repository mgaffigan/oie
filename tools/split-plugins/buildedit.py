"""Text surgery on the existing Gradle scripts. Every edit asserts that its anchor
is present exactly as expected, so a build file that drifted fails loudly instead
of being half-converted."""
import re
from pathlib import Path


class EditError(SystemExit):
    pass


def replace(text, old, new, count=1, what=None):
    n = text.count(old)
    if n != count:
        raise EditError(f'expected {count} occurrence(s) of {what or old!r}, found {n}')
    return text.replace(old, new)


def sub(text, pattern, repl, count=1, flags=re.S):
    result, n = re.subn(pattern, repl, text, flags=flags)
    if n != count:
        raise EditError(f'expected {count} match(es) of /{pattern}/, found {n}')
    return result


def lines_of(text):
    return text.split('\n')


class Sections:
    """Slices a Gradle script into pieces delimited by banner titles or anchors."""

    def __init__(self, text):
        self.lines = lines_of(text)

    def title(self, title):
        """Index of the `// ----` line opening the banner whose title line starts with `title`."""
        hits = [i for i, l in enumerate(self.lines) if l.startswith(title)]
        if len(hits) != 1:
            raise EditError(f'banner {title!r} found {len(hits)} times')
        i = hits[0]
        if not self.lines[i - 1].startswith('// ---'):
            raise EditError(f'{title!r} is not preceded by a banner rule')
        return i - 1

    def anchor(self, prefix):
        hits = [i for i, l in enumerate(self.lines) if l.startswith(prefix)]
        if len(hits) != 1:
            raise EditError(f'anchor {prefix!r} found {len(hits)} times')
        return hits[0]

    def cut(self, start, end):
        return '\n'.join(self.lines[start:end]).rstrip('\n') + '\n'


def read(path):
    with open(path, newline='') as f:      # keep CRLF files as they are
        return f.read()


def write(path, text):
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    with open(p, 'w', newline='') as f:
        f.write(text)
