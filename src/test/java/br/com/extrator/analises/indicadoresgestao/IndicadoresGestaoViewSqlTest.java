package br.com.extrator.analises.indicadoresgestao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class IndicadoresGestaoViewSqlTest {
    private static final Pattern MOJIBAKE_PATTERN = Pattern.compile(
        "\\x{00C3}[\\x{0080}-\\x{00BF}\\x{0192}\\x{201A}\\x{00A2}]|"
            + "\\x{00C2}[\\x{0080}-\\x{00BF}]|\\x{FFFD}"
    );
    private static final Pattern SELECT_STAR_PATTERN = Pattern.compile("(?is)\\bSELECT\\s+\\*\\b");

    @Test
    void fretesPowerBiDeveExporColunasOficiaisDePerformanceECubagem() throws IOException {
        final String sql = lerSql("database/views/012_criar_view_fretes_powerbi.sql");

        assertContem(sql, "[Nº Minuta]");
        assertContem(sql, "[Filial Emissora]");
        assertContem(sql, "[Responsável pela Região de Destino]");
        assertContem(sql, "[Responsável Região Destino Key]");
        assertContem(sql, "[Data de Finalização]");
        assertContem(sql, "[Finalização da Performance]");
        assertContem(sql, "[Performance Diferença de Dias]");
        assertContem(sql, "[Performance Status]");
        assertContem(sql, "[Performance Status Dif de Dias]");
        assertContem(sql, "[Performance Status Dif de Dias Oficial]");
        assertContem(sql, "[Peso Real]");
        assertContem(sql, "[Peso Cubado]");
        assertContem(sql, "[Total M3]");
        assertContem(sql, "COALESCE(lc.invoices_volumes, f.invoices_total_volumes, 0) AS [Volumes]");
        assertFalse(sql.contains("NULLIF(f.invoices_total_volumes, 0)"));
        assertContem(sql, "[Cortesia]");
        assertContem(sql, "[Cortesia Flag]");
        assertContem(sql, "[Comprovante Anexado]");
        assertContem(sql, "FROM dbo.inventario AS inv");
        assertContem(sql, "inv.flag_comprovante_anexado = 1");
        assertContem(sql, "NULLIF(LTRIM(RTRIM(f.nfse_series)), '')");
        assertContem(sql, "LEFT JOIN dbo.localizacao_cargas");
        assertFalse(sql.contains("JSON_VALUE(lc.metadata, '$.cnr_c_s_fit_fte_lce_ore_description')"));
        assertFalse(sql.contains("%Comprovante%Entrega%Anexado%"));
    }

    @Test
    void fretesPowerBiDeveUsarFinishedAtComoFallbackDaFinalizacaoDePerformance() throws IOException {
        final String sql = lerSql("database/views/012_criar_view_fretes_powerbi.sql");

        assertContem(sql, "COALESCE(f.fit_dpn_performance_finished_at, f.finished_at) AS finalizacao_performance_oficial");
    }

    @Test
    void coletasPowerBiDeveExporSolicitacaoComoDataNativaEIndiceDashboard() throws IOException {
        final String tabelaSql = lerSql("database/tabelas/001_criar_tabela_coletas.sql");
        final String viewSql = lerSql("database/views/013_criar_view_coletas_powerbi.sql");
        final String indicesSql = lerSql("database/indices/001_criar_indices_performance.sql");
        final String migrationSql = lerSql("database/migrations/018_adicionar_indice_coletas_request_date_dashboard.sql");

        assertContem(tabelaSql, "request_date DATE");
        assertContem(viewSql, "c.request_date AS [Solicitacao]");
        assertContem(viewSql, "OUTER APPLY (");
        assertContem(viewSql, "SELECT TOP (1)");
        assertContem(viewSql, "ORDER BY m.sequence_code DESC, m.id DESC");
        assertFalse(viewSql.contains("LEFT JOIN dbo.manifestos m"));
        assertContem(indicesSql, "IX_coletas_request_date_dashboard");
        assertContem(indicesSql, "ON dbo.coletas(request_date, status, pick_region, cidade_coleta)");
        assertContem(migrationSql, "IX_coletas_request_date_dashboard");
        assertContem(migrationSql, "ON dbo.coletas(request_date, status, pick_region, cidade_coleta)");
        assertSemMojibake(viewSql, "database/views/013_criar_view_coletas_powerbi.sql");
        assertSemMojibake(migrationSql, "database/migrations/018_adicionar_indice_coletas_request_date_dashboard.sql");
    }

    @Test
    void fretesPowerBiDeveExporFaturamentoMaterializadoSemRegraPesadaNaView() throws IOException {
        final String sql = lerSql("database/views/012_criar_view_fretes_powerbi.sql");

        assertContem(sql, "f.data_referencia_faturamento AS data_referencia_faturamento");
        assertContem(sql, "f.is_elegivel_faturamento AS is_elegivel_faturamento");
        assertFalse(
            sql.contains("%bloqueio%") || sql.contains("%anulacao%") || sql.contains("%isolamento%"),
            "A view de fretes deve apenas expor os campos materializados pelo ETL, sem regra pesada de string."
        );
        assertSemMojibake(sql, "database/views/012_criar_view_fretes_powerbi.sql");
    }

    @Test
    void migrationFretesFaturamentoDeveMaterializarRegraEIndice() throws IOException {
        final String sql = lerSql("database/migrations/016_materializar_faturamento_fretes.sql");

        assertContem(sql, "ALTER TABLE dbo.fretes ADD data_referencia_faturamento DATETIMEOFFSET NULL");
        assertContem(sql, "ALTER TABLE dbo.fretes ADD is_elegivel_faturamento BIT NULL");
        assertContem(sql, "COALESCE(f.cte_issued_at, f.servico_em) AS data_referencia_faturamento");
        assertContem(sql, "WHEN f.cortesia = 1 THEN 0");
        assertContem(sql, "COLLATE Latin1_General_CI_AI LIKE N''%bloqueio%''");
        assertContem(sql, "COLLATE Latin1_General_CI_AI LIKE N''%anulacao%''");
        assertContem(sql, "COLLATE Latin1_General_CI_AI LIKE N''%isolamento%''");
        assertContem(sql, "IX_fretes_faturamento_data_elegivel");
        assertSemMojibake(sql, "database/migrations/016_materializar_faturamento_fretes.sql");
    }

    @Test
    void migrationComprovanteDeveMaterializarFlagNoInventario() throws IOException {
        final String sql = lerSql("database/migrations/021_materializar_comprovante_inventario.sql");
        final String viewSql = lerSql("database/views/012_criar_view_fretes_powerbi.sql");

        assertContem(sql, "ADD flag_comprovante_anexado BIT NOT NULL");
        assertContem(sql, "UPDATE dbo.inventario");
        assertContem(sql, "COLLATE Latin1_General_CI_AI LIKE N'%Comprovante%Entrega%Anexado%'");
        assertContem(sql, "WHERE flag_comprovante_anexado = 1");
        assertFalse(sql.contains("CREATE OR ALTER VIEW dbo.vw_fretes_powerbi"));
        assertContem(viewSql, "inv.flag_comprovante_anexado = 1");
        assertFalse(viewSql.contains("inv.ultima_ocorrencia_descricao COLLATE Latin1_General_CI_AI LIKE"));
        assertSemMojibake(sql, "database/migrations/021_materializar_comprovante_inventario.sql");
    }

    @Test
    void manifestosPowerBiDeveExporLocalDeDescarregamentoComNomeDoNegocio() throws IOException {
        final String sql = lerSql("database/views/018_criar_view_manifestos_powerbi.sql");

        assertContem(sql, "[Filial Emissora]");
        assertContem(sql, "[Local de Descarregamento]");
        assertContem(sql, "FROM dbo.fato_gestao_vista_manifestos");
        assertFalse(sql.contains("OPENJSON"));
        assertSemMojibake(sql, "database/views/018_criar_view_manifestos_powerbi.sql");
    }

    @Test
    void fatoManifestosDashDeveConsumirTabelaMaterializadaSemAgregacaoSobDemanda() throws IOException {
        final String tabelaSql = lerSql("database/tabelas/032_criar_tabela_fato_gestao_vista_manifestos.sql");
        final String migrationSql = lerSql("database/migrations/042_criar_fato_gestao_vista_manifestos.sql");
        final String indicesSql = lerSql("database/indices/002_criar_indices_fato_gestao_vista_manifestos.sql");
        final String procedureSql = lerSql("database/procedures/005_criar_sp_carga_fato_gestao_vista_manifestos.sql");
        final String viewSql = lerSql("database/views/025_criar_view_fato_manifestos_dash.sql");

        assertContem(tabelaSql, "CREATE TABLE dbo.fato_gestao_vista_manifestos");
        assertContem(tabelaSql, "sequence_code BIGINT NOT NULL");
        assertContem(tabelaSql, "receita_total DECIMAL(18, 2) NULL");
        assertContem(tabelaSql, "capacidade_lotacao_kg DECIMAL(18, 2) NULL");
        assertContem(tabelaSql, "CONSTRAINT PK_fato_gv_manifestos PRIMARY KEY CLUSTERED (sequence_code)");
        assertContem(migrationSql, "042_criar_fato_gestao_vista_manifestos");
        assertContem(indicesSql, "IX_fato_manifestos_data_filial");
        assertContem(indicesSql, "IX_fato_manifestos_filtros");
        assertContem(procedureSql, "CREATE OR ALTER PROCEDURE dbo.sp_carga_fato_gestao_vista_manifestos");
        assertContem(procedureSql, "OPENJSON(CASE WHEN ISJSON(c.pick_items_ids) = 1 THEN c.pick_items_ids END)");
        assertContem(procedureSql, "COALESCE(MAX(ml.vehicle_weight_capacity), 0)");
        assertContem(procedureSql, "+ COALESCE(MAX(ml.trailer1_weight_capacity), 0)");
        assertContem(procedureSql, "+ COALESCE(MAX(ml.trailer2_weight_capacity), 0)");
        assertFalse(procedureSql.contains("WHEN COALESCE(MAX(ml.trailer1_weight_capacity), 0) = 0"));
        assertContem(procedureSql, "MERGE dbo.fato_gestao_vista_manifestos");
        assertContem(viewSql, "FROM dbo.fato_gestao_vista_manifestos");
        assertFalse(viewSql.contains("OPENJSON"));
        assertFalse(SELECT_STAR_PATTERN.matcher(viewSql).find());
        assertSemMojibake(tabelaSql, "database/tabelas/032_criar_tabela_fato_gestao_vista_manifestos.sql");
        assertSemMojibake(migrationSql, "database/migrations/042_criar_fato_gestao_vista_manifestos.sql");
        assertSemMojibake(procedureSql, "database/procedures/005_criar_sp_carga_fato_gestao_vista_manifestos.sql");
    }

    @Test
    void inventarioPowerBiDeveExporFilialEmissoraDoFrete() throws IOException {
        final String sql = lerSql("database/views/020_criar_view_inventario_powerbi.sql");

        assertContem(sql, "[Filial da Ordem de Conferência]");
        assertContem(sql, "[Filial Emissora do Frete]");
        assertContem(sql, "[Data de Finalização]");
        assertContem(sql, "CheckIn::Order::Return");
        assertContem(sql, "'Retorno'");
        assertContem(sql, "LEFT JOIN dbo.fretes");
    }

    @Test
    void localizacaoCargasPowerBiDeveExporResponsavelPelaRegiaoDeDestino() throws IOException {
        final String sql = lerSql("database/views/017_criar_view_localizacao_cargas_powerbi.sql");

        assertContem(sql, "[Responsável pela Região de Destino]");
    }

    @Test
    void rasterTransitTimeDeveExporDataDeExtracaoParaDashboardHorariosCorte() throws IOException {
        final String sql = lerSql("database/views/022_criar_view_raster_sm_transit_time.sql");

        assertContem(sql, "v.data_extracao AS viagem_data_extracao");
        assertContem(sql, "p.data_extracao AS parada_data_extracao");
        assertContem(sql, "END AS data_extracao_raster");
        assertContem(sql, "data_extracao_raster AS [Data de extracao]");
    }

    @Test
    void executorDatabaseDevePublicarBaselineEMigrationsNovasSemReexecutarHistorico() throws IOException {
        final String sql = lerSql("database/executar_database.bat");
        final String validacao = lerSql("database/validacao/034_validar_schema_recriacao.sql");
        final String validacaoPerformance =
                lerSql("database/validacao/042_validar_contrato_dashboard_performance.sql");

        assertContem(sql, "set \"MASTER_SQL=%TEMP%\\run_master.sql\"");
        assertContem(sql, "dir /b /a-d /on \"migrations\\*.sql\"");
        assertContem(sql, "migrations\\historico_arquivado");
        assertContem(sql, "--com-cargas");
        assertFalse(sql.contains("migrations\\016_materializar_faturamento_fretes.sql"));
        assertFalse(sql.contains("migrations\\042_criar_fato_gestao_vista_manifestos.sql"));
        assertContem(sql, "tabelas\\008_criar_tabela_dim_calendario.sql");
        assertContem(sql, "tabelas\\032_criar_tabela_fato_gestao_vista_manifestos.sql");
        assertContem(sql, "procedures\\004_criar_sp_carga_fato_gestao_vista_faturas.sql");
        assertContem(sql, "procedures\\005_criar_sp_carga_fato_gestao_vista_manifestos.sql");
        assertContem(sql, "call :MASTER_ADD_EXEC \"dbo.sp_carga_fato_gestao_vista_faturas\"");
        assertContem(sql, "call :MASTER_ADD_EXEC \"dbo.sp_carga_fato_gestao_vista_manifestos\"");
        assertContem(sql, "validacao\\041_validar_fato_gestao_vista_faturas.sql");
        assertContem(sql, "validacao\\045_validar_fato_gestao_vista_manifestos.sql");
        assertContem(sql, "validacao\\036_validar_volumes_fretes_faturamento.sql");
        assertContem(sql, "validacao\\042_validar_contrato_dashboard_performance.sql");
        assertContem(sql, "Deseja reprocessar as tabelas Fato agora");
        assertContem(sql, "Modo silencioso sem --com-cargas: cargas BI ignoradas");
        assertContem(sql, "LOCK_TIMEOUT das cargas BI: 10000 ms");
        assertContem(sql, "SET LOCK_TIMEOUT 10000;");
        assertContem(validacaoPerformance, "Responsável Região Destino Key");
        assertContem(validacaoPerformance, "THROW 53002");
        assertContem(validacao, "019_adicionar_comprovante_fretes_performance");
        assertContem(validacao, "021_materializar_comprovante_inventario");
        assertContem(validacao, "022_corrigir_volumes_fretes_faturamento");
        assertContem(validacao, "025_materializar_chave_responsavel_destino");
        assertContem(validacao, "026_materializar_chave_usuario_cotacoes");
        assertContem(validacao, "032_criar_fato_gestao_vista_faturas");
        assertContem(validacao, "033_tuning_indices_fatos");
        assertContem(validacao, "039_criar_dim_calendario_referencia_faturamento");
        assertContem(validacao, "042_criar_fato_gestao_vista_manifestos");
        assertContem(validacao, "dbo.dim_calendario");
        assertContem(validacao, "dbo.fato_gestao_vista_faturas");
        assertContem(validacao, "dbo.fato_gestao_vista_manifestos");
        assertContem(validacao, "IX_fato_gv_coletores_periodo_filial");
        assertContem(validacao, "IX_fato_manifestos_data_filial");
        assertContem(validacao, "data_emissao_cte");
        assertContem(validacao, "dbo.sp_carga_fato_gestao_vista_faturas");
        assertContem(validacao, "dbo.sp_carga_fato_gestao_vista_manifestos");
        assertContem(validacao, "dbo.vw_fretes_powerbi.[Comprovante Anexado]");
        assertContem(validacao, "dbo.vw_fretes_powerbi.[Responsável Região Destino Key]");
        assertContem(validacao, "dbo.vw_cotacoes_powerbi.[Usuario Key]");
        assertContem(validacao, "lc.invoices_volumes");
    }

    @Test
    void fatoFretesFaturamentoDeveUsarDimCalendarioParaRetroagirDataDeFaturamento() throws IOException {
        final String migrationSql = lerSql("database/migrations/039_criar_dim_calendario_referencia_faturamento.sql");
        final String tabelaCalendarioSql = lerSql("database/tabelas/008_criar_tabela_dim_calendario.sql");
        final String tabelaFatoSql = lerSql("database/tabelas/030_criar_tabela_fato_fretes_faturamento.sql");
        final String procedureSql = lerSql("database/procedures/003_criar_sp_carga_fato_fretes_faturamento.sql");
        final String validacaoSql = lerSql("database/validacao/040_validar_fato_fretes_faturamento.sql");

        assertContem(tabelaCalendarioSql, "CREATE TABLE dbo.dim_calendario");
        assertContem(tabelaCalendarioSql, "is_dia_util BIT NOT NULL");
        assertContem(tabelaCalendarioSql, "data_referencia_faturamento DATE NOT NULL");
        assertContem(tabelaCalendarioSql, "N'Confraternizacao Universal'");
        assertContem(tabelaCalendarioSql, "N'Sexta-feira Santa'");
        assertContem(migrationSql, "039_criar_dim_calendario_referencia_faturamento");
        assertContem(migrationSql, "EXEC sys.sp_executesql");
        assertContem(tabelaFatoSql, "data_referencia_faturamento_real DATETIMEOFFSET NULL");
        assertContem(tabelaFatoSql, "is_data_faturamento_retroagida BIT NOT NULL");
        assertContem(procedureSql, "INNER JOIN dbo.dim_calendario AS cal");
        assertContem(procedureSql, "cal.data_referencia_faturamento AS data_referencia_faturamento_date");
        assertContem(procedureSql, "f.data_referencia_faturamento AS data_referencia_faturamento_real");
        assertContem(procedureSql, "source.is_data_faturamento_retroagida");
        assertContem(validacaoSql, "RETROACAO_FATURAMENTO");
        assertSemMojibake(tabelaCalendarioSql, "database/tabelas/008_criar_tabela_dim_calendario.sql");
        assertSemMojibake(migrationSql, "database/migrations/039_criar_dim_calendario_referencia_faturamento.sql");
        assertSemMojibake(procedureSql, "database/procedures/003_criar_sp_carga_fato_fretes_faturamento.sql");
    }

    @Test
    void faturamentoFrigelarDeveTerRegraDeAtribuicaoParaCwbNoBaselineEMigration() throws IOException {
        final String tabelaRegrasSql = lerSql("database/tabelas/033_criar_tabela_regras_atribuicao_filial.sql");
        final String migrationSql = lerSql("database/migrations/050_garantir_regra_frigelar_cwb.sql");
        final String procedureSql = lerSql("database/procedures/003_criar_sp_carga_fato_fretes_faturamento.sql");
        final String validacaoSql = lerSql("database/validacao/034_validar_schema_recriacao.sql");

        for (String sql : new String[] {tabelaRegrasSql, migrationSql}) {
            assertContem(sql, "N'92660406007040'");
            assertContem(sql, "N'CWB - RODOGARCIA'");
            assertContem(sql, "N'cwb - rodogarcia'");
        }
        assertContem(migrationSql, "050_garantir_regra_frigelar_cwb");
        assertContem(procedureSql, "COALESCE(regra.filial_destino_nome, NULLIF(LTRIM(RTRIM(f.filial_nome)), N'')) AS filial_nome");
        assertContem(procedureSql, "COALESCE(regra.filial_destino_key, f.filial_nome_key");
        assertContem(validacaoSql, "050_garantir_regra_frigelar_cwb");
        assertSemMojibake(tabelaRegrasSql, "database/tabelas/033_criar_tabela_regras_atribuicao_filial.sql");
        assertSemMojibake(migrationSql, "database/migrations/050_garantir_regra_frigelar_cwb.sql");
    }

    @Test
    void expurgoNoturnoDeveMaterializarFatosSemReexecutarInstaladorDoBanco() throws IOException {
        final String script = lerSql("scripts/windows/10-expurgo-orfaos-noturno.ps1");

        assertFalse(script.contains("database\\executar_database.bat"));
        assertContem(script, "Responsável Região Destino Key");
        assertContem(script, "EXEC dbo.sp_carga_fato_gestao_vista_fretes;");
        assertContem(script, "EXEC dbo.sp_carga_fato_gestao_vista_coletores;");
        assertContem(script, "EXEC dbo.sp_carga_fato_fretes_faturamento;");
        assertContem(script, "EXEC dbo.sp_carga_fato_gestao_vista_faturas;");
        assertContem(script, "EXEC dbo.sp_carga_fato_gestao_vista_manifestos;");
    }

    @Test
    void migrationResponsavelDestinoDeveMaterializarChaveEIndices() throws IOException {
        final String migrationSql = lerSql("database/migrations/025_materializar_chave_responsavel_destino.sql");
        final String tabelaFretesSql = lerSql("database/tabelas/002_criar_tabela_fretes.sql");
        final String tabelaLocalizacaoSql = lerSql("database/tabelas/005_criar_tabela_localizacao_cargas.sql");
        final String indicesSql = lerSql("database/indices/001_criar_indices_performance.sql");

        assertContem(tabelaFretesSql, "filial_nome_key AS NULLIF(LOWER(LTRIM(RTRIM(filial_nome))), N'') PERSISTED");
        assertContem(tabelaLocalizacaoSql, "destination_branch_key AS NULLIF(LOWER(LTRIM(RTRIM(destination_branch_nickname))), N'') PERSISTED");
        assertContem(migrationSql, "IX_fretes_faturamento_responsavel_key");
        assertContem(migrationSql, "IX_localizacao_destination_branch_key");
        assertContem(migrationSql, "views\\012_criar_view_fretes_powerbi.sql");
        assertContem(indicesSql, "IX_fretes_faturamento_responsavel_key");
        assertContem(indicesSql, "IX_localizacao_destination_branch_key");
        assertSemMojibake(migrationSql, "database/migrations/025_materializar_chave_responsavel_destino.sql");
    }

    @Test
    void cotacoesPowerBiDeveMaterializarChaveUsuarioEmissor() throws IOException {
        final String viewSql = lerSql("database/views/015_criar_view_cotacoes_powerbi.sql");
        final String tabelaSql = lerSql("database/tabelas/004_criar_tabela_cotacoes.sql");
        final String migrationSql = lerSql("database/migrations/026_materializar_chave_usuario_cotacoes.sql");
        final String indicesSql = lerSql("database/indices/001_criar_indices_performance.sql");

        assertContem(viewSql, "user_name                                       AS [Usuário]");
        assertContem(viewSql, "user_name_key                                   AS [Usuario Key]");
        assertContem(tabelaSql, "user_name_key AS NULLIF(LOWER(LTRIM(RTRIM(user_name))), N'') PERSISTED");
        assertContem(migrationSql, "ADD user_name_key AS NULLIF(LOWER(LTRIM(RTRIM(user_name))), N'') PERSISTED");
        assertContem(migrationSql, "IX_cotacoes_usuario_key_requested_at");
        assertContem(migrationSql, "views\\015_criar_view_cotacoes_powerbi.sql");
        assertContem(indicesSql, "IX_cotacoes_usuario_key_requested_at");
        assertSemMojibake(viewSql, "database/views/015_criar_view_cotacoes_powerbi.sql");
        assertSemMojibake(migrationSql, "database/migrations/026_materializar_chave_usuario_cotacoes.sql");
    }

    @Test
    void viewsCriticasDeIntegracaoNaoPodemUsarSelectStar() throws IOException {
        for (final String caminho : List.of(
            "database/views/012_criar_view_fretes_powerbi.sql",
            "database/views/015_criar_view_cotacoes_powerbi.sql",
            "database/views/018_criar_view_manifestos_powerbi.sql",
            "database/views/025_criar_view_fato_manifestos_dash.sql",
            "database/views/023_publicar_wrappers_dashboard_dev_explicitos.sql"
        )) {
            final String sql = lerSql(caminho);

            assertFalse(
                SELECT_STAR_PATTERN.matcher(sql).find(),
                "View critica nao pode usar SELECT *: " + caminho
            );
        }
    }

    @Test
    void wrappersDashboardDevDevemSerPublicadosComListaExplicita() throws IOException {
        final String sql = lerSql("database/views/023_publicar_wrappers_dashboard_dev_explicitos.sql");

        assertContem(sql, "vw_fretes_powerbi");
        assertContem(sql, "vw_manifestos_powerbi");
        assertContem(sql, "vw_fato_manifestos_dash");
        assertContem(sql, "vw_cotacoes_powerbi");
        assertContem(sql, "STRING_AGG(CONVERT(NVARCHAR(MAX), QUOTENAME(c.name))");
        assertContem(sql, "CREATE OR ALTER VIEW dbo.");
        assertContem(sql, "FROM [$(EtlDb)].dbo.");
    }

    @Test
    void rasterTransitTimeDeveExporCamposConsumidosPeloDashboardSemMojibake() throws IOException {
        final String sql = lerSql("database/views/022_criar_view_raster_sm_transit_time.sql");

        assertContem(sql, "origem_sm AS [ORIGEM - SM]");
        assertContem(sql, "destino_sm AS [DESTINO - SM]");
        assertContem(sql, "origem_destino AS [Origem x Destino]");
        assertContem(sql, "origem_nome AS [ORIGEM]");
        assertContem(sql, "ordem_parada_label AS [ORDEM]");
        assertContem(sql, "destino_nome AS [DESTINO]");
        assertContem(sql, "horario_corte_texto AS [HORÁRIO CORTE]");
        assertContem(sql, "previsao_chegada_destino AS [PREV. CHEGADA (destino)]");
        assertContem(sql, "transit_time_texto AS [TRANSIT TIME]");
        assertContem(sql, "origem_sm");
        assertContem(sql, "destino_sm");
        assertContem(sql, "origem_destino");
        assertContem(sql, "horario_corte_texto");
        assertContem(sql, "previsao_chegada_destino");
        assertContem(sql, "transit_time_texto");
        assertSemMojibake(sql, "database/views/022_criar_view_raster_sm_transit_time.sql");
    }

    private String lerSql(final String caminhoRelativo) throws IOException {
        Path caminho = Path.of(caminhoRelativo);
        if (!Files.exists(caminho) && caminhoRelativo.startsWith("database/migrations/")) {
            caminho = Path.of(caminhoRelativo.replace(
                "database/migrations/",
                "database/migrations/historico_arquivado/"
            ));
        }
        return Files.readString(caminho, StandardCharsets.UTF_8);
    }

    private void assertContem(final String sql, final String trechoEsperado) {
        assertTrue(
            sql.contains(trechoEsperado),
            "Esperado encontrar o trecho '" + trechoEsperado + "' no SQL da view."
        );
    }

    private void assertSemMojibake(final String conteudo, final String nomeArquivo) {
        assertFalse(
            MOJIBAKE_PATTERN.matcher(conteudo).find(),
            "Mojibake detectado em " + nomeArquivo + ". Corrija o arquivo em UTF-8 antes de seguir."
        );
    }
}
