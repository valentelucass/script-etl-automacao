#!/usr/bin/env python3
"""
Fail CI only when PMD violations regress relative to an approved baseline.
"""

from __future__ import annotations

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASELINE_FILE = ROOT / ".ci" / "pmd-baseline.txt"
PMD_REPORT_FILE = Path(os.getenv("PMD_REPORT_FILE", str(ROOT / "target" / "pmd.xml")))
NS = {"p": "http://pmd.sourceforge.net/report/2.0.0"}


def normalizar_path(raw_path: str) -> str:
    raw = raw_path.replace("\\", "/")
    root = str(ROOT).replace("\\", "/")

    if raw.startswith(root + "/"):
        return raw[len(root) + 1 :]

    marker = "src/main/java/"
    idx = raw.find(marker)
    if idx >= 0:
        return raw[idx:]

    return raw


def carregar_baseline(path: Path) -> dict[tuple[str, str], int]:
    if not path.exists():
        raise FileNotFoundError(f"Arquivo de baseline PMD nao encontrado: {path}")

    baseline: dict[tuple[str, str], int] = {}
    for linha in path.read_text(encoding="utf-8", errors="replace").splitlines():
        limpa = linha.strip()
        if not limpa or limpa.startswith("#"):
            continue
        partes = [p.strip().replace("\\", "/") for p in limpa.split("|")]
        if len(partes) != 3:
            raise ValueError(f"Linha invalida no baseline PMD: {linha}")
        arquivo, regra, qtd = partes
        baseline[(arquivo, regra)] = int(qtd)
    return baseline


def carregar_pmd(path: Path) -> dict[tuple[str, str], int]:
    if not path.exists():
        raise FileNotFoundError(f"Relatorio PMD nao encontrado: {path}")

    tree = ET.parse(path)
    root = tree.getroot()
    resultado: dict[tuple[str, str], int] = {}

    for file_node in root.findall("p:file", NS):
        arquivo = normalizar_path(file_node.attrib.get("name", ""))
        if not arquivo:
            continue
        for violacao in file_node.findall("p:violation", NS):
            regra = violacao.attrib.get("rule", "UNKNOWN_RULE")
            chave = (arquivo, regra)
            resultado[chave] = resultado.get(chave, 0) + 1

    return resultado


def main() -> int:
    try:
        baseline = carregar_baseline(BASELINE_FILE)
        atual = carregar_pmd(PMD_REPORT_FILE)
    except Exception as exc:  # noqa: BLE001 - fail closed in CI
        print("PMD Regression Guard: erro interno")
        print(f" - {exc}")
        return 1

    regressao: list[tuple[str, str, int, int]] = []
    for chave, qtd_atual in sorted(atual.items()):
        qtd_base = baseline.get(chave)
        if qtd_base is None:
            regressao.append((chave[0], chave[1], 0, qtd_atual))
            continue
        if qtd_atual > qtd_base:
            regressao.append((chave[0], chave[1], qtd_base, qtd_atual))

    if regressao:
        print("PMD Regression Guard: regressao detectada")
        for arquivo, regra, base, atual_qtd in regressao:
            if base == 0:
                print(f" - NOVO | {arquivo} | {regra} | ocorrencias={atual_qtd}")
            else:
                print(f" - AUMENTO | {arquivo} | {regra} | {base} -> {atual_qtd}")
        return 1

    print("PMD Regression Guard: sem regressao em relacao ao baseline.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
