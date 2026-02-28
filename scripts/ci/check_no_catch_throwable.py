#!/usr/bin/env python3
"""
Fail CI when catch(Throwable) is introduced in production code.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAIN_JAVA_DIR = ROOT / "src" / "main" / "java"
PATTERN = re.compile(r"catch\s*\(\s*Throwable(?:\s+\w+)?\s*\)")


def main() -> int:
    if not MAIN_JAVA_DIR.exists():
        print(f"Diretorio nao encontrado: {MAIN_JAVA_DIR}")
        return 1

    ocorrencias: list[tuple[Path, int, str]] = []
    for arquivo in sorted(MAIN_JAVA_DIR.rglob("*.java")):
        conteudo = arquivo.read_text(encoding="utf-8", errors="replace")
        for numero_linha, linha in enumerate(conteudo.splitlines(), start=1):
            if PATTERN.search(linha):
                ocorrencias.append((arquivo, numero_linha, linha.strip()))

    if not ocorrencias:
        print("Throwable Catch Guard: OK (nenhuma ocorrencia em src/main/java).")
        return 0

    print("Throwable Catch Guard: FALHA (ocorrencias detectadas):")
    for arquivo, linha, trecho in ocorrencias:
        relativo = arquivo.relative_to(ROOT).as_posix()
        print(f" - {relativo}:{linha}: {trecho}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
