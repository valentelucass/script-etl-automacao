package br.com.extrator.comandos.extracao;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import br.com.extrator.auditoria.servicos.IntegridadeEtlValidator;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.db.entity.LogExtracaoEntity;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.runners.dataexport.DataExportRunner;
import br.com.extrator.runners.graphql.GraphQLRunner;
import br.com.extrator.servicos.ValidadorLimiteExtracao;
import br.com.extrator.util.console.BannerUtil;
import br.com.extrator.util.console.LoggerConsole;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.formatacao.FormatadorData;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Comando responsÃƒÂ¡vel por executar extraÃƒÂ§ÃƒÂ£o de dados por intervalo de datas,
 * com divisÃƒÂ£o automÃƒÂ¡tica em blocos de 30 dias e validaÃƒÂ§ÃƒÂ£o de regras de limitaÃƒÂ§ÃƒÂ£o.
 */
public class ExecutarExtracaoPorIntervaloComando implements Comando {
    
    private static final LoggerConsole log = LoggerConsole.getLogger(ExecutarExtracaoPorIntervaloComando.class);
    
    private static final int TAMANHO_BLOCO_DIAS = 30;
    private static final int NUMERO_DE_THREADS = 2;
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";
    
    @Override
    public void executar(final String[] args) throws Exception {
        final List<String> argumentosLimpos = new ArrayList<>();
        boolean incluirFaturasGraphQL = true;
        boolean modoLoopDaemon = false;
        for (final String arg : args) {
            if (arg != null && FLAG_SEM_FATURAS_GRAPHQL.equalsIgnoreCase(arg.trim())) {
                incluirFaturasGraphQL = false;
            } else if (arg != null && FLAG_MODO_LOOP_DAEMON.equalsIgnoreCase(arg.trim())) {
                modoLoopDaemon = true;
            } else {
                argumentosLimpos.add(arg);
            }
        }

        final String[] argsSemFlags = argumentosLimpos.toArray(String[]::new);

        // Validar argumentos
        if (argsSemFlags.length < 3) {
            log.error("Ã¢ÂÅ’ ERRO: Argumentos insuficientes");
            log.console("Uso: --extracao-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--sem-faturas-graphql] [--modo-loop-daemon]");
            log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31");
            log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 graphql");
            log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 dataexport manifestos");
            log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 --sem-faturas-graphql");
            log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 --modo-loop-daemon");
            return;
        }
        
        // Parse das datas
        final LocalDate dataInicio;
        final LocalDate dataFim;
        try {
            dataInicio = LocalDate.parse(argsSemFlags[1], DateTimeFormatter.ISO_DATE);
            dataFim = LocalDate.parse(argsSemFlags[2], DateTimeFormatter.ISO_DATE);
        } catch (final DateTimeParseException e) {
            log.error("Ã¢ÂÅ’ ERRO: Formato de data invÃƒÂ¡lido. Use YYYY-MM-DD");
            log.console("Exemplo: 2024-11-01 2025-03-31");
            return;
        }
        
        // Parse de API e entidade (opcionais)
        String apiEspecifica = null;
        String entidadeEspecifica = null;
        if (argsSemFlags.length >= 4) {
            final String arg3 = argsSemFlags[3].trim().toLowerCase(Locale.ROOT);
            // Validar se o terceiro argumento ÃƒÂ© uma API vÃƒÂ¡lida
            if ("graphql".equals(arg3) || "dataexport".equals(arg3)) {
                apiEspecifica = arg3;
                if (argsSemFlags.length >= 5) {
                    entidadeEspecifica = argsSemFlags[4].trim();
                }
            } else {
                // Se nÃƒÂ£o for uma API vÃƒÂ¡lida, pode ser que a entidade foi passada sem a API
                // Nesse caso, tentamos inferir a API baseado na entidade
                log.warn("Ã¢Å¡Â Ã¯Â¸Â Terceiro argumento '{}' nÃƒÂ£o ÃƒÂ© uma API vÃƒÂ¡lida. Tentando inferir API pela entidade...", arg3);
                entidadeEspecifica = argsSemFlags[3].trim();
                
                // Tentar inferir a API baseado na entidade
                final String entidadeLower = entidadeEspecifica.toLowerCase(Locale.ROOT);
                if (entidadeLower.equals(ConstantesEntidades.COLETAS) || 
                    entidadeLower.equals(ConstantesEntidades.FRETES) || 
                    entidadeLower.equals(ConstantesEntidades.FATURAS_GRAPHQL)) {
                    apiEspecifica = "graphql";
                    log.info("Ã¢Å“â€¦ API inferida: GraphQL (baseado na entidade: {})", entidadeEspecifica);
                } else if (entidadeLower.equals(ConstantesEntidades.MANIFESTOS) ||
                          entidadeLower.equals(ConstantesEntidades.COTACOES) ||
                          entidadeLower.equals(ConstantesEntidades.LOCALIZACAO_CARGAS) ||
                          entidadeLower.equals(ConstantesEntidades.CONTAS_A_PAGAR) ||
                          entidadeLower.equals(ConstantesEntidades.FATURAS_POR_CLIENTE)) {
                    apiEspecifica = "dataexport";
                    log.info("Ã¢Å“â€¦ API inferida: DataExport (baseado na entidade: {})", entidadeEspecifica);
                } else {
                    log.error("Ã¢ÂÅ’ NÃƒÂ£o foi possÃƒÂ­vel inferir a API para a entidade: {}. Use: --extracao-intervalo DATA_INICIO DATA_FIM [api] [entidade]", entidadeEspecifica);
                    return;
                }
            }
        }

        final boolean isSomenteFaturasGraphQL = isEntidadeFaturasGraphQL(entidadeEspecifica);
        if (!incluirFaturasGraphQL && isSomenteFaturasGraphQL) {
            log.warn("Ã¢Å¡Â Ã¯Â¸Â Flag {} ignorada porque a entidade solicitada ÃƒÂ© explicitamente faturas_graphql.", FLAG_SEM_FATURAS_GRAPHQL);
            incluirFaturasGraphQL = true;
        }
        
        // Validar que dataInicio <= dataFim
        if (dataInicio.isAfter(dataFim)) {
            log.error("Ã¢ÂÅ’ ERRO: Data de inÃƒÂ­cio ({}) nÃƒÂ£o pode ser posterior ÃƒÂ  data de fim ({})", 
                     FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
            return;
        }
        
        // Exibir banner
        BannerUtil.exibirBannerExtracaoCompleta();
        
        log.console("\n" + "=".repeat(60));
        log.console("EXTRAÃƒâ€¡ÃƒÆ’O POR INTERVALO DE DATAS");
        log.console("=".repeat(60));
        log.console("PerÃƒÂ­odo solicitado: {} a {}", 
                   FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
        
        // Exibir filtros se especificados
        if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
            log.console("API: {}", apiEspecifica.toUpperCase());
            if (entidadeEspecifica != null && !entidadeEspecifica.isEmpty()) {
                log.console("Entidade: {}", entidadeEspecifica);
            } else {
                log.console("Entidade: TODAS");
            }
        } else {
            log.console("API: TODAS");
            log.console("Entidade: TODAS");
        }
        log.console("Faturas GraphQL: {}", incluirFaturasGraphQL ? "INCLUIDO" : "DESABILITADO (flag --sem-faturas-graphql)");
        
        // Calcular duraÃƒÂ§ÃƒÂ£o do perÃƒÂ­odo
        final ValidadorLimiteExtracao validador = new ValidadorLimiteExtracao();
        final long diasPeriodo = validador.calcularDuracaoPeriodo(dataInicio, dataFim);
        log.console("DuraÃƒÂ§ÃƒÂ£o: {} dias", diasPeriodo);
        
        // Obter limite de horas baseado no perÃƒÂ­odo TOTAL (apenas informativo)
        // NOTA: A validaÃƒÂ§ÃƒÂ£o real serÃƒÂ¡ feita por BLOCO (30 dias), nÃƒÂ£o pelo perÃƒÂ­odo total.
        // Cada bloco de 30 dias serÃƒÂ¡ tratado como "< 31 dias" (sem limite de horas).
        final int limiteHorasPeriodoTotal = validador.obterLimiteHoras(diasPeriodo);
        if (limiteHorasPeriodoTotal == 0) {
            log.console("Regra de limitaÃƒÂ§ÃƒÂ£o: SEM LIMITE (perÃƒÂ­odo < 31 dias)");
        } else if (limiteHorasPeriodoTotal == 1) {
            log.console("Regra de limitaÃƒÂ§ÃƒÂ£o: 1 HORA entre extraÃƒÂ§ÃƒÂµes (perÃƒÂ­odo de 31 dias a 6 meses)");
        } else {
            log.console("Regra de limitaÃƒÂ§ÃƒÂ£o: 12 HORAS entre extraÃƒÂ§ÃƒÂµes (perÃƒÂ­odo > 6 meses)");
        }
        log.console("Ã¢â€žÂ¹Ã¯Â¸Â  Nota: ValidaÃƒÂ§ÃƒÂ£o serÃƒÂ¡ feita por BLOCO (30 dias = sem limite), nÃƒÂ£o pelo perÃƒÂ­odo total");
        
        // Dividir em blocos de 30 dias se necessÃƒÂ¡rio
        final List<BlocoPeriodo> blocos = dividirEmBlocos(dataInicio, dataFim);
        log.console("Total de blocos: {}", blocos.size());
        
        if (blocos.size() > 1) {
            log.console("\nÃ°Å¸â€œÂ¦ PerÃƒÂ­odo serÃƒÂ¡ dividido em {} blocos de atÃƒÂ© {} dias:", blocos.size(), TAMANHO_BLOCO_DIAS);
            for (int i = 0; i < blocos.size(); i++) {
                final BlocoPeriodo bloco = blocos.get(i);
                log.console("  Bloco {}/{}: {} a {} ({} dias)", 
                           i + 1, blocos.size(),
                           FormatadorData.formatBR(bloco.dataInicio),
                           FormatadorData.formatBR(bloco.dataFim),
                           validador.calcularDuracaoPeriodo(bloco.dataInicio, bloco.dataFim));
            }
        }
        
        log.console("=".repeat(60) + "\n");
        
        // Executar extraÃƒÂ§ÃƒÂ£o para cada bloco
        executarPreBackfillReferencialColetas(dataInicio, apiEspecifica, entidadeEspecifica, modoLoopDaemon);

        final LocalDateTime inicioExecucao = LocalDateTime.now();
        int blocosCompletos = 0;
        int blocosFalhados = 0;
        final List<String> blocosFalhadosLista = new ArrayList<>();
        
        for (int i = 0; i < blocos.size(); i++) {
            final BlocoPeriodo bloco = blocos.get(i);
            final int numeroBloco = i + 1;
            final int totalBlocos = blocos.size();
            
            log.console("\n" + "=".repeat(60));
            log.console("BLOCO {}/{}: {} a {}", 
                       numeroBloco, totalBlocos,
                       FormatadorData.formatBR(bloco.dataInicio),
                       FormatadorData.formatBR(bloco.dataFim));
            log.console("=".repeat(60));
            
            // CORRIGIDO: Validar regras de limitaÃƒÂ§ÃƒÂ£o usando o TAMANHO DO BLOCO (30 dias), nÃƒÂ£o perÃƒÂ­odo total
            // EstratÃƒÂ©gia: Dividir em blocos de 30 dias para evitar a regra de 12 horas.
            // Cada bloco de 30 dias serÃƒÂ¡ tratado como "< 31 dias" (sem limite de horas).
            final boolean podeExecutar = validarLimitesParaBloco(bloco, validador, apiEspecifica, entidadeEspecifica, incluirFaturasGraphQL);
            
            if (!podeExecutar) {
                log.warn("Ã¢ÂÂ¸Ã¯Â¸Â Bloco {}/{} bloqueado pelas regras de limitaÃƒÂ§ÃƒÂ£o. Pulando...", numeroBloco, totalBlocos);
                blocosFalhados++;
                blocosFalhadosLista.add("Bloco " + numeroBloco);
                continue;
            }

            log.info("Ã°Å¸â€â€ž Iniciando extraÃƒÂ§ÃƒÂ£o do bloco {}/{}...", numeroBloco, totalBlocos);
            final LocalDateTime inicioExecucaoBloco = LocalDateTime.now();
            boolean blocoComFalha = false;
            boolean graphqlPrincipalConcluido = false;
            final List<String> falhasBloco = new ArrayList<>();

            // Se API especÃƒÂ­fica foi informada, executar apenas essa API
            if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
                if ("graphql".equals(apiEspecifica)) {
                    try {
                        GraphQLRunner.executarPorIntervalo(bloco.dataInicio, bloco.dataFim, entidadeEspecifica);
                        graphqlPrincipalConcluido = true;
                    } catch (final Exception e) {
                        blocoComFalha = true;
                        final String msg = extrairMensagemRaiz(e);
                        falhasBloco.add("GraphQL: " + msg);
                        log.error("Ã¢ÂÅ’ Falha no GraphQLRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, e);
                    }
                } else if ("dataexport".equals(apiEspecifica)) {
                    try {
                        DataExportRunner.executarPorIntervalo(bloco.dataInicio, bloco.dataFim, entidadeEspecifica);
                    } catch (final Exception e) {
                        blocoComFalha = true;
                        final String msg = extrairMensagemRaiz(e);
                        falhasBloco.add("DataExport: " + msg);
                        log.error("Ã¢ÂÅ’ Falha no DataExportRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, e);
                    }
                } else {
                    blocoComFalha = true;
                    falhasBloco.add("API invÃƒÂ¡lida: " + apiEspecifica);
                    log.error("Ã¢ÂÅ’ API invÃƒÂ¡lida: {}. Use 'graphql' ou 'dataexport'", apiEspecifica);
                }
            } else {
                // Executar ambas as APIs em paralelo, tratando falhas separadamente
                final ExecutorService executor = Executors.newFixedThreadPool(NUMERO_DE_THREADS);
                Exception erroGraphQL = null;
                Exception erroDataExport = null;
                try {
                    final Future<?> futuroGraphQL = executor.submit(() -> {
                        try {
                            GraphQLRunner.executarPorIntervalo(bloco.dataInicio, bloco.dataFim);
                        } catch (final Exception e) {
                            throw new RuntimeException("Falha no GraphQLRunner", e);
                        }
                    });

                    final Future<?> futuroDataExport = executor.submit(() -> {
                        try {
                            DataExportRunner.executarPorIntervalo(bloco.dataInicio, bloco.dataFim);
                        } catch (final Exception e) {
                            throw new RuntimeException("Falha no DataExportRunner", e);
                        }
                    });

                    try {
                        futuroGraphQL.get();
                        graphqlPrincipalConcluido = true;
                    } catch (final Exception e) {
                        erroGraphQL = e;
                    }

                    try {
                        futuroDataExport.get();
                    } catch (final Exception e) {
                        erroDataExport = e;
                    }
                } finally {
                    executor.shutdown();
                }

                if (erroGraphQL != null) {
                    blocoComFalha = true;
                    final String msg = extrairMensagemRaiz(erroGraphQL);
                    falhasBloco.add("GraphQL: " + msg);
                    log.error("Ã¢ÂÅ’ Falha no GraphQLRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, erroGraphQL);
                }
                if (erroDataExport != null) {
                    blocoComFalha = true;
                    final String msg = extrairMensagemRaiz(erroDataExport);
                    falhasBloco.add("DataExport: " + msg);
                    log.error("Ã¢ÂÅ’ Falha no DataExportRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, erroDataExport);
                }
            }

            if (!blocoComFalha) {
                log.info("Ã¢Å“â€¦ Bloco {}/{} (entidades principais) concluÃƒÂ­do com sucesso!", numeroBloco, totalBlocos);
            } else {
                log.warn("Ã¢Å¡Â Ã¯Â¸Â Bloco {}/{} concluiu entidades principais com falhas.", numeroBloco, totalBlocos);
            }

            // ========== FASE 3: EXTRAÃƒâ€¡ÃƒÆ’O DE FATURAS GRAPHQL POR ÃƒÅ¡LTIMO ==========
            // Executar faturas_graphql APÃƒâ€œS todas as outras entidades do bloco,
            // EXCETO se a entidade especÃƒÂ­fica for faturas_graphql (jÃƒÂ¡ foi executada acima)
            final boolean semEntidadeEspecifica = entidadeEspecifica == null || entidadeEspecifica.isBlank();
            final boolean deveExecutarFaturasGraphQL =
                (apiEspecifica == null || "graphql".equalsIgnoreCase(apiEspecifica)) &&
                semEntidadeEspecifica &&
                !isSomenteFaturasGraphQL &&
                incluirFaturasGraphQL;

            if (deveExecutarFaturasGraphQL) {
                if (!graphqlPrincipalConcluido) {
                    blocoComFalha = true;
                    falhasBloco.add("FaturasGraphQL: fase principal GraphQL nÃƒÂ£o concluiu");
                    log.warn("Ã¢Å¡Â Ã¯Â¸Â [FASE 3] Faturas GraphQL nÃƒÂ£o foi executado no bloco {}/{} pois GraphQL principal falhou.", numeroBloco, totalBlocos);
                } else {
                    log.info("Ã°Å¸â€â€ž [FASE 3] Executando Faturas GraphQL por ÃƒÂºltimo para bloco {}/{}...", numeroBloco, totalBlocos);
                    try {
                        GraphQLRunner.executarFaturasGraphQLPorIntervalo(bloco.dataInicio, bloco.dataFim);
                        log.info("Ã¢Å“â€¦ Faturas GraphQL do bloco {}/{} concluÃƒÂ­das!", numeroBloco, totalBlocos);
                    } catch (final Exception e) {
                        blocoComFalha = true;
                        final String msg = extrairMensagemRaiz(e);
                        falhasBloco.add("FaturasGraphQL: " + msg);
                        log.error("Ã¢ÂÅ’ Falha na extraÃƒÂ§ÃƒÂ£o de Faturas GraphQL do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, e);
                    }
                }
            } else if (!incluirFaturasGraphQL && (apiEspecifica == null || "graphql".equalsIgnoreCase(apiEspecifica)) && !isSomenteFaturasGraphQL) {
                log.info("Ã¢â€žÂ¹Ã¯Â¸Â [FASE 3] Faturas GraphQL desabilitado por opÃƒÂ§ÃƒÂ£o do operador ({}).", FLAG_SEM_FATURAS_GRAPHQL);
            }

            final LocalDateTime fimExecucaoBloco = LocalDateTime.now();
            final List<String> falhasDeVolume = validarResultadosCriticosDoBloco(
                bloco,
                inicioExecucaoBloco,
                fimExecucaoBloco,
                apiEspecifica,
                entidadeEspecifica,
                incluirFaturasGraphQL,
                modoLoopDaemon
            );
            if (!falhasDeVolume.isEmpty()) {
                blocoComFalha = true;
                falhasBloco.addAll(falhasDeVolume);
                log.warn("Ã¢Å¡Â Ã¯Â¸Â Bloco {}/{} apresentou inconsistÃƒÂªncias de volume em entidades crÃƒÂ­ticas:", numeroBloco, totalBlocos);
                for (final String falhaVolume : falhasDeVolume) {
                    log.warn("   Ã¢â‚¬Â¢ {}", falhaVolume);
                }
            }

            if (blocoComFalha) {
                blocosFalhados++;
                if (falhasBloco.isEmpty()) {
                    blocosFalhadosLista.add("Bloco " + numeroBloco);
                } else {
                    blocosFalhadosLista.add("Bloco " + numeroBloco + " (" + String.join(" | ", falhasBloco) + ")");
                }
            } else {
                blocosCompletos++;
            }
        }
        
        // Exibir resumo final
        final LocalDateTime fimExecucao = LocalDateTime.now();
        final long duracaoMinutos = java.time.Duration.between(inicioExecucao, fimExecucao).toMinutes();
        
        log.console("\n" + "=".repeat(60));
        log.console("RESUMO DA EXTRAÃƒâ€¡ÃƒÆ’O POR INTERVALO");
        log.console("=".repeat(60));
        log.console("PerÃƒÂ­odo: {} a {}", 
                   FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
        log.console("Total de blocos: {}", blocos.size());
        log.console("Blocos completos: {}", blocosCompletos);
        if (blocosFalhados > 0) {
            log.warn("Blocos falhados: {} - {}", blocosFalhados, String.join(", ", blocosFalhadosLista));
        }
        log.console("DuraÃƒÂ§ÃƒÂ£o total: {} minutos", duracaoMinutos);
        log.console("=".repeat(60));
        
        if (blocosFalhados == 0) {
            BannerUtil.exibirBannerSucesso();
        } else {
            BannerUtil.exibirBannerErro();
            throw new PartialExecutionException(
                "ExtraÃƒÂ§ÃƒÂ£o por intervalo concluÃƒÂ­da com falhas parciais. Blocos falhados: "
                    + blocosFalhados + " - " + String.join(", ", blocosFalhadosLista)
            );
        }
    }

    private String extrairMensagemRaiz(final Throwable erro) {
        Throwable atual = erro;
        while (atual.getCause() != null) {
            atual = atual.getCause();
        }
        return atual.getMessage() != null ? atual.getMessage() : atual.toString();
    }
    
    /**
     * Divide o perÃƒÂ­odo em blocos de 30 dias.
     * 
     * @param dataInicio Data de inÃƒÂ­cio do perÃƒÂ­odo
     * @param dataFim Data de fim do perÃƒÂ­odo
     * @return Lista de blocos de perÃƒÂ­odo
     */
    private List<BlocoPeriodo> dividirEmBlocos(final LocalDate dataInicio, final LocalDate dataFim) {
        final List<BlocoPeriodo> blocos = new ArrayList<>();
        
        LocalDate inicioBloco = dataInicio;
        
        while (!inicioBloco.isAfter(dataFim)) {
            // Calcular fim do bloco (inÃƒÂ­cio + 30 dias, ou dataFim se for menor)
            final LocalDate fimBloco = inicioBloco.plusDays(TAMANHO_BLOCO_DIAS - 1);
            final LocalDate fimReal = fimBloco.isAfter(dataFim) ? dataFim : fimBloco;
            
            blocos.add(new BlocoPeriodo(inicioBloco, fimReal));
            
            // PrÃƒÂ³ximo bloco comeÃƒÂ§a no dia seguinte ao fim do bloco atual
            inicioBloco = fimReal.plusDays(1);
        }
        
        return blocos;
    }
    
    /**
     * Valida regras de limitaÃƒÂ§ÃƒÂ£o para as entidades do bloco.
     * CORRIGIDO: A regra de limitaÃƒÂ§ÃƒÂ£o ÃƒÂ© baseada no TAMANHO DO BLOCO (30 dias), nÃƒÂ£o no perÃƒÂ­odo total.
     * Isso permite que blocos de 30 dias usem a regra de "sem limite" em vez de 12 horas.
     * 
     * @param bloco Bloco de perÃƒÂ­odo a validar
     * @param validador Validador de limites
     * @param apiEspecifica API especÃƒÂ­fica (null = todas)
     * @param entidadeEspecifica Entidade especÃƒÂ­fica (null = todas)
     * @return true se pode executar, false se bloqueado
     */
    private boolean validarLimitesParaBloco(final BlocoPeriodo bloco, 
                                           final ValidadorLimiteExtracao validador,
                                           final String apiEspecifica,
                                           final String entidadeEspecifica,
                                           final boolean incluirFaturasGraphQL) {
        // Determinar quais entidades validar
        final List<String> entidadesParaValidar = new ArrayList<>();
        
        if (entidadeEspecifica != null && !entidadeEspecifica.isEmpty()) {
            // Validar apenas a entidade especÃƒÂ­fica
            entidadesParaValidar.add(entidadeEspecifica);
        } else if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
            // Validar todas as entidades da API especÃƒÂ­fica
            if ("graphql".equals(apiEspecifica)) {
                entidadesParaValidar.add(ConstantesEntidades.COLETAS);
                entidadesParaValidar.add(ConstantesEntidades.FRETES);
                if (incluirFaturasGraphQL) {
                    entidadesParaValidar.add(ConstantesEntidades.FATURAS_GRAPHQL);
                }
            } else if ("dataexport".equals(apiEspecifica)) {
                entidadesParaValidar.add(ConstantesEntidades.MANIFESTOS);
                entidadesParaValidar.add(ConstantesEntidades.COTACOES);
                entidadesParaValidar.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
                entidadesParaValidar.add(ConstantesEntidades.CONTAS_A_PAGAR);
                entidadesParaValidar.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
            }
        } else {
            // Validar todas as entidades
            entidadesParaValidar.add(ConstantesEntidades.COLETAS);
            entidadesParaValidar.add(ConstantesEntidades.FRETES);
            entidadesParaValidar.add(ConstantesEntidades.MANIFESTOS);
            entidadesParaValidar.add(ConstantesEntidades.COTACOES);
            entidadesParaValidar.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidadesParaValidar.add(ConstantesEntidades.CONTAS_A_PAGAR);
            entidadesParaValidar.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
            if (incluirFaturasGraphQL) {
                entidadesParaValidar.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
        }
        
        boolean todasPermitidas = true;
        
        for (final String entidade : entidadesParaValidar) {
            // CORRIGIDO: Usa o tamanho do BLOCO (30 dias) para determinar a regra de limitaÃƒÂ§ÃƒÂ£o
            // Isso permite que blocos de 30 dias usem a regra de "sem limite" em vez de 12 horas
            final ValidadorLimiteExtracao.ResultadoValidacao resultado = 
                validador.validarLimiteExtracao(entidade, bloco.dataInicio, bloco.dataFim);
            
            if (!resultado.isPermitido()) {
                log.warn("Ã¢ÂÂ¸Ã¯Â¸Â {}: {}", entidade, resultado.getMotivo());
                log.console("   Ã¢ÂÂ³ Aguarde {} hora(s) antes de tentar novamente", resultado.getHorasRestantes());
                todasPermitidas = false;
            }
        }
        
        return todasPermitidas;
    }

    private boolean isEntidadeFaturasGraphQL(final String entidadeEspecifica) {
        if (entidadeEspecifica == null || entidadeEspecifica.isBlank()) {
            return false;
        }
        return ConstantesEntidades.FATURAS_GRAPHQL.equalsIgnoreCase(entidadeEspecifica)
            || "faturas".equalsIgnoreCase(entidadeEspecifica)
            || "faturasgraphql".equalsIgnoreCase(entidadeEspecifica);
    }

    /**
     * Valida se entidades crÃƒÂ­ticas executaram com integridade no bloco atual, usando logs reais.
     * Regra aplicada: entidade crÃƒÂ­tica sem log, status nÃƒÂ£o COMPLETO ou falha de integridade ÃƒÂ© falha parcial.
     * Volume zero isolado (origem=0, destino=0) nÃƒÂ£o ÃƒÂ© tratado como falha.
     */
    private List<String> validarResultadosCriticosDoBloco(final BlocoPeriodo bloco,
                                                          final LocalDateTime inicioExecucaoBloco,
                                                          final LocalDateTime fimExecucaoBloco,
                                                          final String apiEspecifica,
                                                          final String entidadeEspecifica,
                                                          final boolean incluirFaturasGraphQL,
                                                          final boolean modoLoopDaemon) {
        final List<String> falhas = new ArrayList<>();
        final Set<String> entidadesObrigatorias = determinarEntidadesObrigatoriasParaVolume(
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL
        );

        if (entidadesObrigatorias.isEmpty()) {
            return falhas;
        }

        final LogExtracaoRepository logRepository = new LogExtracaoRepository();
        for (final String entidade : entidadesObrigatorias) {
            final Optional<LogExtracaoEntity> logOpt = logRepository.buscarUltimoLogPorEntidadeNoIntervaloExecucao(
                entidade,
                inicioExecucaoBloco,
                fimExecucaoBloco
            );

            if (logOpt.isEmpty()) {
                falhas.add(String.format(
                    "%s sem log no bloco %s a %s (possÃƒÂ­vel nÃƒÂ£o execuÃƒÂ§ÃƒÂ£o)",
                    entidade,
                    bloco.dataInicio,
                    bloco.dataFim
                ));
                continue;
            }

            final LogExtracaoEntity logEntidade = logOpt.get();
            if (logEntidade.getStatusFinal() != LogExtracaoEntity.StatusExtracao.COMPLETO) {
                falhas.add(String.format(
                    "%s com status %s no bloco %s a %s",
                    entidade,
                    logEntidade.getStatusFinal(),
                    bloco.dataInicio,
                    bloco.dataFim
                ));
                continue;
            }

            final Integer registrosExtraidos = logEntidade.getRegistrosExtraidos();
            if (registrosExtraidos == null) {
                falhas.add(String.format(
                    "%s sem contagem de registros no bloco %s a %s",
                    entidade,
                    bloco.dataInicio,
                    bloco.dataFim
                ));
                continue;
            }

            if (registrosExtraidos == 0) {
                log.info("Ã¢â€žÂ¹Ã¯Â¸Â {} com 0 registros no bloco {} a {} (considerado vÃƒÂ¡lido quando integridade tambÃƒÂ©m estiver OK).",
                    entidade,
                    bloco.dataInicio,
                    bloco.dataFim);
            }
        }

        final Set<String> entidadesIntegridade = determinarEntidadesEsperadasParaIntegridade(
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL
        );
        if (!entidadesIntegridade.isEmpty()) {
            final IntegridadeEtlValidator integridadeValidator = new IntegridadeEtlValidator();
            final IntegridadeEtlValidator.ResultadoValidacao resultadoIntegridade =
                integridadeValidator.validarExecucao(inicioExecucaoBloco, fimExecucaoBloco, entidadesIntegridade, modoLoopDaemon);
            if (!resultadoIntegridade.isValido()) {
                falhas.addAll(resultadoIntegridade.getFalhas());
            }
        }

        return falhas;
    }

    /**
     * Define quais entidades sÃƒÂ£o obrigatÃƒÂ³rias para considerar o bloco "normal".
     * - Se entidade especÃƒÂ­fica foi informada: valida essa entidade.
     * - Caso contrÃƒÂ¡rio: valida o conjunto crÃƒÂ­tico de negÃƒÂ³cio para evitar falso sucesso com volume zerado.
     */
    private Set<String> determinarEntidadesObrigatoriasParaVolume(final String apiEspecifica,
                                                                  final String entidadeEspecifica,
                                                                  final boolean incluirFaturasGraphQL) {
        final Set<String> entidades = new LinkedHashSet<>();

        if (entidadeEspecifica != null && !entidadeEspecifica.isBlank()) {
            final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
            if (entidadeNormalizada != null) {
                entidades.add(entidadeNormalizada);
            }
            return entidades;
        }

        final boolean apiTodas = apiEspecifica == null || apiEspecifica.isBlank();
        final boolean apiGraphQL = "graphql".equalsIgnoreCase(apiEspecifica);
        final boolean apiDataExport = "dataexport".equalsIgnoreCase(apiEspecifica);

        if (apiTodas || apiGraphQL) {
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
        }

        if (apiTodas || apiDataExport) {
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
        }

        return entidades;
    }

    private Set<String> determinarEntidadesEsperadasParaIntegridade(final String apiEspecifica,
                                                                    final String entidadeEspecifica,
                                                                    final boolean incluirFaturasGraphQL) {
        final Set<String> entidades = new LinkedHashSet<>();

        if (entidadeEspecifica != null && !entidadeEspecifica.isBlank()) {
            final String entidadeNormalizada = normalizarEntidade(entidadeEspecifica);
            if (entidadeNormalizada != null) {
                entidades.add(entidadeNormalizada);
                if (ConstantesEntidades.COLETAS.equals(entidadeNormalizada)) {
                    entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
                }
            }
            return entidades;
        }

        final boolean apiTodas = apiEspecifica == null || apiEspecifica.isBlank();
        final boolean apiGraphQL = "graphql".equalsIgnoreCase(apiEspecifica);
        final boolean apiDataExport = "dataexport".equalsIgnoreCase(apiEspecifica);

        if (apiTodas || apiGraphQL) {
            entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
        }

        if (apiTodas || apiDataExport) {
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidades.add(ConstantesEntidades.CONTAS_A_PAGAR);
            entidades.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
        }

        return entidades;
    }

    /**
     * Normaliza alias de entidade para o nome canÃƒÂ´nico salvo em log_extracoes.
     */
    private String normalizarEntidade(final String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return null;
        }
        final String valor = entidade.trim().toLowerCase(Locale.ROOT);

        if (ConstantesEntidades.COLETAS.equals(valor)) {
            return ConstantesEntidades.COLETAS;
        }
        if (ConstantesEntidades.FRETES.equals(valor)) {
            return ConstantesEntidades.FRETES;
        }
        if (ConstantesEntidades.FATURAS_GRAPHQL.equals(valor)
            || "faturas".equals(valor)
            || "faturasgraphql".equals(valor)) {
            return ConstantesEntidades.FATURAS_GRAPHQL;
        }
        if (ConstantesEntidades.USUARIOS_SISTEMA.equals(valor)) {
            return ConstantesEntidades.USUARIOS_SISTEMA;
        }

        if (ConstantesEntidades.MANIFESTOS.equals(valor)) {
            return ConstantesEntidades.MANIFESTOS;
        }
        if (ConstantesEntidades.COTACOES.equals(valor) || "cotacao".equals(valor)) {
            return ConstantesEntidades.COTACOES;
        }
        if (ConstantesEntidades.LOCALIZACAO_CARGAS.equals(valor)
            || "localizacao_carga".equals(valor)
            || "localizacao_de_carga".equals(valor)
            || "localizacao-carga".equals(valor)
            || "localizacao de carga".equals(valor)) {
            return ConstantesEntidades.LOCALIZACAO_CARGAS;
        }
        if (ConstantesEntidades.CONTAS_A_PAGAR.equals(valor)
            || "contasapagar".equals(valor)
            || "contas a pagar".equals(valor)
            || "contas-a-pagar".equals(valor)) {
            return ConstantesEntidades.CONTAS_A_PAGAR;
        }
        if (ConstantesEntidades.FATURAS_POR_CLIENTE.equals(valor)
            || "faturasporcliente".equals(valor)
            || "faturas por cliente".equals(valor)
            || "faturas-por-cliente".equals(valor)) {
            return ConstantesEntidades.FATURAS_POR_CLIENTE;
        }

        return valor;
    }

    private void executarPreBackfillReferencialColetas(final LocalDate dataInicio,
                                                       final String apiEspecifica,
                                                       final String entidadeEspecifica,
                                                       final boolean modoLoopDaemon) {
        if (modoLoopDaemon) {
            return;
        }

        final boolean escopoCompleto =
            (apiEspecifica == null || apiEspecifica.isBlank())
                && (entidadeEspecifica == null || entidadeEspecifica.isBlank());
        if (!escopoCompleto) {
            return;
        }

        final int diasRetroativos = CarregadorConfig.obterEtlReferencialColetasBackfillDias();
        if (diasRetroativos <= 0) {
            log.info("Pre-backfill referencial de coletas desabilitado (etl.referencial.coletas.backfill.dias=0).");
            return;
        }

        final LocalDate backfillInicio = dataInicio.minusDays(diasRetroativos);
        final LocalDate backfillFim = dataInicio.minusDays(1);
        if (backfillInicio.isAfter(backfillFim)) {
            return;
        }

        log.console("\n" + "=".repeat(60));
        log.info(
            "PRE-BACKFILL REFERENCIAL DE COLETAS (INTERVALO) | periodo={} a {} | dias_retroativos={}",
            FormatadorData.formatBR(backfillInicio),
            FormatadorData.formatBR(backfillFim),
            diasRetroativos
        );
        log.console("=".repeat(60));

        try {
            GraphQLRunner.executarPorIntervalo(backfillInicio, backfillFim, ConstantesEntidades.COLETAS);
            log.info("Pre-backfill referencial de coletas (intervalo) concluido.");
        } catch (final Exception e) {
            log.warn(
                "Pre-backfill referencial de coletas (intervalo) falhou: {}. Fluxo principal seguira normalmente.",
                e.getMessage()
            );
            log.debug("Detalhes da falha no pre-backfill referencial de coletas (intervalo):", e);
        }
    }
    
    /**
     * Classe auxiliar para representar um bloco de perÃƒÂ­odo.
     */
    private static class BlocoPeriodo {
        final LocalDate dataInicio;
        final LocalDate dataFim;
        
        BlocoPeriodo(final LocalDate dataInicio, final LocalDate dataFim) {
            this.dataInicio = dataInicio;
            this.dataFim = dataFim;
        }
    }
}

