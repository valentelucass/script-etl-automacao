#!/usr/bin/env python3
"""
Fail CI when mojibake regression is introduced relative to baseline.
"""

from __future__ import annotations

import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASELINE_FILE = ROOT / ".ci" / "mojibake-baseline.txt"
SCAN_DIRS = [
    ROOT / "src" / "main" / "java",
    ROOT / "src" / "main" / "resources",
    ROOT / "docs",
    ROOT / "database",
]
MARKERS = (
    "\u00C3\u0192",
    "\u00C3\u201A",
    "\u00C3\u00A2",
)


def normalizar_caminho(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def carregar_baseline(path: Path) -> dict[str, int]:
    if not path.exists():
        raise FileNotFoundError(f"Arquivo de baseline nao encontrado: {path}")

    baseline: dict[str, int] = {}
    for linha in path.read_text(encoding="utf-8", errors="replace").splitlines():
        limpa = linha.strip()
        if not limpa or limpa.startswith("#"):
            continue
        partes = [p.strip().replace("\\", "/") for p in limpa.split("|")]
        if len(partes) != 2:
            raise ValueError(f"Linha invalida no baseline: {linha}")
        baseline[partes[0]] = int(partes[1])
    return baseline


def contar_mojibake_por_arquivo() -> dict[str, int]:
    resultados: dict[str, int] = {}
    for scan_dir in SCAN_DIRS:
        if not scan_dir.exists():
            continue
        for arquivo in scan_dir.rglob("*"):
            if not arquivo.is_file():
                continue
            try:
                conteudo = arquivo.read_text(encoding="utf-8", errors="replace")
            except OSError:
                continue

            quantidade = sum(
                1
                for linha in conteudo.splitlines()
                if any(marker in linha for marker in MARKERS)
            )
            if quantidade > 0:
                resultados[normalizar_caminho(arquivo)] = quantidade
    return resultados


def main() -> int:
    try:
        baseline = carregar_baseline(BASELINE_FILE)
        atual = contar_mojibake_por_arquivo()
    except Exception as exc:  # noqa: BLE001 - fail closed in CI
        print("Encoding Guard: erro interno")
        print(f" - {exc}")
        return 1

    if not baseline:
        print("Encoding Guard: baseline sem entradas ativas (politica: zero ocorrencias permitidas).")

    regressao: list[tuple[str, str, int, int]] = []
    for arquivo, qtd_atual in sorted(atual.items()):
        qtd_base = baseline.get(arquivo)
        if qtd_base is None:
            regressao.append(("NOVO_ARQUIVO", arquivo, 0, qtd_atual))
            continue
        if qtd_atual > qtd_base:
            regressao.append(("AUMENTO", arquivo, qtd_base, qtd_atual))

    if regressao:
        print("Regressao de mojibake detectada:")
        for tipo, arquivo, base, atual_qtd in regressao:
            if tipo == "NOVO_ARQUIVO":
                print(f" - NOVO: {arquivo} (ocorrencias: {atual_qtd})")
            else:
                print(f" - AUMENTO: {arquivo} ({base} -> {atual_qtd})")
        return 1

    print("Encoding Guard: sem regressao de mojibake em relacao a baseline.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
