/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/extracao/ExecutarExtracaoPorIntervaloComando.java
Classe  : ExecutarExtracaoPorIntervaloComando (class)
Pacote  : br.com.extrator.comandos.extracao
Modulo  : Comando CLI (extracao)
Papel   : Implementa responsabilidade de executar extracao por intervalo comando.

Conecta com:
- IntegridadeEtlValidator (auditoria.servicos)
- Comando (comandos.base)
- LogExtracaoEntity (db.entity)
- LogExtracaoRepository (db.repository)
- DataExportRunner (runners.dataexport)
- GraphQLRunner (runners.graphql)
- ValidadorLimiteExtracao (servicos)
- BannerUtil (util.console)

Fluxo geral:
1) Interpreta parametros e escopo de extracao.
2) Dispara runners/extratores conforme alvo.
3) Consolida status final e tratamento de falhas.

Estrutura interna:
Metodos principais:
- extrairMensagemRaiz(...1 args): realiza operacao relacionada a "extrair mensagem raiz".
- dividirEmBlocos(...2 args): realiza operacao relacionada a "dividir em blocos".
- validarLimitesParaBloco(...5 args): aplica regras de validacao e consistencia.
- isEntidadeFaturasGraphQL(...1 args): retorna estado booleano de controle.
- validarResultadosCriticosDoBloco(...7 args): aplica regras de validacao e consistencia.
- determinarEntidadesObrigatoriasParaVolume(...3 args): realiza operacao relacionada a "determinar entidades obrigatorias para volume".
- determinarEntidadesEsperadasParaIntegridade(...3 args): realiza operacao relacionada a "determinar entidades esperadas para integridade".
- normalizarEntidade(...1 args): realiza operacao relacionada a "normalizar entidade".
- executarPreBackfillReferencialColetas(...4 args): executa o fluxo principal desta responsabilidade.
Atributos-chave:
- log: campo de estado para "log".
- TAMANHO_BLOCO_DIAS: campo de estado para "tamanho bloco dias".
- NUMERO_DE_THREADS: campo de estado para "numero de threads".
- FLAG_SEM_FATURAS_GRAPHQL: campo de estado para "flag sem faturas graphql".
- FLAG_MODO_LOOP_DAEMON: campo de estado para "flag modo loop daemon".
[DOC-FILE-END]============================================================== */

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
 * Comando responsável por executar extração de dados por intervalo de datas,
 * com divisão automática em blocos de 30 dias e validação de regras de limitação.
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
            log.error("❌ ERRO: Argumentos insuficientes");
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
            log.error("❌ ERRO: Formato de data inválido. Use YYYY-MM-DD");
            log.console("Exemplo: 2024-11-01 2025-03-31");
            return;
        }
        
        // Parse de API e entidade (opcionais)
        String apiEspecifica = null;
        String entidadeEspecifica = null;
        if (argsSemFlags.length >= 4) {
            final String arg3 = argsSemFlags[3].trim().toLowerCase(Locale.ROOT);
            // Validar se o terceiro argumento é uma API válida
            if ("graphql".equals(arg3) || "dataexport".equals(arg3)) {
                apiEspecifica = arg3;
                if (argsSemFlags.length >= 5) {
                    entidadeEspecifica = argsSemFlags[4].trim();
                }
            } else {
                // Se não for uma API válida, pode ser que a entidade foi passada sem a API
                // Nesse caso, tentamos inferir a API baseado na entidade
                log.warn("⚠️ Terceiro argumento '{}' não é uma API válida. Tentando inferir API pela entidade...", arg3);
                entidadeEspecifica = argsSemFlags[3].trim();
                
                // Tentar inferir a API baseado na entidade
                final String entidadeLower = entidadeEspecifica.toLowerCase(Locale.ROOT);
                if (entidadeLower.equals(ConstantesEntidades.COLETAS) || 
                    entidadeLower.equals(ConstantesEntidades.FRETES) || 
                    entidadeLower.equals(ConstantesEntidades.FATURAS_GRAPHQL)) {
                    apiEspecifica = "graphql";
                    log.info("✅ API inferida: GraphQL (baseado na entidade: {})", entidadeEspecifica);
                } else if (entidadeLower.equals(ConstantesEntidades.MANIFESTOS) ||
                          entidadeLower.equals(ConstantesEntidades.COTACOES) ||
                          entidadeLower.equals(ConstantesEntidades.LOCALIZACAO_CARGAS) ||
                          entidadeLower.equals(ConstantesEntidades.CONTAS_A_PAGAR) ||
                          entidadeLower.equals(ConstantesEntidades.FATURAS_POR_CLIENTE)) {
                    apiEspecifica = "dataexport";
                    log.info("✅ API inferida: DataExport (baseado na entidade: {})", entidadeEspecifica);
                } else {
                    log.error("❌ Não foi possível inferir a API para a entidade: {}. Use: --extracao-intervalo DATA_INICIO DATA_FIM [api] [entidade]", entidadeEspecifica);
                    return;
                }
            }
        }

        final boolean isSomenteFaturasGraphQL = isEntidadeFaturasGraphQL(entidadeEspecifica);
        if (!incluirFaturasGraphQL && isSomenteFaturasGraphQL) {
            log.warn("⚠️ Flag {} ignorada porque a entidade solicitada é explicitamente faturas_graphql.", FLAG_SEM_FATURAS_GRAPHQL);
            incluirFaturasGraphQL = true;
        }
        
        // Validar que dataInicio <= dataFim
        if (dataInicio.isAfter(dataFim)) {
            log.error("❌ ERRO: Data de início ({}) não pode ser posterior à data de fim ({})", 
                     FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
            return;
        }
        
        // Exibir banner
        BannerUtil.exibirBannerExtracaoCompleta();
        
        log.console("\n" + "=".repeat(60));
        log.console("EXTRAÇÃO POR INTERVALO DE DATAS");
        log.console("=".repeat(60));
        log.console("Período solicitado: {} a {}", 
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
        
        // Calcular duração do período
        final ValidadorLimiteExtracao validador = new ValidadorLimiteExtracao();
        final long diasPeriodo = validador.calcularDuracaoPeriodo(dataInicio, dataFim);
        log.console("Duração: {} dias", diasPeriodo);
        
        // Obter limite de horas baseado no período TOTAL (apenas informativo)
        // NOTA: A validação real será feita por BLOCO (30 dias), não pelo período total.
        // Cada bloco de 30 dias será tratado como "< 31 dias" (sem limite de horas).
        final int limiteHorasPeriodoTotal = validador.obterLimiteHoras(diasPeriodo);
        if (limiteHorasPeriodoTotal == 0) {
            log.console("Regra de limitação: SEM LIMITE (período < 31 dias)");
        } else if (limiteHorasPeriodoTotal == 1) {
            log.console("Regra de limitação: 1 HORA entre extrações (período de 31 dias a 6 meses)");
        } else {
            log.console("Regra de limitação: 12 HORAS entre extrações (período > 6 meses)");
        }
        log.console("ℹ️  Nota: Validação será feita por BLOCO (30 dias = sem limite), não pelo período total");
        
        // Dividir em blocos de 30 dias se necessário
        final List<BlocoPeriodo> blocos = dividirEmBlocos(dataInicio, dataFim);
        log.console("Total de blocos: {}", blocos.size());
        
        if (blocos.size() > 1) {
            log.console("\n📦 Período será dividido em {} blocos de até {} dias:", blocos.size(), TAMANHO_BLOCO_DIAS);
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
        
        // Executar extração para cada bloco
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
            
            // CORRIGIDO: Validar regras de limitação usando o TAMANHO DO BLOCO (30 dias), não período total
            // Estratégia: Dividir em blocos de 30 dias para evitar a regra de 12 horas.
            // Cada bloco de 30 dias será tratado como "< 31 dias" (sem limite de horas).
            final boolean podeExecutar = validarLimitesParaBloco(bloco, validador, apiEspecifica, entidadeEspecifica, incluirFaturasGraphQL);
            
            if (!podeExecutar) {
                log.warn("⏸️ Bloco {}/{} bloqueado pelas regras de limitação. Pulando...", numeroBloco, totalBlocos);
                blocosFalhados++;
                blocosFalhadosLista.add("Bloco " + numeroBloco);
                continue;
            }

            log.info("🔄 Iniciando extração do bloco {}/{}...", numeroBloco, totalBlocos);
            final LocalDateTime inicioExecucaoBloco = LocalDateTime.now();
            boolean blocoComFalha = false;
            boolean graphqlPrincipalConcluido = false;
            final List<String> falhasBloco = new ArrayList<>();

            // Se API específica foi informada, executar apenas essa API
            if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
                if ("graphql".equals(apiEspecifica)) {
                    try {
                        GraphQLRunner.executarPorIntervalo(bloco.dataInicio, bloco.dataFim, entidadeEspecifica);
                        graphqlPrincipalConcluido = true;
                    } catch (final Exception e) {
                        blocoComFalha = true;
                        final String msg = extrairMensagemRaiz(e);
                        falhasBloco.add("GraphQL: " + msg);
                        log.error("❌ Falha no GraphQLRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, e);
                    }
                } else if ("dataexport".equals(apiEspecifica)) {
                    try {
                        DataExportRunner.executarPorIntervalo(bloco.dataInicio, bloco.dataFim, entidadeEspecifica);
                    } catch (final Exception e) {
                        blocoComFalha = true;
                        final String msg = extrairMensagemRaiz(e);
                        falhasBloco.add("DataExport: " + msg);
                        log.error("❌ Falha no DataExportRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, e);
                    }
                } else {
                    blocoComFalha = true;
                    falhasBloco.add("API inválida: " + apiEspecifica);
                    log.error("❌ API inválida: {}. Use 'graphql' ou 'dataexport'", apiEspecifica);
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
                    log.error("❌ Falha no GraphQLRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, erroGraphQL);
                }
                if (erroDataExport != null) {
                    blocoComFalha = true;
                    final String msg = extrairMensagemRaiz(erroDataExport);
                    falhasBloco.add("DataExport: " + msg);
                    log.error("❌ Falha no DataExportRunner do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, erroDataExport);
                }
            }

            if (!blocoComFalha) {
                log.info("✅ Bloco {}/{} (entidades principais) concluído com sucesso!", numeroBloco, totalBlocos);
            } else {
                log.warn("⚠️ Bloco {}/{} concluiu entidades principais com falhas.", numeroBloco, totalBlocos);
            }

            // ========== FASE 3: EXTRAÇÃO DE FATURAS GRAPHQL POR ÚLTIMO ==========
            // Executar faturas_graphql APÓS todas as outras entidades do bloco,
            // EXCETO se a entidade específica for faturas_graphql (já foi executada acima)
            final boolean semEntidadeEspecifica = entidadeEspecifica == null || entidadeEspecifica.isBlank();
            final boolean deveExecutarFaturasGraphQL =
                (apiEspecifica == null || "graphql".equalsIgnoreCase(apiEspecifica)) &&
                semEntidadeEspecifica &&
                !isSomenteFaturasGraphQL &&
                incluirFaturasGraphQL;

            if (deveExecutarFaturasGraphQL) {
                if (!graphqlPrincipalConcluido) {
                    blocoComFalha = true;
                    falhasBloco.add("FaturasGraphQL: fase principal GraphQL não concluiu");
                    log.warn("⚠️ [FASE 3] Faturas GraphQL não foi executado no bloco {}/{} pois GraphQL principal falhou.", numeroBloco, totalBlocos);
                } else {
                    log.info("🔄 [FASE 3] Executando Faturas GraphQL por último para bloco {}/{}...", numeroBloco, totalBlocos);
                    try {
                        GraphQLRunner.executarFaturasGraphQLPorIntervalo(bloco.dataInicio, bloco.dataFim);
                        log.info("✅ Faturas GraphQL do bloco {}/{} concluídas!", numeroBloco, totalBlocos);
                    } catch (final Exception e) {
                        blocoComFalha = true;
                        final String msg = extrairMensagemRaiz(e);
                        falhasBloco.add("FaturasGraphQL: " + msg);
                        log.error("❌ Falha na extração de Faturas GraphQL do bloco {}/{}: {}", numeroBloco, totalBlocos, msg, e);
                    }
                }
            } else if (!incluirFaturasGraphQL && (apiEspecifica == null || "graphql".equalsIgnoreCase(apiEspecifica)) && !isSomenteFaturasGraphQL) {
                log.info("ℹ️ [FASE 3] Faturas GraphQL desabilitado por opção do operador ({}).", FLAG_SEM_FATURAS_GRAPHQL);
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
                log.warn("⚠️ Bloco {}/{} apresentou inconsistências de volume em entidades críticas:", numeroBloco, totalBlocos);
                for (final String falhaVolume : falhasDeVolume) {
                    log.warn("   • {}", falhaVolume);
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
        log.console("RESUMO DA EXTRAÇÃO POR INTERVALO");
        log.console("=".repeat(60));
        log.console("Período: {} a {}", 
                   FormatadorData.formatBR(dataInicio), FormatadorData.formatBR(dataFim));
        log.console("Total de blocos: {}", blocos.size());
        log.console("Blocos completos: {}", blocosCompletos);
        if (blocosFalhados > 0) {
            log.warn("Blocos falhados: {} - {}", blocosFalhados, String.join(", ", blocosFalhadosLista));
        }
        log.console("Duração total: {} minutos", duracaoMinutos);
        log.console("=".repeat(60));
        
        if (blocosFalhados == 0) {
            BannerUtil.exibirBannerSucesso();
        } else {
            BannerUtil.exibirBannerErro();
            throw new PartialExecutionException(
                "Extração por intervalo concluída com falhas parciais. Blocos falhados: "
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
     * Divide o período em blocos de 30 dias.
     * 
     * @param dataInicio Data de início do período
     * @param dataFim Data de fim do período
     * @return Lista de blocos de período
     */
    private List<BlocoPeriodo> dividirEmBlocos(final LocalDate dataInicio, final LocalDate dataFim) {
        final List<BlocoPeriodo> blocos = new ArrayList<>();
        
        LocalDate inicioBloco = dataInicio;
        
        while (!inicioBloco.isAfter(dataFim)) {
            // Calcular fim do bloco (início + 30 dias, ou dataFim se for menor)
            final LocalDate fimBloco = inicioBloco.plusDays(TAMANHO_BLOCO_DIAS - 1);
            final LocalDate fimReal = fimBloco.isAfter(dataFim) ? dataFim : fimBloco;
            
            blocos.add(new BlocoPeriodo(inicioBloco, fimReal));
            
            // Próximo bloco começa no dia seguinte ao fim do bloco atual
            inicioBloco = fimReal.plusDays(1);
        }
        
        return blocos;
    }
    
    /**
     * Valida regras de limitação para as entidades do bloco.
     * CORRIGIDO: A regra de limitação é baseada no TAMANHO DO BLOCO (30 dias), não no período total.
     * Isso permite que blocos de 30 dias usem a regra de "sem limite" em vez de 12 horas.
     * 
     * @param bloco Bloco de período a validar
     * @param validador Validador de limites
     * @param apiEspecifica API específica (null = todas)
     * @param entidadeEspecifica Entidade específica (null = todas)
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
            // Validar apenas a entidade específica
            entidadesParaValidar.add(entidadeEspecifica);
        } else if (apiEspecifica != null && !apiEspecifica.isEmpty()) {
            // Validar todas as entidades da API específica
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
            // CORRIGIDO: Usa o tamanho do BLOCO (30 dias) para determinar a regra de limitação
            // Isso permite que blocos de 30 dias usem a regra de "sem limite" em vez de 12 horas
            final ValidadorLimiteExtracao.ResultadoValidacao resultado = 
                validador.validarLimiteExtracao(entidade, bloco.dataInicio, bloco.dataFim);
            
            if (!resultado.isPermitido()) {
                log.warn("⏸️ {}: {}", entidade, resultado.getMotivo());
                log.console("   ⏳ Aguarde {} hora(s) antes de tentar novamente", resultado.getHorasRestantes());
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
     * Valida se entidades críticas executaram com integridade no bloco atual, usando logs reais.
     * Regra aplicada: entidade crítica sem log, status não COMPLETO ou falha de integridade é falha parcial.
     * Volume zero isolado (origem=0, destino=0) não é tratado como falha.
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
                    "%s sem log no bloco %s a %s (possível não execução)",
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
                log.info("ℹ️ {} com 0 registros no bloco {} a {} (considerado válido quando integridade também estiver OK).",
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
     * Define quais entidades são obrigatórias para considerar o bloco "normal".
     * - Se entidade específica foi informada: valida essa entidade.
     * - Caso contrário: valida o conjunto crítico de negócio para evitar falso sucesso com volume zerado.
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
     * Normaliza alias de entidade para o nome canônico salvo em log_extracoes.
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
     * Classe auxiliar para representar um bloco de período.
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

