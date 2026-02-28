/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/formatacao/ExportadorCSV.java
Classe  : ExportadorCSV (class)
Pacote  : br.com.extrator.util.formatacao
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de exportador csv.

Conecta com:
- ConstantesViewsPowerBI (api.constantes)
- GerenciadorConexao (util.banco)
- LoggerConsole (util.console)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- main(...1 args): ponto de entrada da execucao.
Atributos-chave:
- log: campo de estado para "log".
- PASTA_DESTINO: campo de estado para "pasta destino".
- FORMATTER: campo de estado para "formatter".
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.formatacao;

 
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import br.com.extrator.api.constantes.ConstantesViewsPowerBI;
import br.com.extrator.util.banco.GerenciadorConexao;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Exportador de dados para CSV
 * Exporta todas as entidades do banco para arquivos CSV
 * Inclui: Cotacoes, Coletas, Contas a Pagar, Faturas por Cliente, Faturas GraphQL,
 *         Fretes, Manifestos, Localizacao de Cargas, dim_usuarios, page_audit
 */
public class ExportadorCSV {
    // PROBLEMA #9 CORRIGIDO: Usar LoggerConsole para log duplo (arquivo + console)
    private static final LoggerConsole log = LoggerConsole.getLogger(ExportadorCSV.class);
    
    private static final String[] ENTIDADES = {
        ConstantesEntidades.COTACOES,
        ConstantesEntidades.COLETAS,
        ConstantesEntidades.CONTAS_A_PAGAR,
        ConstantesEntidades.FATURAS_POR_CLIENTE,
        ConstantesEntidades.FATURAS_GRAPHQL,
        ConstantesEntidades.FRETES,
        ConstantesEntidades.MANIFESTOS,
        ConstantesEntidades.LOCALIZACAO_CARGAS,
        "dim_usuarios",  // Tabela dimensão de usuários do sistema
        "page_audit"     // Entidade especial de auditoria, não precisa de constante
    };
    
    private static final String PASTA_DESTINO = "exports";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    public static void main(final String[] args) {
        log.console("===============================================================");
        log.info("  EXPORTADOR CSV - ESL CLOUD DATA");
        log.console("===============================================================");
        
        final String tabelaEspecifica = (args != null && args.length > 0) ? args[0].trim() : null;
        final boolean somenteUmaTabela = tabelaEspecifica != null && !tabelaEspecifica.isEmpty();

        // Criar pasta de destino
        final Path pastaExports = Paths.get(PASTA_DESTINO);
        try {
            if (!Files.exists(pastaExports)) {
                Files.createDirectories(pastaExports);
                log.info("✅ Pasta criada: {}", PASTA_DESTINO);
            }
        } catch (final IOException e) {
            log.error("❌ Erro ao criar pasta: {}", e.getMessage());
            return;
        }
        
        final String timestamp = LocalDateTime.now().format(FORMATTER);
        int totalRegistros = 0;
        int entidadesExportadas = 0;
        
        log.info("📅 Timestamp: {} | 📁 Destino: {}", timestamp, PASTA_DESTINO);
        log.console("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        
        // NOTA: As views Power BI são criadas/atualizadas pelos scripts SQL (database/views/).
        // Não é mais necessário atualizar views aqui.
        
        if (somenteUmaTabela) {
            log.info("📌 Exportando tabela específica: {}", tabelaEspecifica);
            try {
                final int registros = exportarEntidade(tabelaEspecifica, timestamp);
                log.info("    ✅ Sucesso: {} registros", registros);
                totalRegistros += registros;
                entidadesExportadas++;
            } catch (final Exception e) {
                log.error("    ❌ Erro: {}", e.getMessage());
            }
        } else {
            for (int i = 0; i < ENTIDADES.length; i++) {
                final String entidade = ENTIDADES[i];
                log.info("[{}/{}] 📊 Exportando: {}", i + 1, ENTIDADES.length, entidade);
                try {
                    final int registros = exportarEntidade(entidade, timestamp);
                    log.info("    ✅ Sucesso: {} registros", registros);
                    totalRegistros += registros;
                    entidadesExportadas++;
                } catch (final Exception e) {
                    log.error("    ❌ Erro: {}", e.getMessage());
                }
            }
        }
        
        // Resumo final
        log.console("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("  EXPORTAÇÃO CONCLUÍDA!");
        log.console("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        log.info("📊 Resumo: {}/{} entidades | {} registros | Pasta: {}", 
            entidadesExportadas, ENTIDADES.length, totalRegistros, Paths.get(PASTA_DESTINO).toAbsolutePath());
        log.info("💡 Próximos passos: 1) Abra 'exports' 2) Abra os CSVs 3) Compare com dados do portal");
    }

    /**
     * NOTA: As views do Power BI são criadas/atualizadas pelos scripts SQL em database/views/.
     * Não é mais necessário atualizar views aqui, pois elas são gerenciadas via executar_database.bat.
     * Este método foi removido para evitar duplicação de código DDL.
     */
    
    /**
     * Exporta uma entidade para CSV
     */
    private static int exportarEntidade(final String entidade, final String timestamp) throws Exception {
        final String entidadeArquivo = entidade.replaceAll("[^A-Za-z0-9_]+", "_");
        final String nomeArquivo = String.format("%s/%s_%s.csv", PASTA_DESTINO, entidadeArquivo, timestamp);
        
        // Determinar origem (view ou tabela) usando a classe de constantes
        String origem;
        if (ConstantesViewsPowerBI.possuiView(entidade)) {
            origem = "dbo." + ConstantesViewsPowerBI.obterNomeView(entidade);
        } else if ("dim_usuarios".equals(entidade)) {
            // Usar view vw_dim_usuarios se disponível, senão usar tabela diretamente
            origem = "dbo.vw_dim_usuarios";
        } else {
            // Para tabelas sem view, garantir esquema dbo.
            origem = entidade.contains(".") ? entidade : "dbo." + entidade;
        }
        
        log.info("    🔍 Contando registros em: {}", origem);
        
        // Contar usando a MESMA origem que será usada na query
        int totalNoBanco = 0;
        try (Connection connCount = GerenciadorConexao.obterConexao();
             Statement stmtCount = connCount.createStatement();
             ResultSet rsCount = stmtCount.executeQuery("SELECT COUNT(*) as total FROM " + origem)) {
            if (rsCount.next()) {
                totalNoBanco = rsCount.getInt("total");
            }
        }
        
        log.info("    📊 Total de registros encontrados: {}", totalNoBanco);
        
        if (totalNoBanco == 0) {
            log.warn("    ⚠️ Nenhum registro encontrado. CSV será criado apenas com cabeçalho.");
        }
        
        // Obter conexão do GerenciadorConexao
        try (Connection conn = GerenciadorConexao.obterConexao();
             Statement stmt = conn.createStatement();
             java.io.OutputStream os = new java.io.FileOutputStream(nomeArquivo);
             java.io.BufferedWriter writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(os, StandardCharsets.UTF_8))) {
            os.write(new byte[] {(byte)0xEF, (byte)0xBB, (byte)0xBF});
            
            // Configurar Statement para não limitar resultados
            // SQL Server pode ter limite padrão, então garantimos que não há limitação
            stmt.setFetchSize(0); // 0 = fetch all (padrão do driver)
            stmt.setMaxRows(0); // 0 = sem limite
            
            log.info("    🔄 Executando query: SELECT * FROM {}...", origem);
            
            final boolean usarViewPowerBI = !origem.equals(entidade) && !("dbo." + entidade).equals(origem);
            final boolean usarViewDimUsuarios = "dbo.vw_dim_usuarios".equals(origem);
            String query = "SELECT * FROM " + origem;
            // Ordenação consistente
            if (usarViewPowerBI || usarViewDimUsuarios) {
                // Para views PowerBI ou views de dimensão, usar primeira coluna (ID)
                if (usarViewDimUsuarios) {
                    query += " ORDER BY [User ID]"; // View vw_dim_usuarios tem [User ID] como primeira coluna
                } else {
                    // Evitar problemas com nomes com espaços usando posição
                    query += " ORDER BY 1"; // ID
                }
            } else {
                switch (entidade) {
                    case ConstantesEntidades.MANIFESTOS, ConstantesEntidades.COTACOES, ConstantesEntidades.CONTAS_A_PAGAR -> query += " ORDER BY sequence_code";
                    case ConstantesEntidades.LOCALIZACAO_CARGAS -> query += " ORDER BY sequence_number";
                    case ConstantesEntidades.COLETAS, ConstantesEntidades.FRETES -> query += " ORDER BY id";
                    case ConstantesEntidades.FATURAS_POR_CLIENTE -> query += " ORDER BY unique_id";
                    case "dim_usuarios" -> query += " ORDER BY user_id";
                    case "page_audit" -> query += " ORDER BY id";
                    default -> query += " ORDER BY 1"; // Ordenar pela primeira coluna por padrão
                }
            }
            
            log.debug("    📝 Query completa: {}", query);
            
            try (ResultSet rs = stmt.executeQuery(query)) {
                final ResultSetMetaData metaData = rs.getMetaData();
                final int columnCount = metaData.getColumnCount();
                
                log.debug("    📋 Colunas encontradas: {}", columnCount);
                
                // Escrever cabeçalho com alias (label) quando existente
                for (int i = 1; i <= columnCount; i++) {
                    writer.append(metaData.getColumnLabel(i));
                    if (i < columnCount) {
                        writer.append(";");
                    }
                }
                writer.append("\r\n");
                
                // Escrever dados
                int count = 0;
                final int logInterval = Math.max(100, totalNoBanco / 10); // Log a cada 10% ou 100 registros
                
                log.info("    📝 Escrevendo registros no CSV (esperado: {})...", totalNoBanco);
                
                while (rs.next()) {
                    for (int i = 1; i <= columnCount; i++) {
                        final Object value = rs.getObject(i);
                        if (value != null) {
                            String strValue = value.toString();
                            // Escapar valores que contenham vírgula ou aspas
                            if (strValue.contains(";") || strValue.contains("\"") || strValue.contains("\n")) {
                                strValue = "\"" + strValue.replace("\"", "\"\"") + "\"";
                            }
                            writer.append(strValue);
                        }
                        if (i < columnCount) {
                            writer.append(";");
                        }
                    }
                    writer.append("\r\n");
                    count++;
                    
                    // Log de progresso (apenas em debug para não poluir console)
                    if (count % logInterval == 0 || count == totalNoBanco) {
                        final double percentual = totalNoBanco > 0
                            ? (count * 100.0 / totalNoBanco)
                            : 0.0;
                        log.debug("    ⏳ Progresso: {}/{} registros ({}%)",
                                count, totalNoBanco, String.format("%.1f", percentual));
                    }
                }
                
                // Garantir que o buffer seja escrito
                writer.flush();
                
                log.info("    ✅ Exportação concluída: {} registros escritos", count);
                
                // Verificar se há discrepância
                if (count != totalNoBanco) {
                    log.error("    ⚠️ ATENÇÃO: Discrepância detectada!");
                    log.error("       Esperado: {} | Exportados: {} | Diferença: {}", 
                        totalNoBanco, count, totalNoBanco - count);
                    log.error("       Verifique se há algum filtro ou limite sendo aplicado na query!");
                } else {
                    log.info("    ✅ Validação: {} registros exportados = {} esperados (OK)", count, totalNoBanco);
                }
                
                return count;
            }
        }
    }
}
