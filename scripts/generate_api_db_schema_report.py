#!/usr/bin/env python3
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import pyodbc
import requests


RETRYABLE_STATUS = {429, 500, 502, 503, 504}
LOG_COUNT_RE = re.compile(r"(api_count|unique_count|db_upserts|invalid_count)=([0-9]+)")


@dataclass
class EntityConfig:
    name: str
    api_kind: str  # dataexport | graphql
    table: str
    key_sql: str
    key_fn_name: str
    dataexport_template: int | None = None
    dataexport_table: str | None = None
    dataexport_field: str | None = None
    dataexport_order: str | None = None
    dataexport_nested: bool = False
    graphql_query: str | None = None
    graphql_entity: str | None = None
    graphql_params: dict[str, Any] | None = None


GRAPHQL_QUERY_FRETES = """
query Q($params: FreightInput!, $after: String){
  freight(params:$params, after:$after, first:100){
    edges{ node{
      id accountingCreditId referenceNumber serviceAt createdAt total subtotal
      invoicesValue invoicesWeight taxedWeight realWeight cubagesCubedWeight
      totalCubicVolume invoicesTotalVolumes modal modalCte status type serviceDate
      serviceType deliveryPredictionDate destinationCityId corporationId
    } }
    pageInfo{ hasNextPage endCursor }
  }
}
""".strip()

GRAPHQL_QUERY_COLETAS = """
query Q($params: PickInput!, $after: String){
  pick(params:$params, after:$after, first:100){
    edges{ node{
      id status requestDate serviceDate sequenceCode requestHour serviceStartHour
      finishDate serviceEndHour requester comments
    } }
    pageInfo{ hasNextPage endCursor }
  }
}
""".strip()

GRAPHQL_QUERY_FATURAS = """
query Q($params: CreditCustomerBillingInput!, $after: String){
  creditCustomerBilling(params:$params, after:$after, first:100){
    edges{ node{
      id document dueDate issueDate value paidValue valueToPay discountValue
      interestValue paid type comments sequenceCode competenceMonth competenceYear
      ticketAccountId
    } }
    pageInfo{ hasNextPage endCursor }
  }
}
""".strip()


ENTITIES: list[EntityConfig] = [
    EntityConfig(
        name="manifestos",
        api_kind="dataexport",
        table="manifestos",
        key_sql=(
            "CONCAT(CAST(sequence_code AS VARCHAR(50)),'|',"
            "COALESCE(CAST(pick_sequence_code AS VARCHAR(50)),'-1'),'|',"
            "COALESCE(CAST(mdfe_number AS VARCHAR(50)),'-1'))"
        ),
        key_fn_name="manifestos",
        dataexport_template=6399,
        dataexport_table="manifests",
        dataexport_field="service_date",
        dataexport_order="sequence_code asc",
    ),
    EntityConfig(
        name="cotacoes",
        api_kind="dataexport",
        table="cotacoes",
        key_sql="CAST(sequence_code AS VARCHAR(50))",
        key_fn_name="cotacoes",
        dataexport_template=6906,
        dataexport_table="quotes",
        dataexport_field="requested_at",
        dataexport_order="sequence_code asc",
    ),
    EntityConfig(
        name="localizacao_cargas",
        api_kind="dataexport",
        table="localizacao_cargas",
        key_sql="CAST(sequence_number AS VARCHAR(50))",
        key_fn_name="localizacao_cargas",
        dataexport_template=8656,
        dataexport_table="freights",
        dataexport_field="service_at",
        dataexport_order="sequence_number asc",
    ),
    EntityConfig(
        name="contas_a_pagar",
        api_kind="dataexport",
        table="contas_a_pagar",
        key_sql="CAST(sequence_code AS VARCHAR(50))",
        key_fn_name="contas_a_pagar",
        dataexport_template=8636,
        dataexport_table="accounting_debits",
        dataexport_field="issue_date",
        dataexport_order="issue_date desc",
        dataexport_nested=True,
    ),
    EntityConfig(
        name="faturas_por_cliente",
        api_kind="dataexport",
        table="faturas_por_cliente",
        key_sql="unique_id",
        key_fn_name="faturas_por_cliente",
        dataexport_template=4924,
        dataexport_table="freights",
        dataexport_field="service_at",
        dataexport_order="unique_id asc",
    ),
    EntityConfig(
        name="fretes",
        api_kind="graphql",
        table="fretes",
        key_sql="CAST(id AS VARCHAR(50))",
        key_fn_name="graphql_id",
        graphql_query=GRAPHQL_QUERY_FRETES,
        graphql_entity="freight",
    ),
    EntityConfig(
        name="coletas",
        api_kind="graphql",
        table="coletas",
        key_sql="id",
        key_fn_name="graphql_id",
        graphql_query=GRAPHQL_QUERY_COLETAS,
        graphql_entity="pick",
    ),
    EntityConfig(
        name="faturas_graphql",
        api_kind="graphql",
        table="faturas_graphql",
        key_sql="CAST(id AS VARCHAR(50))",
        key_fn_name="graphql_id",
        graphql_query=GRAPHQL_QUERY_FATURAS,
        graphql_entity="creditCustomerBilling",
    ),
]


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
    return sm.group(1).replace(":", ",", 1), dm.group(1)


def odbc_driver() -> str:
    for name in ("ODBC Driver 18 for SQL Server", "ODBC Driver 17 for SQL Server", "SQL Server"):
        if name in pyodbc.drivers():
            return name
    drivers = pyodbc.drivers()
    if not drivers:
        raise RuntimeError("Nenhum driver ODBC disponivel.")
    return drivers[-1]


def conn_str(env: dict[str, str]) -> str:
    server, db = parse_jdbc(env["DB_URL"])
    return (
        f"DRIVER={{{odbc_driver()}}};SERVER={server};DATABASE={db};"
        f"UID={env['DB_USER']};PWD={env['DB_PASSWORD']};TrustServerCertificate=yes;"
    )


def sleep_rate() -> None:
    time.sleep(2.3)


def request_with_retry(sess: requests.Session, method: str, url: str, **kwargs: Any) -> requests.Response:
    last_resp: requests.Response | None = None
    for attempt in range(1, 7):
        resp = sess.request(method, url, **kwargs)
        last_resp = resp
        if resp.status_code not in RETRYABLE_STATUS:
            return resp
        sleep = min(2.3 * (2 ** (attempt - 1)), 30.0)
        time.sleep(sleep)
    if last_resp is None:
        raise RuntimeError("Falha HTTP sem resposta.")
    return last_resp


def snake_case(name: str) -> str:
    out: list[str] = []
    for i, ch in enumerate(name):
        if ch.isupper() and i > 0 and name[i - 1].isalnum() and name[i - 1] != "_":
            out.append("_")
        out.append(ch.lower())
    return "".join(out)


def key_manifestos(row: dict[str, Any]) -> str:
    pick = row.get("mft_pfs_pck_sequence_code")
    mdfe = row.get("mft_mfs_number")
    return f"{row.get('sequence_code')}|{pick if pick is not None else -1}|{mdfe if mdfe is not None else -1}"


def key_cotacoes(row: dict[str, Any]) -> str:
    return str(row.get("sequence_code"))


def key_localizacao(row: dict[str, Any]) -> str:
    return str(row.get("corporation_sequence_number") if row.get("corporation_sequence_number") is not None else row.get("sequence_number"))


def key_contas(row: dict[str, Any]) -> str:
    return str(row.get("ant_ils_sequence_code") if row.get("ant_ils_sequence_code") is not None else row.get("sequence_code"))


def key_faturas_cliente(row: dict[str, Any]) -> str:
    nfse = row.get("fit_nse_number", row.get("nfse_number"))
    if nfse is not None and str(nfse).strip():
        return f"NFSE-{str(nfse).strip()}"
    cte = str(row.get("fit_fhe_cte_key") or "").strip()
    if cte:
        return cte
    doc = str(row.get("fit_ant_document") or "").strip()
    if doc:
        return f"FATURA-{doc}"
    billing = str(row.get("billingId") or "").strip()
    if billing:
        return f"BILLING-{billing}"
    return ""


def key_graphql_id(row: dict[str, Any]) -> str:
    return str(row.get("id"))


KEY_FNS = {
    "manifestos": key_manifestos,
    "cotacoes": key_cotacoes,
    "localizacao_cargas": key_localizacao,
    "contas_a_pagar": key_contas,
    "faturas_por_cliente": key_faturas_cliente,
    "graphql_id": key_graphql_id,
}


def fetch_dataexport_first(
    sess: requests.Session,
    base: str,
    token: str,
    cfg: EntityConfig,
    start: str,
    end: str,
) -> dict[str, Any]:
    assert cfg.dataexport_template is not None
    assert cfg.dataexport_table is not None
    assert cfg.dataexport_field is not None
    assert cfg.dataexport_order is not None

    search = {cfg.dataexport_table: {cfg.dataexport_field: f"{start} - {end}"}}
    if cfg.dataexport_nested:
        search = {cfg.dataexport_table: {cfg.dataexport_field: f"{start} - {end}", "created_at": ""}}

    url = base + f"/api/analytics/reports/{cfg.dataexport_template}/data"
    payload1 = {"search": search, "page": "1", "per": "100", "order_by": cfg.dataexport_order}
    resp1 = request_with_retry(
        sess,
        "GET",
        url,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json"},
        data=json.dumps(payload1),
        timeout=120,
    )
    rows1: list[dict[str, Any]] = []
    try:
        body1 = resp1.json()
        rows = body1.get("data") if isinstance(body1, dict) and "data" in body1 else body1
        if isinstance(rows, list):
            rows1 = [r for r in rows if isinstance(r, dict)]
    except Exception:
        rows1 = []

    sleep_rate()

    payload2 = {"search": search, "page": "2", "per": "100", "order_by": cfg.dataexport_order}
    resp2 = request_with_retry(
        sess,
        "GET",
        url,
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json", "Accept": "application/json"},
        data=json.dumps(payload2),
        timeout=120,
    )
    rows2_count = 0
    try:
        body2 = resp2.json()
        rows = body2.get("data") if isinstance(body2, dict) and "data" in body2 else body2
        if isinstance(rows, list):
            rows2_count = len(rows)
    except Exception:
        rows2_count = -1

    return {
        "status_code": resp1.status_code,
        "headers": dict(resp1.headers),
        "page1_rows": len(rows1),
        "page2_rows": rows2_count,
        "has_pagination": rows2_count > 0,
        "first_row": rows1[0] if rows1 else None,
    }


def fetch_graphql_first(
    sess: requests.Session,
    base: str,
    token: str,
    cfg: EntityConfig,
    start: str,
    end: str,
) -> dict[str, Any]:
    assert cfg.graphql_query is not None
    assert cfg.graphql_entity is not None

    if cfg.name == "fretes":
        params = {"serviceAt": f"{start} - {end}"}
    elif cfg.name == "coletas":
        params = {"requestDate": start}
    else:
        params = {"dueDate": start}

    resp = request_with_retry(
        sess,
        "POST",
        base + "/graphql",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        json={"query": cfg.graphql_query, "variables": {"params": params}},
        timeout=120,
    )
    rows: list[dict[str, Any]] = []
    has_next = False
    end_cursor: str | None = None
    errors: Any = None
    try:
        body = resp.json()
        errors = body.get("errors")
        data = (body.get("data") or {}).get(cfg.graphql_entity) or {}
        edges = data.get("edges") or []
        for edge in edges:
            node = edge.get("node") if isinstance(edge, dict) else None
            if isinstance(node, dict):
                rows.append(node)
        page_info = data.get("pageInfo") or {}
        has_next = bool(page_info.get("hasNextPage"))
        end_cursor = page_info.get("endCursor")
    except Exception:
        pass

    return {
        "status_code": resp.status_code,
        "headers": dict(resp.headers),
        "errors": errors,
        "page1_rows": len(rows),
        "has_pagination": has_next,
        "end_cursor_present": bool(end_cursor),
        "first_row": rows[0] if rows else None,
    }


def fetch_db_columns(cur: pyodbc.Cursor, table: str) -> list[dict[str, Any]]:
    cur.execute(
        """
        SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, CHARACTER_MAXIMUM_LENGTH, NUMERIC_PRECISION, NUMERIC_SCALE
        FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA='dbo' AND TABLE_NAME=?
        ORDER BY ORDINAL_POSITION
        """,
        table,
    )
    out: list[dict[str, Any]] = []
    for row in cur.fetchall():
        out.append(
            {
                "column": row[0],
                "data_type": row[1],
                "nullable": row[2] == "YES",
                "max_length": row[3],
                "precision": row[4],
                "scale": row[5],
            }
        )
    return out


def fetch_db_sample(cur: pyodbc.Cursor, cfg: EntityConfig, key: str) -> tuple[dict[str, Any], dict[str, Any] | None]:
    if not key:
        return {}, None

    cur.execute(f"SELECT TOP 1 *, metadata FROM dbo.{cfg.table} WHERE {cfg.key_sql} = ?", key)
    row = cur.fetchone()
    if row is None:
        return {}, None

    cols = [c[0] for c in cur.description]
    values = {cols[i]: row[i] for i in range(len(cols))}
    metadata = None
    raw_meta = values.get("metadata")
    if raw_meta:
        try:
            parsed = json.loads(raw_meta)
            if isinstance(parsed, dict):
                metadata = parsed
        except Exception:
            metadata = None
    return values, metadata


def parse_log_counts(cur: pyodbc.Cursor, entidade: str, start: str, end: str) -> dict[str, Any]:
    cur.execute(
        """
        SELECT TOP 1 mensagem, timestamp_inicio, timestamp_fim, status_final
        FROM dbo.log_extracoes
        WHERE entidade = ?
          AND status_final = 'COMPLETO'
          AND mensagem LIKE ?
        ORDER BY timestamp_fim DESC
        """,
        entidade,
        f"%Período: {start} a {end}%",
    )
    row = cur.fetchone()
    if not row:
        return {"found": False}

    msg = str(row[0])
    counts: dict[str, int] = {}
    for k, v in LOG_COUNT_RE.findall(msg):
        counts[k] = int(v)
    return {
        "found": True,
        "status_final": row[3],
        "timestamp_inicio": str(row[1]),
        "timestamp_fim": str(row[2]),
        "counts": counts,
        "message": msg,
    }


def simplify_value(v: Any) -> Any:
    if isinstance(v, (str, int, float, bool)) or v is None:
        return v
    return str(v)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--start", default="2026-03-03")
    parser.add_argument("--end", default="2026-03-04")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    env = load_env(root / ".env")
    sess = requests.Session()
    conn = pyodbc.connect(conn_str(env), timeout=60)
    cur = conn.cursor()

    report_entities: list[dict[str, Any]] = []
    for cfg in ENTITIES:
        if cfg.api_kind == "dataexport":
            api_info = fetch_dataexport_first(sess, env["API_BASE_URL"].rstrip("/"), env["API_DATAEXPORT_TOKEN"], cfg, args.start, args.end)
        else:
            api_info = fetch_graphql_first(sess, env["API_BASE_URL"].rstrip("/"), env["API_GRAPHQL_TOKEN"], cfg, args.start, args.end)
            sleep_rate()

        api_row = api_info.get("first_row") or {}
        key_fn = KEY_FNS[cfg.key_fn_name]
        key = key_fn(api_row) if isinstance(api_row, dict) and api_row else ""

        db_columns = fetch_db_columns(cur, cfg.table)
        db_row, db_meta = fetch_db_sample(cur, cfg, key)
        log_counts = parse_log_counts(cur, cfg.name, args.start, args.end)

        column_map = {c["column"].lower(): c for c in db_columns}
        api_fields = sorted(api_row.keys()) if isinstance(api_row, dict) else []
        field_rows: list[dict[str, Any]] = []
        ignored_fields: list[str] = []
        for field in api_fields:
            field_l = field.lower()
            field_snake = snake_case(field)
            mapped_col = None
            if field_l in column_map:
                mapped_col = column_map[field_l]["column"]
            elif field_snake in column_map:
                mapped_col = column_map[field_snake]["column"]

            col_meta = column_map.get((mapped_col or "").lower()) if mapped_col else None
            api_val = api_row.get(field) if isinstance(api_row, dict) else None
            db_val = db_row.get(mapped_col) if mapped_col else None
            meta_has = isinstance(db_meta, dict) and field in db_meta
            meta_val = db_meta.get(field) if isinstance(db_meta, dict) and field in db_meta else None

            row_info = {
                "api_field": field,
                "mapping": mapped_col if mapped_col else ("metadata_only" if meta_has else "not_captured"),
                "api_sample_value": simplify_value(api_val),
                "db_sample_value": simplify_value(db_val) if mapped_col else simplify_value(meta_val),
                "metadata_has_field": bool(meta_has),
                "db_data_type": col_meta["data_type"] if col_meta else None,
                "db_nullable": col_meta["nullable"] if col_meta else None,
                "db_max_length": col_meta["max_length"] if col_meta else None,
                "db_precision": col_meta["precision"] if col_meta else None,
                "db_scale": col_meta["scale"] if col_meta else None,
            }
            field_rows.append(row_info)
            if row_info["mapping"] == "not_captured":
                ignored_fields.append(field)

        report_entities.append(
            {
                "entity": cfg.name,
                "table": cfg.table,
                "api_kind": cfg.api_kind,
                "api_key_sample": key,
                "api_request_evidence": {
                    "status_code": api_info.get("status_code"),
                    "page1_rows": api_info.get("page1_rows"),
                    "page2_rows": api_info.get("page2_rows"),
                    "has_pagination": api_info.get("has_pagination"),
                    "end_cursor_present": api_info.get("end_cursor_present"),
                    "errors": api_info.get("errors"),
                    "headers": api_info.get("headers"),
                },
                "log_extracao_periodo": log_counts,
                "db_columns_count": len(db_columns),
                "api_fields_count": len(api_fields),
                "typed_mapped_fields": sum(1 for x in field_rows if x["mapping"] not in ("metadata_only", "not_captured")),
                "metadata_only_fields": sum(1 for x in field_rows if x["mapping"] == "metadata_only"),
                "not_captured_fields": ignored_fields,
                "field_comparison": field_rows,
            }
        )

    generated_at = dt.datetime.now().isoformat()
    report = {
        "generated_at": generated_at,
        "window": {"start": args.start, "end": args.end},
        "entities": report_entities,
    }

    logs_dir = root / "logs"
    logs_dir.mkdir(exist_ok=True)
    ts = dt.datetime.now().strftime("%Y-%m-%d_%H-%M-%S")
    out_json = logs_dir / f"api_db_schema_audit_{ts}.json"
    out_md = logs_dir / f"api_db_schema_audit_{ts}.md"
    out_json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")

    md_lines = [f"# API x DB Schema Audit ({args.start} .. {args.end})", ""]
    for ent in report_entities:
        md_lines.append(f"## {ent['entity']}")
        ev = ent["api_request_evidence"]
        md_lines.append(
            f"- request: status={ev.get('status_code')} page1_rows={ev.get('page1_rows')} "
            f"page2_rows={ev.get('page2_rows')} has_pagination={ev.get('has_pagination')}"
        )
        log_info = ent.get("log_extracao_periodo") or {}
        if log_info.get("found"):
            counts = log_info.get("counts") or {}
            md_lines.append(
                f"- log_extracao: api_count={counts.get('api_count')} unique_count={counts.get('unique_count')} "
                f"db_upserts={counts.get('db_upserts')} invalid_count={counts.get('invalid_count')}"
            )
        else:
            md_lines.append("- log_extracao: nao encontrado para periodo.")
        md_lines.append(
            f"- mapping: api_fields={ent['api_fields_count']} typed={ent['typed_mapped_fields']} "
            f"metadata_only={ent['metadata_only_fields']} not_captured={len(ent['not_captured_fields'])}"
        )
        if ent["not_captured_fields"]:
            md_lines.append(f"- not_captured_sample: {ent['not_captured_fields'][:10]}")
        md_lines.append("")
    out_md.write_text("\n".join(md_lines), encoding="utf-8")

    print(f"Relatorio JSON: {out_json}")
    print(f"Relatorio MD:   {out_md}")

    cur.close()
    conn.close()
    sess.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

