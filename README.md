<!-- PORTFOLIO-FEATURED
title: Extrator de Dados ESL Cloud (ETL)
description: Sistema de automacao ETL (Java) para extrair dados das APIs GraphQL e Data Export do ESL Cloud e carregar em SQL Server.
technologies: Java 17, Maven, SQL Server, Jackson, SLF4J, HikariCP
demo: N/A (Backend CLI Tool)
highlight: true
image: public/foto1.png
-->

# Extrator de Dados ESL Cloud

Sistema ETL em Java para extrair dados do ESL Cloud (GraphQL e Data Export), persistir em SQL Server e gerar validacoes operacionais.

## Escopo

- Extracao paralela das APIs GraphQL e Data Export
- Carga em SQL Server com estrategia de MERGE/UPSERT
- Validacoes de completude, gaps e janela temporal
- Exportacao CSV e auditorias operacionais
- Execucao por scripts `.bat` para uso em producao

## Requisitos

- Java 17+
- Maven 3.9+
- SQL Server configurado
- Variaveis de ambiente no arquivo `.env` (baseado em `.env.example`)

## Setup rapido

1. Copie o arquivo de exemplo:

```bat
copy .env.example .env
```

2. Preencha os valores obrigatorios no `.env`:
- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `API_GRAPHQL_TOKEN`, `API_DATAEXPORT_TOKEN`
- `API_BASE_URL`

3. Crie as tabelas e views no banco usando scripts em `database/`.

## Build e execucao

Build local:

```bat
mvn clean package
```

Jar gerado:

```text
target\extrator.jar
```

Menu principal de operacao:

```bat
00-PRODUCAO_START.bat
```

Execucao via Maven (recomendada para comandos pontuais):

```bat
mvn --% -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=br.com.extrator.Main -Dexec.args="--ajuda"
```

## Validacao de extracao (ultimas 24h)

1. Rode os testes unitarios:

```bat
mvn test
```

2. Rode a validacao detalhada API x banco (modo Postman-like):

```bat
mvn --% -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.0:java -Dexec.mainClass=br.com.extrator.Main -Dexec.args="--validar-api-banco-24h-detalhado --sem-faturas-graphql"
```

3. Gere comparativo manual chave-a-chave (JSON + MD em `logs/`):

```bat
python scripts\manual_api_db_compare_24h.py --start 2026-02-26 --end 2026-02-27
```

Arquivos gerados:
- `logs/manual_api_db_compare_YYYY-MM-DD_HH-MM-SS.json`
- `logs/manual_api_db_compare_YYYY-MM-DD_HH-MM-SS.md`

4. Conferencia direta por `curl` (DataExport/GraphQL):

Importante no Windows: para payload JSON funcionar de forma consistente, rode `curl.exe` via `cmd /c` (evita problemas de escaping do PowerShell).

```bat
cmd /c curl.exe -s -X GET "https://SEU_HOST/api/analytics/reports/6399/data" ^
  -H "Authorization: Bearer SEU_TOKEN_DATAEXPORT" ^
  -H "Content-Type: application/json" ^
  -H "Accept: application/json" ^
  --data "{\"search\":{\"manifests\":{\"service_date\":\"2026-02-26 - 2026-02-27\"}},\"page\":\"1\",\"per\":\"10000\",\"order_by\":\"sequence_code asc\"}"
```

Recomendacao operacional:
- Rode a validacao fora da janela de escrita ativa do daemon para evitar comparacoes em estado intermediario.
- Se o daemon estiver com `--sem-faturas-graphql`, mantenha a mesma flag na validacao para evitar falso negativo nessa entidade.

## Scripts principais

- `01-executar_extracao_completa.bat`: execucao completa
- `02-testar_api_especifica.bat`: teste por API/entidade
- `03-validar_config.bat`: validacao de configuracao
- `04-extracao_por_intervalo.bat`: extracao por periodo
- `05-loop_extracao_30min.bat`: loop continuo
- `06-relatorio-completo-validacao.bat`: relatorio unificado
- `07-exportar_csv.bat`: exportacao CSV
- `08-auditar_api.bat`: auditoria de estrutura
- `09-gerenciar_usuarios.bat`: operacoes de seguranca
- `limpar_logs.bat`: remove arquivos `.log` preservando historico `.csv`
- `limpar_logs.bat /full`: limpeza ampliada (temporarios em `target/`, cache Python e artefatos de diagnostico)

## Estrutura de pastas

```text
script-automacao/
|-- src/                 # codigo Java e testes
|-- database/            # scripts SQL (tabelas, views, validacoes)
|-- docs/                # documentacao detalhada
|-- logs/                # logs e estado de execucao
|-- exports/             # CSVs exportados
|-- relatorios/          # relatorios gerados por scripts
|-- backups/             # backups zip locais
|-- target/              # artefatos de build
`-- *.bat                # automacoes operacionais
```

## Limpeza e manutencao

Limpeza de logs:

```bat
limpar_logs.bat
```

Limpeza ampliada de temporarios:

```bat
limpar_logs.bat /full
```

Limpeza de build Maven:

```bat
mvn clean
```

Itens locais que nao devem ir para versionamento ja estao cobertos no `.gitignore`:

- segredos (`.env`, credenciais)
- artefatos de build (`target/`, `build/`, `out/`)
- logs e saidas (`logs/`, `exports/`, `relatorios/`, `backups/`)
- caches locais (`__pycache__/`, `.venv/`, `node_modules/`)

## Documentacao

- `README_RESUMIDO.md`: visao resumida
- `docs/README.md`: indice completo de documentacao
- `database/README.md`: guia dos scripts SQL

## Observacao sobre Git

Se esta pasta ainda nao estiver inicializada como repositorio, execute:

```bat
git init
```

Depois disso, o `.gitignore` deste projeto passa a ser aplicado normalmente.
