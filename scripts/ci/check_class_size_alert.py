#!/usr/bin/env python3
"""
Alert/fail CI when Java class size exceeds configured thresholds.
"""

from __future__ import annotations

import os
import sys
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
MAIN_JAVA_DIR = ROOT / "src" / "main" / "java"
BASELINE_FILE = ROOT / ".ci" / "class-size-baseline.txt"
ALERT_THRESHOLD = int(os.getenv("CLASS_SIZE_ALERT_THRESHOLD", "800"))
FAIL_THRESHOLD = int(os.getenv("CLASS_SIZE_FAIL_THRESHOLD", "1300"))
TOP_LIMIT = int(os.getenv("CLASS_SIZE_TOP_LIMIT", "10"))
ENFORCE_NO_GROWTH = os.getenv("CLASS_SIZE_ENFORCE_NO_GROWTH", "1") not in {"0", "false", "False"}


@dataclass(frozen=True)
class ClassSize:
    path: Path
    lines: int


def contar_linhas(path: Path) -> int:
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        return sum(1 for _ in handle)


def carregar_baseline(path: Path) -> dict[str, int]:
    if not path.exists():
        return {}

    baseline: dict[str, int] = {}
    for linha in path.read_text(encoding="utf-8", errors="replace").splitlines():
        limpa = linha.strip()
        if not limpa or limpa.startswith("#"):
            continue
        partes = [p.strip().replace("\\", "/") for p in limpa.split("|")]
        if len(partes) != 2:
            raise ValueError(f"Linha invalida no baseline de class size: {linha}")
        baseline[partes[0]] = int(partes[1])
    return baseline


def main() -> int:
    if not MAIN_JAVA_DIR.exists():
        print(f"Diretorio nao encontrado: {MAIN_JAVA_DIR}")
        return 1

    classes: list[ClassSize] = []
    for arquivo in MAIN_JAVA_DIR.rglob("*.java"):
        classes.append(ClassSize(path=arquivo, lines=contar_linhas(arquivo)))
    classes.sort(key=lambda c: c.lines, reverse=True)

    alertas = [c for c in classes if c.lines >= ALERT_THRESHOLD]
    falhas = [c for c in classes if c.lines >= FAIL_THRESHOLD]
    baseline = carregar_baseline(BASELINE_FILE) if ENFORCE_NO_GROWTH else {}

    if not alertas:
        print(
            "Class Size Guard: OK "
            f"(nenhuma classe >= {ALERT_THRESHOLD} linhas; fail threshold={FAIL_THRESHOLD})."
        )
        return 0

    print(
        "Class Size Guard: ALERTA "
        f"({len(alertas)} classe(s) >= {ALERT_THRESHOLD} linhas; fail threshold={FAIL_THRESHOLD})."
    )
    for classe in alertas[:TOP_LIMIT]:
        rel = classe.path.relative_to(ROOT).as_posix()
        print(f" - {classe.lines:4d} linhas | {rel}")

    if falhas:
        print("Class Size Guard: FALHA (classe(s) acima do limite de bloqueio):")
        for classe in falhas:
            rel = classe.path.relative_to(ROOT).as_posix()
            print(f" - {classe.lines:4d} linhas | {rel}")
        return 1

    if ENFORCE_NO_GROWTH:
        if not baseline:
            print(
                "Class Size Guard: baseline ausente/vazio para no-growth em "
                f"{BASELINE_FILE.as_posix()} (regra no-growth sera ignorada)."
            )
        else:
            regressao: list[tuple[str, int, int]] = []
            for classe in alertas:
                rel = classe.path.relative_to(ROOT).as_posix()
                base = baseline.get(rel)
                if base is None:
                    regressao.append((rel, 0, classe.lines))
                elif classe.lines > base:
                    regressao.append((rel, base, classe.lines))

            if regressao:
                print(
                    "Class Size Guard: FALHA (regra no-growth para classes acima do alerta foi violada):"
                )
                for rel, base, atual in regressao:
                    if base == 0:
                        print(f" - NOVA classe acima do alerta: {rel} ({atual} linhas)")
                    else:
                        print(f" - CRESCIMENTO: {rel} ({base} -> {atual})")
                return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
