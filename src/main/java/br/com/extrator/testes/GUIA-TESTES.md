# Guia de Testes - Extrator ESL Cloud

## Comandos Disponíveis

### 1. Ajuda
```bash
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--ajuda"
```

### 2. Modo Normal
```bash
# Extração completa com data atual
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main"

# Extração completa com data específica
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="2025-10-01"
```

### 3. Testes Completos
```bash
# Teste todas as APIs (limite 5 registros)
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste"

# Teste todas as APIs com data específica
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste 2025-10-01"
```

### 4. Testes Isolados por API

#### API REST (Faturas e Ocorrências)
```bash
# Teste apenas API REST
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste-rest"

# Teste API REST com data específica
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste-rest 2025-10-01"
```

#### API GraphQL (Coletas e Fretes)
```bash
# Teste apenas API GraphQL
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste-graphql"

# Teste API GraphQL com data específica
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste-graphql 2025-10-01"
```

#### API Data Export (Manifestos e Localização)
```bash
# Teste apenas API Data Export
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste-data-export"

# Teste API Data Export com data específica
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--teste-data-export 2025-10-01"
```

## Comandos Úteis

### Compilar apenas
```bash
mvn compile
```

### Executar JAR (após build)
```bash
# Build do projeto
mvn clean package

# Executar JAR
java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-jar-with-dependencies.jar --teste-rest
```

### Introspecção GraphQL
```bash
mvn compile exec:java -Dexec.mainClass="br.com.extrator.Main" -Dexec.args="--introspeccao"
```

## Características dos Testes

- **Modo Teste**: Limita a 5 registros por entidade
- **Modo Normal**: Extração completa sem limite
- **Testes Isolados**: Executam apenas APIs específicas, pulando as demais
- **Economia de Quota**: Testes isolados reduzem consumo de API

## Tempo Estimado de Execução

- **--teste-data-export**: ~4 segundos
- **--teste-graphql**: ~11 segundos  
- **--teste-rest**: ~28 segundos
- **--teste** (completo): ~40 segundos