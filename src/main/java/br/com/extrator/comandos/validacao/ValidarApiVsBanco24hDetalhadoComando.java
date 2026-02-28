/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/validacao/ValidarApiVsBanco24hDetalhadoComando.java
Classe  : ValidarApiVsBanco24hDetalhadoComando (class)
Pacote  : br.com.extrator.comandos.validacao
Modulo  : Comando CLI (validacao)
Papel   : Implementa responsabilidade de validar api vs banco24h detalhado comando.

Conecta com:
- ClienteApiDataExport (api)
- ClienteApiGraphQL (api)
- ResultadoExtracao (api)
- Comando (comandos.base)
- ContasAPagarDataExportEntity (db.entity)
- CotacaoEntity (db.entity)
- FaturaPorClienteEntity (db.entity)
- LocalizacaoCargaEntity (db.entity)

Fluxo geral:
1) Executa validacoes de acesso, timestamps e consistencia.
2) Compara API versus banco quando aplicavel.
3) Emite resultado de qualidade para operacao.

Estrutura interna:
Metodos principais:
- JanelaExecucao(...2 args): realiza operacao relacionada a "janela execucao".
- ResultadoApiChaves(...10 args): realiza operacao relacionada a "resultado api chaves".
- ResultadoComparacao(...9 args): realiza operacao relacionada a "resultado comparacao".
- construirDetalheComparacao(...5 args): realiza operacao relacionada a "construir detalhe comparacao".
- amostraChaves(...1 args): realiza operacao relacionada a "amostra chaves".
- carregarChavesManifestos(...4 args): realiza operacao relacionada a "carregar chaves manifestos".
- carregarChavesCotacoes(...4 args): realiza operacao relacionada a "carregar chaves cotacoes".
- carregarChavesLocalizacao(...4 args): realiza operacao relacionada a "carregar chaves localizacao".
- carregarChavesContasAPagar(...4 args): realiza operacao relacionada a "carregar chaves contas apagar".
- carregarChavesFaturasPorCliente(...4 args): realiza operacao relacionada a "carregar chaves faturas por cliente".
- carregarChavesFretes(...4 args): realiza operacao relacionada a "carregar chaves fretes".
- carregarChavesColetas(...4 args): realiza operacao relacionada a "carregar chaves coletas".
- hashMetadata(...1 args): realiza operacao relacionada a "hash metadata".
- chaveManifesto(...1 args): realiza operacao relacionada a "chave manifesto".
Atributos-chave:
- log: campo de estado para "log".
- AMOSTRA_MAX: campo de estado para "amostra max".
- LIMITE_BACKFILL_FATURAS_ORFAAS: campo de estado para "limite backfill faturas orfaas".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.validacao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.db.entity.CotacaoEntity;
import br.com.extrator.db.entity.FaturaPorClienteEntity;
import br.com.extrator.db.entity.LocalizacaoCargaEntity;
import br.com.extrator.db.entity.ManifestoEntity;
import br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarDTO;
import br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarMapper;
import br.com.extrator.modelo.dataexport.cotacao.CotacaoDTO;
import br.com.extrator.modelo.dataexport.cotacao.CotacaoMapper;
import br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO;
import br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteMapper;
import br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaDTO;
import br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaMapper;
import br.com.extrator.modelo.dataexport.manifestos.ManifestoDTO;
import br.com.extrator.modelo.dataexport.manifestos.ManifestoMapper;
import br.com.extrator.modelo.graphql.coletas.ColetaMapper;
import br.com.extrator.modelo.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.modelo.graphql.faturas.CreditCustomerBillingNodeDTO;
import br.com.extrator.modelo.graphql.fretes.FreteMapper;
import br.com.extrator.modelo.graphql.fretes.FreteNodeDTO;
import br.com.extrator.runners.dataexport.services.Deduplicator;
import br.com.extrator.util.banco.GerenciadorConexao;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.mapeamento.MapperUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Validacao detalhada API x Banco para ultimas 24h:
 * compara chave por chave em cada entidade na janela da ultima extracao COMPLETA.
 */
public class ValidarApiVsBanco24hDetalhadoComando implements Comando {

    private static final LoggerConsole log = LoggerConsole.getLogger(ValidarApiVsBanco24hDetalhadoComando.class);
    private static final int AMOSTRA_MAX = 15;
    private static final int LIMITE_BACKFILL_FATURAS_ORFAAS = 2000;

    private record JanelaExecucao(LocalDateTime inicio, LocalDateTime fim) { }

    private record ResultadoApiChaves(
        int apiBruto,
        int apiUnico,
        int invalidos,
        Set<String> chaves,
        Map<String, String> hashesPorChave,
        Map<String, Set<String>> hashesAceitosPorChave,
        String detalhe,
        Set<String> chavesToleradasNoBanco
    ) { }

    private record ResultadoComparacao(
        String entidade,
        int apiBruto,
        int apiUnico,
        int invalidos,
        int banco,
        int faltantes,
        int excedentes,
        int divergenciasDados,
        String detalhe
    ) {
        boolean ok() {
            return faltantes == 0 && excedentes == 0 && divergenciasDados == 0;
        }
    }

    @Override
    public void executar(final String[] args) throws Exception {
        final boolean incluirFaturasGraphQL = !possuiFlag(args, "--sem-faturas-graphql");
        final LocalDate dataReferenciaSistema = LocalDate.now();

        final ClienteApiDataExport clienteDataExport = new ClienteApiDataExport();
        final ClienteApiGraphQL clienteGraphQL = new ClienteApiGraphQL();

        final ManifestoMapper manifestoMapper = new ManifestoMapper();
        final CotacaoMapper cotacaoMapper = new CotacaoMapper();
        final LocalizacaoCargaMapper localizacaoMapper = new LocalizacaoCargaMapper();
        final ContasAPagarMapper contasMapper = new ContasAPagarMapper();
        final FaturaPorClienteMapper faturaPorClienteMapper = new FaturaPorClienteMapper();
        final FreteMapper freteMapper = new FreteMapper();
        final ColetaMapper coletaMapper = new ColetaMapper();

        final List<ResultadoComparacao> resultados = new ArrayList<>();

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            final LocalDate dataReferencia = resolverDataReferenciaLogs(conexao, dataReferenciaSistema);
            final LocalDate dataInicio = dataReferencia.minusDays(1);
            final LocalDate dataFim = dataReferencia;

            log.console("\n" + "=".repeat(88));
            log.info("VALIDACAO DETALHADA 24H | API (POSTMAN-LIKE) x BANCO | COMPARACAO CHAVE A CHAVE");
            log.info("Periodo API: {} a {}", dataInicio, dataFim);
            log.info("Data de referencia dos logs: {}", dataReferencia);
            log.console("=".repeat(88));

            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.MANIFESTOS,
                    carregarChavesManifestos(clienteDataExport, manifestoMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );
            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.COTACOES,
                    carregarChavesCotacoes(clienteDataExport, cotacaoMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );
            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.LOCALIZACAO_CARGAS,
                    carregarChavesLocalizacao(clienteDataExport, localizacaoMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );
            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.CONTAS_A_PAGAR,
                    carregarChavesContasAPagar(clienteDataExport, contasMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );
            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.FATURAS_POR_CLIENTE,
                    carregarChavesFaturasPorCliente(clienteDataExport, faturaPorClienteMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );
            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.FRETES,
                    carregarChavesFretes(clienteGraphQL, freteMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );
            resultados.add(
                compararEntidade(
                    conexao,
                    ConstantesEntidades.COLETAS,
                    carregarChavesColetas(clienteGraphQL, coletaMapper, dataInicio, dataFim),
                    dataReferencia,
                    dataInicio,
                    dataFim
                )
            );

            if (incluirFaturasGraphQL) {
                resultados.add(
                    compararEntidade(
                        conexao,
                        ConstantesEntidades.FATURAS_GRAPHQL,
                        carregarChavesFaturasGraphQL(conexao, clienteGraphQL, dataInicio, dataFim),
                        dataReferencia,
                        dataInicio,
                        dataFim
                    )
                );
            }
        }

        int totalOk = 0;
        int totalFalhas = 0;
        for (ResultadoComparacao r : resultados) {
            if (r.ok()) {
                totalOk++;
                log.info(
                    "API_VS_BANCO_24H_DETALHADO | entidade={} | status=OK | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    r.entidade, r.apiBruto, r.apiUnico, r.invalidos, r.banco, r.faltantes, r.excedentes, r.divergenciasDados
                );
            } else {
                totalFalhas++;
                log.error(
                    "API_VS_BANCO_24H_DETALHADO | entidade={} | status=FALHA | api_bruto={} | api_unico={} | invalidos={} | banco={} | faltantes={} | excedentes={} | divergencias_dados={}",
                    r.entidade, r.apiBruto, r.apiUnico, r.invalidos, r.banco, r.faltantes, r.excedentes, r.divergenciasDados
                );
            }
            if (r.detalhe != null && !r.detalhe.isBlank()) {
                log.info("API_VS_BANCO_24H_DETALHADO | entidade={} | detalhe={}", r.entidade, r.detalhe);
            }
        }

        log.console("=".repeat(88));
        log.info("RESUMO_API_VS_BANCO_24H_DETALHADO | ok={} | falhas={}", totalOk, totalFalhas);
        log.console("=".repeat(88));

        if (totalFalhas > 0) {
            throw new RuntimeException(
                "Comparacao detalhada API x Banco 24h reprovada: " + totalFalhas + " entidade(s) com divergencia."
            );
        }
    }

    private LocalDate resolverDataReferenciaLogs(final Connection conexao,
                                                 final LocalDate dataPreferida) throws SQLException {
        if (existeLogCompleto24hNaData(conexao, dataPreferida)) {
            return dataPreferida;
        }

        final LocalDate diaAnterior = dataPreferida.minusDays(1);
        if (existeLogCompleto24hNaData(conexao, diaAnterior)) {
            log.warn(
                "Sem log COMPLETO 24h para {}. Usando dia anterior {} como referencia.",
                dataPreferida,
                diaAnterior
            );
            return diaAnterior;
        }

        if (existeLogCompletoNaData(conexao, dataPreferida)) {
            log.warn(
                "Sem log COMPLETO 24h para {}. Usando logs COMPLETO do proprio dia (sem filtro de periodo).",
                dataPreferida
            );
            return dataPreferida;
        }

        if (existeLogCompletoNaData(conexao, diaAnterior)) {
            log.warn(
                "Sem log COMPLETO 24h para {}. Usando logs COMPLETO do dia anterior {} (sem filtro de periodo).",
                dataPreferida,
                diaAnterior
            );
            return diaAnterior;
        }

        final Optional<LocalDate> ultimaData = buscarUltimaDataComLogCompleto(conexao);
        if (ultimaData.isPresent()) {
            log.warn(
                "Sem log COMPLETO em {} ou {}. Usando ultima data disponivel {}.",
                dataPreferida,
                diaAnterior,
                ultimaData.get()
            );
            return ultimaData.get();
        }

        log.warn("Nenhum log COMPLETO encontrado. Mantendo data de referencia {}.", dataPreferida);
        return dataPreferida;
    }

    private boolean existeLogCompleto24hNaData(final Connection conexao, final LocalDate data) throws SQLException {
        final LocalDate dataInicio = data.minusDays(1);
        final String sql = """
            SELECT TOP 1 1
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND mensagem LIKE ?
              AND mensagem LIKE ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            stmt.setString(2, "%" + dataInicio + "%");
            stmt.setString(3, "%" + data + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean existeLogCompletoNaData(final Connection conexao, final LocalDate data) throws SQLException {
        final String sql = """
            SELECT TOP 1 1
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setDate(1, java.sql.Date.valueOf(data));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private Optional<LocalDate> buscarUltimaDataComLogCompleto(final Connection conexao) throws SQLException {
        final String sql = """
            SELECT TOP 1 CAST(timestamp_inicio AS DATE) AS data_ref
            FROM dbo.log_extracoes
            WHERE status_final = 'COMPLETO'
            ORDER BY timestamp_fim DESC
            """;
        try (PreparedStatement stmt = conexao.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return Optional.of(rs.getDate("data_ref").toLocalDate());
            }
        }
        return Optional.empty();
    }

    private ResultadoComparacao compararEntidade(final Connection conexao,
                                                 final String entidade,
                                                 final ResultadoApiChaves api,
                                                 final LocalDate dataReferencia,
                                                 final LocalDate periodoInicio,
                                                 final LocalDate periodoFim) throws SQLException {
        final Optional<JanelaExecucao> janelaOpt =
            buscarUltimaJanelaCompletaDoDia(conexao, entidade, dataReferencia, periodoInicio, periodoFim);
        if (janelaOpt.isEmpty()) {
            final int faltantes = api.apiUnico;
            return new ResultadoComparacao(
                entidade,
                api.apiBruto,
                api.apiUnico,
                api.invalidos,
                0,
                faltantes,
                0,
                0,
                "Sem log COMPLETO no dia para comparar."
            );
        }

        final JanelaExecucao janela = janelaOpt.get();
        final Set<String> chavesBanco = carregarChavesBancoNaJanela(conexao, entidade, janela);
        final Map<String, String> hashesBanco = carregarHashesMetadataBancoNaJanela(conexao, entidade, janela);

        final Set<String> faltantes = new HashSet<>(api.chaves);
        faltantes.removeAll(chavesBanco);

        final Set<String> excedentes = new HashSet<>(chavesBanco);
        excedentes.removeAll(api.chaves);

        int excedentesTolerados = 0;
        if (ConstantesEntidades.FATURAS_GRAPHQL.equals(entidade)
            && !excedentes.isEmpty()
            && api.chavesToleradasNoBanco != null
            && !api.chavesToleradasNoBanco.isEmpty()) {
            final int antes = excedentes.size();
            excedentes.removeIf(api.chavesToleradasNoBanco::contains);
            excedentesTolerados = antes - excedentes.size();
        }

        final Set<String> chavesComparaveis = new HashSet<>(api.chaves);
        chavesComparaveis.retainAll(chavesBanco);
        final Set<String> divergenciasDados = new HashSet<>();
        for (final String chave : chavesComparaveis) {
            final String hashApi = api.hashesPorChave.get(chave);
            final String hashBanco = hashesBanco.get(chave);
            final Set<String> hashesAceitos = api.hashesAceitosPorChave == null
                ? null
                : api.hashesAceitosPorChave.get(chave);
            final boolean hashCompativel;
            if (hashBanco == null) {
                hashCompativel = false;
            } else if (hashesAceitos != null && !hashesAceitos.isEmpty()) {
                hashCompativel = hashesAceitos.contains(hashBanco);
            } else {
                hashCompativel = hashApi != null && hashApi.equals(hashBanco);
            }
            if (!hashCompativel) {
                divergenciasDados.add(chave);
            }
        }

        final String detalhe = construirDetalheComparacao(janela, faltantes, excedentes, divergenciasDados, api.detalhe);
        final String detalheFinal = excedentesTolerados > 0
            ? detalhe + " | excedentes_tolerados_referenciais=" + excedentesTolerados
            : detalhe;
        return new ResultadoComparacao(
            entidade,
            api.apiBruto,
            api.apiUnico,
            api.invalidos,
            chavesBanco.size(),
            faltantes.size(),
            excedentes.size(),
            divergenciasDados.size(),
            detalheFinal
        );
    }

    private String construirDetalheComparacao(final JanelaExecucao janela,
                                              final Set<String> faltantes,
                                              final Set<String> excedentes,
                                              final Set<String> divergenciasDados,
                                              final String detalheApi) {
        final StringBuilder sb = new StringBuilder();
        sb.append("janela=[").append(janela.inicio).append(" .. ").append(janela.fim).append("]");
        if (detalheApi != null && !detalheApi.isBlank()) {
            sb.append(" | ").append(detalheApi);
        }
        if (!faltantes.isEmpty()) {
            sb.append(" | amostra_faltantes=").append(amostraChaves(faltantes));
        }
        if (!excedentes.isEmpty()) {
            sb.append(" | amostra_excedentes=").append(amostraChaves(excedentes));
        }
        if (!divergenciasDados.isEmpty()) {
            sb.append(" | amostra_divergencias_dados=").append(amostraChaves(divergenciasDados));
        }
        return sb.toString();
    }

    private String amostraChaves(final Set<String> chaves) {
        return chaves.stream()
            .sorted()
            .limit(AMOSTRA_MAX)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private ResultadoApiChaves carregarChavesManifestos(final ClienteApiDataExport clienteApi,
                                                        final ManifestoMapper mapper,
                                                        final LocalDate dataInicio,
                                                        final LocalDate dataFim) {
        final ResultadoExtracao<ManifestoDTO> resultado = clienteApi.buscarManifestos(dataInicio, dataFim);
        final List<ManifestoDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final List<ManifestoEntity> mapeadas = new ArrayList<>();
        for (ManifestoDTO dto : dtos) {
            try {
                final ManifestoEntity entity = mapper.toEntity(dto);
                if (entity == null || entity.getSequenceCode() == null) {
                    invalidos++;
                    continue;
                }
                mapeadas.add(entity);
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final List<ManifestoEntity> deduplicadas = Deduplicator.deduplicarManifestos(mapeadas);
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (final ManifestoEntity e : deduplicadas) {
            final String chave = chaveManifesto(e);
            hashesPorChave.put(chave, hashMetadata(e.getMetadata()));
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        return new ResultadoApiChaves(bruto, chaves.size(), invalidos, chaves, hashesPorChave, Map.of(), null, Set.of());
    }

    private ResultadoApiChaves carregarChavesCotacoes(final ClienteApiDataExport clienteApi,
                                                      final CotacaoMapper mapper,
                                                      final LocalDate dataInicio,
                                                      final LocalDate dataFim) {
        final ResultadoExtracao<CotacaoDTO> resultado = clienteApi.buscarCotacoes(dataInicio, dataFim);
        final List<CotacaoDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final List<CotacaoEntity> mapeadas = new ArrayList<>();
        for (CotacaoDTO dto : dtos) {
            try {
                final CotacaoEntity entity = mapper.toEntity(dto);
                if (entity == null || entity.getSequenceCode() == null) {
                    invalidos++;
                    continue;
                }
                mapeadas.add(entity);
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final List<CotacaoEntity> deduplicadas = Deduplicator.deduplicarCotacoes(mapeadas);
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (final CotacaoEntity e : deduplicadas) {
            final String chave = String.valueOf(e.getSequenceCode());
            hashesPorChave.put(chave, hashMetadata(e.getMetadata()));
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        return new ResultadoApiChaves(bruto, chaves.size(), invalidos, chaves, hashesPorChave, Map.of(), null, Set.of());
    }

    private ResultadoApiChaves carregarChavesLocalizacao(final ClienteApiDataExport clienteApi,
                                                         final LocalizacaoCargaMapper mapper,
                                                         final LocalDate dataInicio,
                                                         final LocalDate dataFim) {
        final ResultadoExtracao<LocalizacaoCargaDTO> resultado = clienteApi.buscarLocalizacaoCarga(dataInicio, dataFim);
        final List<LocalizacaoCargaDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final List<LocalizacaoCargaEntity> mapeadas = new ArrayList<>();
        for (LocalizacaoCargaDTO dto : dtos) {
            try {
                final LocalizacaoCargaEntity entity = mapper.toEntity(dto);
                if (entity == null || entity.getSequenceNumber() == null) {
                    invalidos++;
                    continue;
                }
                mapeadas.add(entity);
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final List<LocalizacaoCargaEntity> deduplicadas = Deduplicator.deduplicarLocalizacoes(mapeadas);
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (final LocalizacaoCargaEntity e : deduplicadas) {
            final String chave = String.valueOf(e.getSequenceNumber());
            hashesPorChave.put(chave, hashMetadata(e.getMetadata()));
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        return new ResultadoApiChaves(bruto, chaves.size(), invalidos, chaves, hashesPorChave, Map.of(), null, Set.of());
    }

    private ResultadoApiChaves carregarChavesContasAPagar(final ClienteApiDataExport clienteApi,
                                                          final ContasAPagarMapper mapper,
                                                          final LocalDate dataInicio,
                                                          final LocalDate dataFim) {
        final ResultadoExtracao<ContasAPagarDTO> resultado = clienteApi.buscarContasAPagar(dataInicio, dataFim);
        final List<ContasAPagarDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final List<ContasAPagarDataExportEntity> mapeadas = new ArrayList<>();
        for (ContasAPagarDTO dto : dtos) {
            try {
                final ContasAPagarDataExportEntity entity = mapper.toEntity(dto);
                if (entity == null || entity.getSequenceCode() == null) {
                    invalidos++;
                    continue;
                }
                mapeadas.add(entity);
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final List<ContasAPagarDataExportEntity> deduplicadas = Deduplicator.deduplicarFaturasAPagar(mapeadas);
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (final ContasAPagarDataExportEntity e : deduplicadas) {
            final String chave = String.valueOf(e.getSequenceCode());
            hashesPorChave.put(chave, hashMetadata(e.getMetadata()));
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        return new ResultadoApiChaves(bruto, chaves.size(), invalidos, chaves, hashesPorChave, Map.of(), null, Set.of());
    }

    private ResultadoApiChaves carregarChavesFaturasPorCliente(final ClienteApiDataExport clienteApi,
                                                               final FaturaPorClienteMapper mapper,
                                                               final LocalDate dataInicio,
                                                               final LocalDate dataFim) {
        final ResultadoExtracao<FaturaPorClienteDTO> resultado = clienteApi.buscarFaturasPorCliente(dataInicio, dataFim);
        final List<FaturaPorClienteDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final List<FaturaPorClienteEntity> mapeadas = new ArrayList<>();
        final Map<String, Set<String>> hashesAceitosPorChave = new LinkedHashMap<>();
        for (FaturaPorClienteDTO dto : dtos) {
            try {
                final FaturaPorClienteEntity entity = mapper.toEntity(dto);
                if (entity == null || entity.getUniqueId() == null || entity.getUniqueId().isBlank()) {
                    invalidos++;
                    continue;
                }
                mapeadas.add(entity);
                final String hash = hashMetadata(entity.getMetadata());
                hashesAceitosPorChave
                    .computeIfAbsent(entity.getUniqueId(), k -> new HashSet<>())
                    .add(hash);
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final List<FaturaPorClienteEntity> deduplicadas = Deduplicator.deduplicarFaturasPorCliente(mapeadas);
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (final FaturaPorClienteEntity e : deduplicadas) {
            final String chave = e.getUniqueId();
            hashesPorChave.put(chave, hashMetadata(e.getMetadata()));
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        int chavesComHashesConflitantes = 0;
        for (final Set<String> hashes : hashesAceitosPorChave.values()) {
            if (hashes != null && hashes.size() > 1) {
                chavesComHashesConflitantes++;
            }
        }
        final String detalhe = chavesComHashesConflitantes > 0
            ? "chaves_com_hashes_conflitantes=" + chavesComHashesConflitantes
            : null;
        return new ResultadoApiChaves(
            bruto,
            chaves.size(),
            invalidos,
            chaves,
            hashesPorChave,
            hashesAceitosPorChave,
            detalhe,
            Set.of()
        );
    }

    private ResultadoApiChaves carregarChavesFretes(final ClienteApiGraphQL clienteApi,
                                                    final FreteMapper mapper,
                                                    final LocalDate dataInicio,
                                                    final LocalDate dataFim) {
        final ResultadoExtracao<FreteNodeDTO> resultado = clienteApi.buscarFretes(dataInicio, dataFim);
        final List<FreteNodeDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (FreteNodeDTO dto : dtos) {
            try {
                final var entity = mapper.toEntity(dto);
                if (entity == null || entity.getId() == null) {
                    invalidos++;
                    continue;
                }
                final String chave = String.valueOf(entity.getId());
                hashesPorChave.put(chave, hashMetadata(entity.getMetadata())); // keep last
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        return new ResultadoApiChaves(bruto, chaves.size(), invalidos, chaves, hashesPorChave, Map.of(), null, Set.of());
    }

    private ResultadoApiChaves carregarChavesColetas(final ClienteApiGraphQL clienteApi,
                                                     final ColetaMapper mapper,
                                                     final LocalDate dataInicio,
                                                     final LocalDate dataFim) {
        final ResultadoExtracao<ColetaNodeDTO> resultado = clienteApi.buscarColetas(dataInicio, dataFim);
        final List<ColetaNodeDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (ColetaNodeDTO dto : dtos) {
            try {
                final var entity = mapper.toEntity(dto);
                if (entity == null || entity.getId() == null || entity.getId().isBlank()) {
                    invalidos++;
                    continue;
                }
                hashesPorChave.put(entity.getId(), hashMetadata(entity.getMetadata())); // keep last
            } catch (RuntimeException e) {
                invalidos++;
            }
        }
        final Set<String> chaves = new HashSet<>(hashesPorChave.keySet());
        return new ResultadoApiChaves(bruto, chaves.size(), invalidos, chaves, hashesPorChave, Map.of(), null, Set.of());
    }

    private ResultadoApiChaves carregarChavesFaturasGraphQL(final Connection conexao,
                                                            final ClienteApiGraphQL clienteApi,
                                                            final LocalDate dataInicio,
                                                            final LocalDate dataFim) throws SQLException {
        final ResultadoExtracao<CreditCustomerBillingNodeDTO> resultado = clienteApi.buscarCapaFaturas(dataInicio, dataFim);
        final List<CreditCustomerBillingNodeDTO> dtos = resultado.getDados() != null ? resultado.getDados() : List.of();
        final int bruto = dtos.size();
        int invalidos = 0;

        final Map<Long, CreditCustomerBillingNodeDTO> porId = new LinkedHashMap<>();
        for (CreditCustomerBillingNodeDTO dto : dtos) {
            if (dto == null || dto.getId() == null) {
                invalidos++;
                continue;
            }
            porId.put(dto.getId(), dto); // keep last
        }

        // IDs de fretes na janela podem existir no banco por enriquecimento referencial/backfill.
        // Se aparecerem como excedente no banco em relacao a capa da API, sao tolerados.
        final List<Long> idsFretesJanela = listarAccountingCreditIdsFretes(conexao, dataInicio, dataFim, LIMITE_BACKFILL_FATURAS_ORFAAS);

        final Set<String> chaves = porId.keySet().stream()
            .map(String::valueOf)
            .collect(Collectors.toSet());
        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        for (final Map.Entry<Long, CreditCustomerBillingNodeDTO> entry : porId.entrySet()) {
            final String chave = String.valueOf(entry.getKey());
            hashesPorChave.put(chave, hashMetadata(MapperUtil.toJson(entry.getValue())));
        }
        final Set<String> chavesToleradasNoBanco = idsFretesJanela.stream()
            .map(String::valueOf)
            .collect(Collectors.toSet());

        final String detalhe = "ids_fretes_janela=" + idsFretesJanela.size()
            + " | tolerancia_excedentes_referenciais_ativa=true";

        return new ResultadoApiChaves(
            bruto,
            chaves.size(),
            invalidos,
            chaves,
            hashesPorChave,
            Map.of(),
            detalhe,
            chavesToleradasNoBanco
        );
    }

    private List<Long> listarAccountingCreditIdsFretes(final Connection conexao,
                                                       final LocalDate dataInicio,
                                                       final LocalDate dataFim,
                                                       final int limite) throws SQLException {
        final String sql = """
            SELECT DISTINCT TOP (?) CAST(f.accounting_credit_id AS BIGINT) AS accounting_credit_id
            FROM dbo.fretes f
            WHERE f.accounting_credit_id IS NOT NULL
              AND CAST(f.data_extracao AS DATE) BETWEEN ? AND ?
            ORDER BY CAST(f.accounting_credit_id AS BIGINT)
            """;

        final List<Long> ids = new ArrayList<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setInt(1, limite);
            stmt.setDate(2, java.sql.Date.valueOf(dataInicio));
            stmt.setDate(3, java.sql.Date.valueOf(dataFim));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final long id = rs.getLong("accounting_credit_id");
                    if (!rs.wasNull()) {
                        ids.add(id);
                    }
                }
            }
        }
        return ids;
    }

    private Optional<JanelaExecucao> buscarUltimaJanelaCompletaDoDia(final Connection conexao,
                                                                      final String entidade,
                                                                      final LocalDate dataReferencia,
                                                                      final LocalDate periodoInicio,
                                                                      final LocalDate periodoFim) throws SQLException {
        final String sqlComPeriodo = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
              AND mensagem LIKE ?
              AND mensagem LIKE ?
            ORDER BY timestamp_fim DESC
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sqlComPeriodo)) {
            stmt.setString(1, entidade);
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
            stmt.setString(3, "%" + periodoInicio + "%");
            stmt.setString(4, "%" + periodoFim + "%");
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    return Optional.of(new JanelaExecucao(inicio, fim));
                }
            }
        }

        final String sqlFallback = """
            SELECT TOP 1 timestamp_inicio, timestamp_fim
            FROM dbo.log_extracoes
            WHERE entidade = ?
              AND status_final = 'COMPLETO'
              AND CAST(timestamp_inicio AS DATE) = ?
            ORDER BY timestamp_fim DESC
            """;

        try (PreparedStatement stmt = conexao.prepareStatement(sqlFallback)) {
            stmt.setString(1, entidade);
            stmt.setDate(2, java.sql.Date.valueOf(dataReferencia));
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    final LocalDateTime inicio = rs.getTimestamp("timestamp_inicio").toLocalDateTime();
                    final LocalDateTime fim = rs.getTimestamp("timestamp_fim").toLocalDateTime();
                    return Optional.of(new JanelaExecucao(inicio, fim));
                }
            }
        }

        return Optional.empty();
    }

    private Set<String> carregarChavesBancoNaJanela(final Connection conexao,
                                                    final String entidade,
                                                    final JanelaExecucao janela) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT CONCAT(
                    CAST(sequence_code AS VARCHAR(50)),
                    '|',
                    COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), '-1'),
                    '|',
                    COALESCE(CAST(mdfe_number AS VARCHAR(50)), '-1')
                ) AS chave
                FROM dbo.manifestos
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave
                FROM dbo.cotacoes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave
                FROM dbo.localizacao_cargas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_number IS NOT NULL
                """;
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave
                FROM dbo.contas_a_pagar
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave
                FROM dbo.faturas_por_cliente
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND unique_id IS NOT NULL
                """;
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave
                FROM dbo.fretes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave
                FROM dbo.coletas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave
                FROM dbo.faturas_graphql
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Set<String> chaves = new HashSet<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = rs.getString("chave");
                    if (chave != null && !chave.isBlank()) {
                        chaves.add(chave.trim());
                    }
                }
            }
        }
        return chaves;
    }

    private Map<String, String> carregarHashesMetadataBancoNaJanela(final Connection conexao,
                                                                    final String entidade,
                                                                    final JanelaExecucao janela) throws SQLException {
        final String sql = switch (entidade) {
            case ConstantesEntidades.MANIFESTOS ->
                """
                SELECT CONCAT(
                    CAST(sequence_code AS VARCHAR(50)),
                    '|',
                    COALESCE(CAST(pick_sequence_code AS VARCHAR(50)), '-1'),
                    '|',
                    COALESCE(CAST(mdfe_number AS VARCHAR(50)), '-1')
                ) AS chave,
                metadata
                FROM dbo.manifestos
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.COTACOES ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.cotacoes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.LOCALIZACAO_CARGAS ->
                """
                SELECT CAST(sequence_number AS VARCHAR(50)) AS chave, metadata
                FROM dbo.localizacao_cargas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_number IS NOT NULL
                """;
            case ConstantesEntidades.CONTAS_A_PAGAR ->
                """
                SELECT CAST(sequence_code AS VARCHAR(50)) AS chave, metadata
                FROM dbo.contas_a_pagar
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND sequence_code IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_POR_CLIENTE ->
                """
                SELECT unique_id AS chave, metadata
                FROM dbo.faturas_por_cliente
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND unique_id IS NOT NULL
                """;
            case ConstantesEntidades.FRETES ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.fretes
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.COLETAS ->
                """
                SELECT id AS chave, metadata
                FROM dbo.coletas
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            case ConstantesEntidades.FATURAS_GRAPHQL ->
                """
                SELECT CAST(id AS VARCHAR(50)) AS chave, metadata
                FROM dbo.faturas_graphql
                WHERE data_extracao >= ? AND data_extracao <= ?
                  AND id IS NOT NULL
                """;
            default -> throw new IllegalArgumentException("Entidade nao suportada na comparacao detalhada: " + entidade);
        };

        final Map<String, String> hashesPorChave = new LinkedHashMap<>();
        try (PreparedStatement stmt = conexao.prepareStatement(sql)) {
            stmt.setTimestamp(1, Timestamp.valueOf(janela.inicio));
            stmt.setTimestamp(2, Timestamp.valueOf(janela.fim));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    final String chave = rs.getString("chave");
                    if (chave == null || chave.isBlank()) {
                        continue;
                    }
                    final String metadata = rs.getString("metadata");
                    hashesPorChave.put(chave.trim(), hashMetadata(metadata));
                }
            }
        }
        return hashesPorChave;
    }

    private String hashMetadata(final String metadata) {
        final String normalizado = metadata == null ? "__NULL__" : metadata.trim();
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] hash = digest.digest(normalizado.getBytes(StandardCharsets.UTF_8));
            final StringBuilder sb = new StringBuilder(hash.length * 2);
            for (final byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 nao disponivel", e);
        }
    }

    private String chaveManifesto(final ManifestoEntity e) {
        final long pick = e.getPickSequenceCode() != null ? e.getPickSequenceCode() : -1L;
        final int mdfe = e.getMdfeNumber() != null ? e.getMdfeNumber() : -1;
        return e.getSequenceCode() + "|" + pick + "|" + mdfe;
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }
}
