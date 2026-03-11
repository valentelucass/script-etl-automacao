/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/cli/extracao/ExecutarExtracaoPorIntervaloComando.java
Classe  : ExecutarExtracaoPorIntervaloComando (public class)
Pacote  : br.com.extrator.comandos.cli.extracao
Modulo  : Comando CLI - Extracao

Papel   : Comando CLI para extracao em intervalo (data inicio, data fim, com filtros opcionais de API/entidade).

Conecta com:
- ExtracaoPorIntervaloUseCase (aplicacao.extracao) - delegacao
- LoggerConsole (suporte.console)
- FormatadorData (suporte.formatacao)
- ConstantesEntidades (suporte.validacao)

Fluxo geral:
1) executar(String[] args) extrai datas, flags (--sem-faturas-graphql, --modo-loop-daemon), API/entidade.
2) Validacao: datas ISO-8601, API vs entidade inference.
3) Delega a ExtracaoPorIntervaloUseCase com ExtracaoPorIntervaloRequest.

Estrutura interna:
Atributos-chave:
- extracaoPorIntervaloUseCase: delegate para use case.
Inner record ParametrosParseados: wrapper para ExtracaoPorIntervaloRequest.
Metodos principais:
- executar(String[]): parser principal (delegacao).
- parseArgs(String[]): extrai data inicio/fim, API, entidade, flags.
- inferirApiPorEntidade(String): mapeia entidade -> API (GraphQL vs DataExport).
- isEntidadeFaturasGraphQL(String): verifica tolerancia de faturas.
[DOC-FILE-END]============================================================== */
package br.com.extrator.comandos.cli.extracao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import br.com.extrator.aplicacao.extracao.ExtracaoPorIntervaloRequest;
import br.com.extrator.aplicacao.extracao.ExtracaoPorIntervaloUseCase;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ExecutarExtracaoPorIntervaloComando implements Comando {
    private static final LoggerConsole log = LoggerConsole.getLogger(ExecutarExtracaoPorIntervaloComando.class);
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";
    private static final String FLAG_MODO_LOOP_DAEMON = "--modo-loop-daemon";

    private final ExtracaoPorIntervaloUseCase extracaoPorIntervaloUseCase;

    public ExecutarExtracaoPorIntervaloComando() {
        this(new ExtracaoPorIntervaloUseCase());
    }

    ExecutarExtracaoPorIntervaloComando(final ExtracaoPorIntervaloUseCase extracaoPorIntervaloUseCase) {
        this.extracaoPorIntervaloUseCase = extracaoPorIntervaloUseCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        final ParametrosParseados parametros = parseArgs(args);
        if (parametros == null) {
            return;
        }

        log.debug(
            "Delegando extracao por intervalo para ExtracaoPorIntervaloUseCase | inicio={} | fim={} | api={} | entidade={} | incluir_faturas_graphql={} | modo_loop_daemon={}",
            parametros.request.dataInicio(),
            parametros.request.dataFim(),
            parametros.request.apiEspecifica(),
            parametros.request.entidadeEspecifica(),
            parametros.request.incluirFaturasGraphQL(),
            parametros.request.modoLoopDaemon()
        );
        extracaoPorIntervaloUseCase.executar(parametros.request);
    }

    private ParametrosParseados parseArgs(final String[] args) {
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
        if (argsSemFlags.length < 3) {
            exibirUso();
            return null;
        }

        final LocalDate dataInicio;
        final LocalDate dataFim;
        try {
            dataInicio = LocalDate.parse(argsSemFlags[1], DateTimeFormatter.ISO_DATE);
            dataFim = LocalDate.parse(argsSemFlags[2], DateTimeFormatter.ISO_DATE);
        } catch (final DateTimeParseException e) {
            log.error("ERRO: Formato de data invalido. Use YYYY-MM-DD");
            log.console("Exemplo: 2024-11-01 2025-03-31");
            return null;
        }

        String apiEspecifica = null;
        String entidadeEspecifica = null;
        if (argsSemFlags.length >= 4) {
            final String arg3 = argsSemFlags[3].trim().toLowerCase(Locale.ROOT);
            if ("graphql".equals(arg3) || "dataexport".equals(arg3)) {
                apiEspecifica = arg3;
                if (argsSemFlags.length >= 5) {
                    entidadeEspecifica = argsSemFlags[4].trim();
                }
            } else {
                entidadeEspecifica = argsSemFlags[3].trim();
                apiEspecifica = inferirApiPorEntidade(entidadeEspecifica);
                if (apiEspecifica == null) {
                    log.error(
                        "Nao foi possivel inferir a API para a entidade: {}. Use: --extracao-intervalo DATA_INICIO DATA_FIM [api] [entidade]",
                        entidadeEspecifica
                    );
                    return null;
                }
            }
        }

        final boolean isSomenteFaturasGraphQL = isEntidadeFaturasGraphQL(entidadeEspecifica);
        if (!incluirFaturasGraphQL && isSomenteFaturasGraphQL) {
            log.warn(
                "Flag {} ignorada porque a entidade solicitada e explicitamente faturas_graphql.",
                FLAG_SEM_FATURAS_GRAPHQL
            );
            incluirFaturasGraphQL = true;
        }

        if (dataInicio.isAfter(dataFim)) {
            log.error(
                "ERRO: Data de inicio ({}) nao pode ser posterior a data de fim ({})",
                FormatadorData.formatBR(dataInicio),
                FormatadorData.formatBR(dataFim)
            );
            return null;
        }

        final ExtracaoPorIntervaloRequest request = new ExtracaoPorIntervaloRequest(
            dataInicio,
            dataFim,
            apiEspecifica,
            entidadeEspecifica,
            incluirFaturasGraphQL,
            modoLoopDaemon
        );
        return new ParametrosParseados(request);
    }

    private void exibirUso() {
        log.error("ERRO: Argumentos insuficientes");
        log.console(
            "Uso: --extracao-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--sem-faturas-graphql] [--modo-loop-daemon]"
        );
        log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31");
        log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 graphql");
        log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 dataexport manifestos");
        log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 --sem-faturas-graphql");
        log.console("Exemplo: --extracao-intervalo 2024-11-01 2025-03-31 --modo-loop-daemon");
    }

    private String inferirApiPorEntidade(final String entidadeEspecifica) {
        final String entidadeLower = entidadeEspecifica.toLowerCase(Locale.ROOT);
        if (entidadeLower.equals(ConstantesEntidades.COLETAS)
            || entidadeLower.equals(ConstantesEntidades.FRETES)
            || entidadeLower.equals(ConstantesEntidades.FATURAS_GRAPHQL)) {
            log.info("API inferida: GraphQL (baseado na entidade: {})", entidadeEspecifica);
            return "graphql";
        }
        if (entidadeLower.equals(ConstantesEntidades.MANIFESTOS)
            || entidadeLower.equals(ConstantesEntidades.COTACOES)
            || entidadeLower.equals(ConstantesEntidades.LOCALIZACAO_CARGAS)
            || entidadeLower.equals(ConstantesEntidades.CONTAS_A_PAGAR)
            || entidadeLower.equals(ConstantesEntidades.FATURAS_POR_CLIENTE)) {
            log.info("API inferida: DataExport (baseado na entidade: {})", entidadeEspecifica);
            return "dataexport";
        }
        return null;
    }

    private boolean isEntidadeFaturasGraphQL(final String entidadeEspecifica) {
        if (entidadeEspecifica == null || entidadeEspecifica.isBlank()) {
            return false;
        }
        return ConstantesEntidades.FATURAS_GRAPHQL.equalsIgnoreCase(entidadeEspecifica)
            || "faturas".equalsIgnoreCase(entidadeEspecifica)
            || "faturasgraphql".equalsIgnoreCase(entidadeEspecifica);
    }

    private record ParametrosParseados(ExtracaoPorIntervaloRequest request) {
    }
}
