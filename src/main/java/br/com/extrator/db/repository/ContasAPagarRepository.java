package br.com.extrator.db.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.util.GerenciadorConexao;

/**
 * Repository para persistência de dados de Contas a Pagar (Data Export) no SQL Server.
 * Tabela: contas_a_pagar
 * Template ID: 8636
 */
public class ContasAPagarRepository {
    private static final Logger logger = LoggerFactory.getLogger(ContasAPagarRepository.class);
    private static final String NOME_TABELA = "contas_a_pagar";
    private static boolean viewVerificada = false;

    /**
     * Cria a tabela se não existir.
     */
    public void criarTabelaSeNaoExistir() throws SQLException {
        final String sql = String.join("\n",
            "IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'contas_a_pagar')",
            "BEGIN",
            "    CREATE TABLE contas_a_pagar (",
            "        sequence_code BIGINT PRIMARY KEY,",
            "        document_number VARCHAR(100),",
            "        issue_date DATE,",
            "        tipo_lancamento NVARCHAR(100),",
            "        valor_original DECIMAL(18,2),",
            "        valor_juros DECIMAL(18,2),",
            "        valor_desconto DECIMAL(18,2),",
            "        valor_a_pagar DECIMAL(18,2),",
            "        valor_pago DECIMAL(18,2),",
            "        status_pagamento NVARCHAR(50),",
            "        mes_competencia INT,",
            "        ano_competencia INT,",
            "        data_criacao DATETIMEOFFSET,",
            "        data_liquidacao DATE,",
            "        data_transacao DATE,",
            "        nome_fornecedor NVARCHAR(255),",
            "        nome_filial NVARCHAR(255),",
            "        nome_centro_custo NVARCHAR(255),",
            "        valor_centro_custo DECIMAL(18,2),",
            "        classificacao_contabil NVARCHAR(100),",
            "        descricao_contabil NVARCHAR(255),",
            "        valor_contabil DECIMAL(18,2),",
            "        area_lancamento NVARCHAR(255),",
            "        observacoes NVARCHAR(MAX),",
            "        descricao_despesa NVARCHAR(MAX),",
            "        nome_usuario NVARCHAR(255),",
            "        reconciliado BIT,",
            "        metadata NVARCHAR(MAX),",
            "        data_extracao DATETIME2 DEFAULT GETDATE()",
            "    );",
            "    CREATE INDEX IX_fp_data_export_issue_date ON contas_a_pagar(issue_date);",
            "    CREATE INDEX IX_fp_data_export_status ON contas_a_pagar(status_pagamento);",
            "    CREATE INDEX IX_fp_data_export_fornecedor ON contas_a_pagar(nome_fornecedor);",
            "    CREATE INDEX IX_fp_data_export_filial ON contas_a_pagar(nome_filial);",
            "    CREATE INDEX IX_fp_data_export_competencia ON contas_a_pagar(ano_competencia, mes_competencia);",
            "END"
        );

        try (Connection conn = GerenciadorConexao.obterConexao();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
            logger.info("✓ Tabela {} verificada/criada com sucesso", NOME_TABELA);

            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "valor_juros", "DECIMAL(18,2)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "valor_desconto", "DECIMAL(18,2)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "valor_pago", "DECIMAL(18,2)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "status_pagamento", "NVARCHAR(50)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "mes_competencia", "INT");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "ano_competencia", "INT");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "data_criacao", "DATETIMEOFFSET");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "data_liquidacao", "DATE");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "data_transacao", "DATE");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "nome_fornecedor", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "nome_filial", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "nome_centro_custo", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "valor_centro_custo", "DECIMAL(18,2)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "classificacao_contabil", "NVARCHAR(100)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "descricao_contabil", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "valor_contabil", "DECIMAL(18,2)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "area_lancamento", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "observacoes", "NVARCHAR(MAX)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "descricao_despesa", "NVARCHAR(MAX)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "nome_usuario", "NVARCHAR(255)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "reconciliado", "BIT");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "metadata", "NVARCHAR(MAX)");
            adicionarColunaSeNaoExistir(conn, NOME_TABELA, "data_extracao", "DATETIME2");

            if (!viewVerificada) {
                criarViewPowerBISeNaoExistir(conn);
                viewVerificada = true;
                logger.info("View do Power BI verificada/atualizada para {}.", NOME_TABELA);
            }
        }
    }

    private boolean colunaExiste(final Connection conn, final String tabela, final String coluna) throws SQLException {
        final java.sql.DatabaseMetaData md = conn.getMetaData();
        try (java.sql.ResultSet rs = md.getColumns(null, null, tabela.toUpperCase(), coluna.toUpperCase())) {
            return rs.next();
        }
    }

    private void adicionarColunaSeNaoExistir(final Connection conn, final String tabela, final String coluna, final String definicao) throws SQLException {
        if (!colunaExiste(conn, tabela, coluna)) {
            try (PreparedStatement ps = conn.prepareStatement("ALTER TABLE " + tabela + " ADD " + coluna + " " + definicao)) {
                ps.execute();
                logger.info("✓ Coluna adicionada: {}.{}", tabela, coluna);
            }
        }
    }

    private void criarViewPowerBISeNaoExistir(final Connection conn) throws SQLException {
        final String sqlView = """
            CREATE OR ALTER VIEW dbo.vw_contas_a_pagar_powerbi AS
            SELECT
                sequence_code AS [Lançamento a Pagar/N°],
                document_number AS [N° Documento],
                issue_date AS [Emissão],
                tipo_lancamento AS [Tipo],
                valor_original AS [Valor],
                valor_juros AS [Juros],
                valor_desconto AS [Desconto],
                valor_a_pagar AS [Valor a pagar],
                CASE WHEN status_pagamento = 'PAGO' THEN 'Sim' ELSE 'Não' END AS [Pago],
                valor_pago AS [Valor pago],
                nome_fornecedor AS [Fornecedor/Nome],
                nome_filial AS [Filial],
                classificacao_contabil AS [Conta Contábil/Classificação],
                descricao_contabil AS [Conta Contábil/Descrição],
                valor_contabil AS [Conta Contábil/Valor],
                nome_centro_custo AS [Centro de custo/Nome],
                valor_centro_custo AS [Centro de custo/Valor],
                area_lancamento AS [Área de Lançamento],
                mes_competencia AS [Mês de Competência],
                ano_competencia AS [Ano de Competência],
                data_criacao AS [Data criação],
                observacoes AS [Observações],
                descricao_despesa AS [Descrição da despesa],
                data_liquidacao AS [Baixa/Data liquidação],
                data_transacao AS [Data transação],
                nome_usuario AS [Usuário/Nome],
                status_pagamento AS [Status Pagamento],
                CASE WHEN reconciliado = 1 THEN 'Conciliado'
                     WHEN reconciliado = 0 THEN 'Não conciliado'
                     ELSE NULL
                END AS [Conciliado],
                metadata AS [Metadata],
                data_extracao AS [Data de extracao]
            FROM dbo.contas_a_pagar;
        """;
        try (PreparedStatement ps = conn.prepareStatement(sqlView)) {
            ps.execute();
        }
    }

    /**
     * Salva lista de entidades usando MERGE (INSERT ou UPDATE).
     */
    public int salvar(final List<ContasAPagarDataExportEntity> entidades) throws SQLException {
        if (entidades == null || entidades.isEmpty()) {
            logger.warn("Lista de {} vazia, nada a salvar", NOME_TABELA);
            return 0;
        }

        criarTabelaSeNaoExistir();

        final String sqlMerge = String.join("\n",
            "MERGE INTO contas_a_pagar AS target",
            "USING (SELECT ? AS sequence_code) AS source",
            "ON target.sequence_code = source.sequence_code",
            "WHEN MATCHED THEN",
            "    UPDATE SET",
            "        document_number = ?,",
            "        issue_date = ?,",
            "        tipo_lancamento = ?,",
            "        valor_original = ?,",
            "        valor_juros = ?,",
            "        valor_desconto = ?,",
            "        valor_a_pagar = ?,",
            "        valor_pago = ?,",
            "        status_pagamento = ?,",
            "        mes_competencia = ?,",
            "        ano_competencia = ?,",
            "        data_criacao = ?,",
            "        data_liquidacao = ?,",
            "        data_transacao = ?,",
            "        nome_fornecedor = ?,",
            "        nome_filial = ?,",
            "        nome_centro_custo = ?,",
            "        valor_centro_custo = ?,",
            "        classificacao_contabil = ?,",
            "        descricao_contabil = ?,",
            "        valor_contabil = ?,",
            "        area_lancamento = ?,",
            "        observacoes = ?,",
            "        descricao_despesa = ?,",
            "        nome_usuario = ?,",
            "        reconciliado = ?,",
            "        metadata = ?,",
            "        data_extracao = GETDATE()",
            "WHEN NOT MATCHED THEN",
            "    INSERT (sequence_code, document_number, issue_date, tipo_lancamento,",
            "            valor_original, valor_juros, valor_desconto, valor_a_pagar, valor_pago,",
            "            status_pagamento, mes_competencia, ano_competencia,",
            "            data_criacao, data_liquidacao, data_transacao,",
            "            nome_fornecedor, nome_filial, nome_centro_custo, valor_centro_custo,",
            "            classificacao_contabil, descricao_contabil, valor_contabil, area_lancamento,",
            "            observacoes, descricao_despesa, nome_usuario, reconciliado,",
            "            metadata, data_extracao)",
            "    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, GETDATE());"
        );
        int totalProcessados = 0;

        try (Connection conn = GerenciadorConexao.obterConexao()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(sqlMerge)) {
                for (final ContasAPagarDataExportEntity entity : entidades) {
                    // Validação
                    if (entity.getSequenceCode() == null) {
                        logger.warn("Entidade com sequence_code null ignorada");
                        continue;
                    }
                    // Parâmetros do MERGE (ON)
                    ps.setLong(1, entity.getSequenceCode());
                    // Parâmetros do UPDATE
                    ps.setString(2, entity.getDocumentNumber());
                    ps.setObject(3, entity.getIssueDate());
                    ps.setString(4, entity.getTipoLancamento());
                    ps.setBigDecimal(5, entity.getValorOriginal());
                    ps.setBigDecimal(6, entity.getValorJuros());
                    ps.setBigDecimal(7, entity.getValorDesconto());
                    ps.setBigDecimal(8, entity.getValorAPagar());
                    ps.setBigDecimal(9, entity.getValorPago());
                    ps.setString(10, entity.getStatusPagamento());
                    ps.setObject(11, entity.getMesCompetencia());
                    ps.setObject(12, entity.getAnoCompetencia());
                    ps.setObject(13, entity.getDataCriacao());
                    ps.setObject(14, entity.getDataLiquidacao());
                    ps.setObject(15, entity.getDataTransacao());
                    ps.setString(16, entity.getNomeFornecedor());
                    ps.setString(17, entity.getNomeFilial());
                    ps.setString(18, entity.getNomeCentroCusto());
                    ps.setBigDecimal(19, entity.getValorCentroCusto());
                    ps.setString(20, entity.getClassificacaoContabil());
                    ps.setString(21, entity.getDescricaoContabil());
                    ps.setBigDecimal(22, entity.getValorContabil());
                    ps.setString(23, entity.getAreaLancamento());
                    ps.setString(24, entity.getObservacoes());
                    ps.setString(25, entity.getDescricaoDespesa());
                    ps.setString(26, entity.getNomeUsuario());
                    ps.setObject(27, entity.getReconciliado());
                    ps.setString(28, entity.getMetadata());
                    // Parâmetros do INSERT (mesmos valores)
                    ps.setLong(29, entity.getSequenceCode());
                    ps.setString(30, entity.getDocumentNumber());
                    ps.setObject(31, entity.getIssueDate());
                    ps.setString(32, entity.getTipoLancamento());
                    ps.setBigDecimal(33, entity.getValorOriginal());
                    ps.setBigDecimal(34, entity.getValorJuros());
                    ps.setBigDecimal(35, entity.getValorDesconto());
                    ps.setBigDecimal(36, entity.getValorAPagar());
                    ps.setBigDecimal(37, entity.getValorPago());
                    ps.setString(38, entity.getStatusPagamento());
                    ps.setObject(39, entity.getMesCompetencia());
                    ps.setObject(40, entity.getAnoCompetencia());
                    ps.setObject(41, entity.getDataCriacao());
                    ps.setObject(42, entity.getDataLiquidacao());
                    ps.setObject(43, entity.getDataTransacao());
                    ps.setString(44, entity.getNomeFornecedor());
                    ps.setString(45, entity.getNomeFilial());
                    ps.setString(46, entity.getNomeCentroCusto());
                    ps.setBigDecimal(47, entity.getValorCentroCusto());
                    ps.setString(48, entity.getClassificacaoContabil());
                    ps.setString(49, entity.getDescricaoContabil());
                    ps.setBigDecimal(50, entity.getValorContabil());
                    ps.setString(51, entity.getAreaLancamento());
                    ps.setString(52, entity.getObservacoes());
                    ps.setString(53, entity.getDescricaoDespesa());
                    ps.setString(54, entity.getNomeUsuario());
                    ps.setObject(55, entity.getReconciliado());
                    ps.setString(56, entity.getMetadata());
                    final int rowsAffected = ps.executeUpdate();
                    if (rowsAffected > 0) {
                        totalProcessados++;
                    }
                }
                conn.commit();
                logger.info("✓ Processados {}/{} registros em {}",
                    totalProcessados, entidades.size(), NOME_TABELA);

            } catch (final SQLException e) {
                conn.rollback();
                logger.error("Erro ao salvar em {}: {}", NOME_TABELA, e.getMessage());
                throw e;
            }
        }

        return totalProcessados;
    }
}
