#!/usr/bin/env python3
"""Fail CI when duplicate SQL files are introduced in database/."""

from __future__ import annotations

import hashlib
import sys
from pathlib import Path


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    root = Path("database")
    if not root.exists():
        print("Diretorio 'database/' nao encontrado.")
        return 1

    sql_files = sorted(path for path in root.rglob("*.sql") if path.is_file())
    if not sql_files:
        print("Nenhum arquivo SQL encontrado em database/.")
        return 0

    hash_to_files: dict[str, list[Path]] = {}
    for path in sql_files:
        hash_to_files.setdefault(sha256_file(path), []).append(path)

    duplicate_groups = [files for files in hash_to_files.values() if len(files) > 1]
    if not duplicate_groups:
        print(f"Duplicate SQL Guard: sem duplicacao em {len(sql_files)} arquivo(s).")
        return 0

    print("Duplicate SQL Guard: duplicacao detectada em database/:")
    for group in duplicate_groups:
        print(" - Grupo duplicado:")
        for path in sorted(group):
            print(f"   - {path.as_posix()}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
