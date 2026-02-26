# 📚 Documentação - ESL Cloud Extrator

## 🎯 Navegação Rápida

### 📘 Início Rápido
- [**Leia-me Primeiro**](documentacao-tecnica/inicio-rapido/leia-me-primeiro.md) - Comece aqui
- [**Guia Rápido**](documentacao-tecnica/inicio-rapido/guia-rapido.md) - 5 minutos para começar
- [**Scripts**](documentacao-tecnica/inicio-rapido/scripts.md) - Todos os scripts disponíveis
- [**Início Rápido**](documentacao-tecnica/inicio-rapido/inicio-rapido.md) - 3 passos para começar

### 🔌 Documentação de APIs
- [**API REST**](02-apis/rest/) - Faturas a Pagar, Faturas a Receber, Ocorrências
- [**API GraphQL**](02-apis/graphql/) - Coletas, Fretes
- [**API DataExport**](02-apis/dataexport/) - Manifestos, Cotações, Localização de Carga
- [**Análise Crítica**](02-apis/analise-critica.md) - Análise completa dos endpoints

### 📊 Diagramas e Arquitetura
- [**DER Classes Java**](DER-CLASSES-JAVA-COMPLETO.md) - Diagrama de classes completo
- [**DER Banco de Dados**](DER-COMPLETO-BANCO-DADOS.md) - Diagrama entidade-relacionamento
- [**Fluxograma do Sistema**](FLUXOGRAMA-COMPLETO-SISTEMA.md) - Fluxo completo do sistema

### 📈 Análises e Validações
- [**Análises**](analises/) - Análises de deduplicação, erros, refinamentos e validações

### ⚙️ Configuração e Troubleshooting
- [**Configuração**](configuracao/) - Configuração do sistema e máquina
- [**Insomnia**](configuracao/insomnia/) - Guias de instalação e uso do Insomnia
- [**Troubleshooting**](configuracao/troubleshooting/) - Solução de problemas comuns

### 📋 Documentação Técnica
- [**Documentos Gerais**](documentacao-tecnica/documentos-gerais/) - Documentos gerais do projeto
- [**Especificações Técnicas**](documentacao-tecnica/especificacoes/) - Especificações técnicas completas
- [**Versões**](documentacao-tecnica/versoes/) - Documentação de versões
- [**Referências**](documentacao-tecnica/referencias/) - Referências e templates
- [**Ideias Futuras**](documentacao-tecnica/ideias-futuras/) - Ideias e melhorias futuras

### 📊 Dashboards e Relatórios
- [**Dashboards**](dashboards/) - Documentação de dashboards (Power BI)
- [**Relatórios Diários**](relatorios-diarios/) - Relatórios diários de execução

### 🔍 Descobertas
- [**Descobertas**](descobertas/) - Problemas e descobertas do sistema

### 🧭 Direcionamento
- [**Direcionamento**](direcionamento/) - Verdades comprovadas, plano de ação e to-do operacional

---

## 📁 Estrutura da Documentação

```
docs/
├── 📄 README.md                          # Este arquivo
│
├── 📊 Diagramas (Raiz)
│   ├── DER-CLASSES-JAVA-COMPLETO.md     # Diagrama de classes Java
│   ├── DER-COMPLETO-BANCO-DADOS.md      # Diagrama entidade-relacionamento
│   └── FLUXOGRAMA-COMPLETO-SISTEMA.md   # Fluxograma do sistema
│
├── 🔌 02-apis/                          # Documentação de APIs (não mexer)
│   ├── rest/                            # API REST
│   ├── graphql/                         # API GraphQL
│   ├── dataexport/                      # API DataExport
│   └── analise-critica.md
│
├── 📈 analises/                         # Análises e validações
│   ├── ANALISE-DEDUPLICACAO-CRITICA.md
│   ├── ANALISE-ERROS-LOG-2026-01-14.md
│   ├── ANALISE-REFINAMENTOS-PROJETO.md
│   ├── RESUMO-VALIDACAO-COMPLETA.md
│   ├── REVISAO-OTIMIZACAO.md
│   ├── VALIDACAO-EXTRACAO-2026-01-14.md
│   └── VERIFICACAO_TIPO_DESTROY_USER_ID.md
│
├── ⚙️ configuracao/                      # Configuração e troubleshooting
│   ├── configuracao-maquina-windows.md
│   ├── insomnia/                        # Documentação do Insomnia
│   └── troubleshooting/                # Solução de problemas
│
├── 📋 documentacao-tecnica/             # Documentação técnica consolidada
│   ├── documentos-gerais/              # Documentos gerais do projeto
│   ├── inicio-rapido/                   # Guias de início rápido
│   ├── especificacoes/                  # Especificações técnicas
│   ├── versoes/                         # Documentação de versões
│   ├── referencias/                     # Referências e templates
│   └── ideias-futuras/                  # Ideias e recomendações
│
├── 📊 dashboards/                        # Dashboards e visualizações
│   ├── powerbi.md                       # Documentação Power BI
│   └── 1.md
│
├── 🔍 descobertas/                      # Problemas e descobertas
│   ├── problema-esl.md
│   ├── query.md
│   └── README.md
│
├── 🧭 direcionamento/                   # Direção técnica viva
│   ├── README.md
│   ├── TODO-EXECUCAO-DAEMON.md
│   ├── planos/
│   └── verdades/
│
├── 📊 relatorios-diarios/               # Relatórios diários (não mexer)
│   └── [26 arquivos de relatórios]
│
├── 📁 endpoints/                        # Endpoints
│   └── README.md
│
└── 🔒 08-arquivos-secretos/             # Arquivos sensíveis (não versionados)
    ├── armazenamento.md
    ├── dataexport-guia.md
    └── Rodogarcia.postman_collection.md
```

---

## 🚀 Início Rápido

### 1. Primeira Vez?
Leia: [**Leia-me Primeiro**](documentacao-tecnica/inicio-rapido/leia-me-primeiro.md)

### 2. Quer Começar Rápido?
Leia: [**Guia Rápido**](documentacao-tecnica/inicio-rapido/guia-rapido.md)

### 3. Problemas?
Veja: [**Troubleshooting**](configuracao/troubleshooting/)

### 4. Quer Entender as APIs?
Veja: [**Documentação de APIs**](02-apis/)

### 5. Quer Entender a Arquitetura?
Veja: [**DER Classes Java**](DER-CLASSES-JAVA-COMPLETO.md) | [**DER Banco de Dados**](DER-COMPLETO-BANCO-DADOS.md) | [**Fluxograma**](FLUXOGRAMA-COMPLETO-SISTEMA.md)

---

## 📊 Índice Completo da Documentação

### 📘 Início Rápido
- [**leia-me-primeiro.md**](documentacao-tecnica/inicio-rapido/leia-me-primeiro.md) - Primeiros passos
- [**guia-rapido.md**](documentacao-tecnica/inicio-rapido/guia-rapido.md) - Guia rápido de 5 minutos
- [**scripts.md**](documentacao-tecnica/inicio-rapido/scripts.md) - Todos os scripts disponíveis
- [**inicio-rapido.md**](documentacao-tecnica/inicio-rapido/inicio-rapido.md) - 3 passos para começar
- [**banners-estilizados.md**](documentacao-tecnica/inicio-rapido/banners-estilizados.md) - Banners ASCII art

### 🔌 APIs
Documentação completa de todas as APIs disponíveis.

#### REST
- [**faturas-a-pagar.md**](02-apis/rest/faturas-a-pagar.md) - API de Faturas a Pagar
- [**faturas-a-receber.md**](02-apis/rest/faturas-a-receber.md) - API de Faturas a Receber
- [**ocorrencias.md**](02-apis/rest/ocorrencias.md) - API de Ocorrências

#### GraphQL
- [**coletas.md**](02-apis/graphql/coletas.md) - API GraphQL de Coletas
- [**fretes.md**](02-apis/graphql/fretes.md) - API GraphQL de Fretes

#### DataExport
- [**manifestos.md**](02-apis/dataexport/manifestos.md) - API DataExport de Manifestos
- [**cotacoes.md**](02-apis/dataexport/cotacoes.md) - API DataExport de Cotações
- [**localizacao-carga.md**](02-apis/dataexport/localizacao-carga.md) - API DataExport de Localização de Carga
- [**contasapagar.md**](02-apis/dataexport/contasapagar.md) - API DataExport de Contas a Pagar
- [**faturaporcliente.md**](02-apis/dataexport/faturaporcliente.md) - API DataExport de Faturas por Cliente

#### Análise
- [**analise-critica.md**](02-apis/analise-critica.md) - Análise crítica dos endpoints

### 📊 Diagramas e Arquitetura
- [**DER-CLASSES-JAVA-COMPLETO.md**](DER-CLASSES-JAVA-COMPLETO.md) - Diagrama de classes Java completo
- [**DER-COMPLETO-BANCO-DADOS.md**](DER-COMPLETO-BANCO-DADOS.md) - Diagrama entidade-relacionamento do banco
- [**FLUXOGRAMA-COMPLETO-SISTEMA.md**](FLUXOGRAMA-COMPLETO-SISTEMA.md) - Fluxograma completo do sistema

### 📈 Análises e Validações
- [**ANALISE-DEDUPLICACAO-CRITICA.md**](analises/ANALISE-DEDUPLICACAO-CRITICA.md) - Análise crítica de deduplicação
- [**ANALISE-ERROS-LOG-2026-01-14.md**](analises/ANALISE-ERROS-LOG-2026-01-14.md) - Análise de erros em logs
- [**ANALISE-REFINAMENTOS-PROJETO.md**](analises/ANALISE-REFINAMENTOS-PROJETO.md) - Análise de refinamentos
- [**RESUMO-VALIDACAO-COMPLETA.md**](analises/RESUMO-VALIDACAO-COMPLETA.md) - Resumo de validação completa
- [**REVISAO-OTIMIZACAO.md**](analises/REVISAO-OTIMIZACAO.md) - Revisão e otimização
- [**VALIDACAO-EXTRACAO-2026-01-14.md**](analises/VALIDACAO-EXTRACAO-2026-01-14.md) - Validação de extração
- [**VERIFICACAO_TIPO_DESTROY_USER_ID.md**](analises/VERIFICACAO_TIPO_DESTROY_USER_ID.md) - Verificação de tipo

### ⚙️ Configuração
Configuração do sistema e solução de problemas.

#### Configuração Geral
- [**configuracao-maquina-windows.md**](configuracao/configuracao-maquina-windows.md) - Configuração da máquina Windows

#### Insomnia
- [**instalacao.md**](configuracao/insomnia/instalacao.md) - Instalação do Insomnia
- [**requisicoes-rest.md**](configuracao/insomnia/requisicoes-rest.md) - Requisições API REST
- [**requisicoes-graphql.md**](configuracao/insomnia/requisicoes-graphql.md) - Requisições API GraphQL
- [**requisicoes-dataexport.md**](configuracao/insomnia/requisicoes-dataexport.md) - Requisições API DataExport
- [**obter-tokens.md**](configuracao/insomnia/obter-tokens.md) - Como obter tokens
- [**guia-rapido.md**](configuracao/insomnia/guia-rapido.md) - Guia rápido de testes
- [**analise-resposta-manifestos.md**](configuracao/insomnia/analise-resposta-manifestos.md) - Análise de resposta de manifestos

#### Troubleshooting
- [**compilacao.md**](configuracao/troubleshooting/compilacao.md) - Guia de compilação
- [**maven.md**](configuracao/troubleshooting/maven.md) - Solução para Maven
- [**java-home.md**](configuracao/troubleshooting/java-home.md) - Configurar JAVA_HOME
- [**jar-em-uso.md**](configuracao/troubleshooting/jar-em-uso.md) - Resolver JAR em uso

### 📋 Documentação Técnica

#### Documentos Gerais
- [**resumo-executivo.md**](documentacao-tecnica/documentos-gerais/resumo-executivo.md) - Resumo executivo do projeto
- [**entrega-completa.md**](documentacao-tecnica/documentos-gerais/entrega-completa.md) - Documentação de entrega completa
- [**solicitacao-suporte-esl-autenticacao-api.md**](documentacao-tecnica/documentos-gerais/solicitacao-suporte-esl-autenticacao-api.md) - Solicitação de suporte ESL

#### Especificações Técnicas
- [**design.md**](documentacao-tecnica/especificacoes/implementacao-apis/design.md) - Design do sistema
- [**requirements.md**](documentacao-tecnica/especificacoes/implementacao-apis/requirements.md) - Requisitos
- [**technical-specification.md**](documentacao-tecnica/especificacoes/implementacao-apis/technical-specification.md) - Especificação técnica completa
- [**resumo-tecnico-graphql-dataexport.md**](documentacao-tecnica/especificacoes/implementacao-apis/resumo-tecnico-graphql-dataexport.md) - Resumo técnico GraphQL e DataExport

#### Versões
- [**release-notes.md**](documentacao-tecnica/versoes/v2.0/release-notes.md) - Release notes
- [**exemplos-uso.md**](documentacao-tecnica/versoes/v2.0/exemplos-uso.md) - Exemplos de uso
- [**checklist-validacao.md**](documentacao-tecnica/versoes/v2.0/checklist-validacao.md) - Checklist de validação
- [**diagrama-estrutura.md**](documentacao-tecnica/versoes/v2.0/diagrama-estrutura.md) - Diagrama de estrutura
- [**sumario-executivo.md**](documentacao-tecnica/versoes/v2.0/sumario-executivo.md) - Sumário executivo

#### Referências
- [**como-converter-xlsx.md**](documentacao-tecnica/referencias/csvs/como-converter-xlsx.md) - Como converter XLSX para CSV
- [**evidencias-para-buscar.md**](documentacao-tecnica/referencias/csvs/evidencias-para-buscar.md) - Evidências para buscar
- [**template-mapeamento.md**](documentacao-tecnica/referencias/mapeamento/template-mapeamento.md) - Template de mapeamento
- Arquivos CSV e XLSX de referência

#### Ideias Futuras
- [**recomendacoes-melhorias.md**](documentacao-tecnica/ideias-futuras/recomendacoes-melhorias.md) - Recomendações de melhorias

### 📊 Dashboards
- [**powerbi.md**](dashboards/powerbi.md) - Documentação completa do Power BI

### 🔍 Descobertas
- [**problema-esl.md**](descobertas/problema-esl.md) - Problemas encontrados no ESL
- [**query.md**](descobertas/query.md) - Queries úteis

### 🧭 Direcionamento
- [**README.md**](direcionamento/README.md) - Regras de manutenção do direcionamento
- [**PLANO-ACAO-DAEMON-RECONCILIACAO-2026-02.md**](direcionamento/planos/PLANO-ACAO-DAEMON-RECONCILIACAO-2026-02.md) - Plano priorizado P0/P1/P2
- [**TODO-EXECUCAO-DAEMON.md**](direcionamento/TODO-EXECUCAO-DAEMON.md) - Checklist de execução com testes pesados
- [**VERDADES-OPERACIONAIS.md**](direcionamento/verdades/VERDADES-OPERACIONAIS.md) - Registro vivo de fatos comprovados

### 📊 Relatórios Diários
📊 **Relatórios diários de execução (mantido como está - não mexer)**

- Relatórios diários de execução do sistema
- Documentação de classes de APIs
- Dúvidas e pedidos de endpoints

---

## 🚀 Fluxo de Leitura Recomendado

### Para Começar
1. [README.md](README.md) - Visão geral da documentação
2. [documentacao-tecnica/inicio-rapido/leia-me-primeiro.md](documentacao-tecnica/inicio-rapido/leia-me-primeiro.md) - Primeiros passos
3. [documentacao-tecnica/inicio-rapido/scripts.md](documentacao-tecnica/inicio-rapido/scripts.md) - Conhecer os scripts

### Para Desenvolver
1. [02-apis/](02-apis/) - Entender as APIs disponíveis
2. [DER-CLASSES-JAVA-COMPLETO.md](DER-CLASSES-JAVA-COMPLETO.md) - Arquitetura de classes
3. [DER-COMPLETO-BANCO-DADOS.md](DER-COMPLETO-BANCO-DADOS.md) - Estrutura do banco
4. [FLUXOGRAMA-COMPLETO-SISTEMA.md](FLUXOGRAMA-COMPLETO-SISTEMA.md) - Fluxo do sistema
5. [documentacao-tecnica/especificacoes/implementacao-apis/design.md](documentacao-tecnica/especificacoes/implementacao-apis/design.md) - Design detalhado

### Para Configurar
1. [configuracao/insomnia/instalacao.md](configuracao/insomnia/instalacao.md) - Instalar Insomnia
2. [configuracao/insomnia/obter-tokens.md](configuracao/insomnia/obter-tokens.md) - Obter tokens
3. [configuracao/insomnia/requisicoes-rest.md](configuracao/insomnia/requisicoes-rest.md) - Testar APIs

### Para Resolver Problemas
1. [configuracao/troubleshooting/compilacao.md](configuracao/troubleshooting/compilacao.md) - Compilação
2. [configuracao/troubleshooting/maven.md](configuracao/troubleshooting/maven.md) - Problemas com Maven
3. [configuracao/troubleshooting/java-home.md](configuracao/troubleshooting/java-home.md) - JAVA_HOME

### Para Analisar
1. [analises/](analises/) - Análises e validações do sistema

### Para Apresentar
1. [documentacao-tecnica/documentos-gerais/resumo-executivo.md](documentacao-tecnica/documentos-gerais/resumo-executivo.md) - Resumo executivo
2. [documentacao-tecnica/versoes/v2.0/sumario-executivo.md](documentacao-tecnica/versoes/v2.0/sumario-executivo.md) - Sumário executivo
3. [documentacao-tecnica/versoes/v2.0/release-notes.md](documentacao-tecnica/versoes/v2.0/release-notes.md) - Release notes

---

## 📊 Documentação por Tipo

### 🔌 APIs
- **REST**: Faturas a Pagar, Faturas a Receber, Ocorrências
- **GraphQL**: Coletas, Fretes
- **DataExport**: Manifestos, Cotações, Localização de Carga, Contas a Pagar, Faturas por Cliente

### 📊 Diagramas
- **DER Classes Java**: Diagrama completo de classes do sistema
- **DER Banco de Dados**: Diagrama entidade-relacionamento
- **Fluxograma**: Fluxo completo do sistema

### 📈 Análises
- **Deduplicação**: Análise crítica de deduplicação
- **Erros**: Análise de erros em logs
- **Validações**: Validações de extração e completude
- **Refinamentos**: Análise de refinamentos do projeto

### ⚙️ Configuração
- **Insomnia**: Instalação, configuração e uso
- **Troubleshooting**: Solução de problemas comuns
- **Tokens**: Como obter e configurar tokens
- **Máquina Windows**: Configuração do ambiente

### 📋 Especificações
- **Design**: Arquitetura e design do sistema
- **Requirements**: Requisitos funcionais e não funcionais
- **Technical Specification**: Especificação técnica completa

### 📦 Versões
- **v2.0/2.1**: Documentação da versão atual

---

## 🔒 Arquivos Sensíveis

⚠️ **IMPORTANTE**: A pasta `08-arquivos-secretos/` contém arquivos sensíveis e **não é versionada no GitHub**. Esta pasta está configurada no `.gitignore`.

---

## 📞 Suporte

- **Documentação:** Você está aqui!
- **README Principal:** [../README.md](../README.md)

---

**Versão:** 2.3.1  
**Data:** 24/01/2026  
**Última Atualização:** Reorganização completa da documentação - estrutura consolidada e simplificada
