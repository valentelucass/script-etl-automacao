# 📊 Sistema de Métricas - Extrator ESL Cloud

## Visão Geral

O sistema de métricas fornece **visibilidade completa** sobre o desempenho, confiabilidade e volume de dados do processo de extração. Todas as métricas são coletadas automaticamente durante a execução e apresentadas em um relatório detalhado no final.

## 🎯 Objetivos

- **Monitoramento de Performance**: Identificar gargalos e operações lentas
- **Análise de Confiabilidade**: Rastrear sucessos e falhas por API
- **Controle de Volume**: Acompanhar quantidade de registros processados
- **Otimização Contínua**: Dados para melhorar o desempenho do sistema

## 📈 Métricas Coletadas

### 1. **Sucessos e Falhas por API**
- Contador de operações bem-sucedidas
- Contador de falhas com identificação da API
- Taxa de sucesso calculada automaticamente

**APIs Monitoradas:**
- `API_REST_Faturas_Receber`
- `API_REST_Faturas_Pagar` 
- `API_REST_Ocorrencias`
- `API_GraphQL_Ocorrencias`
- `API_GraphQL_Coletas`
- `API_GraphQL_Fretes`
- `API_DataExport_Fretes`
- `API_DataExport_Manifestos`
- `API_DataExport_Localizacao`

### 2. **Tempo de Execução (Timers)**
- Tempo de resposta de cada chamada de API
- Identificação de operações mais lentas
- Análise de tendências de performance

**Operações Cronometradas:**
- `extracao_faturas_receber`
- `extracao_faturas_pagar`
- `extracao_ocorrencias_rest`
- `extracao_ocorrencias_graphql`
- `extracao_coletas`
- `extracao_fretes_graphql`
- `extracao_fretes_dataexport`
- `extracao_manifestos`
- `extracao_localizacao_carga`

### 3. **Registros Processados**
- Quantidade de registros salvos no banco por entidade
- Controle de volume de dados
- Identificação de picos ou quedas no volume

**Entidades Monitoradas:**
- `faturas_a_receber`
- `faturas_a_pagar`
- `ocorrencias`
- `ocorrencias_graphql`
- `coletas`
- `fretes`
- `fretes_dataexport`
- `manifestos`
- `localizacao_carga`

## 🔍 Como Interpretar o Relatório

### Exemplo de Relatório:
```
MÉTRICAS DE EXECUÇÃO:
═══════════════════════════════════════════════════════════════

📊 SUCESSOS E FALHAS POR API:
   ✅ API_REST_Faturas_Receber: 1 sucesso(s), 0 falha(s)
   ✅ API_REST_Faturas_Pagar: 1 sucesso(s), 0 falha(s)
   ❌ API_GraphQL_Coletas: 0 sucesso(s), 1 falha(s)

⏱️  TEMPO DE EXECUÇÃO:
   🚀 extracao_faturas_receber: 1.234s
   🐌 extracao_coletas: 5.678s (LENTA)
   🚀 extracao_manifestos: 0.892s

📈 REGISTROS PROCESSADOS:
   📄 faturas_a_receber: 150 registros
   📄 manifestos: 75 registros
   📄 Total geral: 225 registros

📋 RESUMO GERAL:
   • Total de operações: 9
   • Sucessos: 8 (88.9%)
   • Falhas: 1 (11.1%)
   • Tempo total de APIs: 12.345s
```

### 🚨 Indicadores de Atenção

**Performance:**
- ⚠️ Operações > 3 segundos são marcadas como "LENTA"
- 🐌 Identifique gargalos para otimização

**Confiabilidade:**
- ❌ Falhas indicam problemas de conectividade ou configuração
- 📊 Taxa de sucesso < 95% requer investigação

**Volume:**
- 📉 Quedas bruscas no volume podem indicar problemas na fonte
- 📈 Picos podem impactar performance do banco

## 🛠️ Como Usar

### 1. **Execução Automática**
As métricas são coletadas automaticamente durante qualquer execução:

```bash
# Execução normal - métricas incluídas
java -jar extrator-esl.jar

# Execução com data específica - métricas incluídas  
java -jar extrator-esl.jar 2024-01-15

# Modo de teste - métricas incluídas
java -jar extrator-esl.jar --teste
```

### 2. **Localização do Relatório**
- **Terminal**: Exibido no final da execução na seção "MÉTRICAS DE EXECUÇÃO"
- **Logs**: Registrado no arquivo `logs/extrator-esl.log`

### 3. **Análise Histórica**
Para acompanhar tendências ao longo do tempo:

```bash
# Windows - Buscar métricas nos logs
findstr "MÉTRICAS DE EXECUÇÃO" logs\extrator-esl.log

# Linux/macOS - Buscar métricas nos logs  
grep "MÉTRICAS DE EXECUÇÃO" logs/extrator-esl.log
```

## 📊 Casos de Uso Práticos

### **Identificar APIs Problemáticas**
```
❌ API_GraphQL_Coletas: 0 sucesso(s), 3 falha(s)
```
**Ação**: Verificar conectividade, credenciais ou status da API GraphQL

### **Otimizar Performance**
```
🐌 extracao_manifestos: 8.456s (LENTA)
```
**Ação**: Investigar consultas, índices do banco ou otimizar filtros

### **Monitorar Volume de Dados**
```
📄 faturas_a_receber: 0 registros
```
**Ação**: Verificar se há dados no período ou problemas na fonte

### **Acompanhar Saúde Geral**
```
📋 RESUMO GERAL:
   • Sucessos: 9 (100%)
   • Tempo total de APIs: 15.234s
```
**Status**: ✅ Sistema funcionando perfeitamente

## 🔧 Configurações Avançadas

### **Personalizar Limites de Performance**
No arquivo `MetricasService.java`, você pode ajustar:

```java
// Limite para operações lentas (padrão: 3 segundos)
private static final double LIMITE_OPERACAO_LENTA = 3.0;
```

### **Adicionar Novas Métricas**
Para monitorar novas operações:

```java
// Iniciar timer
metricasService.iniciarTimer("nova_operacao");

// Sua operação aqui
resultado = minhaNovaOperacao();

// Parar timer
metricasService.pararTimer("nova_operacao");

// Registrar resultado
if (sucesso) {
    metricasService.registrarSucesso("MinhaAPI");
    metricasService.adicionarRegistrosProcessados("minha_entidade", quantidade);
} else {
    metricasService.registrarFalha("MinhaAPI");
}
```

## 🚀 Benefícios

- **Proatividade**: Identifique problemas antes que afetem o negócio
- **Otimização**: Dados concretos para melhorar performance
- **Confiabilidade**: Monitore a saúde do sistema em tempo real
- **Transparência**: Visibilidade completa do processo de extração
- **Histórico**: Acompanhe tendências e padrões ao longo do tempo

## 📞 Suporte

Para dúvidas sobre métricas ou interpretação dos dados:
1. Consulte os logs detalhados em `logs/extrator-esl.log`
2. Verifique a documentação técnica em `docs/ARQUITETURA-TECNICA.md`
3. Execute em modo de teste para validar configurações

---

**💡 Dica**: Use as métricas regularmente para manter o sistema otimizado e identificar oportunidades de melhoria!