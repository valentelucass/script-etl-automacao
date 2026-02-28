# Scripts de MigraÃ§Ã£o do Banco de Dados

Esta pasta contÃ©m todos os scripts SQL necessÃ¡rios para criar as tabelas e views do banco de dados em **produÃ§Ã£o**.

## ðŸ“‹ Estrutura

### Tabelas (001-013)
- `001_criar_tabela_coletas.sql`
- `002_criar_tabela_fretes.sql`
- `003_criar_tabela_manifestos.sql`
- `004_criar_tabela_cotacoes.sql`
- `005_criar_tabela_localizacao_cargas.sql`
- `006_criar_tabela_contas_a_pagar.sql`
- `007_criar_tabela_faturas_por_cliente.sql`
- `008_criar_tabela_faturas_graphql.sql`
- `009_criar_tabela_log_extracoes.sql`
- `010_criar_tabela_page_audit.sql`
- `011_criar_tabela_dim_usuarios.sql`
- `012_criar_tabela_sys_execution_history.sql`
- `013_criar_tabela_sys_auditoria_temp.sql`

### Views Power BI Principais (011-018)
- `011_criar_view_faturas_por_cliente_powerbi.sql`
- `012_criar_view_fretes_powerbi.sql`
- `013_criar_view_coletas_powerbi.sql`
- `014_criar_view_faturas_graphql_powerbi.sql`
- `015_criar_view_cotacoes_powerbi.sql`
- `016_criar_view_contas_a_pagar_powerbi.sql`
- `017_criar_view_localizacao_cargas_powerbi.sql`
- `018_criar_view_manifestos_powerbi.sql`

### Views de DimensÃµes (019-024)
- `019_criar_view_dim_filiais.sql`
- `020_criar_view_dim_clientes.sql`
- `021_criar_view_dim_veiculos.sql`
- `022_criar_view_dim_motoristas.sql`
- `023_criar_view_dim_planocontas.sql`
- `024_criar_view_dim_usuarios.sql`

**Nota sobre numeraÃ§Ã£o:** Os prefixos numÃ©ricos podem se repetir entre pastas diferentes (ex.: `024` existe em `seguranca/` e em `views-dimensao/`). A ordem correta de execuÃ§Ã£o Ã© definida em `executar_database.bat`.

### ConfiguraÃ§Ã£o de SeguranÃ§a (024)
- `024_configurar_permissoes_usuario.sql` - Configura permissÃµes do usuÃ¡rio da aplicaÃ§Ã£o (PRINCÃPIO DO MENOR PRIVILÃ‰GIO)

### ValidaÃ§Ã£o (025-029)
- `025_validar_views_dimensao.sql` - Valida unicidade das chaves primÃ¡rias nas views de dimensÃ£o (CRÃTICO para Power BI)
- `026_validar_tipo_destroy_user_id.sql` - Valida tipo de destroyUserId e cancellationUserId
- `027_diagnosticar_campos_null_coletas.sql` - Diagnostica campos NULL em coletas
- `028_validacao_rapida_extracao.sql` - ValidaÃ§Ã£o rÃ¡pida de extraÃ§Ã£o
- `029_verificar_duplicacao_faturas.sql` - Verifica duplicaÃ§Ã£o de faturas

## ðŸš€ Como Executar

### OpÃ§Ã£o 1: Script AutomÃ¡tico (Recomendado)

1. **Configure o `config.bat`** (copie `config_exemplo.bat` para `config.bat` e preencha):
   
   **Para autenticaÃ§Ã£o SQL Server:**
   ```cmd
   set DB_SERVER=servidor
   set DB_NAME=banco_de_dados
   set DB_USER=usuario
   set DB_PASSWORD=senha
   ```
   
   **Para autenticaÃ§Ã£o integrada do Windows:** deixe `DB_USER` e `DB_PASSWORD` vazios no `config.bat`.

2. Execute na pasta `database`:
   ```cmd
   executar_database.bat
   ```

   O `executar_database.bat` usa **sqlcmd** e o `config.bat`; nÃ£o Ã© preciso ter o SSMS aberto. Ele roda na ordem: tabelas, views, views-dimensÃ£o, seguranÃ§a (024) e **todas as validaÃ§Ãµes (025-029)**.

### OpÃ§Ã£o 2: SQL Server Management Studio (SSMS)

1. Abra o SQL Server Management Studio
2. Conecte-se ao banco de dados de produÃ§Ã£o
3. Execute os scripts na ordem definida em `executar_database.bat`
4. **IMPORTANTE**: Execute primeiro as tabelas (001-013), depois as views principais (011-018), views de dimensÃµes (019-024), seguranÃ§a (024) e validaÃ§Ãµes (025-029)

### OpÃ§Ã£o 3: sqlcmd (Linha de Comando)

```cmd
sqlcmd -S servidor -d banco -U usuario -P senha -i 001_criar_tabela_coletas.sql
sqlcmd -S servidor -d banco -U usuario -P senha -i 002_criar_tabela_fretes.sql
... (continue para todos os scripts)
```

## âš ï¸ Importante

1. **Execute apenas UMA VEZ** no ambiente de produÃ§Ã£o
2. **Ordem de execuÃ§Ã£o Ã© crÃ­tica**: 
   - Primeiro as tabelas (001-013)
   - Depois as views principais (011-018)
   - Por Ãºltimo as views de dimensÃµes (019-024)
   - **SeguranÃ§a**: Configure permissÃµes (024 em `seguranca/`) - PRINCÃPIO DO MENOR PRIVILÃ‰GIO
   - **ValidaÃ§Ãµes**: Execute scripts de validaÃ§Ã£o (025-029) - Garante integridade e unicidade
3. **Backup**: FaÃ§a backup do banco antes de executar
4. **Teste**: Teste primeiro em ambiente de desenvolvimento/staging
5. **NumeraÃ§Ã£o**: A ordem correta estÃ¡ definida em `executar_database.bat` (prefixos podem se repetir entre pastas)

## ðŸ”’ SeguranÃ§a: PermissÃµes do UsuÃ¡rio da AplicaÃ§Ã£o

**IMPORTANTE**: ApÃ³s criar as tabelas e views, configure as permissÃµes do usuÃ¡rio da aplicaÃ§Ã£o seguindo o **PrincÃ­pio do Menor PrivilÃ©gio**.

### Por que isso Ã© importante?

Como a aplicaÃ§Ã£o **nÃ£o cria mais tabelas automaticamente**, o usuÃ¡rio do banco de dados configurado no `config.properties` (DB_USER) **nÃ£o precisa e nÃ£o deve ter permissÃµes DDL** (CREATE, ALTER, DROP).

### PermissÃµes NecessÃ¡rias

O usuÃ¡rio da aplicaÃ§Ã£o precisa apenas de:
- **SELECT** (leitura) - para consultas e validaÃ§Ãµes
- **INSERT, UPDATE, DELETE** (escrita) - para operaÃ§Ãµes MERGE (UPSERT)

**NÃƒO precisa de:**
- CREATE TABLE
- ALTER TABLE
- DROP TABLE
- ALTER SCHEMA
- CONTROL

### Como Configurar

1. Execute o script `024_configurar_permissoes_usuario.sql`
2. Substitua `usuario_aplicacao` pelo nome do usuÃ¡rio configurado no `config.properties` (DB_USER)
3. Substitua `seu_banco_de_dados` pelo nome do banco de dados
4. Descomente e execute a OPÃ‡ÃƒO 1 (recomendado) que usa roles padrÃ£o do SQL Server

### BenefÃ­cio de SeguranÃ§a

Se houver uma injeÃ§Ã£o de SQL, o atacante **nÃ£o poderÃ¡ destruir sua estrutura de dados**, pois o usuÃ¡rio da aplicaÃ§Ã£o nÃ£o tem permissÃµes para criar, alterar ou excluir tabelas.

## âš ï¸ VALIDAÃ‡ÃƒO CRÃTICA: Views de DimensÃ£o

**IMPORTANTE**: ApÃ³s criar as views de dimensÃ£o (019-023), **SEMPRE** execute o script `025_validar_views_dimensao.sql`.

### Por que isso Ã© crÃ­tico?

As views de dimensÃ£o devem ter **chaves primÃ¡rias Ãºnicas** para funcionar corretamente no Power BI com modelo Star Schema:

- **vw_dim_clientes**: Chave = `Nome` normalizado (deve ser Ãºnico)
- **vw_dim_filiais**: Chave = `NomeFilial` (deve ser Ãºnico)
- **vw_dim_veiculos**: Chave = `Placa` (deve ser Ãºnico)
- **vw_dim_motoristas**: Chave = `NomeMotorista` (deve ser Ãºnico)
- **vw_dim_planocontas**: Chave = `Descricao` (deve ser Ãºnico)

### O que acontece se houver duplicatas?

Se uma view dimensional retornar chaves duplicadas, o Power BI **nÃ£o conseguirÃ¡ criar relacionamentos** corretos (1:N) e gerarÃ¡ relacionamentos Muitos-para-Muitos nÃ£o intencionais, quebrando o modelo Star Schema.

### CorreÃ§Ãµes Aplicadas

As views foram corrigidas para garantir unicidade:

- **vw_dim_clientes**: Usa `DISTINCT` com normalizaÃ§Ã£o `UPPER + LTRIM + RTRIM` para eliminar nomes duplicados
- **vw_dim_filiais**: Usa `UNION` com `LTRIM(RTRIM)` para normalizar nomes
- **vw_dim_veiculos**: JÃ¡ usava `GROUP BY Placa` corretamente
- **vw_dim_motoristas**: Usa `DISTINCT` com normalizaÃ§Ã£o `UPPER + LTRIM + RTRIM`
- **vw_dim_planocontas**: JÃ¡ usava `GROUP BY Descricao` corretamente

## ðŸ“ Notas

- Todos os scripts usam `IF NOT EXISTS` para evitar erros se executados mÃºltiplas vezes
- As views usam `CREATE OR ALTER` para permitir atualizaÃ§Ãµes futuras
- Os scripts foram extraÃ­dos diretamente do cÃ³digo Java do projeto
- **Views de dimensÃ£o foram corrigidas para garantir unicidade das chaves primÃ¡rias**
