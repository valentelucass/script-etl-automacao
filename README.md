<!-- PORTFOLIO-FEATURED
title: Extrator de Dados ESL Cloud (ETL)
description: Sistema ETL em Java 17 para extrair dados das APIs GraphQL e Data Export do ESL Cloud, persistir em SQL Server e validar a integridade operacional.
technologies: Java 17, Maven, SQL Server, Jackson, SLF4J, HikariCP
demo: N/A (Backend CLI Tool)
highlight: true
image: docs/assets/foto1.png
-->

# Extrator de Dados ESL Cloud

Sistema ETL em Java para extrair dados do ESL Cloud, persistir em SQL Server e executar validacoes operacionais e de qualidade.

## Arquitetura final

Pacotes principais em `src/main/java/br/com/extrator`:

```text
bootstrap
aplicacao
dominio
integracao
persistencia
observabilidade
seguranca
suporte
comandos/cli
```

Pontos de entrada:

- `br.com.extrator.bootstrap.Main`
- `scripts/windows/00-PRODUCAO_START.bat`

## Estrutura do repositorio

```text
script-automacao/
|-- config/             # exemplos e artefatos de configuracao versionados
|-- database/           # scripts SQL
|-- docs/               # documentacao funcional e arquitetural
|-- runtime/            # estado, relatorios, exports e arquivos do daemon
|-- scripts/            # automacoes operacionais
|-- src/                # codigo Java e testes
|-- test-environment/   # apoio a testes locais
|-- pom.xml
`-- README.md
```

## Requisitos

- Java 17+
- Maven 3.9+
- SQL Server configurado
- arquivo `.env` na raiz do projeto

## Setup rapido

1. Gere o `.env` local:

```bat
copy config\.env.example .env
```

2. Preencha no `.env`:

- `DB_URL`, `DB_USER`, `DB_PASSWORD`
- `API_GRAPHQL_TOKEN`, `API_DATAEXPORT_TOKEN`
- `API_BASEURL`

3. Crie as tabelas e views com os scripts em `database/`.

## Build e execucao

Build completo:

```bat
mvn clean package
```

Compilacao rapida:

```bat
mvn -q -DskipTests compile
```

Testes:

```bat
mvn -q test
```

Execucao via Maven:

```bat
mvn --% -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=br.com.extrator.bootstrap.Main -Dexec.args="--ajuda"
```

Validacao API x banco nas ultimas 24h:

```bat
mvn --% -q -DskipTests org.codehaus.mojo:exec-maven-plugin:3.5.1:java -Dexec.mainClass=br.com.extrator.bootstrap.Main -Dexec.args="--validar-api-banco-24h-detalhado --sem-faturas-graphql"
```

## Scripts principais

- `scripts/windows/01-executar_extracao_completa.bat`
- `scripts/windows/04-extracao_por_intervalo.bat`
- `scripts/windows/05-loop_extracao_30min.bat`
- `scripts/windows/06-relatorio-completo-validacao.bat`
- `scripts/windows/08-auditar_api.bat`
- `scripts/windows/09-gerenciar_usuarios.bat`

## Documentacao

- `docs/etl-modernization/README.md`
- `docs/etl-modernization/AUDITORIA-FINAL.md`
- `docs/etl-modernization/TODO-ARQUITETURAL.md`
- `docs/README.md`
- `database/README.md`
