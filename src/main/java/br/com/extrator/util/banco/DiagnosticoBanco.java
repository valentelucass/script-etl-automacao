/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/banco/DiagnosticoBanco.java
Classe  : DiagnosticoBanco (class)
Pacote  : br.com.extrator.util.banco
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de diagnostico banco.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- main(...1 args): ponto de entrada da execucao.
- verificarTabelasCriticas(...1 args): realiza operacao relacionada a "verificar tabelas criticas".
- verificarViewsPowerBI(...1 args): realiza operacao relacionada a "verificar views power bi".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.banco;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ferramenta de diagnóstico unificada para inspecionar o schema do banco de dados.
 * Esta classe deve ser usada apenas para desenvolvimento e depuração, executando seu método main.
 * Substitui as classes ListadorTabelasBanco e VerificadorTabelas.
 */
public class DiagnosticoBanco {

    public static void main(final String[] args) {
        System.out.println("=== FERRAMENTA DE DIAGNÓSTICO DO BANCO DE DADOS ===");
        System.out.println();

        try (Connection connection = GerenciadorConexao.obterConexao()) {
            System.out.println("✅ Conectado ao banco de dados com sucesso!");
            System.out.println();

            listarTodasTabelas(connection);
            System.out.println();

            buscarTabelasPorTermos(connection);
            System.out.println();

            verificarTabelasCriticas(connection);
            System.out.println();
            verificarViewsPowerBI(connection);

        } catch (final SQLException e) {
            System.err.println("❌ Erro fatal durante o diagnóstico: " + e.getMessage());
        }
    }

    private static void listarTodasTabelas(final Connection connection) throws SQLException {
        System.out.println("📋 LISTANDO TODAS AS TABELAS NO BANCO DE DADOS:");
        System.out.println("-".repeat(50));

        final DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
            final List<String> nomeTabelas = new ArrayList<>();
            while (tables.next()) {
                final String nomeTabela = tables.getString("TABLE_NAME");
                final String esquema = tables.getString("TABLE_SCHEM");
                nomeTabelas.add(nomeTabela);
                System.out.println("• " + (esquema != null ? esquema + "." : "") + nomeTabela);
            }
            System.out.println("\nTotal de tabelas encontradas: " + nomeTabelas.size());
        }
    }

    private static void buscarTabelasPorTermos(final Connection connection) throws SQLException {
        System.out.println("🔍 BUSCANDO TABELAS POR TERMOS RELEVANTES:");
        System.out.println("-".repeat(50));

        final String[] termosRelevantes = {
            "fatura", "receber", "pagar", "coleta", "manifesto", "frete",
            "ocorrencia", "cotacao", "localizacao", "carga"
        };

        final DatabaseMetaData metaData = connection.getMetaData();

        for (final String termo : termosRelevantes) {
            System.out.println("🔎 Buscando por: '" + termo + "'");
            try (ResultSet tables = metaData.getTables(null, null, "%" + termo + "%", new String[]{"TABLE"})) {
                boolean encontrou = false;
                while (tables.next()) {
                    final String nomeTabela = tables.getString("TABLE_NAME");
                    final String esquema = tables.getString("TABLE_SCHEM");
                    System.out.println("  -> Encontrada: " + (esquema != null ? esquema + "." : "") + nomeTabela);
                    encontrou = true;
                }
                if (!encontrou) {
                    System.out.println("  -- Nenhuma tabela encontrada.");
                }
            }
        }
    }

    private static void verificarTabelasCriticas(final Connection connection) {
        System.out.println("🎯 VERIFICANDO ACESSIBILIDADE DAS TABELAS DO PROJETO:");
        System.out.println("-".repeat(50));

        final String[] tabelasDoProjeto = {
            "faturas_a_pagar", "faturas_a_receber", "ocorrencias",
            "coletas", "fretes", "cotacoes", "localizacao_cargas", "manifestos"
        };

        for (final String nomeTabela : tabelasDoProjeto) {
            try {
                // Tenta executar uma query simples que não retorna dados.
                final String sql = "SELECT 1 FROM " + nomeTabela + " WHERE 1=0";
                try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                    stmt.executeQuery();
                    System.out.println("✅ Tabela '" + nomeTabela + "' existe e é acessível.");
                }
            } catch (final SQLException e) {
                System.out.println("❌ Tabela '" + nomeTabela + "' NÃO existe ou está inacessível.");
                System.out.println("   (Erro reportado: " + e.getSQLState() + " - " + e.getMessage() + ")");
            }
        }
    }

    private static void verificarViewsPowerBI(final Connection connection) {
        System.out.println("🧩 VERIFICANDO COLUNAS DAS VIEWS POWER BI:");
        System.out.println("-".repeat(50));
        final String[] views = {
            "dbo.vw_cotacoes_powerbi",
            "dbo.vw_coletas_powerbi",
            "dbo.vw_contas_a_pagar_powerbi",
            "dbo.vw_faturas_por_cliente_powerbi",
            "dbo.vw_fretes_powerbi",
            "dbo.vw_manifestos_powerbi",
            "dbo.vw_localizacao_cargas_powerbi"
        };
        for (final String v : views) {
            try (PreparedStatement ps = connection.prepareStatement("SELECT TOP 0 * FROM " + v);
                 ResultSet rs = ps.executeQuery()) {
                final java.sql.ResultSetMetaData md = rs.getMetaData();
                final StringBuilder cols = new StringBuilder();
                for (int i = 1; i <= md.getColumnCount(); i++) {
                    if (i > 1) cols.append(", ");
                    cols.append(md.getColumnLabel(i));
                }
                System.out.println("✅ " + v + " → " + cols);
            } catch (final SQLException e) {
                System.out.println("❌ " + v + " inacessível: " + e.getMessage());
            }
        }
    }
}
