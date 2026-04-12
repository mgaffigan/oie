#!/usr/bin/env python3
"""Generate .dockerignore from .gitignore.

.gitignore is the authoritative list of build outputs and local artifacts.
The Dockerfile builds via `COPY . .`, so anything git ignores (and that a
clean CI checkout therefore never has) must also be kept out of the image
build context; otherwise stale local artifacts leak into the image (this is
how a leftover server/setup/appdata/keystore.jks once broke TLS startup).

.gitignore and Docker's .dockerignore use *opposite* default anchoring:

    git:    a pattern with no leading/embedded slash matches at ANY depth
            (`build/` matches `client/build/`).
    docker: a pattern is matched against the full path from the context root
            (`build/` matches only root `build`; any depth needs `**/build`).

This script translates the anchoring so the two stay equivalent. Edit
.gitignore, then re-run:  python3 tools/gen-dockerignore.py
"""
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
GITIGNORE = ROOT / ".gitignore"
DOCKERIGNORE = ROOT / ".dockerignore"

# Excludes that are NOT build outputs (so they are not in .gitignore) but that
# the image build does not need. Keeping them out shrinks the context and
# avoids busting the build cache on unrelated changes.
DOCKER_SPECIFIC = [
    ".git",            # VCS metadata; never auto-excluded by Docker
    ".github/",        # CI config, not needed inside the image
    "ci/",             # smoke-test harness, built from its own context
    "**/Dockerfile*",
    "**/.dockerignore",
]


def translate(line: str) -> str | None:
    """Translate one .gitignore pattern to .dockerignore anchoring."""
    line = line.strip()
    if not line or line.startswith("#"):
        return None
    neg = line.startswith("!")
    if neg:
        line = line[1:]
    line = line.rstrip("/")  # dockerignore matches a directory by its name
    if not line:
        return None
    if line.startswith("/"):
        out = line[1:]           # git root-anchored -> docker root-relative
    elif "/" in line:
        out = line               # embedded slash: already root-anchored in both
    else:
        out = "**/" + line       # git "any depth" -> explicit in docker
    return ("!" + out) if neg else out


def main() -> None:
    header = [
        "# GENERATED FROM .gitignore by tools/gen-dockerignore.py -- DO NOT EDIT BY HAND.",
        "# .gitignore is authoritative; edit it and re-run the generator.",
        "# (git and docker use opposite default path anchoring -- see the script.)",
        "",
    ]
    out_lines = []
    for raw in GITIGNORE.read_text().splitlines():
        stripped = raw.strip()
        # Drop the many empty "# /path/" section-placeholder comments that
        # pad .gitignore; keep banner/section comments for readability.
        if stripped.startswith("#"):
            if stripped.lstrip("#").strip().startswith("/"):
                continue
            out_lines.append(raw.rstrip())
            continue
        if not stripped:
            # collapse runs of blank lines
            if out_lines and out_lines[-1] == "":
                continue
            out_lines.append("")
            continue
        t = translate(raw)
        if t is not None:
            out_lines.append(t)
    out_lines += ["", "# ---- Docker-specific (not build outputs, so not in .gitignore) ----"]
    out_lines += DOCKER_SPECIFIC
    DOCKERIGNORE.write_text("\n".join(header + out_lines) + "\n")
    print(f"Wrote {DOCKERIGNORE.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
