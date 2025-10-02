# 📁 Estrutura do Projeto - Extrator ESL Cloud

## Visão Geral

Este documento descreve a **estrutura completa** do projeto, detalhando a finalidade de cada arquivo e pasta para facilitar a navegação e manutenção do código.

## 🏗️ Estrutura de Diretórios

```
script-automacao/
├── 📄 .gitignore                    # Arquivos ignorados pelo Git
├── 📄 pom.xml                       # Configuração Maven (dependências, build)
├── 📁 docs/                         # 📚 Documentação do projeto
├── 📁 src/                          # 💻 Código fonte
└── 📁 logs/                         # 📋 Arquivos de log (criado em runtime)
```

---

## 📚 Pasta `docs/` - Documentação

### **Documentação Principal**
| Arquivo | Finalidade |
|---------|------------|
| `README.md` | **Visão geral** do projeto e instruções básicas |
| `INSTRUCOES.md` | **Manual completo** de instalação e uso |
| `ARQUITETURA-TECNICA.md` | **Documentação técnica** detalhada da arquitetura |
| `GUIA-VALIDACAO.md` | **Procedimentos** de validação e testes |
| `DOCUMENTACAO-METRICAS.md` | **Sistema de métricas** - como usar e interpretar |
| `ESTRUTURA-PROJETO.md` | **Este arquivo** - estrutura e organização |

### **Subpastas de Documentação**

#### `📁 docs/ideias-futuras/`
**Finalidade**: Armazenar ideias, melhorias e planejamento futuro

| Arquivo | Finalidade |
|---------|------------|
| `01-10-25.md` | Ideias e planejamentos do dia 01/10/2025 |
| `recomendacoes-melhorias.md` | **Lista de melhorias** sugeridas para o sistema |
| `recomendacoes-melhorias.pdf` | Versão PDF das recomendações |

#### `📁 docs/relatorios-diarios/`
**Finalidade**: Histórico de desenvolvimento e progresso diário

| Arquivo | Finalidade |
|---------|------------|
| `19-09-25.md` | Relatório de desenvolvimento - 19/09/2025 |
| `22-09-25.md` | Relatório de desenvolvimento - 22/09/2025 |
| `23-09-25.md` | Relatório de desenvolvimento - 23/09/2025 |
| `23-09-25-final.md` | **Relatório final** - 23/09/2025 |
| `24-09-25.md` | Relatório de desenvolvimento - 24/09/2025 |
| `25-09-25.md` | Relatório de desenvolvimento - 25/09/2025 |
| `01-10-25.md` | Relatório de desenvolvimento - 01/10/2025 |
| `duvidas-api.md` | **Dúvidas e soluções** relacionadas às APIs |

---

## 💻 Pasta `src/` - Código Fonte

### **Estrutura Maven Padrão**
```
src/
├── main/
│   ├── java/           # Código Java principal
│   └── resources/      # Recursos e configurações
└── test/               # Testes unitários (não implementado ainda)
```

### **Pacote Principal: `br.com.extrator`**

#### **📄 Arquivo Raiz**
| Arquivo | Finalidade |
|---------|------------|
| `Main.java` | **Classe principal** - ponto de entrada da aplicação, orquestra todo o processo de extração |

---

## 📦 Subpacotes e Suas Finalidades

### `📁 api/` - Clientes de API
**Finalidade**: Comunicação com APIs externas (REST, GraphQL, Data Export)

| Arquivo | Finalidade |
|---------|------------|
| `ClienteApiRest.java` | **Cliente REST** - Faturas a Receber, Faturas a Pagar, Ocorrências |
| `ClienteApiGraphQL.java` | **Cliente GraphQL** - Ocorrências, Coletas, Fretes |
| `ClienteApiDataExport.java` | **Cliente Data Export** - Fretes, Manifestos, Localização da Carga |

**Responsabilidades**:
- 🔗 Estabelecer conexões com APIs
- 🔐 Gerenciar autenticação e tokens
- 📡 Executar requisições HTTP/GraphQL
- 🛡️ Tratar erros de comunicação
- 📊 Processar respostas JSON

### `📁 db/` - Banco de Dados
**Finalidade**: Interação com banco de dados PostgreSQL

| Arquivo | Finalidade |
|---------|------------|
| `ServicoBancoDadosDinamico.java` | **Serviço principal** - operações CRUD, conexão, transações |
| `GeradorTabelasDinamico.java` | **Gerador de tabelas** - criação automática de estruturas no BD |

**Responsabilidades**:
- 🗄️ Gerenciar conexões com PostgreSQL
- 📋 Criar tabelas dinamicamente
- 💾 Inserir, atualizar e consultar dados
- 🔄 Gerenciar transações
- 🛡️ Tratar erros de banco de dados

### `📁 modelo/` - Modelos de Dados
**Finalidade**: Representação de entidades e estruturas de dados

| Arquivo | Finalidade |
|---------|------------|
| `EntidadeDinamica.java` | **Modelo genérico** - representa qualquer entidade extraída das APIs |

**Responsabilidades**:
- 📊 Definir estrutura de dados
- 🔄 Mapear JSON para objetos Java
- 🏗️ Facilitar criação dinâmica de tabelas
- 📝 Validar dados recebidos

### `📁 servicos/` - Serviços de Negócio
**Finalidade**: Lógica de negócio e serviços auxiliares

| Arquivo | Finalidade |
|---------|------------|
| `MetricasService.java` | **Sistema de métricas** - coleta e relatórios de performance |

**Responsabilidades**:
- 📊 Coletar métricas de execução
- ⏱️ Cronometrar operações
- 📈 Contar sucessos e falhas
- 📋 Gerar relatórios detalhados
- 🎯 Monitorar performance do sistema

### `📁 testes/` - Testes e Validação
**Finalidade**: Ferramentas de teste e validação do sistema

| Arquivo | Finalidade |
|---------|------------|
| `TesteModoExecucao.java` | **Modo de teste** - validação de configurações e conectividade |
| `GUIA-TESTES.md` | **Documentação** de testes e procedimentos de validação |

**Responsabilidades**:
- 🧪 Validar configurações
- 🔍 Testar conectividade com APIs
- 🗄️ Verificar conexão com banco
- 📋 Exibir informações de diagnóstico
- ❓ Fornecer ajuda e documentação

### `📁 util/` - Utilitários
**Finalidade**: Funções auxiliares e utilitários gerais

| Arquivo | Finalidade |
|---------|------------|
| `CarregadorConfig.java` | **Carregador de configurações** - variáveis de ambiente e propriedades |

**Responsabilidades**:
- ⚙️ Carregar variáveis de ambiente
- 🔧 Gerenciar configurações
- 🛡️ Validar parâmetros obrigatórios
- 📝 Fornecer valores padrão
- 🔍 Facilitar debugging de configurações

---

## 📋 Pasta `resources/` - Recursos

### **Configurações**
| Arquivo | Finalidade |
|---------|------------|
| `logback.xml` | **Configuração de logs** - níveis, formatos, rotação de arquivos |

**Responsabilidades**:
- 📝 Definir níveis de log (DEBUG, INFO, WARN, ERROR)
- 📄 Configurar formato das mensagens
- 🔄 Gerenciar rotação de arquivos
- 📍 Definir localização dos logs
- 🎯 Filtrar mensagens por categoria

---

## 📋 Pasta `logs/` - Arquivos de Log

**Finalidade**: Armazenar logs de execução (criada automaticamente)

### **Estrutura de Logs**
```
logs/
├── extrator-esl.log           # Log atual
├── extrator-esl.2024-01-15.log   # Log arquivado
└── extrator-esl.2024-01-14.log   # Log arquivado
```

**Características**:
- 🔄 **Rotação diária** automática
- 📅 **Retenção** de 30 dias
- 📊 **Métricas** incluídas nos logs
- 🛡️ **Rastreamento** completo de erros

---

## 🔧 Arquivo `pom.xml` - Configuração Maven

**Finalidade**: Gerenciamento de dependências e build do projeto

### **Principais Seções**:
- 📦 **Dependencies**: Bibliotecas utilizadas (Jackson, PostgreSQL, SLF4J, etc.)
- 🏗️ **Build**: Configurações de compilação
- ☕ **Properties**: Versão do Java e encoding
- 📋 **Metadata**: Informações do projeto

### **Dependências Principais**:
- `jackson-databind` - Processamento JSON
- `postgresql` - Driver do banco de dados
- `slf4j-api` + `logback-classic` - Sistema de logs
- `maven-compiler-plugin` - Compilação Java

---

## 🚀 Fluxo de Execução

### **Ordem de Inicialização**:
1. 🎯 `Main.java` - Ponto de entrada
2. ⚙️ `CarregadorConfig.java` - Carrega configurações
3. 🗄️ `ServicoBancoDadosDinamico.java` - Conecta ao banco
4. 📊 `MetricasService.java` - Inicia coleta de métricas
5. 🔗 Clientes API - Estabelecem conexões
6. 📋 Processo de extração - Executa operações
7. 📈 Relatório final - Exibe métricas e resultados

### **Interações Entre Componentes**:
```
Main.java
├── CarregadorConfig.java (configurações)
├── MetricasService.java (métricas)
├── ServicoBancoDadosDinamico.java (banco)
├── ClienteApiRest.java (APIs REST)
├── ClienteApiGraphQL.java (APIs GraphQL)
└── ClienteApiDataExport.java (Data Export)
```

---

## 📞 Navegação Rápida

### **Para Desenvolvedores**:
- 🎯 **Lógica principal**: `src/main/java/br/com/extrator/Main.java`
- 🔗 **APIs**: `src/main/java/br/com/extrator/api/`
- 🗄️ **Banco de dados**: `src/main/java/br/com/extrator/db/`
- 📊 **Métricas**: `src/main/java/br/com/extrator/servicos/MetricasService.java`

### **Para Operações**:
- 📚 **Documentação**: `docs/INSTRUCOES.md`
- 📋 **Logs**: `logs/extrator-esl.log`
- ⚙️ **Configurações**: `src/main/resources/logback.xml`
- 🧪 **Testes**: `src/main/java/br/com/extrator/testes/`

### **Para Gestão**:
- 📊 **Métricas**: `docs/DOCUMENTACAO-METRICAS.md`
- 🏗️ **Arquitetura**: `docs/ARQUITETURA-TECNICA.md`
- 💡 **Melhorias**: `docs/ideias-futuras/recomendacoes-melhorias.md`
- 📈 **Relatórios**: `docs/relatorios-diarios/`

---

**💡 Dica**: Esta estrutura foi projetada para ser **modular**, **escalável** e **fácil de manter**. Cada componente tem responsabilidades bem definidas e pode ser modificado independentemente!