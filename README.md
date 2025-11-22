<!-- PORTFOLIO-FEATURED
title: Extrator de Dados ESL Cloud (ETL)
description: Sistema de automação ETL (Java) para extrair dados de 3 APIs (REST, GraphQL, Data Export) do ESL Cloud e carregar em SQL Server, com sistema robusto de deduplicação e execução paralela resiliente.
technologies: Java 17, Maven, SQL Server (mssql-jdbc), Jackson, SLF4J
demo: N/A (Backend CLI Tool)
highlight: true
image: public/foto1.png
-->

<p align="center">
  <img src="public/foto1.png" alt="Capa do projeto" width="1200">
</p>

# Extrator de Dados ESL Cloud

**Sistema de Automação ETL (Extract, Transform, Load)** desenvolvido em Java para extrair dados das APIs GraphQL e Data Export do ESL Cloud e carregá-los em SQL Server, com coleta automática de métricas de execução e sistema robusto de deduplicação.

**Versão:** 2.2.0 | **Última Atualização:** 22/11/2025 | **Status:** ✅ Estável

---

## 📋 Índice

1. [Visão Geral](#visão-geral)
2. [Arquitetura do Sistema](#arquitetura-do-sistema)
3. [APIs e Entidades Completas](#apis-e-entidades-completas)
4. [Processo de Extração (ETL)](#processo-de-extração-etl)
5. [Sistema de Deduplicação e MERGE](#sistema-de-deduplicação-e-merge)
6. [Estrutura de Dados por Entidade](#estrutura-de-dados-por-entidade)
7. [Classes e Componentes](#classes-e-componentes)
8. [Como Usar](#como-usar)
9. [Tecnologias Utilizadas](#tecnologias-utilizadas)
10. [Estrutura de Arquivos](#estrutura-de-arquivos)
11. [Problemas Resolvidos](#problemas-resolvidos)

---

## 🎯 Visão Geral

### O Que Este Projeto Faz?

Este projeto é um **sistema de automação ETL** que:

1. **Extrai dados** de 2 APIs do ESL Cloud (GraphQL, Data Export)
2. **Transforma** os dados JSON em entidades estruturadas
3. **Carrega** os dados em um banco SQL Server usando operações MERGE (UPSERT)
4. **Garante integridade** através de sistema robusto de deduplicação
5. **Registra métricas** de execução e gera logs detalhados
6. **Exporta dados** para CSV para análise externa

### Objetivo Principal

Automatizar a extração de dados operacionais do ESL Cloud (sistema de gestão de transportes) para um banco de dados SQL Server, permitindo:
- Análise de dados históricos
- Relatórios customizados
- Integração com outros sistemas
- Auditoria e rastreabilidade

### Características Principais

- ✅ **2 APIs Integradas**: GraphQL e Data Export
- ✅ **8 Entidades Extraídas**: Contas a Pagar (Data Export), Faturas por Cliente (Data Export), Coletas, Fretes, Manifestos, Cotações, Localização de Carga, Ocorrências (Data Export)
- ✅ **Ocorrências (Data Export)**: pasta e classes preparadas
- ✅ **Sistema MERGE Robusto**: Previne duplicados falsos e preserva duplicados naturais
- ✅ **Deduplicação Inteligente**: Remove duplicados da API antes de salvar
- ✅ **Paginação Completa**: Garante 100% de cobertura dos dados
- ✅ **Logs Estruturados**: Rastreamento completo de todas as operações
- ✅ **Métricas Automáticas**: Coleta de performance e estatísticas
- ✅ **Exportação CSV**: Exportação completa de todos os dados
- ✅ **Validação Automática**: Comandos para validar integridade dos dados

---

## 🏗️ Arquitetura do Sistema

### Padrão Arquitetural

O sistema segue um **padrão de orquestração** com runners especializados:

```
Main.java (Orquestrador)
    ├── GraphQLRunner.java (API GraphQL)
    │   ├── Coletas
    │   └── Fretes
    └── DataExportRunner.java (API Data Export)
        ├── Manifestos
        ├── Cotações
        ├── Localização de Carga
        ├── Contas a Pagar
        └── Faturas por Cliente
```

### Componentes Principais

#### 1. **Orquestrador (`Main.java`)**
- Ponto de entrada do sistema
- Interpreta argumentos da linha de comando
- Delega execução para runners especializados
- Gerencia logging e tratamento de erros

#### 2. **Runners (`runners/*.java`)**
- **GraphQLRunner**: Executa extração de dados via API GraphQL
- **DataExportRunner**: Executa extração de dados via API Data Export

#### 3. **Clientes de API (`api/*.java`)**
- **ClienteApiGraphQL**: Cliente HTTP para API GraphQL
- **ClienteApiDataExport**: Cliente HTTP para API Data Export
- Implementam paginação, retry, timeout e tratamento de erros

#### 4. **DTOs e Mappers (`modelo/*.java`)**
- **DTOs (Data Transfer Objects)**: Representam dados da API
- **Mappers**: Convertem DTOs em Entities
- Capturam campos explícitos + metadata JSON completo

#### 5. **Entities (`db/entity/*.java`)**
- Representam linhas nas tabelas do banco
- Contêm campos essenciais para indexação
- Incluem coluna `metadata` (JSON completo) para resiliência

#### 6. **Repositories (`db/repository/*.java`)**
- Implementam persistência no banco
- Executam operações MERGE (UPSERT)
- Validam dados antes de salvar
- Tratam erros e registram logs

#### 7. **Utilitários (`util/*.java`)**
- **ExportadorCSV**: Exporta dados para CSV
- **GerenciadorConexao**: Gerencia conexões com banco
- **CarregadorConfig**: Carrega configurações
- **LoggingService**: Sistema de logging estruturado

#### 8. **Comandos (`comandos/*.java`)**
- **ExecutarFluxoCompletoComando**: Executa extração completa
- **ValidarManifestosComando**: Valida integridade de manifestos
- **ExecutarAuditoriaComando**: Executa auditoria de dados
- **TestarApiComando**: Testa conectividade com APIs

---

## 🔌 APIs e Entidades Completas


---

### API GraphQL

**Autenticação:** `Authorization: Bearer {{token_graphql}}`

**Endpoint:** `POST {{base_url}}/graphql`

**Cliente:** `ClienteApiGraphQL.java`

#### 4. Coletas (`ColetaEntity`)

**Query:** `BuscarColetasExpandidaV2`

**Tipo GraphQL:** `Pick`

**Classes Relacionadas:**
- **DTO**: `ColetaNodeDTO.java` (modelo/graphql/coletas/)
- **Mapper**: `ColetaMapper.java` (modelo/graphql/coletas/)
- **Entity**: `ColetaEntity.java` (db/entity/)
- **Repository**: `ColetaRepository.java` (db/repository/)
- **DTOs Auxiliares**: 
  - `CityDTO.java` (modelo/graphql/coletas/)
  - `CustomerDTO.java` (modelo/graphql/coletas/)
  - `PickAddressDTO.java` (modelo/graphql/coletas/)
  - `StateDTO.java` (modelo/graphql/coletas/)
  - `UserDTO.java` (modelo/graphql/coletas/)

**Características:**
- **Chave Primária**: `id` (VARCHAR)
- **Chave de Negócio**: `sequence_code` (BIGINT)
- **Campos Principais**: 22 campos mapeados
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: 2 dias (dia anterior + dia atual)
- **Paginação**: Cursor-based (`first` e `after`)

**Campos Mapeados (22):**
1. `id` - Chave primária
2. `sequence_code` - Código sequencial
3. `request_date` - Data da solicitação
4. `service_date` - Data do serviço
5. `status` - Status da coleta
6. `total_value` - Valor total
7. `total_weight` - Peso total
8. `total_volumes` - Total de volumes
9. `cliente_id` - ID do cliente
10. `cliente_nome` - Nome do cliente
11. `local_coleta` - Local de coleta
12. `cidade_coleta` - Cidade de coleta
13. `uf_coleta` - UF de coleta
14. `usuario_id` - ID do usuário
15. `usuario_nome` - Nome do usuário
16. `request_hour` - Hora da solicitação
17. `service_start_hour` - Hora de início do serviço
18. `finish_date` - Data de finalização
19. `service_end_hour` - Hora de término do serviço
20. `requester` - Solicitante
21. `taxed_weight` - Peso taxado
22. `comments` - Comentários

**Repository:**
- **MERGE**: Usa `id` como chave de matching
- **Tabela**: `coletas`
- **Criação Automática**: Sim

#### 5. Fretes (`FreteEntity`)

**Query:** `BuscarFretesExpandidaV3`

**Tipo GraphQL:** `FreightBase`

**Classes Relacionadas:**
- **DTO**: `FreteNodeDTO.java` (modelo/graphql/fretes/)
- **Mapper**: `FreteMapper.java` (modelo/graphql/fretes/)
- **Entity**: `FreteEntity.java` (db/entity/)
- **Repository**: `FreteRepository.java` (db/repository/)
- **DTOs Auxiliares**: 
  - `CityDTO.java` (modelo/graphql/fretes/)
  - `CorporationDTO.java` (modelo/graphql/fretes/)
  - `CostCenterDTO.java` (modelo/graphql/fretes/)
  - `CustomerPriceTableDTO.java` (modelo/graphql/fretes/)
  - `FreightClassificationDTO.java` (modelo/graphql/fretes/)
  - `FreightInvoiceDTO.java` (modelo/graphql/fretes/)
  - `MainAddressDTO.java` (modelo/graphql/fretes/)
  - `PayerDTO.java` (modelo/graphql/fretes/)
  - `ReceiverDTO.java` (modelo/graphql/fretes/)
  - `SenderDTO.java` (modelo/graphql/fretes/)
  - `StateDTO.java` (modelo/graphql/fretes/)
  - `UserDTO.java` (modelo/graphql/fretes/)

**Características:**
- **Chave Primária**: `id` (BIGINT)
- **Campos Principais**: 22 campos mapeados
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: Últimas 24 horas
- **Paginação**: Cursor-based (`first` e `after`)

**Campos Mapeados (22):**
1. `id` - Chave primária
2. `servico_em` - Data/hora do serviço
3. `criado_em` - Data/hora de criação
4. `status` - Status do frete
5. `modal` - Modalidade
6. `tipo_frete` - Tipo de frete
7. `valor_total` - Valor total
8. `valor_notas` - Valor das notas
9. `peso_notas` - Peso das notas
10. `id_corporacao` - ID da corporação
11. `id_cidade_destino` - ID da cidade de destino
12. `data_previsao_entrega` - Data de previsão de entrega
13. `pagador_id` - ID do pagador
14. `pagador_nome` - Nome do pagador
15. `remetente_id` - ID do remetente
16. `remetente_nome` - Nome do remetente
17. `origem_cidade` - Cidade de origem
18. `origem_uf` - UF de origem
19. `destinatario_id` - ID do destinatário
20. `destinatario_nome` - Nome do destinatário
21. `destino_cidade` - Cidade de destino
22. `destino_uf` - UF de destino

**Repository:**
- **MERGE**: Usa `id` como chave de matching
- **Tabela**: `fretes`
- **Criação Automática**: Sim

---

### API Data Export

**Autenticação:** `Authorization: Bearer {{token_dataexport}}`

**Endpoint Base:** `GET {{base_url}}/api/analytics/reports/{templateId}/data`

**Cliente:** `ClienteApiDataExport.java`

#### 6. Manifestos (`ManifestoEntity`)

**Template ID:** `6399`

**Classes Relacionadas:**
- **DTO**: `ManifestoDTO.java` (modelo/dataexport/manifestos/)
- **Mapper**: `ManifestoMapper.java` (modelo/dataexport/manifestos/)
- **Entity**: `ManifestoEntity.java` (db/entity/)
- **Repository**: `ManifestoRepository.java` (db/repository/)

**Características:**
- **Chave Primária**: `id` (BIGINT, auto-incrementado)
- **Chave de Negócio**: `(sequence_code, pick_sequence_code, mdfe_number)` (chave composta)
- **Chave Única**: `(sequence_code, identificador_unico)` (constraint UNIQUE)
- **Campos Principais**: 40 campos mapeados
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: Últimas 24 horas
- **Paginação**: `page` e `per` (até 10000 registros por página)
- **Timeout**: 120 segundos por página
- **Especial**: Suporta múltiplos MDF-es e duplicados naturais

**Campos Mapeados (40):**
1. `sequence_code` - Código sequencial do manifesto (BIGINT)
2. `identificador_unico` - Identificador único calculado (NVARCHAR)
3. `status` - Status do manifesto (NVARCHAR)
4. `created_at` - Data de criação (DATETIMEOFFSET)
5. `departured_at` - Data de saída (DATETIMEOFFSET)
6. `closed_at` - Data de fechamento (DATETIMEOFFSET)
7. `finished_at` - Data de finalização (DATETIMEOFFSET)
8. `mdfe_number` - Número do MDF-e (INT)
9. `mdfe_key` - Chave do MDF-e (NVARCHAR)
10. `mdfe_status` - Status do MDF-e (NVARCHAR)
11. `distribution_pole` - Polo de distribuição (NVARCHAR)
12. `classification` - Classificação (NVARCHAR)
13. `vehicle_plate` - Placa do veículo (NVARCHAR)
14. `vehicle_type` - Tipo de veículo (NVARCHAR)
15. `vehicle_owner` - Proprietário do veículo (NVARCHAR)
16. `driver_name` - Nome do motorista (NVARCHAR)
17. `branch_nickname` - Apelido da filial (NVARCHAR)
18. `vehicle_departure_km` - KM de saída (INT)
19. `closing_km` - KM de fechamento (INT)
20. `traveled_km` - KM rodado (INT)
21. `invoices_count` - Total de notas (INT)
22. `invoices_volumes` - Total de volumes (INT)
23. `invoices_weight` - Peso real (DECIMAL(18,3)) ✅
24. `total_taxed_weight` - Peso taxado (DECIMAL(18,3)) ✅
25. `total_cubic_volume` - Cubagem (DECIMAL(18,6)) ✅
26. `invoices_value` - Valor das notas (DECIMAL(18,2)) ✅
27. `manifest_freights_total` - Valor dos fretes (DECIMAL(18,2)) ✅
28. `pick_sequence_code` - Código sequencial da coleta (BIGINT)
29. `contract_number` - Número do contrato (NVARCHAR)
30. `daily_subtotal` - Subtotal de diárias (DECIMAL(18,2)) ✅
31. `total_cost` - Custo total (DECIMAL(18,2)) ✅
32. `operational_expenses_total` - Despesas operacionais (DECIMAL(18,2)) ✅
33. `inss_value` - Valor do INSS (DECIMAL(18,2)) ✅
34. `sest_senat_value` - Valor do SEST/SENAT (DECIMAL(18,2)) ✅
35. `ir_value` - Valor do IR (DECIMAL(18,2)) ✅
36. `paying_total` - Valor a pagar (DECIMAL(18,2)) ✅
37. `creation_user_name` - Nome do usuário de criação (NVARCHAR)
38. `adjustment_user_name` - Nome do usuário de ajuste (NVARCHAR)
39. `metadata` - JSON completo (NVARCHAR(MAX))
40. `data_extracao` - Data de extração (DATETIME2)

**✅ Campos Numéricos:** 11 campos numéricos usam `DECIMAL` para permitir análises (SUM, AVG, comparações)

**Identificador Único:**
- **Prioridade 1**: Se `pick_sequence_code` está preenchido → usa `pick_sequence_code`
- **Prioridade 2**: Se `pick_sequence_code` é NULL mas `mdfe_number` está preenchido → usa `sequence_code + "_MDFE_" + mdfe_number`
- **Prioridade 3**: Se ambos são NULL → calcula hash SHA-256 do metadata (excluindo campos voláteis)

**Campos Voláteis Excluídos do Hash:**
- Timestamps: `mobile_read_at`, `departured_at`, `closed_at`, `finished_at`
- Quilometragens: `vehicle_departure_km`, `closing_km`, `traveled_km`
- Contadores: `finalized_manifest_items_count`
- MDF-e: `mft_mfs_number`, `mft_mfs_key`, `mdfe_status`
- Ajustes: `mft_aoe_comments`, `mft_aoe_rer_name`

**Repository:**
- **MERGE**: Usa `(sequence_code, pick_sequence_code, mdfe_number)` como chave de matching
- **Tabela**: `manifestos`
- **Constraint UNIQUE**: `(sequence_code, identificador_unico)`
- **Criação Automática**: Sim
- **Deduplicação**: Sim (antes de salvar)

#### 7. Cotações (`CotacaoEntity`)

**Template ID:** `6906`

**Classes Relacionadas:**
- **DTO**: `CotacaoDTO.java` (modelo/dataexport/cotacao/)
- **Mapper**: `CotacaoMapper.java` (modelo/dataexport/cotacao/)
- **Entity**: `CotacaoEntity.java` (db/entity/)
- **Repository**: `CotacaoRepository.java` (db/repository/)

**Características:**
- **Chave Primária**: `sequence_code` (BIGINT)
- **Campos Principais**: 19 campos mapeados
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: Últimas 24 horas
- **Paginação**: `page` e `per` (até 1000 registros por página)

**Campos Mapeados (19):**
1. `sequence_code` - Código sequencial
2. `requested_at` - Data/hora da solicitação
3. `operation_type` - Tipo de operação
4. `customer_doc` - Documento do cliente
5. `customer_name` - Nome do cliente
6. `origin_city` - Cidade de origem
7. `origin_state` - Estado de origem
8. `destination_city` - Cidade de destino
9. `destination_state` - Estado de destino
10. `price_table` - Tabela de preços
11. `volumes` - Volumes
12. `taxed_weight` - Peso taxado
13. `invoices_value` - Valor das notas
14. `total_value` - Valor total
15. `user_name` - Nome do usuário
16. `branch_nickname` - Apelido da filial
17. `company_name` - Nome da empresa
18. `requester_name` - Nome do solicitante
19. `real_weight` - Peso real

**Repository:**
- **MERGE**: Usa `sequence_code` como chave de matching
- **Tabela**: `cotacoes`
- **Criação Automática**: Sim
- **Deduplicação**: Sim (antes de salvar)

#### 8. Localização de Carga (`LocalizacaoCargaEntity`)

**Template ID:** `8656`

**Classes Relacionadas:**
- **DTO**: `LocalizacaoCargaDTO.java` (modelo/dataexport/localizacaocarga/)
- **Mapper**: `LocalizacaoCargaMapper.java` (modelo/dataexport/localizacaocarga/)
- **Entity**: `LocalizacaoCargaEntity.java` (db/entity/)
- **Repository**: `LocalizacaoCargaRepository.java` (db/repository/)

**Características:**
- **Chave Primária**: `sequence_number` (BIGINT)
- **Campos Principais**: 17 campos mapeados
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: Últimas 24 horas
- **Paginação**: `page` e `per` (até 10000 registros por página)

**Campos Mapeados (17):**
1. `sequence_number` - Número sequencial
2. `service_at` - Data/hora do serviço
3. `freight_id` - ID do frete
4. `latitude` - Latitude
5. `longitude` - Longitude
6. `address` - Endereço
7. `city` - Cidade
8. `state` - Estado
9. `postal_code` - CEP
10. `country` - País
11. `accuracy` - Precisão
12. `speed` - Velocidade
13. `heading` - Direção
14. `altitude` - Altitude
15. `device_id` - ID do dispositivo
16. `device_type` - Tipo de dispositivo
17. `metadata` - JSON completo

**Repository:**
- **MERGE**: Usa `sequence_number` como chave de matching
- **Tabela**: `localizacao_cargas`
- **Criação Automática**: Sim
- **Deduplicação**: Sim (antes de salvar)

#### 9. Contas a Pagar (`ContasAPagarDataExportEntity`)

**Template ID:** `8636`

**Classes Relacionadas:**
- **DTO**: `ContasAPagarDTO.java` (modelo/dataexport/contasapagar/)
- **Mapper**: `ContasAPagarMapper.java` (modelo/dataexport/contasapagar/)
- **Entity**: `ContasAPagarDataExportEntity.java` (db/entity/)
- **Repository**: `ContasAPagarRepository.java` (db/repository/)

**Características:**
- **Chave Primária**: `sequence_code` (BIGINT)
- **Campos Principais**: documento, emissão, tipo, valores (`valor_original`, `valor_a_pagar`, `valor_pago`), status_pagamento, competência (mês/ano), datas (criação, liquidação, transação), fornecedor, filial, centro de custo, conta contábil, observações
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: Últimas 24 horas
- **Paginação**: `page` e `per` (até 100 registros por página)

**Repository:**
- **MERGE**: Usa `sequence_code` como chave de matching
- **Tabela**: `contas_a_pagar`
- **Criação Automática**: Sim (com índices e view para PowerBI)

#### 10. Faturas por Cliente (`FaturaPorClienteEntity`)

**Template ID:** `4924`

**Classes Relacionadas:**
- **DTO**: `FaturaPorClienteDTO.java` (modelo/dataexport/faturaporcliente/)
- **Mapper**: `FaturaPorClienteMapper.java` (modelo/dataexport/faturaporcliente/)
- **Entity**: `FaturaPorClienteEntity.java` (db/entity/)
- **Repository**: `FaturaPorClienteRepository.java` (db/repository/)

**Características:**
- **Chave Primária**: `unique_id` (NVARCHAR)
- **Campos Principais**: valores (`valor_frete`, `valor_fatura`), documentos fiscais (CT-e, NFS-e), fatura (número, emissão, vencimento, baixa), classificação (filial, tipo frete, estado), envolvidos (pagador, remetente, destinatário, vendedor), listas (notas fiscais, pedidos)
- **Metadata**: `metadata` (JSON completo)
- **Filtro**: Últimas 24 horas

**Repository:**
- **MERGE**: Usa `unique_id` como chave de matching
- **Tabela**: `faturas_por_cliente`
- **Criação Automática**: Sim (com índices e view para PowerBI)

#### Ocorrências (Data Export) — pendente
- Template ID: A definir
- Classes preparadas:
  - `OcorrenciaDTO.java` (modelo/dataexport/ocorrencias/)
  - `OcorrenciaMapper.java` (modelo/dataexport/ocorrencias/)
- Observação: implementação de Entity e Repository será adicionada na próxima versão

---

## 🔄 Processo de Extração (ETL)

### Fluxo Completo

```
1. INICIALIZAÇÃO
   ├── Carregar configurações (tokens, URLs, credenciais DB)
   ├── Validar conexão com banco de dados
   └── Inicializar sistema de logging

2. EXTRAÇÃO (Extract)
   ├── Para cada API:
   │   ├── Construir requisição HTTP
   │   ├── Adicionar autenticação (Bearer token)
   │   ├── Aplicar filtros de data (últimas 24h)
   │   ├── Executar requisição
   │   ├── Processar paginação (se necessário)
   │   └── Retornar lista de DTOs
   │
   └── Resultado: Lista de DTOs (JSON deserializado)

3. TRANSFORMAÇÃO (Transform)
   ├── Para cada DTO:
   │   ├── Mapper converte DTO → Entity
   │   ├── Calcular campos derivados
   │   ├── Validar dados obrigatórios
   │   ├── Truncar strings longas (se necessário)
   │   └── Calcular identificador único (apenas para manifestos)
   │
   └── Resultado: Lista de Entities (prontas para salvar)

4. DEDUPLICAÇÃO (Opcional - para algumas entidades)
   ├── Manifestos: Usa (sequence_code + identificador_unico)
   ├── Cotações: Usa sequence_code
   ├── Localização de Carga: Usa sequence_number
   ├── Outras entidades: Não aplica deduplicação (MERGE já previne duplicados)
   └── Resultado: Lista de Entities únicas

5. CARREGAMENTO (Load)
   ├── Para cada Entity:
   │   ├── Verificar se tabela existe (criar se não existir)
   │   ├── Executar MERGE (UPSERT)
   │   │   ├── Se registro existe → UPDATE
   │   │   └── Se registro não existe → INSERT
   │   ├── Validar rowsAffected > 0
   │   └── Registrar sucesso/falha
   │
   └── Resultado: Contagem de registros processados

6. REGISTRO DE LOG
   ├── Criar LogExtracaoEntity
   ├── Registrar: entidade, timestamps, status, contagens
   └── Salvar no banco (tabela log_extracoes)

7. EXPORTAÇÃO CSV (Opcional)
   ├── Para cada entidade:
   │   ├── Executar SELECT * FROM tabela
   │   ├── Escrever cabeçalho CSV
   │   ├── Escrever dados CSV
   │   └── Validar integridade
   │
   └── Resultado: Arquivos CSV em pasta exports/
```

### Paginação

O sistema implementa paginação robusta para garantir 100% de cobertura:

- **API GraphQL**: Usa `first` e `after` (cursor-based)
- **API Data Export**: Usa `page` e `per` no corpo JSON

**Características:**
- Timeout dinâmico por template (120s para Manifestos)
- Retry automático em caso de falha
- Logs detalhados de cada página processada
- Detecção de fim de paginação

---

## 🔐 Sistema de Deduplicação e MERGE

### Visão Geral

O sistema implementa **duas camadas de proteção** contra duplicados:

1. **Deduplicação Antes de Salvar**: Remove duplicados da resposta da API
2. **MERGE (UPSERT)**: Atualiza registros existentes ou insere novos

### Deduplicação por Entidade

#### Entidades com Deduplicação

**Manifestos** (`DataExportRunner.deduplicarManifestos()`):
- **Chave**: `sequence_code + "_" + identificador_unico`
- **Método**: `Collectors.toMap` com merge function
- **Resultado**: Mantém o primeiro registro encontrado

**Cotações** (`DataExportRunner.deduplicarCotacoes()`):
- **Chave**: `sequence_code`
- **Método**: `Collectors.toMap` com merge function
- **Resultado**: Mantém o primeiro registro encontrado

**Localização de Carga** (`DataExportRunner.deduplicarLocalizacoes()`):
- **Chave**: `sequence_number`
- **Método**: `Collectors.toMap` com merge function
- **Resultado**: Mantém o primeiro registro encontrado

#### Entidades sem Deduplicação

**Contas a Pagar, Faturas por Cliente, Coletas, Fretes**:
- Não aplicam deduplicação antes de salvar
- O MERGE já previne duplicados usando a chave primária

### MERGE (UPSERT) por Entidade

#### Entidades com MERGE Simples (Chave Primária)

**Contas a Pagar (Data Export)** (`ContasAPagarRepository`):
- **Chave de Matching**: `sequence_code`
- **Tabela**: `contas_a_pagar`
- **Operação**: `MERGE ... ON target.sequence_code = source.sequence_code`

**Faturas por Cliente (Data Export)** (`FaturaPorClienteRepository`):
- **Chave de Matching**: `unique_id`
- **Tabela**: `faturas_por_cliente`
- **Operação**: `MERGE ... ON target.unique_id = source.unique_id`

**Coletas** (`ColetaRepository`):
- **Chave de Matching**: `id`
- **Tabela**: `coletas`
- **Operação**: `MERGE ... ON target.id = source.id`

**Fretes** (`FreteRepository`):
- **Chave de Matching**: `id`
- **Tabela**: `fretes`
- **Operação**: `MERGE ... ON target.id = source.id`

**Cotações** (`CotacaoRepository`):
- **Chave de Matching**: `sequence_code`
- **Tabela**: `cotacoes`
- **Operação**: `MERGE ... ON target.sequence_code = source.sequence_code`

**Localização de Carga** (`LocalizacaoCargaRepository`):
- **Chave de Matching**: `sequence_number`
- **Tabela**: `localizacao_cargas`
- **Operação**: `MERGE ... ON target.sequence_number = source.sequence_number`

#### Entidade com MERGE Complexo (Chave Composta)

**Manifestos** (`ManifestoRepository`):
- **Chave de Matching**: `(sequence_code, pick_sequence_code, mdfe_number)`
- **Tabela**: `manifestos`
- **Operação**: `MERGE ... ON target.sequence_code = source.sequence_code AND COALESCE(target.pick_sequence_code, -1) = COALESCE(source.pick_sequence_code, -1) AND COALESCE(target.mdfe_number, -1) = COALESCE(source.mdfe_number, -1)`
- **Constraint UNIQUE**: `(sequence_code, identificador_unico)`
- **Especial**: Preserva múltiplos MDF-es e duplicados naturais

### Por Que Manifestos Usam Chave Composta?

**Problema Original:**
- Manifestos podem ter múltiplos MDF-es (mesmo `sequence_code`, diferentes `mdfe_number`)
- Manifestos podem ter múltiplas coletas (mesmo `sequence_code`, diferentes `pick_sequence_code`)
- Usar apenas `sequence_code` como chave causaria perda de dados (sobrescreveria registros)

**Solução:**
- Usar `(sequence_code, pick_sequence_code, mdfe_number)` como chave de matching no MERGE
- Isso garante que múltiplos MDF-es e coletas sejam preservados
- O `identificador_unico` é usado apenas na constraint UNIQUE, não no MERGE

### Identificador Único de Manifestos

**Cálculo** (`ManifestoEntity.calcularIdentificadorUnico()`):

1. **Prioridade 1**: Se `pick_sequence_code` E `mdfe_number` estão preenchidos
   - Usar `pick_sequence_code + "_MDFE_" + mdfe_number`
   - **CRÍTICO**: Incluir mdfe_number para diferenciar múltiplos MDF-es com mesmo pick
   - Exemplo: `identificador_unico = "72433_MDFE_3545"`

2. **Prioridade 2**: Se apenas `pick_sequence_code` está preenchido (mdfe_number é NULL)
   - Usar `pick_sequence_code` como identificador
   - Exemplo: `identificador_unico = "72288"`

3. **Prioridade 3**: Se apenas `mdfe_number` está preenchido (pick_sequence_code é NULL)
   - Usar `sequence_code + "_MDFE_" + mdfe_number`
   - Exemplo: `identificador_unico = "48990_MDFE_1503"`

4. **Prioridade 4**: Se ambos são NULL
   - Calcular hash SHA-256 do metadata **excluindo campos voláteis**
   - Exemplo: `identificador_unico = "a1b2c3d4e5f6..."`

**Campos Voláteis Excluídos do Hash:**
- Timestamps: `mobile_read_at`, `departured_at`, `closed_at`, `finished_at`
- Quilometragens: `vehicle_departure_km`, `closing_km`, `traveled_km`
- Contadores: `finalized_manifest_items_count`
- MDF-e: `mft_mfs_number`, `mft_mfs_key`, `mdfe_status`
- Ajustes: `mft_aoe_comments`, `mft_aoe_rer_name`

**Por que excluir campos voláteis?**
- Esses campos podem mudar **durante** a extração
- Se incluídos no hash, causariam diferentes hashes para o mesmo manifesto
- Resultado: Duplicados falsos no banco

---

## 📊 Estrutura de Dados por Entidade

### Arquitetura Híbrida

O sistema usa uma **arquitetura híbrida** para cada entidade:

1. **Campos Essenciais**: Colunas dedicadas para campos mais usados em relatórios
2. **Coluna Metadata**: JSON completo do objeto original (garante 100% de completude)

### Tabelas do Banco de Dados


#### 1. `contas_a_pagar`

**Chave Primária**: `sequence_code` (BIGINT)

**Estrutura:**
```sql
CREATE TABLE contas_a_pagar (
    sequence_code BIGINT PRIMARY KEY,
    document_number VARCHAR(100),
    issue_date DATE,
    tipo_lancamento NVARCHAR(100),
    valor_original DECIMAL(18,2),
    valor_juros DECIMAL(18,2),
    valor_desconto DECIMAL(18,2),
    valor_a_pagar DECIMAL(18,2),
    valor_pago DECIMAL(18,2),
    status_pagamento NVARCHAR(50),
    mes_competencia INT,
    ano_competencia INT,
    data_criacao DATETIMEOFFSET,
    data_liquidacao DATE,
    data_transacao DATE,
    nome_fornecedor NVARCHAR(255),
    nome_filial NVARCHAR(255),
    nome_centro_custo NVARCHAR(255),
    valor_centro_custo DECIMAL(18,2),
    classificacao_contabil NVARCHAR(100),
    descricao_contabil NVARCHAR(255),
    valor_contabil DECIMAL(18,2),
    area_lancamento NVARCHAR(255),
    observacoes NVARCHAR(MAX),
    descricao_despesa NVARCHAR(MAX),
    nome_usuario NVARCHAR(255),
    reconciliado BIT,
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE()
);
CREATE INDEX IX_fp_data_export_issue_date ON contas_a_pagar(issue_date);
CREATE INDEX IX_fp_data_export_status ON contas_a_pagar(status_pagamento);
CREATE INDEX IX_fp_data_export_fornecedor ON contas_a_pagar(nome_fornecedor);
CREATE INDEX IX_fp_data_export_filial ON contas_a_pagar(nome_filial);
CREATE INDEX IX_fp_data_export_competencia ON contas_a_pagar(ano_competencia, mes_competencia);
```

#### 2. `faturas_por_cliente`

**Chave Primária**: `unique_id` (NVARCHAR)

**Estrutura:**
```sql
CREATE TABLE faturas_por_cliente (
    unique_id NVARCHAR(100) PRIMARY KEY,
    valor_frete DECIMAL(18,2),
    valor_fatura DECIMAL(18,2),
    numero_cte BIGINT,
    chave_cte NVARCHAR(100),
    numero_nfse BIGINT,
    status_cte NVARCHAR(255),
    data_emissao_cte DATETIMEOFFSET,
    numero_fatura NVARCHAR(50),
    data_emissao_fatura DATE,
    data_vencimento_fatura DATE,
    data_baixa_fatura DATE,
    filial NVARCHAR(255),
    tipo_frete NVARCHAR(100),
    classificacao NVARCHAR(100),
    estado NVARCHAR(50),
    pagador_nome NVARCHAR(255),
    pagador_documento NVARCHAR(50),
    remetente_nome NVARCHAR(255),
    destinatario_nome NVARCHAR(255),
    vendedor_nome NVARCHAR(255),
    notas_fiscais NVARCHAR(MAX),
    pedidos_cliente NVARCHAR(MAX),
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE()
);
CREATE INDEX IX_fpc_vencimento ON faturas_por_cliente(data_vencimento_fatura);
CREATE INDEX IX_fpc_pagador ON faturas_por_cliente(pagador_nome);
CREATE INDEX IX_fpc_filial ON faturas_por_cliente(filial);
CREATE INDEX IX_fpc_chave_cte ON faturas_por_cliente(chave_cte);
```

#### 3. `coletas`

**Chave Primária**: `id` (VARCHAR)

**Campos Principais**: 22 campos + 1 metadata

**Estrutura:**
```sql
CREATE TABLE coletas (
    id VARCHAR(50) PRIMARY KEY,
    sequence_code BIGINT,
    request_date DATE,
    service_date DATE,
    status NVARCHAR(50),
    total_value DECIMAL(18,2),
    total_weight DECIMAL(18,2),
    total_volumes INT,
    cliente_id BIGINT,
    cliente_nome NVARCHAR(200),
    local_coleta NVARCHAR(500),
    cidade_coleta NVARCHAR(100),
    uf_coleta VARCHAR(2),
    usuario_id BIGINT,
    usuario_nome NVARCHAR(200),
    request_hour VARCHAR(10),
    service_start_hour VARCHAR(10),
    finish_date DATE,
    service_end_hour VARCHAR(10),
    requester NVARCHAR(200),
    taxed_weight DECIMAL(18,2),
    comments NVARCHAR(MAX),
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE()
);
```

#### 4. `fretes`

**Chave Primária**: `id` (BIGINT)

**Campos Principais**: 22 campos + 1 metadata

**Estrutura:**
```sql
CREATE TABLE fretes (
    id BIGINT PRIMARY KEY,
    servico_em DATETIME2,
    criado_em DATETIME2,
    status NVARCHAR(50),
    modal NVARCHAR(50),
    tipo_frete NVARCHAR(50),
    valor_total DECIMAL(18,2),
    valor_notas DECIMAL(18,2),
    peso_notas DECIMAL(18,2),
    id_corporacao BIGINT,
    id_cidade_destino BIGINT,
    data_previsao_entrega DATE,
    pagador_id BIGINT,
    pagador_nome NVARCHAR(200),
    remetente_id BIGINT,
    remetente_nome NVARCHAR(200),
    origem_cidade NVARCHAR(100),
    origem_uf VARCHAR(2),
    destinatario_id BIGINT,
    destinatario_nome NVARCHAR(200),
    destino_cidade NVARCHAR(100),
    destino_uf VARCHAR(2),
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE()
);
```

#### 5. `manifestos`

**Chave Primária**: `id` (BIGINT, auto-incrementado)

**Chave de Negócio**: `(sequence_code, pick_sequence_code, mdfe_number)`

**Constraint UNIQUE**: `(sequence_code, identificador_unico)`

**Campos Principais**: 40 campos + 1 metadata

**Estrutura:**
```sql
CREATE TABLE manifestos (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    sequence_code BIGINT NOT NULL,
    identificador_unico NVARCHAR(100) NOT NULL,
    status NVARCHAR(50),
    created_at DATETIMEOFFSET,
    departured_at DATETIMEOFFSET,
    closed_at DATETIMEOFFSET,
    finished_at DATETIMEOFFSET,
    mdfe_number INT,
    mdfe_key NVARCHAR(100),
    mdfe_status NVARCHAR(50),
    distribution_pole NVARCHAR(255),
    classification NVARCHAR(255),
    vehicle_plate NVARCHAR(10),
    vehicle_type NVARCHAR(255),
    vehicle_owner NVARCHAR(255),
    driver_name NVARCHAR(255),
    branch_nickname NVARCHAR(255),
    vehicle_departure_km INT,
    closing_km INT,
    traveled_km INT,
    invoices_count INT,
    invoices_volumes INT,
    invoices_weight DECIMAL(18, 3),              -- ✅ DECIMAL (antes: NVARCHAR)
    total_taxed_weight DECIMAL(18, 3),           -- ✅ DECIMAL (antes: NVARCHAR)
    total_cubic_volume DECIMAL(18, 6),           -- ✅ DECIMAL (antes: NVARCHAR)
    invoices_value DECIMAL(18, 2),               -- ✅ DECIMAL (antes: NVARCHAR)
    manifest_freights_total DECIMAL(18, 2),      -- ✅ DECIMAL (antes: NVARCHAR)
    pick_sequence_code BIGINT,
    contract_number NVARCHAR(50),
    daily_subtotal DECIMAL(18, 2),               -- ✅ DECIMAL (antes: NVARCHAR)
    total_cost DECIMAL(18, 2),
    operational_expenses_total DECIMAL(18, 2),   -- ✅ DECIMAL (antes: NVARCHAR)
    inss_value DECIMAL(18, 2),                   -- ✅ DECIMAL (antes: NVARCHAR)
    sest_senat_value DECIMAL(18, 2),             -- ✅ DECIMAL (antes: NVARCHAR)
    ir_value DECIMAL(18, 2),                     -- ✅ DECIMAL (antes: NVARCHAR)
    paying_total DECIMAL(18, 2),                 -- ✅ DECIMAL (antes: NVARCHAR)
    creation_user_name NVARCHAR(255),
    adjustment_user_name NVARCHAR(255),
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE(),
    CONSTRAINT UQ_manifestos_sequence_identificador UNIQUE (sequence_code, identificador_unico)
);

CREATE INDEX IX_manifestos_sequence_code ON manifestos(sequence_code);
```

**✅ Tipos Numéricos:** Todos os campos numéricos (valores monetários, pesos, volumes) usam `DECIMAL` para permitir análises numéricas no banco de dados.

#### 6. `cotacoes`

**Chave Primária**: `sequence_code` (BIGINT)

**Campos Principais**: 19 campos + 1 metadata

**Estrutura:**
```sql
CREATE TABLE cotacoes (
    sequence_code BIGINT PRIMARY KEY,
    requested_at DATETIME2,
    operation_type NVARCHAR(50),
    customer_doc VARCHAR(20),
    customer_name NVARCHAR(200),
    origin_city NVARCHAR(100),
    origin_state VARCHAR(2),
    destination_city NVARCHAR(100),
    destination_state VARCHAR(2),
    price_table NVARCHAR(100),
    volumes INT,
    taxed_weight DECIMAL(18,2),
    invoices_value DECIMAL(18,2),
    total_value DECIMAL(18,2),
    user_name NVARCHAR(200),
    branch_nickname NVARCHAR(200),
    company_name NVARCHAR(200),
    requester_name NVARCHAR(200),
    real_weight DECIMAL(18,2),
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE()
);
```

#### 7. `localizacao_cargas`

**Chave Primária**: `sequence_number` (BIGINT)

**Campos Principais**: 17 campos + 1 metadata

**Estrutura:**
```sql
CREATE TABLE localizacao_cargas (
    sequence_number BIGINT PRIMARY KEY,
    service_at DATETIME2,
    freight_id BIGINT,
    latitude DECIMAL(10,8),
    longitude DECIMAL(11,8),
    address NVARCHAR(500),
    city NVARCHAR(100),
    state VARCHAR(2),
    postal_code VARCHAR(10),
    country VARCHAR(50),
    accuracy DECIMAL(10,2),
    speed DECIMAL(10,2),
    heading DECIMAL(10,2),
    altitude DECIMAL(10,2),
    device_id VARCHAR(50),
    device_type NVARCHAR(50),
    metadata NVARCHAR(MAX),
    data_extracao DATETIME2 DEFAULT GETDATE()
);
```

#### 8. `log_extracoes`

**Chave Primária**: `id` (BIGINT, auto-incrementado)

**Estrutura:**
```sql
CREATE TABLE log_extracoes (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    entidade NVARCHAR(50) NOT NULL,
    timestamp_inicio DATETIME2 NOT NULL,
    timestamp_fim DATETIME2 NOT NULL,
    status NVARCHAR(20) NOT NULL, -- COMPLETO, INCOMPLETO, ERRO_API
    registros_extraidos INT NOT NULL,
    paginas_processadas INT,
    mensagem NVARCHAR(MAX)
);
```

---

## 🔧 Classes e Componentes

### Estrutura de Classes por Entidade

#### API REST

Descontinuada. O projeto foi migrado para GraphQL e Data Export.

#### API GraphQL

**Coletas:**
- `ColetaNodeDTO.java` - DTO da API GraphQL
- `ColetaMapper.java` - Mapper DTO → Entity
- `ColetaEntity.java` - Entity do banco
- `ColetaRepository.java` - Repository (persistência)
- `CityDTO.java` - DTO auxiliar (cidade)
- `CustomerDTO.java` - DTO auxiliar (cliente)
- `PickAddressDTO.java` - DTO auxiliar (endereço)
- `StateDTO.java` - DTO auxiliar (estado)
- `UserDTO.java` - DTO auxiliar (usuário)

**Fretes:**
- `FreteNodeDTO.java` - DTO da API GraphQL
- `FreteMapper.java` - Mapper DTO → Entity
- `FreteEntity.java` - Entity do banco
- `FreteRepository.java` - Repository (persistência)
- `CityDTO.java` - DTO auxiliar (cidade)
- `CorporationDTO.java` - DTO auxiliar (corporação)
- `CostCenterDTO.java` - DTO auxiliar (centro de custo)
- `CustomerPriceTableDTO.java` - DTO auxiliar (tabela de preços)
- `FreightClassificationDTO.java` - DTO auxiliar (classificação)
- `FreightInvoiceDTO.java` - DTO auxiliar (nota fiscal)
- `MainAddressDTO.java` - DTO auxiliar (endereço principal)
- `PayerDTO.java` - DTO auxiliar (pagador)
- `ReceiverDTO.java` - DTO auxiliar (destinatário)
- `SenderDTO.java` - DTO auxiliar (remetente)
- `StateDTO.java` - DTO auxiliar (estado)
- `UserDTO.java` - DTO auxiliar (usuário)

#### API Data Export

**Manifestos:**
- `ManifestoDTO.java` - DTO da API
- `ManifestoMapper.java` - Mapper DTO → Entity
- `ManifestoEntity.java` - Entity do banco
- `ManifestoRepository.java` - Repository (persistência)

**Cotações:**
- `CotacaoDTO.java` - DTO da API
- `CotacaoMapper.java` - Mapper DTO → Entity
- `CotacaoEntity.java` - Entity do banco
- `CotacaoRepository.java` - Repository (persistência)

**Localização de Carga:**
- `LocalizacaoCargaDTO.java` - DTO da API
- `LocalizacaoCargaMapper.java` - Mapper DTO → Entity
- `LocalizacaoCargaEntity.java` - Entity do banco
- `LocalizacaoCargaRepository.java` - Repository (persistência)

**Contas a Pagar (Data Export):**
- `ContasAPagarDTO.java` - DTO da API
- `ContasAPagarMapper.java` - Mapper DTO → Entity
- `ContasAPagarDataExportEntity.java` - Entity do banco
- `ContasAPagarRepository.java` - Repository (persistência)

**Faturas por Cliente (Data Export):**
- `FaturaPorClienteDTO.java` - DTO da API
- `FaturaPorClienteMapper.java` - Mapper DTO → Entity
- `FaturaPorClienteEntity.java` - Entity do banco
- `FaturaPorClienteRepository.java` - Repository (persistência)

**Tabelas Data Export (adicionais):**
- `contas_a_pagar` (Data Export)
- `faturas_por_cliente` (Data Export)

### Classes Comuns

**Cliente de APIs:**
- `ClienteApiGraphQL.java` - Cliente HTTP para API GraphQL
- `ClienteApiDataExport.java` - Cliente HTTP para API Data Export

**Runners:**
- `GraphQLRunner.java` - Runner para API GraphQL
- `DataExportRunner.java` - Runner para API Data Export

**Repositories Base:**
- `AbstractRepository.java` - Classe base abstrata para todos os repositories
- `LogExtracaoRepository.java` - Repository para logs de extração

**Entities Comuns:**
- `LogExtracaoEntity.java` - Entity para logs de extração

**Utilitários:**
- `ExportadorCSV.java` - Exporta dados para CSV
- `GerenciadorConexao.java` - Gerencia conexões com banco
- `CarregadorConfig.java` - Carrega configurações
- `LoggingService.java` - Sistema de logging estruturado
- `BannerUtil.java` - Utilitário para banners
- `DiagnosticoBanco.java` - Diagnóstico do banco
- `LimpadorTabelas.java` - Limpa tabelas
- `GerenciadorRequisicaoHttp.java` - Gerencia requisições HTTP

**Comandos:**
- `ExecutarFluxoCompletoComando.java` - Executa extração completa
- `ValidarManifestosComando.java` - Valida integridade de manifestos
- `ExecutarAuditoriaComando.java` - Executa auditoria de dados
- `TestarApiComando.java` - Testa conectividade com APIs
- `ValidarAcessoComando.java` - Valida acesso às APIs
- `ExibirAjudaComando.java` - Exibe ajuda
- `LimparTabelasComando.java` - Limpa tabelas
- `RealizarIntrospeccaoGraphQLComando.java` - Introspecção GraphQL
- `VerificarTimestampsComando.java` - Verifica timestamps
- `VerificarTimezoneComando.java` - Verifica timezone

**Serviços:**
- `ExtracaoServico.java` - Serviço de extração
- `LoggingService.java` - Serviço de logging

**Auditoria:**
- `AuditoriaService.java` - Serviço de auditoria
- `AuditoriaValidator.java` - Validador de auditoria
- `AuditoriaRelatorio.java` - Relatório de auditoria
- `CompletudeValidator.java` - Validador de completude
- `ResultadoAuditoria.java` - Resultado de auditoria
- `ResultadoValidacaoEntidade.java` - Resultado de validação
- `StatusAuditoria.java` - Status de auditoria
- `StatusValidacao.java` - Status de validação

**API:**
- `PaginatedGraphQLResponse.java` - Resposta paginada GraphQL
- `ResultadoExtracao.java` - Resultado de extração

---

## 🚀 Como Usar

### 1. Compilar o Projeto

```bash
mvn clean package
```

**Resultado:** JAR gerado em `target/extrator.jar`

### 2. Configurar Variáveis de Ambiente

```bash
# PowerShell
$env:API_BASEURL="https://sua-empresa.eslcloud.com.br"
$env:API_GRAPHQL_TOKEN="seu_token_graphql"
$env:API_DATAEXPORT_TOKEN="seu_token_dataexport"
$env:DB_URL="jdbc:sqlserver://localhost:1433;databaseName=esl_cloud"
$env:DB_USER="sa"
$env:DB_PASSWORD="sua_senha"
```

**OU** editar `src/main/resources/config.properties`

### 3. Executar Extração

#### Opção 1: Script Batch (Recomendado)

```bash
# Extração completa (todas as APIs)
01-executar_extracao_completa.bat

# Testar API específica
02-testar_api_especifica.bat

# Validar manifestos
07-validar-manifestos.bat

# Exportar CSV
06-exportar_csv.bat
```

#### Opção 2: Linha de Comando

```bash
# Extração completa
java -jar target/extrator.jar --fluxo-completo

# Validar acesso
java -jar target/extrator.jar --validar

# Validar manifestos
java -jar target/extrator.jar --validar-manifestos

# Executar auditoria
java -jar target/extrator.jar --auditoria

# Ver ajuda
java -jar target/extrator.jar --ajuda
```

### 4. Validar Dados

```sql
-- Verificar coletas
SELECT TOP 10 
    id, 
    sequence_code, 
    status, 
    total_value
FROM coletas
ORDER BY data_extracao DESC;

-- Verificar fretes
SELECT TOP 10 
    id, 
    servico_em, 
    status, 
    valor_total
FROM fretes
ORDER BY data_extracao DESC;

-- Verificar manifestos
SELECT TOP 10 
    sequence_code, 
    status, 
    created_at, 
    mdfe_number,
    invoices_value,        -- ✅ Agora é DECIMAL (permite SUM, AVG)
    total_cost,            -- ✅ Agora é DECIMAL (permite SUM, AVG)
    paying_total,          -- ✅ Agora é DECIMAL (permite SUM, AVG)
    data_extracao
FROM manifestos
ORDER BY data_extracao DESC;

-- Verificar contas a pagar (Data Export)
SELECT TOP 10 
    sequence_code,
    document_number,
    valor_a_pagar,
    status_pagamento,
    nome_fornecedor,
    nome_filial,
    data_extracao
FROM contas_a_pagar
ORDER BY data_extracao DESC;

-- Verificar faturas por cliente (Data Export)
SELECT TOP 10 
    unique_id,
    numero_fatura,
    valor_fatura,
    valor_frete,
    chave_cte,
    numero_nfse,
    pagador_nome,
    filial,
    data_vencimento_fatura,
    data_extracao
FROM faturas_por_cliente
ORDER BY data_extracao DESC;

-- Análises numéricas (agora possível com tipos DECIMAL)
SELECT 
    branch_nickname,
    COUNT(*) as total_manifestos,
    SUM(invoices_value) as soma_valor_notas,
    AVG(invoices_value) as media_valor_notas,
    SUM(total_cost) as soma_custo_total,
    SUM(paying_total) as soma_valor_pagar
FROM manifestos
WHERE invoices_value IS NOT NULL
GROUP BY branch_nickname
ORDER BY soma_valor_notas DESC;

-- Verificar cotações
SELECT TOP 10 
    sequence_code, 
    customer_name, 
    total_value
FROM cotacoes
ORDER BY data_extracao DESC;

-- Verificar localização de carga
SELECT TOP 10 
    sequence_number, 
    service_at, 
    latitude, 
    longitude
FROM localizacao_cargas
ORDER BY data_extracao DESC;

-- Verificar logs de extração
SELECT TOP 10 
    entidade,
    timestamp_inicio,
    timestamp_fim,
    status,
    registros_extraidos
FROM log_extracoes
ORDER BY timestamp_fim DESC;

-- Verificar duplicados naturais (manifestos com múltiplos MDF-es)
SELECT 
    sequence_code,
    COUNT(*) as total_mdfes,
    STRING_AGG(CAST(mdfe_number AS VARCHAR), ', ') as mdfe_numbers
FROM manifestos
WHERE mdfe_number IS NOT NULL
GROUP BY sequence_code
HAVING COUNT(*) > 1
ORDER BY total_mdfes DESC;
```

---

## 🛠️ Tecnologias Utilizadas

### Linguagem e Framework

- **Java 17** (LTS)
- **Maven 3.6+** (Gerenciamento de dependências)
- **Spring Boot 3.5.6** (Parent POM, mas sem dependências web)

### Bibliotecas Principais

- **Jackson** (`jackson-databind`, `jackson-datatype-jsr310`)
  - Serialização/deserialização JSON
  - Suporte a tipos de data/hora

- **SQL Server JDBC Driver** (`mssql-jdbc`)
  - Conexão com banco de dados
  - Execução de queries SQL

- **SLF4J + Logback** (`slf4j-api`, `logback-classic`)
  - Sistema de logging estruturado

- **Apache POI** (`poi`, `poi-ooxml`)
  - Processamento de arquivos Excel (para validação)

- **JUnit 5** (`junit-jupiter`)
  - Testes unitários

### Ferramentas de Build

- **Maven Assembly Plugin**: Gerar JAR com dependências
- **Maven Compiler Plugin**: Compilação Java 17

---

## 📁 Estrutura de Arquivos

```
script-automacao/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── br/com/extrator/
│   │   │       ├── Main.java                    # Orquestrador principal
│   │   │       ├── api/                         # Clientes de API
│   │   │       │   ├── ClienteApiGraphQL.java
│   │   │       │   ├── ClienteApiDataExport.java
│   │   │       │   ├── PaginatedGraphQLResponse.java
│   │   │       │   └── ResultadoExtracao.java
│   │   │       ├── runners/                     # Runners especializados
│   │   │       │   ├── GraphQLRunner.java
│   │   │       │   └── DataExportRunner.java
│   │   │       ├── modelo/                      # DTOs e Mappers
│   │   │       │   ├── graphql/
│   │   │       │   │   ├── coletas/
│   │   │       │   │   │   ├── ColetaNodeDTO.java
│   │   │       │   │   │   ├── ColetaMapper.java
│   │   │       │   │   │   ├── CityDTO.java
│   │   │       │   │   │   ├── CustomerDTO.java
│   │   │       │   │   │   ├── PickAddressDTO.java
│   │   │       │   │   │   ├── StateDTO.java
│   │   │       │   │   │   └── UserDTO.java
│   │   │       │   │   └── fretes/
│   │   │       │   │       ├── FreteNodeDTO.java
│   │   │       │   │       ├── FreteMapper.java
│   │   │       │   │       ├── CityDTO.java
│   │   │       │   │       ├── CorporationDTO.java
│   │   │       │   │       ├── CostCenterDTO.java
│   │   │       │   │       ├── CustomerPriceTableDTO.java
│   │   │       │   │       ├── FreightClassificationDTO.java
│   │   │       │   │       ├── FreightInvoiceDTO.java
│   │   │       │   │       ├── MainAddressDTO.java
│   │   │       │   │       ├── PayerDTO.java
│   │   │       │   │       ├── ReceiverDTO.java
│   │   │       │   │       ├── SenderDTO.java
│   │   │       │   │       ├── StateDTO.java
│   │   │       │   │       └── UserDTO.java
│   │   │       │   └── dataexport/
│   │   │       │       ├── manifestos/
│   │   │       │       │   ├── ManifestoDTO.java
│   │   │       │       │   └── ManifestoMapper.java
│   │   │       │       ├── cotacao/
│   │   │       │       │   ├── CotacaoDTO.java
│   │   │       │       │   └── CotacaoMapper.java
│   │   │       │       ├── localizacaocarga/
│   │   │       │       │   ├── LocalizacaoCargaDTO.java
│   │   │       │       │   └── LocalizacaoCargaMapper.java
│   │   │       │       ├── ocorrencias/
│   │   │       │       │   ├── OcorrenciaDTO.java
│   │   │       │       │   └── OcorrenciaMapper.java
│   │   │       │       ├── contasapagar/
│   │   │       │       │   ├── ContasAPagarDTO.java
│   │   │       │       │   └── ContasAPagarMapper.java
│   │   │       │       └── faturaporcliente/
│   │   │       │           ├── FaturaPorClienteDTO.java
│   │   │       │           └── FaturaPorClienteMapper.java
│   │   │       ├── db/
│   │   │       │   ├── entity/                  # Entities (tabelas)
│   │   │       │   │   ├── ColetaEntity.java
│   │   │       │   │   ├── ContasAPagarDataExportEntity.java
│   │   │       │   │   ├── CotacaoEntity.java
│   │   │       │   │   ├── FaturaPorClienteEntity.java
│   │   │       │   │   ├── FreteEntity.java
│   │   │       │   │   ├── LocalizacaoCargaEntity.java
│   │   │       │   │   ├── LogExtracaoEntity.java
│   │   │       │   │   └── ManifestoEntity.java
│   │   │       │   └── repository/              # Repositories (persistência)
│   │   │       │       ├── AbstractRepository.java
│   │   │       │       ├── ColetaRepository.java
│   │   │       │       ├── ContasAPagarRepository.java
│   │   │       │       ├── CotacaoRepository.java
│   │   │       │       ├── FaturaPorClienteRepository.java
│   │   │       │       ├── FreteRepository.java
│   │   │       │       ├── LocalizacaoCargaRepository.java
│   │   │       │       ├── LogExtracaoRepository.java
│   │   │       │       └── ManifestoRepository.java
│   │   │       ├── comandos/                    # Comandos CLI
│   │   │       │   ├── ExecutarFluxoCompletoComando.java
│   │   │       │   ├── ValidarManifestosComando.java
│   │   │       │   ├── ExecutarAuditoriaComando.java
│   │   │       │   ├── TestarApiComando.java
│   │   │       │   ├── ValidarAcessoComando.java
│   │   │       │   ├── ExibirAjudaComando.java
│   │   │       │   ├── LimparTabelasComando.java
│   │   │       │   ├── RealizarIntrospeccaoGraphQLComando.java
│   │   │       │   ├── VerificarTimestampsComando.java
│   │   │       │   └── VerificarTimezoneComando.java
│   │   │       ├── servicos/                    # Serviços auxiliares
│   │   │       │   ├── ExtracaoServico.java
│   │   │       │   └── LoggingService.java
│   │   │       ├── auditoria/                   # Auditoria
│   │   │       │   ├── AuditoriaService.java
│   │   │       │   ├── AuditoriaValidator.java
│   │   │       │   ├── AuditoriaRelatorio.java
│   │   │       │   ├── CompletudeValidator.java
│   │   │       │   ├── ResultadoAuditoria.java
│   │   │       │   ├── ResultadoValidacaoEntidade.java
│   │   │       │   ├── StatusAuditoria.java
│   │   │       │   └── StatusValidacao.java
│   │   │       └── util/                        # Utilitários
│   │   │           ├── ExportadorCSV.java
│   │   │           ├── GerenciadorConexao.java
│   │   │           ├── CarregadorConfig.java
│   │   │           ├── LoggingService.java
│   │   │           ├── BannerUtil.java
│   │   │           ├── DiagnosticoBanco.java
│   │   │           ├── LimpadorTabelas.java
│   │   │           └── GerenciadorRequisicaoHttp.java
│   │   └── resources/
│   │       ├── config.properties                # Configurações
│   │       ├── logback.xml                      # Configuração de logging
│   │       └── sql/                             # Scripts SQL
│   └── test/
│       └── java/                                # Testes unitários
├── docs/                                        # Documentação completa
├── logs/                                        # Logs de execução
├── exports/                                     # CSVs exportados
├── metricas/                                    # Métricas JSON
├── scripts/                                      # Scripts auxiliares
├── pom.xml                                      # Configuração Maven
└── README.md                                    # Este arquivo
```

---

## 🔧 Problemas Resolvidos

### 1. Duplicados Falsos em Manifestos

**Problema:**
- Mesmo manifesto sendo salvo múltiplas vezes
- Causa: Campos voláteis mudando durante extração
- Impacto: Dados duplicados no banco

**Solução:**
- Exclusão de campos voláteis do hash do `identificador_unico`
- MERGE usando chave de negócio `(sequence_code, pick_sequence_code, mdfe_number)`
- Deduplicação antes de salvar

**Status:** ✅ Resolvido

### 2. Perda de Múltiplos MDF-es

**Problema:**
- Manifestos com múltiplos MDF-es perdendo registros
- Causa: MERGE usando apenas `sequence_code`
- Impacto: Dados incompletos

**Solução:**
- MERGE usando `(sequence_code, pick_sequence_code, mdfe_number)`
- Preservação de duplicados naturais (múltiplos MDF-es)

**Status:** ✅ Resolvido

### 3. Discrepância de Contagem (INSERT vs UPDATE)

**Problema:**
- Log mostra "processados: 277" mas banco tem 276 registros
- Causa: UPDATEs não adicionam novas linhas
- Impacto: Confusão nos logs

**Solução:**
- Logs melhorados explicando que "processados" = INSERTs + UPDATEs
- Notas explicativas sobre comportamento esperado

**Status:** ✅ Resolvido

### 4. Campos Voláteis no Hash

**Problema:**
- Campos como `mobile_read_at`, `mft_mfs_number` mudando durante extração
- Causa: Hash diferente para mesmo manifesto
- Impacto: Duplicados falsos

**Solução:**
- Lista completa de 13 campos voláteis excluídos do hash
- Hash baseado apenas em campos estáveis

**Status:** ✅ Resolvido

### 5. Tipos de Dados Numéricos (String → BigDecimal)

**Problema:**
- Campos numéricos (`invoices_value`, `total_cost`, `paying_total`, etc.) salvos como `NVARCHAR(50)`
- Causa: Impossibilidade de realizar análises numéricas (SUM, AVG, comparações)
- Impacto: Dados não podem ser usados para relatórios e análises financeiras

**Solução:**
- Alteração de 11 campos numéricos de `String` para `BigDecimal` em `ManifestoEntity.java`
- Conversão automática de strings para `BigDecimal` em `ManifestoMapper.java`
- Criação de tabela com tipos `DECIMAL(18,2)` ou `DECIMAL(18,3)` em `ManifestoRepository.java`
- Uso de `setBigDecimalParameter()` no MERGE para garantir tipos corretos

**Campos Corrigidos:**
- `invoices_weight`: DECIMAL(18, 3)
- `total_taxed_weight`: DECIMAL(18, 3)
- `total_cubic_volume`: DECIMAL(18, 6)
- `invoices_value`: DECIMAL(18, 2)
- `manifest_freights_total`: DECIMAL(18, 2)
- `daily_subtotal`: DECIMAL(18, 2)
- `operational_expenses_total`: DECIMAL(18, 2)
- `inss_value`: DECIMAL(18, 2)
- `sest_senat_value`: DECIMAL(18, 2)
- `ir_value`: DECIMAL(18, 2)
- `paying_total`: DECIMAL(18, 2)

**Status:** ✅ Resolvido

### 6. Expansão de Campos REST

**Problema:**
- Faturas a Pagar com apenas 11 campos mapeados
- Causa: Campos adicionais não estavam sendo capturados
- Impacto: Dados incompletos

**Solução:**
- Expansão para 14 campos mapeados
- Adição de 10 campos futuros (placeholders)
- Captura de dados contábeis e de filial

**Status:** ✅ Resolvido

---

## 📚 Documentação Adicional

A documentação completa está em `docs/`:

- **[📚 Documentação Completa](docs/README.md)** - Índice principal
- **[🚀 Início Rápido](docs/01-inicio-rapido/)** - Guias de início rápido
- **[🔌 APIs](docs/02-apis/)** - Documentação completa das APIs
- **[⚙️ Configuração](docs/03-configuracao/)** - Guias de configuração
- **[📋 Especificações Técnicas](docs/04-especificacoes-tecnicas/)** - Detalhes técnicos

---

## 🔭 Roadmap

- Data Export: suportar `order_by` nas requisições GET
- Data Export: tornar `per` configurável por template via `config.properties`
- Observabilidade: incluir métricas e dashboards para novas tabelas de Data Export
- Exportação: habilitar CSV para `contas_a_pagar` e `faturas_por_cliente`
- CLI: adicionar flags dedicadas para executar apenas relatórios específicos do Data Export
- Data Export: implementar Entity e Repository para Ocorrências

---

## 🔒 Segurança

**⚠️ IMPORTANTE:**

- Tokens e credenciais **NUNCA** devem ser commitados no Git
- Use variáveis de ambiente ou arquivos `.env` (adicionados ao `.gitignore`)
- Arquivos sensíveis estão em `docs/08-arquivos-secretos/` (não versionados)

---

## 📊 Métricas e Monitoramento

O sistema gera métricas automáticas em `metricas/metricas-YYYY-MM-DD.json`:

- Tempos de execução por API
- Quantidade de registros processados
- Taxa de sucesso/falha
- Performance (registros/segundo)
- Histórico de execuções

---

## 🆘 Suporte e Troubleshooting

Para problemas ou dúvidas:

1. **Verifique os logs** em `logs/`
2. **Consulte a documentação** em `docs/`
3. **Execute validação**: `07-validar-manifestos.bat`
4. **Use os scripts .bat** para operações padronizadas
5. **Monitore as métricas** para identificar problemas

---

## 📝 Changelog

### v2.2.0 (22/11/2025)
- ✅ Remoção total da API REST do projeto
- ✅ Consolidação dos fluxos em GraphQL e Data Export
- ✅ Inclusão dos relatórios de `contas_a_pagar` (8636) e `faturas_por_cliente` (4924)
- ✅ Atualização de variáveis de ambiente (remoção de `API_REST_TOKEN`)
- ✅ Documentação revisada: arquitetura, ETL, paginação, classes e estrutura de arquivos

### v2.1.0 (11/11/2025)
- ✅ Sistema de deduplicação robusto para manifestos, cotações e localização de carga
- ✅ Correção de duplicados falsos (campos voláteis)
- ✅ Preservação de múltiplos MDF-es
- ✅ MERGE usando chave de negócio
- ✅ Logs melhorados explicando INSERTs vs UPDATEs
- ✅ Validação automática de manifestos
- ✅ Correção de tipos de dados numéricos (String → BigDecimal/DECIMAL)
  - 11 campos numéricos agora usam DECIMAL para permitir análises (SUM, AVG, comparações)
  - Conversão automática de strings para BigDecimal no Mapper
  - Tabela criada com tipos DECIMAL corretos

### v2.0.0 (04/11/2025)
- ✅ Expansão de campos REST (+27% mais dados)
- ✅ Refatoração para script de linha de comando
- ✅ Remoção de dependências web
- ✅ Métricas aprimoradas com salvamento automático
- ✅ Scripts de automação (.bat)

---

## 📄 Licença

Este projeto é interno e proprietário. Todos os direitos reservados.

---

**Última Atualização:** 22/11/2025  
**Versão:** 2.2.0  
**Status:** ✅ Estável
