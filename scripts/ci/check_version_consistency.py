#!/usr/bin/env python3
"""
Fail CI when release version metadata drifts across canonical files.

Canonical source: pom.xml <version>.
Validated files:
- README_RESUMIDO.md ("**Versao:** X.Y.Z")
- docs/README.md ("**Versao:** X.Y.Z")
- README_RESUMIDO.md must contain "Novidades X.Y.Z".
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
POM_FILE = ROOT / "pom.xml"
README_RESUMIDO_FILE = ROOT / "README_RESUMIDO.md"
DOCS_README_FILE = ROOT / "docs" / "README.md"

VERSION_PATTERN = re.compile(r"\b(\d+\.\d+\.\d+)\b")


def read_text(path: Path) -> str:
    if not path.exists():
        raise FileNotFoundError(f"Arquivo nao encontrado: {path}")
    return path.read_text(encoding="utf-8", errors="replace")


def parse_pom_version(path: Path) -> str:
    tree = ET.parse(path)
    root = tree.getroot()

    # Maven POM uses namespaces in the root tag.
    ns_match = re.match(r"\{(.+)\}", root.tag)
    ns = {"m": ns_match.group(1)} if ns_match else {}

    version_node = root.find("./m:version", ns) if ns else root.find("./version")
    if version_node is None or not (version_node.text or "").strip():
        raise ValueError("Nao foi possivel localizar <version> em pom.xml")
    return version_node.text.strip()


def parse_markdown_declared_version(path: Path) -> str:
    content = read_text(path)
    for line in content.splitlines():
        if "**" not in line:
            continue
        if "vers" not in line.lower():
            continue
        match = VERSION_PATTERN.search(line)
        if match:
            return match.group(1)
    raise ValueError(f"Nao foi possivel localizar versao declarada em {path}")


def ensure_readme_has_release_section(path: Path, version: str) -> None:
    content = read_text(path)
    if f"Novidades {version}" not in content:
        raise ValueError(
            f"{path} nao contem secao de release para a versao atual: 'Novidades {version}'"
        )


def main() -> int:
    try:
        pom_version = parse_pom_version(POM_FILE)
        resumido_version = parse_markdown_declared_version(README_RESUMIDO_FILE)
        docs_version = parse_markdown_declared_version(DOCS_README_FILE)

        errors: list[str] = []
        if resumido_version != pom_version:
            errors.append(
                f"README_RESUMIDO.md divergente: {resumido_version} (esperado: {pom_version})"
            )
        if docs_version != pom_version:
            errors.append(
                f"docs/README.md divergente: {docs_version} (esperado: {pom_version})"
            )

        try:
            ensure_readme_has_release_section(README_RESUMIDO_FILE, pom_version)
        except ValueError as exc:
            errors.append(str(exc))

        if errors:
            print("Version Consistency Guard: FALHA")
            for error in errors:
                print(f" - {error}")
            return 1

        print(
            "Version Consistency Guard: OK "
            f"(pom={pom_version}, README_RESUMIDO={resumido_version}, docs/README={docs_version})"
        )
        return 0
    except Exception as exc:  # noqa: BLE001 - fail closed in CI
        print("Version Consistency Guard: erro interno")
        print(f" - {exc}")
        return 1


if __name__ == "__main__":
    sys.exit(main())
