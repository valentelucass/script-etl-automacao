package br.com.extrator.runners;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.db.entity.ContasAPagarDataExportEntity;
import br.com.extrator.db.entity.CotacaoEntity;
import br.com.extrator.db.entity.FaturaPorClienteEntity;
import br.com.extrator.db.entity.LocalizacaoCargaEntity;
import br.com.extrator.db.entity.LogExtracaoEntity;
import br.com.extrator.db.entity.ManifestoEntity;
import br.com.extrator.db.repository.ContasAPagarRepository;
import br.com.extrator.db.repository.CotacaoRepository;
import br.com.extrator.db.repository.FaturaPorClienteRepository;
import br.com.extrator.db.repository.LocalizacaoCargaRepository;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.db.repository.ManifestoRepository;
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

/**
 * Runner independente para a API Data Export (Manifestos, Cotações e Localização de Carga).
 */
public final class DataExportRunner {
    private static final Logger logger = LoggerFactory.getLogger(DataExportRunner.class);

    private DataExportRunner() {}

    public static void executar(final LocalDate dataInicio) throws Exception {
        System.out.println("🔄 Executando runner DataExport...");

        br.com.extrator.util.CarregadorConfig.validarConexaoBancoDados();

        final ClienteApiDataExport clienteApiDataExport = new ClienteApiDataExport();
        clienteApiDataExport.setExecutionUuid(java.util.UUID.randomUUID().toString());
        final ManifestoRepository manifestoRepository = new ManifestoRepository();
        final CotacaoRepository cotacaoRepository = new CotacaoRepository();
        final LocalizacaoCargaRepository localizacaoRepository = new LocalizacaoCargaRepository();
        final LogExtracaoRepository logExtracaoRepository = new LogExtracaoRepository();
        final ContasAPagarRepository contasAPagarRepository = new ContasAPagarRepository();
        final FaturaPorClienteRepository faturasPorClienteRepository = new FaturaPorClienteRepository();

        final ManifestoMapper manifestoMapper = new ManifestoMapper();
        final CotacaoMapper cotacaoMapper = new CotacaoMapper();
        final LocalizacaoCargaMapper localizacaoMapper = new LocalizacaoCargaMapper();
        final ContasAPagarMapper contasAPagarMapper = new ContasAPagarMapper();
        final FaturaPorClienteMapper faturasPorClienteMapper = new FaturaPorClienteMapper();

        // Garante que a tabela log_extracoes existe
        logExtracaoRepository.criarTabelaSeNaoExistir();

        // Manifestos
        System.out.println("\n🧾 Extraindo Manifestos (últimas 24h)...");
        final LocalDateTime inicioManifestos = LocalDateTime.now();
        try {
            final ResultadoExtracao<ManifestoDTO> resultadoManifestos = clienteApiDataExport.buscarManifestos();
            final List<ManifestoDTO> manifestosDTO = resultadoManifestos.getDados();
            System.out.println("✓ Extraídos: " + manifestosDTO.size() + " manifestos" + 
                              (resultadoManifestos.isCompleto() ? "" : " (INCOMPLETO: " + resultadoManifestos.getMotivoInterrupcao() + ")"));
            int registrosSalvos = 0;
            
            int totalUnicos = 0;
            final int registrosExtraidos = resultadoManifestos.getRegistrosExtraidos();
            if (!manifestosDTO.isEmpty()) {
                final List<ManifestoEntity> manifestosEntities = manifestosDTO.stream()
                    .map(manifestoMapper::toEntity)
                    .collect(Collectors.toList());
                
                // Deduplicar registros (proteção contra duplicados da API)
                final List<ManifestoEntity> manifestosUnicos = deduplicarManifestos(manifestosEntities);
                totalUnicos = manifestosUnicos.size();
                
                // Log se houver duplicados removidos
                if (manifestosEntities.size() != manifestosUnicos.size()) {
                    final int duplicadosRemovidos = manifestosEntities.size() - manifestosUnicos.size();
                    System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                    logger.warn("🔄 API retornou {} duplicados para manifestos. Removidos antes de salvar. Total único: {}", 
                        duplicadosRemovidos, manifestosUnicos.size());
                }
                
                registrosSalvos = manifestoRepository.salvar(manifestosUnicos);
                System.out.println("✓ Processados: " + registrosSalvos + "/" + totalUnicos + " manifestos (INSERTs + UPDATEs)");
            }
            
            // Registrar no log (status enum + quantidade extraída)
            // NOTA: "salvos" = operações bem-sucedidas (INSERTs + UPDATEs)
            // UPDATEs não adicionam novas linhas, apenas atualizam existentes
            // registrosExtraidos = quantidade retornada pela API (pode incluir duplicados)
            // totalUnicos = quantidade após deduplicação (registros únicos)
            // registrosSalvos = quantidade processada com sucesso (INSERTs + UPDATEs)
            final LogExtracaoEntity.StatusExtracao statusFinal = 
                resultadoManifestos.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;

            final String mensagem = resultadoManifestos.isCompleto() ?
                ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                ("Extração incompleta (" + resultadoManifestos.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");

            final LogExtracaoEntity logManifestos = new LogExtracaoEntity(
                "manifestos",
                inicioManifestos,
                LocalDateTime.now(),
                statusFinal,
                totalUnicos, // ← CORRIGIDO: usar totalUnicos (após deduplicação) em vez de registrosExtraidos (bruto da API)
                resultadoManifestos.getPaginasProcessadas(),
                mensagem
            );
            logExtracaoRepository.gravarLogExtracao(logManifestos);

        } catch (RuntimeException | java.sql.SQLException e) {
            // Registrar erro no log
            final LogExtracaoEntity logErro = new LogExtracaoEntity(
                "manifestos",
                inicioManifestos,
                LocalDateTime.now(),
                "ERRO_API",
                0,
                0,
                "Erro: " + e.getMessage()
            );
            logExtracaoRepository.gravarLogExtracao(logErro);
            throw new RuntimeException("Falha na extração de manifestos", e);
        }

        Thread.sleep(2000);

        // Cotações
        System.out.println("\n💹 Extraindo Cotações (últimas 24h)...");
        final LocalDateTime inicioCotacoes = LocalDateTime.now();
        try {
            final ResultadoExtracao<CotacaoDTO> resultadoCotacoes = clienteApiDataExport.buscarCotacoes();
            final List<CotacaoDTO> cotacoesDTO = resultadoCotacoes.getDados();
            System.out.println("✓ Extraídas: " + cotacoesDTO.size() + " cotações" + 
                              (resultadoCotacoes.isCompleto() ? "" : " (INCOMPLETO: " + resultadoCotacoes.getMotivoInterrupcao() + ")"));
            
            
            int totalUnicos = 0;
            int registrosSalvos = 0;
            final int registrosExtraidos = resultadoCotacoes.getRegistrosExtraidos();
            if (!cotacoesDTO.isEmpty()) {
                final List<CotacaoEntity> cotacoesEntities = cotacoesDTO.stream()
                    .map(cotacaoMapper::toEntity)
                    .collect(Collectors.toList());
                
                // Deduplicar registros (proteção contra duplicados da API)
                final List<CotacaoEntity> cotacoesUnicas = deduplicarCotacoes(cotacoesEntities);
                totalUnicos = cotacoesUnicas.size();
                
                // Log se houver duplicados removidos
                if (cotacoesEntities.size() != cotacoesUnicas.size()) {
                    final int duplicadosRemovidos = cotacoesEntities.size() - cotacoesUnicas.size();
                    System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                    logger.warn("🔄 API retornou {} duplicados para cotações. Removidos antes de salvar. Total único: {}",
                        duplicadosRemovidos, cotacoesUnicas.size());
                }
                
                registrosSalvos = cotacaoRepository.salvar(cotacoesUnicas);
                System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " cotações (INSERTs + UPDATEs)");
            }
            
            // Registrar no log (status enum + quantidade extraída)
            // NOTA: "salvos" = operações bem-sucedidas (INSERTs + UPDATEs)
            // UPDATEs não adicionam novas linhas, apenas atualizam existentes
            // registrosExtraidos = quantidade retornada pela API (pode incluir duplicados)
            // totalUnicos = quantidade após deduplicação (registros únicos)
            // registrosSalvos = quantidade processada com sucesso (INSERTs + UPDATEs)
            final LogExtracaoEntity.StatusExtracao statusFinal = 
                resultadoCotacoes.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;

            final String mensagem = resultadoCotacoes.isCompleto() ?
                ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                ("Extração incompleta (" + resultadoCotacoes.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");

            final LogExtracaoEntity logCotacoes = new LogExtracaoEntity(
                "cotacoes",
                inicioCotacoes,
                LocalDateTime.now(),
                statusFinal,
                totalUnicos, // ← CORRIGIDO: usar totalUnicos (após deduplicação) em vez de registrosExtraidos (bruto da API)
                resultadoCotacoes.getPaginasProcessadas(),
                mensagem
            );
            logExtracaoRepository.gravarLogExtracao(logCotacoes);

        } catch (RuntimeException | java.sql.SQLException e) {
            // Registrar erro no log
            final LogExtracaoEntity logErro = new LogExtracaoEntity(
                "cotacoes",
                inicioCotacoes,
                LocalDateTime.now(),
                "ERRO_API",
                0,
                0,
                "Erro: " + e.getMessage()
            );
            logExtracaoRepository.gravarLogExtracao(logErro);
            throw new RuntimeException("Falha na extração de cotações", e);
        }

        Thread.sleep(2000);

        // Localização de Carga
        System.out.println("\n📍 Extraindo Localização de Carga (últimas 24h)...");
        final LocalDateTime inicioLocalizacoes = LocalDateTime.now();
        try {
            final ResultadoExtracao<LocalizacaoCargaDTO> resultadoLocalizacoes = clienteApiDataExport.buscarLocalizacaoCarga();
            final List<LocalizacaoCargaDTO> localizacoesDTO = resultadoLocalizacoes.getDados();
            System.out.println("✓ Extraídas: " + localizacoesDTO.size() + " localizações" + 
                              (resultadoLocalizacoes.isCompleto() ? "" : " (INCOMPLETO: " + resultadoLocalizacoes.getMotivoInterrupcao() + ")"));
            
            final int registrosExtraidos = resultadoLocalizacoes.getRegistrosExtraidos();
            if (!localizacoesDTO.isEmpty()) {
                final List<LocalizacaoCargaEntity> localizacoesEntities = localizacoesDTO.stream()
                    .map(localizacaoMapper::toEntity)
                    .collect(Collectors.toList());
                
                // Deduplicar registros (proteção contra duplicados da API)
                final List<LocalizacaoCargaEntity> localizacoesUnicas = deduplicarLocalizacoes(localizacoesEntities);
                
                // Log se houver duplicados removidos
                if (localizacoesEntities.size() != localizacoesUnicas.size()) {
                    final int duplicadosRemovidos = localizacoesEntities.size() - localizacoesUnicas.size();
                    System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                    logger.warn("🔄 API retornou {} duplicados para localizações. Removidos antes de salvar. Total único: {}", 
                        duplicadosRemovidos, localizacoesUnicas.size());
                }
                
                final int registrosSalvos = localizacaoRepository.salvar(localizacoesUnicas);
                final int totalUnicos = localizacoesUnicas.size();
                System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " localizações (INSERTs + UPDATEs)");
                
                // Registrar no log (status enum + quantidade extraída)
                // NOTA: "salvos" = operações bem-sucedidas (INSERTs + UPDATEs)
                // UPDATEs não adicionam novas linhas, apenas atualizam existentes
                // registrosExtraidos = quantidade retornada pela API (pode incluir duplicados)
                // totalUnicos = quantidade após deduplicação (registros únicos)
                // registrosSalvos = quantidade processada com sucesso (INSERTs + UPDATEs)
                final LogExtracaoEntity.StatusExtracao statusFinal = 
                    resultadoLocalizacoes.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;

                final String mensagem = resultadoLocalizacoes.isCompleto() ?
                    ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                    ("Extração incompleta (" + resultadoLocalizacoes.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");

                final LogExtracaoEntity logLocalizacoes = new LogExtracaoEntity(
                    "localizacao_cargas",
                    inicioLocalizacoes,
                    LocalDateTime.now(),
                    statusFinal,
                    totalUnicos, // ← CORRIGIDO: usar totalUnicos (após deduplicação) em vez de registrosExtraidos (bruto da API)
                    resultadoLocalizacoes.getPaginasProcessadas(),
                    mensagem
                );
                logExtracaoRepository.gravarLogExtracao(logLocalizacoes);
            }

        } catch (RuntimeException | java.sql.SQLException e) {
            // Registrar erro no log
            final LogExtracaoEntity logErro = new LogExtracaoEntity(
                "localizacao_cargas",
                inicioLocalizacoes,
                LocalDateTime.now(),
                "ERRO_API",
                0,
                0,
                "Erro: " + e.getMessage()
            );
            logExtracaoRepository.gravarLogExtracao(logErro);
            throw new RuntimeException("Falha na extração de localização de cargas", e);
        }

        Thread.sleep(2000);

        // Faturas a Pagar (Data Export)
        System.out.println("\n💰 Extraindo Faturas a Pagar - Data Export (últimas 24h)...");
        final LocalDateTime inicioFaturasAPagar = LocalDateTime.now();
        try {
            final ResultadoExtracao<ContasAPagarDTO> resultadoFaturasAPagar = clienteApiDataExport.buscarContasAPagar();
            final List<ContasAPagarDTO> faturasAPagarDTO = resultadoFaturasAPagar.getDados();
            System.out.println("✓ Extraídas: " + faturasAPagarDTO.size() + " faturas a pagar" +
                              (resultadoFaturasAPagar.isCompleto() ? "" : " (INCOMPLETO: " + resultadoFaturasAPagar.getMotivoInterrupcao() + ")"));

            int totalUnicos = 0;
            int registrosSalvos = 0;
            final int registrosExtraidos = resultadoFaturasAPagar.getRegistrosExtraidos();

            if (!faturasAPagarDTO.isEmpty()) {
                final List<ContasAPagarDataExportEntity> faturasAPagarEntities = faturasAPagarDTO.stream()
                    .map(contasAPagarMapper::toEntity)
                    .collect(Collectors.toList());

                final List<ContasAPagarDataExportEntity> faturasAPagarUnicas = deduplicarFaturasAPagar(faturasAPagarEntities);
                totalUnicos = faturasAPagarUnicas.size();

                if (faturasAPagarEntities.size() != faturasAPagarUnicas.size()) {
                    final int duplicadosRemovidos = faturasAPagarEntities.size() - faturasAPagarUnicas.size();
                    System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                    logger.warn("🔄 API retornou {} duplicados para faturas a pagar. Removidos antes de salvar. Total único: {}",
                        duplicadosRemovidos, faturasAPagarUnicas.size());
                }

                registrosSalvos = contasAPagarRepository.salvar(faturasAPagarUnicas);
                System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " faturas a pagar (INSERTs + UPDATEs)");
            }

            final LogExtracaoEntity.StatusExtracao statusFinal =
                resultadoFaturasAPagar.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;

            final String mensagem = resultadoFaturasAPagar.isCompleto() ?
                ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                ("Extração incompleta (" + resultadoFaturasAPagar.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");

            final LogExtracaoEntity logFaturasAPagar = new LogExtracaoEntity(
                "contas_a_pagar",
                inicioFaturasAPagar,
                LocalDateTime.now(),
                statusFinal,
                totalUnicos,
                resultadoFaturasAPagar.getPaginasProcessadas(),
                mensagem
            );
            logExtracaoRepository.gravarLogExtracao(logFaturasAPagar);

        } catch (RuntimeException | java.sql.SQLException e) {
            final LogExtracaoEntity logErro = new LogExtracaoEntity(
                "contas_a_pagar",
                inicioFaturasAPagar,
                LocalDateTime.now(),
                "ERRO_API",
                0,
                0,
                "Erro: " + e.getMessage()
            );
            logExtracaoRepository.gravarLogExtracao(logErro);
            throw new RuntimeException("Falha na extração de faturas a pagar", e);
        }

        Thread.sleep(2000);
        System.out.println("\n💰 Extraindo Faturas por Cliente (últimas 24h)...");
        final LocalDateTime inicioFaturasPorCliente = LocalDateTime.now();
        try {
            final ResultadoExtracao<FaturaPorClienteDTO> resultadoFaturasPorCliente = 
                clienteApiDataExport.buscarFaturasPorCliente();
            final List<FaturaPorClienteDTO> faturasPorClienteDTO = resultadoFaturasPorCliente.getDados();

            System.out.println("✓ Extraídas: " + faturasPorClienteDTO.size() + " faturas por cliente" +
                    (resultadoFaturasPorCliente.isCompleto() ? "" : 
                     " (INCOMPLETO: " + resultadoFaturasPorCliente.getMotivoInterrupcao() + ")"));

            int totalUnicos = 0;
            int registrosSalvos = 0;
            final int registrosExtraidos = resultadoFaturasPorCliente.getRegistrosExtraidos();

            if (!faturasPorClienteDTO.isEmpty()) {
                final List<FaturaPorClienteEntity> faturasPorClienteEntities = faturasPorClienteDTO.stream()
                    .map(faturasPorClienteMapper::toEntity)
                    .collect(Collectors.toList());

                final List<FaturaPorClienteEntity> faturasPorClienteUnicas = 
                    deduplicarFaturasPorCliente(faturasPorClienteEntities);
                totalUnicos = faturasPorClienteUnicas.size();

                if (faturasPorClienteEntities.size() != faturasPorClienteUnicas.size()) {
                    final int duplicadosRemovidos = faturasPorClienteEntities.size() - faturasPorClienteUnicas.size();
                    System.out.println("⚠️ Removidos " + duplicadosRemovidos + 
                                     " duplicados da resposta da API antes de salvar");
                    logger.warn("🔄 API retornou {} duplicados para faturas por cliente. Removidos antes de salvar. Total único: {}", 
                            duplicadosRemovidos, faturasPorClienteUnicas.size());
                }

                registrosSalvos = faturasPorClienteRepository.salvar(faturasPorClienteUnicas);
                System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + 
                                 " faturas por cliente (INSERTs + UPDATEs)");
            }

            final LogExtracaoEntity.StatusExtracao statusFinal =
                resultadoFaturasPorCliente.isCompleto() ? 
                LogExtracaoEntity.StatusExtracao.COMPLETO : 
                LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;

            final String mensagem = resultadoFaturasPorCliente.isCompleto() ?
                ("Extração completa – extraídos " + registrosExtraidos + 
                 " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                ("Extração incompleta (" + resultadoFaturasPorCliente.getMotivoInterrupcao() + 
                 ") – extraídos " + registrosExtraidos + 
                 " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");

            final LogExtracaoEntity logFaturasPorCliente = new LogExtracaoEntity(
                "faturas_por_cliente",
                inicioFaturasPorCliente,
                LocalDateTime.now(),
                statusFinal,
                totalUnicos,
                resultadoFaturasPorCliente.getPaginasProcessadas(),
                mensagem
            );
            logExtracaoRepository.gravarLogExtracao(logFaturasPorCliente);

        } catch (RuntimeException | java.sql.SQLException e) {
            final LogExtracaoEntity logErro = new LogExtracaoEntity(
                "faturas_por_cliente",
                inicioFaturasPorCliente,
                LocalDateTime.now(),
                "ERRO_API",
                0,
                0,
                "Erro: " + e.getMessage()
            );
            logExtracaoRepository.gravarLogExtracao(logErro);
            throw new RuntimeException("Falha na extração de faturas por cliente", e);
        }
        logExtracaoRepository.criarOuAtualizarViewDimFiliais();
    }

    public static void executar(final LocalDate dataInicio, final String entidade) throws Exception {
        System.out.println("🔄 Executando runner DataExport...");

        br.com.extrator.util.CarregadorConfig.validarConexaoBancoDados();

        final ClienteApiDataExport clienteApiDataExport = new ClienteApiDataExport();
        clienteApiDataExport.setExecutionUuid(java.util.UUID.randomUUID().toString());
        final ManifestoRepository manifestoRepository = new ManifestoRepository();
        final CotacaoRepository cotacaoRepository = new CotacaoRepository();
        final LocalizacaoCargaRepository localizacaoRepository = new LocalizacaoCargaRepository();
        final LogExtracaoRepository logExtracaoRepository = new LogExtracaoRepository();
        final ContasAPagarRepository contasAPagarRepository = new ContasAPagarRepository();
        final FaturaPorClienteRepository faturasPorClienteRepository = new FaturaPorClienteRepository();

        final ManifestoMapper manifestoMapper = new ManifestoMapper();
        final CotacaoMapper cotacaoMapper = new CotacaoMapper();
        final LocalizacaoCargaMapper localizacaoMapper = new LocalizacaoCargaMapper();
        final ContasAPagarMapper contasAPagarMapper = new ContasAPagarMapper();
        final FaturaPorClienteMapper faturasPorClienteMapper = new FaturaPorClienteMapper();

        logExtracaoRepository.criarTabelaSeNaoExistir();

        final String ent = entidade == null ? "" : entidade.trim().toLowerCase();
        final boolean executarManifestos = ent.isEmpty() || "manifestos".equals(ent);
        final boolean executarCotacoes = ent.isEmpty() || "cotacoes".equals(ent) || "cotacao".equals(ent);
        final boolean executarLocalizacao = ent.isEmpty() || "localizacao_carga".equals(ent) || "localizacao_de_carga".equals(ent) || "localizacao-carga".equals(ent) || "localizacao de carga".equals(ent);
        final boolean executarContasAPagar = ent.isEmpty() || "contas_a_pagar".equals(ent) || "contasapagar".equals(ent) || "contas a pagar".equals(ent) || "contas-a-pagar".equals(ent) || "constas a pagar".equals(ent) || "constas-a-pagar".equals(ent);
        final boolean executarFaturasPorCliente = ent.isEmpty() || "faturas_por_cliente".equals(ent) || "faturasporcliente".equals(ent) || "faturas por cliente".equals(ent) || "faturas-por-cliente".equals(ent);

        if (executarManifestos) {
            System.out.println("\n🧾 Extraindo Manifestos (últimas 24h)...");
            final LocalDateTime inicioManifestos = LocalDateTime.now();
            try {
                final ResultadoExtracao<ManifestoDTO> resultadoManifestos = clienteApiDataExport.buscarManifestos();
                final List<ManifestoDTO> manifestosDTO = resultadoManifestos.getDados();
                System.out.println("✓ Extraídos: " + manifestosDTO.size() + " manifestos" +
                                  (resultadoManifestos.isCompleto() ? "" : " (INCOMPLETO: " + resultadoManifestos.getMotivoInterrupcao() + ")"));
                int registrosSalvos = 0;
                int totalUnicos = 0;
                final int registrosExtraidos = resultadoManifestos.getRegistrosExtraidos();
                if (!manifestosDTO.isEmpty()) {
                    final List<ManifestoEntity> manifestosEntities = manifestosDTO.stream()
                        .map(manifestoMapper::toEntity)
                        .collect(Collectors.toList());
                    final List<ManifestoEntity> manifestosUnicos = deduplicarManifestos(manifestosEntities);
                    totalUnicos = manifestosUnicos.size();
                    if (manifestosEntities.size() != manifestosUnicos.size()) {
                        final int duplicadosRemovidos = manifestosEntities.size() - manifestosUnicos.size();
                        System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                        logger.warn("🔄 API retornou {} duplicados para manifestos. Removidos antes de salvar. Total único: {}",
                            duplicadosRemovidos, manifestosUnicos.size());
                    }
                    registrosSalvos = manifestoRepository.salvar(manifestosUnicos);
                    System.out.println("✓ Processados: " + registrosSalvos + "/" + totalUnicos + " manifestos (INSERTs + UPDATEs)");
                }
                final LogExtracaoEntity.StatusExtracao statusFinal =
                    resultadoManifestos.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;
                final String mensagem = resultadoManifestos.isCompleto() ?
                    ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                    ("Extração incompleta (" + resultadoManifestos.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");
                final LogExtracaoEntity logManifestos = new LogExtracaoEntity(
                    "manifestos",
                    inicioManifestos,
                    LocalDateTime.now(),
                    statusFinal,
                    totalUnicos,
                    resultadoManifestos.getPaginasProcessadas(),
                    mensagem
                );
                logExtracaoRepository.gravarLogExtracao(logManifestos);
                logExtracaoRepository.criarOuAtualizarViewDimVeiculos();
                logExtracaoRepository.criarOuAtualizarViewDimMotoristas();
            } catch (RuntimeException | java.sql.SQLException e) {
                final LogExtracaoEntity logErro = new LogExtracaoEntity(
                    "manifestos",
                    inicioManifestos,
                    LocalDateTime.now(),
                    "ERRO_API",
                    0,
                    0,
                    "Erro: " + e.getMessage()
                );
                logExtracaoRepository.gravarLogExtracao(logErro);
                throw new RuntimeException("Falha na extração de manifestos", e);
            }
            Thread.sleep(2000);
        }

        if (executarCotacoes) {
            System.out.println("\n💹 Extraindo Cotações (últimas 24h)...");
            final LocalDateTime inicioCotacoes = LocalDateTime.now();
            try {
                final ResultadoExtracao<CotacaoDTO> resultadoCotacoes = clienteApiDataExport.buscarCotacoes();
                final List<CotacaoDTO> cotacoesDTO = resultadoCotacoes.getDados();
                System.out.println("✓ Extraídas: " + cotacoesDTO.size() + " cotações" +
                                  (resultadoCotacoes.isCompleto() ? "" : " (INCOMPLETO: " + resultadoCotacoes.getMotivoInterrupcao() + ")"));
                int totalUnicos = 0;
                int registrosSalvos = 0;
                final int registrosExtraidos = resultadoCotacoes.getRegistrosExtraidos();
                if (!cotacoesDTO.isEmpty()) {
                    final List<CotacaoEntity> cotacoesEntities = cotacoesDTO.stream()
                        .map(cotacaoMapper::toEntity)
                        .collect(Collectors.toList());
                    final List<CotacaoEntity> cotacoesUnicas = deduplicarCotacoes(cotacoesEntities);
                    totalUnicos = cotacoesUnicas.size();
                    if (cotacoesEntities.size() != cotacoesUnicas.size()) {
                        final int duplicadosRemovidos = cotacoesEntities.size() - cotacoesUnicas.size();
                        System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                        logger.warn("🔄 API retornou {} duplicados para cotações. Removidos antes de salvar. Total único: {}",
                            duplicadosRemovidos, cotacoesUnicas.size());
                    }
                    registrosSalvos = cotacaoRepository.salvar(cotacoesUnicas);
                    System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " cotações (INSERTs + UPDATEs)");
                }
                final LogExtracaoEntity.StatusExtracao statusFinal =
                    resultadoCotacoes.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;
                final String mensagem = resultadoCotacoes.isCompleto() ?
                    ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                    ("Extração incompleta (" + resultadoCotacoes.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");
                final LogExtracaoEntity logCotacoes = new LogExtracaoEntity(
                    "cotacoes",
                    inicioCotacoes,
                    LocalDateTime.now(),
                    statusFinal,
                    totalUnicos,
                    resultadoCotacoes.getPaginasProcessadas(),
                    mensagem
                );
                logExtracaoRepository.gravarLogExtracao(logCotacoes);
            } catch (RuntimeException | java.sql.SQLException e) {
                final LogExtracaoEntity logErro = new LogExtracaoEntity(
                    "cotacoes",
                    inicioCotacoes,
                    LocalDateTime.now(),
                    "ERRO_API",
                    0,
                    0,
                    "Erro: " + e.getMessage()
                );
                logExtracaoRepository.gravarLogExtracao(logErro);
                throw new RuntimeException("Falha na extração de cotações", e);
            }
            Thread.sleep(2000);
        }

        if (executarLocalizacao) {
            System.out.println("\n📍 Extraindo Localização de Carga (últimas 24h)...");
            final LocalDateTime inicioLocalizacoes = LocalDateTime.now();
            try {
                final ResultadoExtracao<LocalizacaoCargaDTO> resultadoLocalizacoes = clienteApiDataExport.buscarLocalizacaoCarga();
                final List<LocalizacaoCargaDTO> localizacoesDTO = resultadoLocalizacoes.getDados();
                System.out.println("✓ Extraídas: " + localizacoesDTO.size() + " localizações" +
                                  (resultadoLocalizacoes.isCompleto() ? "" : " (INCOMPLETO: " + resultadoLocalizacoes.getMotivoInterrupcao() + ")"));
                final int registrosExtraidos = resultadoLocalizacoes.getRegistrosExtraidos();
                if (!localizacoesDTO.isEmpty()) {
                    final List<LocalizacaoCargaEntity> localizacoesEntities = localizacoesDTO.stream()
                        .map(localizacaoMapper::toEntity)
                        .collect(Collectors.toList());
                    final List<LocalizacaoCargaEntity> localizacoesUnicas = deduplicarLocalizacoes(localizacoesEntities);
                    if (localizacoesEntities.size() != localizacoesUnicas.size()) {
                        final int duplicadosRemovidos = localizacoesEntities.size() - localizacoesUnicas.size();
                        System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                        logger.warn("🔄 API retornou {} duplicados para localizações. Removidos antes de salvar. Total único: {}",
                            duplicadosRemovidos, localizacoesUnicas.size());
                    }
                    final int registrosSalvos = localizacaoRepository.salvar(localizacoesUnicas);
                    final int totalUnicos = localizacoesUnicas.size();
                    System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " localizações (INSERTs + UPDATEs)");
                    final LogExtracaoEntity.StatusExtracao statusFinal =
                        resultadoLocalizacoes.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;
                    final String mensagem = resultadoLocalizacoes.isCompleto() ?
                        ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                        ("Extração incompleta (" + resultadoLocalizacoes.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");
                    final LogExtracaoEntity logLocalizacoes = new LogExtracaoEntity(
                        "localizacao_cargas",
                        inicioLocalizacoes,
                        LocalDateTime.now(),
                        statusFinal,
                        totalUnicos,
                        resultadoLocalizacoes.getPaginasProcessadas(),
                        mensagem
                    );
                    logExtracaoRepository.gravarLogExtracao(logLocalizacoes);
                }
            } catch (RuntimeException | java.sql.SQLException e) {
                final LogExtracaoEntity logErro = new LogExtracaoEntity(
                    "localizacao_cargas",
                    inicioLocalizacoes,
                    LocalDateTime.now(),
                    "ERRO_API",
                    0,
                    0,
                    "Erro: " + e.getMessage()
                );
                logExtracaoRepository.gravarLogExtracao(logErro);
                throw new RuntimeException("Falha na extração de localização de cargas", e);
            }
            Thread.sleep(2000);
        }

        if (executarContasAPagar) {
            System.out.println("\n💰 Extraindo Faturas a Pagar - Data Export (últimas 24h)...");
            final LocalDateTime inicioFaturasAPagar = LocalDateTime.now();
            try {
                final ResultadoExtracao<ContasAPagarDTO> resultadoFaturasAPagar = clienteApiDataExport.buscarContasAPagar();
                final List<ContasAPagarDTO> faturasAPagarDTO = resultadoFaturasAPagar.getDados();
                System.out.println("✓ Extraídas: " + faturasAPagarDTO.size() + " faturas a pagar" +
                                  (resultadoFaturasAPagar.isCompleto() ? "" : " (INCOMPLETO: " + resultadoFaturasAPagar.getMotivoInterrupcao() + ")"));
                int totalUnicos = 0;
                int registrosSalvos = 0;
                final int registrosExtraidos = resultadoFaturasAPagar.getRegistrosExtraidos();
                if (!faturasAPagarDTO.isEmpty()) {
                    final List<ContasAPagarDataExportEntity> faturasAPagarEntities = faturasAPagarDTO.stream()
                        .map(contasAPagarMapper::toEntity)
                        .collect(Collectors.toList());
                    final List<ContasAPagarDataExportEntity> faturasAPagarUnicas = deduplicarFaturasAPagar(faturasAPagarEntities);
                    totalUnicos = faturasAPagarUnicas.size();
                    if (faturasAPagarEntities.size() != faturasAPagarUnicas.size()) {
                        final int duplicadosRemovidos = faturasAPagarEntities.size() - faturasAPagarUnicas.size();
                        System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                        logger.warn("🔄 API retornou {} duplicados para faturas a pagar. Removidos antes de salvar. Total único: {}",
                            duplicadosRemovidos, faturasAPagarUnicas.size());
                    }
                    registrosSalvos = contasAPagarRepository.salvar(faturasAPagarUnicas);
                    System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " faturas a pagar (INSERTs + UPDATEs)");
                }
                final LogExtracaoEntity.StatusExtracao statusFinal =
                    resultadoFaturasAPagar.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;
                final String mensagem = resultadoFaturasAPagar.isCompleto() ?
                    ("Extração completa – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                    ("Extração incompleta (" + resultadoFaturasAPagar.getMotivoInterrupcao() + ") – extraídos " + registrosExtraidos + " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");
                final LogExtracaoEntity logFaturasAPagar = new LogExtracaoEntity(
                    "contas_a_pagar",
                    inicioFaturasAPagar,
                    LocalDateTime.now(),
                    statusFinal,
                    totalUnicos,
                    resultadoFaturasAPagar.getPaginasProcessadas(),
                    mensagem
                );
                logExtracaoRepository.gravarLogExtracao(logFaturasAPagar);
                logExtracaoRepository.criarOuAtualizarViewDimPlanoContas();
            } catch (RuntimeException | java.sql.SQLException e) {
                final LogExtracaoEntity logErro = new LogExtracaoEntity(
                    "contas_a_pagar",
                    inicioFaturasAPagar,
                    LocalDateTime.now(),
                    "ERRO_API",
                    0,
                    0,
                    "Erro: " + e.getMessage()
                );
                logExtracaoRepository.gravarLogExtracao(logErro);
                throw new RuntimeException("Falha na extração de faturas a pagar", e);
            }
            Thread.sleep(2000);
        }

        if (executarFaturasPorCliente) {
            System.out.println("\n💰 Extraindo Faturas por Cliente (últimas 24h)...");
            final LocalDateTime inicioFaturasPorCliente = LocalDateTime.now();
            try {
                final ResultadoExtracao<FaturaPorClienteDTO> resultadoFaturasPorCliente =
                    clienteApiDataExport.buscarFaturasPorCliente();
                final List<FaturaPorClienteDTO> faturasPorClienteDTO = resultadoFaturasPorCliente.getDados();
                System.out.println("✓ Extraídas: " + faturasPorClienteDTO.size() + " faturas por cliente" +
                        (resultadoFaturasPorCliente.isCompleto() ? "" : 
                         " (INCOMPLETO: " + resultadoFaturasPorCliente.getMotivoInterrupcao() + ")"));
                int totalUnicos = 0;
                int registrosSalvos = 0;
                final int registrosExtraidos = resultadoFaturasPorCliente.getRegistrosExtraidos();
                if (!faturasPorClienteDTO.isEmpty()) {
                    final List<FaturaPorClienteEntity> faturasPorClienteEntities = faturasPorClienteDTO.stream()
                        .map(faturasPorClienteMapper::toEntity)
                        .collect(Collectors.toList());
                    final List<FaturaPorClienteEntity> faturasPorClienteUnicas =
                        deduplicarFaturasPorCliente(faturasPorClienteEntities);
                    totalUnicos = faturasPorClienteUnicas.size();
                    if (faturasPorClienteEntities.size() != faturasPorClienteUnicas.size()) {
                        final int duplicadosRemovidos = faturasPorClienteEntities.size() - faturasPorClienteUnicas.size();
                        System.out.println("⚠️ Removidos " + duplicadosRemovidos + " duplicados da resposta da API antes de salvar");
                        logger.warn("🔄 API retornou {} duplicados para faturas por cliente. Removidos antes de salvar. Total único: {}",
                            duplicadosRemovidos, faturasPorClienteUnicas.size());
                    }
                    registrosSalvos = faturasPorClienteRepository.salvar(faturasPorClienteUnicas);
                    System.out.println("✓ Processadas: " + registrosSalvos + "/" + totalUnicos + " faturas por cliente (INSERTs + UPDATEs)");
                }
                final LogExtracaoEntity.StatusExtracao statusFinal =
                    resultadoFaturasPorCliente.isCompleto() ? LogExtracaoEntity.StatusExtracao.COMPLETO : LogExtracaoEntity.StatusExtracao.INCOMPLETO_LIMITE;
                final String mensagem = resultadoFaturasPorCliente.isCompleto() ?
                    ("Extração completa – extraídos " + registrosExtraidos + 
                     " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)") :
                    ("Extração incompleta (" + resultadoFaturasPorCliente.getMotivoInterrupcao() + 
                     ") – extraídos " + registrosExtraidos + 
                     " (únicos: " + totalUnicos + "), processados " + registrosSalvos + " (INSERTs + UPDATEs)");
                final LogExtracaoEntity logFaturasPorCliente = new LogExtracaoEntity(
                    "faturas_por_cliente",
                    inicioFaturasPorCliente,
                    LocalDateTime.now(),
                    statusFinal,
                    totalUnicos,
                    resultadoFaturasPorCliente.getPaginasProcessadas(),
                    mensagem
                );
                logExtracaoRepository.gravarLogExtracao(logFaturasPorCliente);
            } catch (RuntimeException | java.sql.SQLException e) {
                final LogExtracaoEntity logErro = new LogExtracaoEntity(
                    "faturas_por_cliente",
                    inicioFaturasPorCliente,
                    LocalDateTime.now(),
                    "ERRO_API",
                    0,
                    0,
                    "Erro: " + e.getMessage()
                );
                logExtracaoRepository.gravarLogExtracao(logErro);
                throw new RuntimeException("Falha na extração de faturas por cliente", e);
            }
        }
        logExtracaoRepository.criarOuAtualizarViewDimFiliais();
    }
    
    /**
     * Deduplica lista de manifestos removendo registros duplicados da API.
     * Usa chave composta (sequence_code + identificador_unico) para identificar duplicados.
     * Mantém o primeiro registro encontrado e descarta duplicados subsequentes.
     * 
     * @param manifestos Lista de manifestos a deduplicar
     * @return Lista deduplicada de manifestos
     */
    private static List<ManifestoEntity> deduplicarManifestos(final List<ManifestoEntity> manifestos) {
        if (manifestos == null || manifestos.isEmpty()) {
            return manifestos;
        }
        
        return manifestos.stream()
            .collect(Collectors.toMap(
                m -> {
                    // Chave única: sequence_code + "_" + identificador_unico
                    if (m.getSequenceCode() == null) {
                        throw new IllegalStateException("Manifesto com sequence_code NULL não pode ser deduplicado");
                    }
                    if (m.getIdentificadorUnico() == null || m.getIdentificadorUnico().trim().isEmpty()) {
                        throw new IllegalStateException("Manifesto com identificador_unico NULL/vazio não pode ser deduplicado");
                    }
                    return m.getSequenceCode() + "_" + m.getIdentificadorUnico();
                },
                m -> m,
                (primeiro, segundo) -> {
                    // Se houver duplicado, manter o primeiro e logar o segundo
                    logger.warn("⚠️ Duplicado detectado na resposta da API: sequence_code={}, identificador_unico={}", 
                        segundo.getSequenceCode(), segundo.getIdentificadorUnico());
                    return primeiro; // Mantém o primeiro
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
    
    /**
     * Deduplica lista de cotações removendo registros duplicados da API.
     * Usa sequence_code como chave única (PRIMARY KEY da tabela).
     * Mantém o primeiro registro encontrado e descarta duplicados subsequentes.
     * 
     * @param cotacoes Lista de cotações a deduplicar
     * @return Lista deduplicada de cotações
     */
    private static List<CotacaoEntity> deduplicarCotacoes(final List<CotacaoEntity> cotacoes) {
        if (cotacoes == null || cotacoes.isEmpty()) {
            return cotacoes;
        }
        
        return cotacoes.stream()
            .collect(Collectors.toMap(
                c -> {
                    // Chave única: sequence_code
                    if (c.getSequenceCode() == null) {
                        throw new IllegalStateException("Cotação com sequence_code NULL não pode ser deduplicada");
                    }
                    return c.getSequenceCode();
                },
                c -> c,
                (primeiro, segundo) -> {
                    // Se houver duplicado, manter o primeiro e logar o segundo
                    logger.warn("⚠️ Duplicado detectado na resposta da API: sequence_code={}", 
                        segundo.getSequenceCode());
                    return primeiro; // Mantém o primeiro
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
    
    /**
     * Deduplica lista de localizações removendo registros duplicados da API.
     * Usa sequence_number como chave única (PRIMARY KEY da tabela).
     * Mantém o primeiro registro encontrado e descarta duplicados subsequentes.
     * 
     * @param localizacoes Lista de localizações a deduplicar
     * @return Lista deduplicada de localizações
     */
    private static List<LocalizacaoCargaEntity> deduplicarLocalizacoes(final List<LocalizacaoCargaEntity> localizacoes) {
        if (localizacoes == null || localizacoes.isEmpty()) {
            return localizacoes;
        }
        
        return localizacoes.stream()
            .collect(Collectors.toMap(
                l -> {
                    // Chave única: sequence_number
                    if (l.getSequenceNumber() == null) {
                        throw new IllegalStateException("Localização com sequence_number NULL não pode ser deduplicada");
                    }
                    return l.getSequenceNumber();
                },
                l -> l,
                (primeiro, segundo) -> {
                    // Se houver duplicado, manter o primeiro e logar o segundo
                    logger.warn("⚠️ Duplicado detectado na resposta da API: sequence_number={}", 
                        segundo.getSequenceNumber());
                    return primeiro; // Mantém o primeiro
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }

    /**
     * Deduplica Faturas a Pagar por sequence_code (chave primária).
     */
    private static List<ContasAPagarDataExportEntity> deduplicarFaturasAPagar(final List<ContasAPagarDataExportEntity> lista) {
        if (lista == null || lista.isEmpty()) {
            return lista;
        }

        return lista.stream()
            .collect(Collectors.toMap(
                e -> {
                    if (e.getSequenceCode() == null) {
                        throw new IllegalStateException("Fatura a pagar com sequence_code NULL não pode ser deduplicada");
                    }
                    return e.getSequenceCode();
                },
                e -> e,
                (primeiro, segundo) -> {
                    logger.warn("⚠️ Duplicado detectado: sequence_code={}", segundo.getSequenceCode());
                    return primeiro;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }

    private static List<FaturaPorClienteEntity> deduplicarFaturasPorCliente(final List<FaturaPorClienteEntity> lista) {
        if (lista == null || lista.isEmpty()) {
            return lista;
        }

        return lista.stream()
            .collect(Collectors.toMap(
                e -> {
                    if (e.getUniqueId() == null || e.getUniqueId().trim().isEmpty()) {
                        throw new IllegalStateException(
                            "Fatura por cliente com unique_id NULL não pode ser deduplicada");
                    }
                    return e.getUniqueId();
                },
                e -> e,
                (primeiro, segundo) -> {
                    logger.warn("⚠️ Duplicado detectado: unique_id={}", segundo.getUniqueId());
                    return primeiro;
                }
            ))
            .values()
            .stream()
            .collect(Collectors.toList());
    }
}
