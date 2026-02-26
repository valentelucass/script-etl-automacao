#!/usr/bin/env python3
from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import decimal
import hashlib
import json
import re
import sys
import time
from pathlib import Path
from typing import Any, Callable

import pyodbc
import requests


RE_NUM = re.compile(r"^-?\d+(?:\.\d+)?$")
RE_DATE = re.compile(r"^(\d{4}-\d{2}-\d{2})")
RETRYABLE_STATUS = {429, 500, 502, 503, 504}


def http_retry(
    sess: requests.Session,
    method: str,
    url: str,
    *,
    max_attempts: int = 6,
    min_interval_sec: float = 2.3,
    **kwargs: Any,
) -> requests.Response:
    last_exc: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            resp = sess.request(method, url, **kwargs)
            if resp.status_code not in RETRYABLE_STATUS:
                return resp
            if attempt == max_attempts:
                return resp
            wait = min_interval_sec * (2 ** (attempt - 1))
            time.sleep(min(wait, 30.0))
        except requests.RequestException as exc:
            last_exc = exc
            if attempt == max_attempts:
                break
            wait = min_interval_sec * (2 ** (attempt - 1))
            time.sleep(min(wait, 30.0))
    if last_exc is not None:
        raise last_exc
    raise RuntimeError("Falha HTTP sem resposta.")


def load_env(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8-sig").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
    return env


def parse_jdbc(url: str) -> tuple[str, str]:
    sm = re.search(r"jdbc:sqlserver://([^;]+)", url)
    dm = re.search(r"databaseName=([^;]+)", url, re.I)
    if not sm or not dm:
        raise ValueError(f"DB_URL invalida: {url}")
    server = sm.group(1).replace(":", ",", 1)
    db = dm.group(1)
    return server, db


def odbc_driver() -> str:
    drv = pyodbc.drivers()
    for name in ("ODBC Driver 18 for SQL Server", "ODBC Driver 17 for SQL Server", "SQL Server"):
        if name in drv:
            return name
    if not drv:
        raise RuntimeError("Nenhum driver ODBC disponivel.")
    return drv[-1]


def conn_str(env: dict[str, str]) -> str:
    server, db = parse_jdbc(env["DB_URL"])
    return (
        f"DRIVER={{{odbc_driver()}}};SERVER={server};DATABASE={db};"
        f"UID={env['DB_USER']};PWD={env['DB_PASSWORD']};TrustServerCertificate=yes;"
    )


def query_from_java(root: Path, const_name: str) -> str:
    src = (
        root
        / "src"
        / "main"
        / "java"
        / "br"
        / "com"
        / "extrator"
        / "api"
        / "graphql"
        / "GraphQLQueries.java"
    ).read_text(encoding="utf-8")
    m = re.search(
        rf"public static final String {const_name}\s*=\s*\"\"\"(.*?)\"\"\";",
        src,
        re.S,
    )
    if not m:
        raise RuntimeError(f"Constante GraphQL nao encontrada: {const_name}")
    return m.group(1).strip()


def dparse(v: Any) -> dt.date | None:
    if v is None:
        return None
    m = RE_DATE.match(str(v).strip())
    if not m:
        return None
    try:
        return dt.date.fromisoformat(m.group(1))
    except ValueError:
        return None


def in_win(v: Any, d0: dt.date, d1: dt.date) -> bool:
    d = dparse(v)
    return d is not None and d0 <= d <= d1


def norm(v: Any) -> str:
    if v is None:
        return "__NULL__"
    if isinstance(v, bool):
        return "__BOOL__1" if v else "__BOOL__0"
    if isinstance(v, (int, float, decimal.Decimal)):
        return "__NUM__" + norm_num(str(v))
    if isinstance(v, str):
        s = v.strip()
        if RE_NUM.match(s):
            return "__NUM__" + norm_num(s)
        return "__STR__" + norm_dt(s)
    return "__JSON__" + json.dumps(v, ensure_ascii=False, sort_keys=True)


def norm_num(s: str) -> str:
    try:
        d = decimal.Decimal(s).normalize()
    except decimal.InvalidOperation:
        return s
    out = format(d, "f")
    if "." in out:
        out = out.rstrip("0").rstrip(".")
    return out or "0"


def norm_dt(s: str) -> str:
    try:
        return dt.datetime.fromisoformat(s.replace("Z", "+00:00")).isoformat()
    except ValueError:
        return s


def flat(v: Any, p: str = "", out: dict[str, Any] | None = None) -> dict[str, Any]:
    if out is None:
        out = {}
    if isinstance(v, dict):
        for k in sorted(v.keys()):
            flat(v[k], f"{p}.{k}" if p else str(k), out)
        return out
    if isinstance(v, list):
        for i, x in enumerate(v):
            flat(x, f"{p}[{i}]", out)
        return out
    out[p or "$"] = v
    return out


def short(v: Any, n: int = 180) -> str:
    t = str(v)
    return t if len(t) <= n else t[: n - 3] + "..."


def safe(v: Any) -> str:
    if v is None:
        return "NULL"
    t = str(v).strip()
    return t if t else "NULL"


def graphql_paged(sess: requests.Session, base: str, token: str, query: str, entity: str, params: dict[str, Any]) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    after = None
    for _ in range(3000):
        vars_: dict[str, Any] = {"params": params}
        if after:
            vars_["after"] = after
        r = http_retry(
            sess,
            "POST",
            base + "/graphql",
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
            json={"query": query, "variables": vars_},
            timeout=120,
        )
        if r.status_code != 200:
            raise RuntimeError(f"GraphQL {entity} status={r.status_code} body={short(r.text)}")
        body = r.json()
        if "errors" in body:
            raise RuntimeError(f"GraphQL {entity} errors={short(body['errors'])}")
        data = (body.get("data") or {}).get(entity)
        if not isinstance(data, dict):
            break
        edges = data.get("edges") or []
        for e in edges:
            node = e.get("node") if isinstance(e, dict) else None
            if isinstance(node, dict):
                out.append(node)
        pi = data.get("pageInfo") or {}
        has_next = bool(pi.get("hasNextPage"))
        nxt = pi.get("endCursor")
        if not has_next or not nxt or nxt == after:
            break
        after = nxt
        time.sleep(2.3)
    return out


def dataexport_rows(
    sess: requests.Session,
    base: str,
    token: str,
    template: int,
    table: str,
    field: str,
    d0: dt.date,
    d1: dt.date,
    per: int,
    order_by: str,
    nested: bool,
    timeout: int,
) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    p = 1
    rg = f"{d0.isoformat()} - {d1.isoformat()}"
    while True:
        search = {table: {field: rg}}
        if nested:
            search = {table: {field: rg, "created_at": ""}}
        payload = {"search": search, "page": str(p), "per": str(per), "order_by": order_by}
        r = http_retry(
            sess,
            "GET",
            base + f"/api/analytics/reports/{template}/data",
            headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json"},
            data=json.dumps(payload),
            timeout=timeout,
        )
        if r.status_code != 200:
            raise RuntimeError(f"DataExport t={template} page={p} status={r.status_code} body={short(r.text)}")
        b = r.json()
        rows = b.get("data") if isinstance(b, dict) and "data" in b else b
        if not isinstance(rows, list) or not rows:
            break
        out.extend(rows)
        p += 1
        time.sleep(2.3)
    return out


def manifesto_key(x: dict[str, Any]) -> str:
    return f"{safe(x.get('sequence_code'))}|{safe(x.get('mft_pfs_pck_sequence_code'))}|{safe(x.get('mft_mfs_number'))}"


def contas_key(x: dict[str, Any]) -> str:
    return safe(x.get("ant_ils_sequence_code") if x.get("ant_ils_sequence_code") is not None else x.get("sequence_code"))


def fatura_api_key(x: dict[str, Any]) -> str:
    max_len = 100
    nfse = x.get("fit_nse_number", x.get("nfse_number"))
    if nfse is not None and str(nfse).strip():
        return f"NFSE-{str(nfse).strip()}"
    cte = str(x.get("fit_fhe_cte_key") or "").strip()
    if cte:
        return cte if len(cte) <= max_len else "FPC-KEYHASH-" + hashlib.sha256(cte.encode()).hexdigest()
    doc = str(x.get("fit_ant_document") or "").strip()
    if doc:
        raw = f"FATURA-{doc}"
        return raw if len(raw) <= max_len else "FPC-KEYHASH-" + hashlib.sha256(raw.encode()).hexdigest()
    bill = str(x.get("billingId") or "").strip()
    if bill:
        raw = f"BILLING-{bill}"
        return raw if len(raw) <= max_len else "FPC-KEYHASH-" + hashlib.sha256(raw.encode()).hexdigest()
    parts = [
        ("nfse", nfse),
        ("cteNumber", x.get("fit_fhe_cte_number")),
        ("cteKey", x.get("fit_fhe_cte_key")),
        ("cteIssuedAt", x.get("fit_fhe_cte_issued_at")),
        ("cteStatus", x.get("fit_fhe_cte_status")),
        ("cteStatusResult", x.get("fit_fhe_cte_status_result")),
        ("document", x.get("fit_ant_document")),
        ("issueDate", x.get("fit_ant_issue_date")),
        ("dueDate", x.get("fit_ant_ils_due_date")),
        ("baixaDate", x.get("fit_ant_ils_atn_transaction_date")),
        ("originalDueDate", x.get("fit_ant_ils_original_due_date")),
        ("faturaValue", x.get("fit_ant_value")),
        ("valorFrete", x.get("total")),
        ("thirdParty", x.get("third_party_ctes_value")),
        ("tipoFrete", x.get("type")),
        ("filial", x.get("fit_crn_psn_nickname")),
        ("estado", x.get("fit_diy_sae_name")),
        ("classificacao", x.get("fit_fsn_name")),
        ("pagadorNome", x.get("fit_pyr_name")),
        ("pagadorDocumento", x.get("fit_pyr_document")),
        ("remetenteNome", x.get("fit_rpt_name")),
        ("remetenteDocumento", x.get("fit_rpt_document")),
        ("destinatarioNome", x.get("fit_sdr_name")),
        ("destinatarioDocumento", x.get("fit_sdr_document")),
        ("vendedorNome", x.get("fit_sps_slr_psn_name")),
        ("billingId", x.get("billingId")),
        ("notasFiscais", sorted([str(v).strip() for v in (x.get("invoices_mapping") or []) if str(v).strip()])),
        ("pedidosCliente", sorted([str(v).strip() for v in (x.get("fit_fte_invoices_order_number") or []) if str(v).strip()])),
    ]
    can = "|".join(f"{k}={safe(v)}" for k, v in parts)
    return "FPC-HASH-" + hashlib.sha256(can.encode()).hexdigest()


def map_key(rows: list[dict[str, Any]], fn: Callable[[dict[str, Any]], str]) -> tuple[dict[str, dict[str, Any]], int]:
    out: dict[str, dict[str, Any]] = {}
    dups = 0
    for r in rows:
        k = fn(r)
        if not k or k == "NULL":
            continue
        if k in out:
            dups += 1
        out[k] = r
    return out, dups


def cmp_rows(a: dict[str, Any], b: dict[str, Any]) -> tuple[int, int, list[dict[str, str]]]:
    fa, fb = flat(a), flat(b)
    compared = diff = 0
    samples: list[dict[str, str]] = []
    ignore_paths = {"nfse_number"}
    for p in sorted(fa.keys()):
        if p in ignore_paths:
            continue
        compared += 1
        if p not in fb or norm(fa[p]) != norm(fb[p]):
            diff += 1
            if len(samples) < 5:
                samples.append({"path": p, "api": short(fa.get(p, "<ausente>")), "db": short(fb.get(p, "<ausente>"))})
    return compared, diff, samples


@dataclasses.dataclass
class Result:
    name: str
    status: str
    api_keys: int
    db_keys: int
    missing: int
    extra: int
    row_diff: int
    field_diff: int
    api_raw: int
    db_rows: int
    notes: str
    sample_missing: list[str]
    sample_extra: list[str]
    sample_fields: list[dict[str, str]]


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--start")
    ap.add_argument("--end")
    args = ap.parse_args()

    root = Path(__file__).resolve().parents[1]
    env = load_env(root / ".env")
    d1 = dt.date.fromisoformat(args.end) if args.end else dt.date.today()
    d0 = dt.date.fromisoformat(args.start) if args.start else (d1 - dt.timedelta(days=1))
    if d1 < d0:
        raise ValueError("Periodo invalido.")

    sess = requests.Session()
    gql_pick = query_from_java(root, "QUERY_COLETAS")
    gql_fretes = query_from_java(root, "QUERY_FRETES")
    gql_pick_input = query_from_java(root, "INTROSPECTION_PICK_INPUT")

    conn = pyodbc.connect(conn_str(env), timeout=60)
    cur = conn.cursor()

    def db_meta(table: str, key_col: str, keep: Callable[[dict[str, Any]], bool], key_fn: Callable[[Any, dict[str, Any]], str]) -> tuple[dict[str, dict[str, Any]], int, int]:
        cur.execute(f"SELECT {key_col}, metadata FROM dbo.{table} WHERE metadata IS NOT NULL")
        rows: list[tuple[Any, dict[str, Any]]] = []
        for k, m in cur.fetchall():
            try:
                j = json.loads(m)
            except Exception:
                continue
            if isinstance(j, dict) and keep(j):
                rows.append((k, j))
        out: dict[str, dict[str, Any]] = {}
        dups = 0
        for k, j in rows:
            kk = key_fn(k, j)
            if not kk or kk == "NULL":
                continue
            if kk in out:
                dups += 1
            out[kk] = j
        return out, dups, len(rows)

    # coletas mode
    r = http_retry(
        sess,
        "POST",
        env["API_BASE_URL"].rstrip("/") + "/graphql",
        headers={"Authorization": f"Bearer {env['API_GRAPHQL_TOKEN']}", "Content-Type": "application/json"},
        json={"query": gql_pick_input},
        timeout=60,
    )
    pick_fields = set()
    if r.status_code == 200:
        for it in ((r.json().get("data", {}).get("__type", {}) or {}).get("inputFields") or []):
            if isinstance(it, dict) and it.get("name"):
                pick_fields.add(str(it["name"]))
    pick_service = "serviceDate" in pick_fields

    def compare(name: str, api_rows: list[dict[str, Any]], api_key: Callable[[dict[str, Any]], str], db_map: dict[str, dict[str, Any]], db_rows_count: int, note: str = "") -> Result:
        api_map, _ = map_key(api_rows, api_key)
        akeys, bkeys = set(api_map.keys()), set(db_map.keys())
        miss = sorted(akeys - bkeys)
        extra = sorted(bkeys - akeys)
        common = sorted(akeys & bkeys)
        row_diff = field_diff = compared = 0
        sample_fields: list[dict[str, str]] = []
        for k in common:
            c, d, s = cmp_rows(api_map[k], db_map[k])
            compared += c
            field_diff += d
            if d:
                row_diff += 1
                if len(sample_fields) < 10:
                    for item in s:
                        if len(sample_fields) >= 10:
                            break
                        sample_fields.append({"key": k, **item})
        status = "OK" if not miss and not extra and row_diff == 0 else "FAIL"
        return Result(
            name=name,
            status=status,
            api_keys=len(api_map),
            db_keys=len(db_map),
            missing=len(miss),
            extra=len(extra),
            row_diff=row_diff,
            field_diff=field_diff,
            api_raw=len(api_rows),
            db_rows=db_rows_count,
            notes=note,
            sample_missing=miss[:10],
            sample_extra=extra[:10],
            sample_fields=sample_fields,
        )

    base = env["API_BASE_URL"].rstrip("/")
    de_token = env["API_DATAEXPORT_TOKEN"]
    gq_token = env["API_GRAPHQL_TOKEN"]

    # API fetches
    api_manifestos = dataexport_rows(sess, base, de_token, 6399, "manifests", "service_date", d0, d1, 10000, "sequence_code asc", False, 120)
    api_cotacoes = dataexport_rows(sess, base, de_token, 6906, "quotes", "requested_at", d0, d1, 1000, "sequence_code asc", False, 60)
    api_localizacao = dataexport_rows(sess, base, de_token, 8656, "freights", "service_at", d0, d1, 10000, "sequence_number asc", False, 90)
    # contas com retry
    api_contas: list[dict[str, Any]] = []
    last_err = None
    for per, timeout in [(100, 60), (50, 120), (25, 180)]:
        try:
            api_contas = dataexport_rows(sess, base, de_token, 8636, "accounting_debits", "issue_date", d0, d1, per, "issue_date desc", True, timeout)
            last_err = None
            break
        except Exception as e:
            last_err = e
    if last_err is not None:
        raise RuntimeError(f"Falha em contas_a_pagar: {last_err}")
    api_faturas = dataexport_rows(sess, base, de_token, 4924, "freights", "service_at", d0, d1, 100, "unique_id asc", False, 60)

    # coletas
    api_coletas: list[dict[str, Any]] = []
    for day in [d0 + dt.timedelta(days=i) for i in range((d1 - d0).days + 1)]:
        ds = day.isoformat()
        api_coletas.extend(graphql_paged(sess, base, gq_token, gql_pick, "pick", {"requestDate": ds}))
        if pick_service:
            api_coletas.extend(graphql_paged(sess, base, gq_token, gql_pick, "pick", {"serviceDate": ds}))
    api_coletas, _ = map_key(api_coletas, lambda x: safe(x.get("id")))
    api_coletas_rows = list(api_coletas.values())

    # fretes
    api_fretes = graphql_paged(sess, base, gq_token, gql_fretes, "freight", {"serviceAt": f"{d0.isoformat()} - {d1.isoformat()}"})

    # DB maps
    db_manifestos, _, db_manifestos_count = db_meta(
        "manifestos",
        "sequence_code",
        lambda m: (
            (d := dparse(m.get("created_at"))) is not None
            and (d0 - dt.timedelta(days=1)) <= d <= d1
        ),
        lambda _, m: manifesto_key(m),
    )
    db_cotacoes, _, db_cotacoes_count = db_meta("cotacoes", "sequence_code", lambda m: in_win(m.get("requested_at"), d0, d1), lambda k, _: safe(k))
    db_localizacao, _, db_localizacao_count = db_meta("localizacao_cargas", "sequence_number", lambda m: in_win(m.get("service_at"), d0, d1), lambda k, _: safe(k))
    db_contas, _, db_contas_count = db_meta("contas_a_pagar", "sequence_code", lambda m: in_win(m.get("issue_date"), d0, d1), lambda k, _: safe(k))
    db_faturas, _, db_faturas_count = db_meta("faturas_por_cliente", "unique_id", lambda m: in_win(m.get("fit_ant_issue_date"), d0, d1) or in_win(m.get("fit_fhe_cte_issued_at"), d0, d1), lambda k, _: safe(k))
    db_coletas, _, db_coletas_count = db_meta(
        "coletas",
        "id",
        (lambda m: in_win(m.get("requestDate"), d0, d1) or in_win(m.get("serviceDate"), d0, d1)) if pick_service else (lambda m: in_win(m.get("requestDate"), d0, d1)),
        lambda k, _: safe(k),
    )
    db_fretes, _, db_fretes_count = db_meta("fretes", "id", lambda m: in_win(m.get("serviceAt"), d0, d1) or in_win(m.get("serviceDate"), d0, d1), lambda k, _: safe(k))

    results = [
        compare("manifestos", api_manifestos, manifesto_key, db_manifestos, db_manifestos_count),
        compare("cotacoes", api_cotacoes, lambda x: safe(x.get("sequence_code")), db_cotacoes, db_cotacoes_count),
        compare("localizacao_cargas", api_localizacao, lambda x: safe(x.get("corporation_sequence_number") if x.get("corporation_sequence_number") is not None else x.get("sequence_number")), db_localizacao, db_localizacao_count),
        compare("contas_a_pagar", api_contas, contas_key, db_contas, db_contas_count),
        compare("faturas_por_cliente", api_faturas, fatura_api_key, db_faturas, db_faturas_count),
        compare("coletas", api_coletas_rows, lambda x: safe(x.get("id")), db_coletas, db_coletas_count, "requestDate+serviceDate" if pick_service else "requestDate"),
        compare("fretes", api_fretes, lambda x: safe(x.get("id")), db_fretes, db_fretes_count),
    ]

    for rres in results:
        print(
            f"{rres.name:18} | {rres.status:4} | api={rres.api_keys} db={rres.db_keys} "
            f"missing={rres.missing} extra={rres.extra} row_diff={rres.row_diff} field_diff={rres.field_diff}"
        )

    payload = {
        "generated_at": dt.datetime.now().isoformat(),
        "window": {"start": d0.isoformat(), "end": d1.isoformat()},
        "coletas_mode": "requestDate+serviceDate" if pick_service else "requestDate",
        "entities": [dataclasses.asdict(x) for x in results],
    }
    logs = root / "logs"
    logs.mkdir(exist_ok=True)
    ts = dt.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    jpath = logs / f"manual_api_db_compare_{ts}.json"
    mpath = logs / f"manual_api_db_compare_{ts}.md"
    jpath.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    md_lines = [f"# Manual API x DB ({d0} .. {d1})", "", f"- Coletas mode: `{payload['coletas_mode']}`", ""]
    for rres in results:
        md_lines.append(
            f"- `{rres.name}` status={rres.status} api={rres.api_keys} db={rres.db_keys} "
            f"missing={rres.missing} extra={rres.extra} row_diff={rres.row_diff} field_diff={rres.field_diff}"
        )
    mpath.write_text("\n".join(md_lines), encoding="utf-8")
    print(f"Relatorio JSON: {jpath}")
    print(f"Relatorio MD:   {mpath}")

    cur.close()
    conn.close()
    sess.close()
    return 1 if any(rres.status != "OK" for rres in results) else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as e:  # pylint: disable=broad-except
        print(f"ERRO: {e}", file=sys.stderr)
        raise SystemExit(2)
