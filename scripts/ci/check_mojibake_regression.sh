#!/usr/bin/env bash
set -euo pipefail

BASELINE_FILE=".ci/mojibake-baseline.txt"
TMP_CURRENT="$(mktemp)"
TMP_BASELINE="$(mktemp)"
TMP_REPORT="$(mktemp)"

cleanup() {
  rm -f "$TMP_CURRENT" "$TMP_BASELINE" "$TMP_REPORT"
}
trap cleanup EXIT

if ! command -v rg >/dev/null 2>&1; then
  echo "rg (ripgrep) nao encontrado no ambiente de CI."
  exit 1
fi

if [[ ! -f "$BASELINE_FILE" ]]; then
  echo "Arquivo de baseline nao encontrado: $BASELINE_FILE"
  exit 1
fi

# Gera snapshot atual por arquivo (arquivo|quantidade).
# Marcadores escolhidos via escape Unicode para evitar problemas de encoding no proprio script.
rg -n "\x{00C3}\x{0192}|\x{00C3}\x{201A}|\x{00C3}\x{00A2}" src/main/java src/main/resources 2>/dev/null \
  | awk -F: '{print $1}' \
  | sed 's#\\#/#g' \
  | sort \
  | uniq -c \
  | awk '{print $2 "|" $1}' > "$TMP_CURRENT" || true

# Limpa comentarios/linhas vazias da baseline e normaliza separador.
grep -vE '^\s*#|^\s*$' "$BASELINE_FILE" \
  | sed 's#\\#/#g' > "$TMP_BASELINE"

# Regressao = arquivo novo com mojibake ou aumento de ocorrencias em arquivo existente.
awk -F'|' '
  NR==FNR { baseline[$1]=$2; next }
  {
    if (!($1 in baseline)) {
      print "NOVO_ARQUIVO|" $1 "|0|" $2
    } else if (($2 + 0) > (baseline[$1] + 0)) {
      print "AUMENTO|" $1 "|" baseline[$1] "|" $2
    }
  }
' "$TMP_BASELINE" "$TMP_CURRENT" > "$TMP_REPORT"

if [[ -s "$TMP_REPORT" ]]; then
  echo "Regressao de mojibake detectada:"
  awk -F'|' '
    $1 == "NOVO_ARQUIVO" {
      printf " - NOVO: %s (ocorrencias: %s)\n", $2, $4
    }
    $1 == "AUMENTO" {
      printf " - AUMENTO: %s (%s -> %s)\n", $2, $3, $4
    }
  ' "$TMP_REPORT"
  exit 1
fi

echo "Encoding Guard: sem regressao de mojibake em relacao a baseline."
