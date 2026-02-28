/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/utilitarios/TestarApiComando.java
Classe  : TestarApiComando (class)
Pacote  : br.com.extrator.comandos.utilitarios
Modulo  : Componente Java
Papel   : Implementa comportamento de testar api comando.

Conecta com:
- Comando (comandos.base)
- LogExtracaoEntity (db.entity)
- LogExtracaoRepository (db.repository)
- DataExportRunner (runners.dataexport)
- GraphQLRunner (runners.graphql)
- BannerUtil (util.console)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Define comportamento principal deste modulo.
2) Interage com camadas relacionadas do sistema.
3) Entrega resultado para o fluxo chamador.

Estrutura interna:
Metodos principais:
- parseArgs(...1 args): realiza operacao relacionada a "parse args".
- isEntidadeFaturasGraphQL(...1 args): retorna estado booleano de controle.
- ParsedArgs(...2 args): realiza operacao relacionada a "parsed args".
- validarStatusDasEntidadesExecutadas(...5 args): aplica regras de validacao e consistencia.
- obterEntidadesEsperadas(...4 args): recupera dados configurados ou calculados.
- normalizarEntidadeGraphQL(...1 args): realiza operacao relacionada a "normalizar entidade graph ql".
- normalizarEntidadeDataExport(...1 args): realiza operacao relacionada a "normalizar entidade data export".
Atributos-chave:
- logger: logger da classe para diagnostico.
- FLAG_SEM_FATURAS_GRAPHQL: campo de estado para "flag sem faturas graphql".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.utilitarios;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.db.entity.LogExtracaoEntity;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.runners.dataexport.DataExportRunner;
import br.com.extrator.runners.graphql.GraphQLRunner;
import br.com.extrator.util.console.BannerUtil;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Comando responsavel por testar uma API especifica do sistema.
 */
public class TestarApiComando implements Comando {
    private static final Logger logger = LoggerFactory.getLogger(TestarApiComando.class);
    private static final String FLAG_SEM_FATURAS_GRAPHQL = "--sem-faturas-graphql";

    @Override
    public void executar(final String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("ERRO: Tipo de API nao especificado.");
            System.err.println("Uso: --testar-api <tipo> [entidade] [--sem-faturas-graphql]");
            System.err.println("Tipos validos: graphql, dataexport");
            throw new IllegalArgumentException("Tipo de API nao especificado. Tipos validos: graphql, dataexport");
        }

        final String tipoApi = args[1];
        final ParsedArgs parsedArgs = parseArgs(args);
        final String entidade = parsedArgs.entidade();
        boolean incluirFaturasGraphQL = parsedArgs.incluirFaturasGraphQL();
        final boolean somenteFaturasGraphQL = isEntidadeFaturasGraphQL(entidade);

        if (somenteFaturasGraphQL && !incluirFaturasGraphQL) {
            logger.warn("Flag {} ignorada porque a entidade solicitada e explicitamente faturas_graphql.", FLAG_SEM_FATURAS_GRAPHQL);
            incluirFaturasGraphQL = true;
        }

        // Janela padrao de teste: ultimas 24h (ontem -> hoje), igual ao fluxo completo.
        final LocalDate dataFim = LocalDate.now();
        final LocalDate dataInicio = dataFim.minusDays(1);

        switch (tipoApi.toLowerCase()) {
            case "graphql" -> BannerUtil.exibirBannerApiGraphQL();
            case "dataexport" -> BannerUtil.exibirBannerApiDataExport();
            default -> {
                System.err.println("ERRO: Tipo de API invalido: " + tipoApi);
                System.err.println("Tipos validos: graphql, dataexport");
                throw new IllegalArgumentException("Tipo de API invalido: " + tipoApi);
            }
        }

        System.out.println(
            "Periodo de teste: "
                + dataInicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " a "
                + dataFim.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " (ultimas 24h)"
        );
        if ("graphql".equalsIgnoreCase(tipoApi) && entidade == null) {
            System.out.println(
                "Faturas GraphQL: "
                    + (incluirFaturasGraphQL ? "INCLUIDO (fase final)" : "DESABILITADO (" + FLAG_SEM_FATURAS_GRAPHQL + ")")
            );
        }
        System.out.println();

        try {
            final LocalDateTime inicioExecucao = LocalDateTime.now();
            switch (tipoApi.toLowerCase()) {
                case "graphql" -> executarGraphQL(dataInicio, dataFim, entidade, incluirFaturasGraphQL, somenteFaturasGraphQL);
                case "dataexport" -> executarDataExport(dataInicio, dataFim, entidade, incluirFaturasGraphQL);
                default -> {
                    System.err.println("ERRO: Tipo de API invalido: " + tipoApi);
                    System.err.println("Tipos validos: graphql, dataexport");
                    throw new IllegalArgumentException("Tipo de API invalido: " + tipoApi);
                }
            }
            validarStatusDasEntidadesExecutadas(
                inicioExecucao,
                tipoApi,
                entidade,
                incluirFaturasGraphQL,
                somenteFaturasGraphQL
            );

            BannerUtil.exibirBannerSucesso();
            System.out.println("Teste da API " + tipoApi.toUpperCase() + " concluido com sucesso!");
        } catch (final Exception e) {
            BannerUtil.exibirBannerErro();
            System.err.println("Erro durante execucao: " + e.getMessage());
            logger.error("Erro durante execucao: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void executarGraphQL(final LocalDate dataInicio,
                                 final LocalDate dataFim,
                                 final String entidade,
                                 final boolean incluirFaturasGraphQL,
                                 final boolean somenteFaturasGraphQL) throws Exception {
        if (somenteFaturasGraphQL) {
            logger.info("Executando somente Faturas GraphQL para o periodo {} a {}", dataInicio, dataFim);
            GraphQLRunner.executarFaturasGraphQLPorIntervalo(dataInicio, dataFim);
            return;
        }

        if (entidade != null && !entidade.isBlank()) {
            GraphQLRunner.executarPorIntervalo(dataInicio, dataFim, entidade);
            return;
        }

        GraphQLRunner.executarPorIntervalo(dataInicio, dataFim);
        if (incluirFaturasGraphQL) {
            logger.info("[FASE 3] Executando Faturas GraphQL por ultimo no teste de API...");
            GraphQLRunner.executarFaturasGraphQLPorIntervalo(dataInicio, dataFim);
        } else {
            logger.info("[FASE 3] Faturas GraphQL desabilitado no teste de API (flag {}).", FLAG_SEM_FATURAS_GRAPHQL);
        }
    }

    private void executarDataExport(final LocalDate dataInicio,
                                    final LocalDate dataFim,
                                    final String entidade,
                                    final boolean incluirFaturasGraphQL) throws Exception {
        if (!incluirFaturasGraphQL) {
            logger.info("Flag {} ignorada para DataExport.", FLAG_SEM_FATURAS_GRAPHQL);
        }

        if (entidade != null && !entidade.isBlank()) {
            DataExportRunner.executarPorIntervalo(dataInicio, dataFim, entidade);
            return;
        }

        DataExportRunner.executarPorIntervalo(dataInicio, dataFim);
    }

    private ParsedArgs parseArgs(final String[] args) {
        boolean incluirFaturasGraphQL = true;
        final List<String> posicionais = new ArrayList<>();

        for (int i = 2; i < args.length; i++) {
            final String arg = args[i];
            if (FLAG_SEM_FATURAS_GRAPHQL.equalsIgnoreCase(arg)) {
                incluirFaturasGraphQL = false;
            } else {
                posicionais.add(arg);
            }
        }

        if (posicionais.size() > 1) {
            throw new IllegalArgumentException(
                "Argumentos invalidos para --testar-api. Uso: --testar-api <tipo> [entidade] [--sem-faturas-graphql]"
            );
        }

        final String entidade = posicionais.isEmpty() ? null : posicionais.get(0);
        return new ParsedArgs(entidade, incluirFaturasGraphQL);
    }

    private boolean isEntidadeFaturasGraphQL(final String entidade) {
        if (entidade == null || entidade.isBlank()) {
            return false;
        }
        return "faturas_graphql".equalsIgnoreCase(entidade)
            || "faturas".equalsIgnoreCase(entidade)
            || "faturasgraphql".equalsIgnoreCase(entidade);
    }

    private record ParsedArgs(String entidade, boolean incluirFaturasGraphQL) {
    }

    private void validarStatusDasEntidadesExecutadas(final LocalDateTime inicioExecucao,
                                                     final String tipoApi,
                                                     final String entidade,
                                                     final boolean incluirFaturasGraphQL,
                                                     final boolean somenteFaturasGraphQL) {
        final List<String> esperadas = obterEntidadesEsperadas(tipoApi, entidade, incluirFaturasGraphQL, somenteFaturasGraphQL);
        if (esperadas.isEmpty()) {
            return;
        }

        final LogExtracaoRepository repository = new LogExtracaoRepository();
        final LocalDateTime inicioJanela = inicioExecucao.minusSeconds(5);
        final LocalDateTime fimJanela = LocalDateTime.now().plusSeconds(5);
        final List<String> semLog = new ArrayList<>();
        final List<String> naoCompletas = new ArrayList<>();

        for (final String entidadeEsperada : esperadas) {
            final Optional<LogExtracaoEntity> optLog =
                repository.buscarUltimoLogPorEntidadeNoIntervaloExecucao(entidadeEsperada, inicioJanela, fimJanela);

            if (optLog.isEmpty()) {
                semLog.add(entidadeEsperada);
                continue;
            }

            final LogExtracaoEntity log = optLog.get();
            if (log.getStatusFinal() != LogExtracaoEntity.StatusExtracao.COMPLETO) {
                naoCompletas.add(entidadeEsperada + "=" + log.getStatusFinal().getValor());
            }
        }

        if (!semLog.isEmpty() || !naoCompletas.isEmpty()) {
            final StringBuilder detalhe = new StringBuilder("Teste de API reprovado na validacao de status.");
            if (!semLog.isEmpty()) {
                detalhe.append(" Sem log na janela para: ").append(String.join(", ", semLog)).append(".");
            }
            if (!naoCompletas.isEmpty()) {
                detalhe.append(" Status nao COMPLETO: ").append(String.join(", ", naoCompletas)).append(".");
            }
            throw new IllegalStateException(detalhe.toString());
        }
    }

    private List<String> obterEntidadesEsperadas(final String tipoApi,
                                                 final String entidade,
                                                 final boolean incluirFaturasGraphQL,
                                                 final boolean somenteFaturasGraphQL) {
        final Set<String> entidades = new LinkedHashSet<>();
        final String tipo = tipoApi == null ? "" : tipoApi.toLowerCase();

        if ("graphql".equals(tipo)) {
            if (somenteFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
                return new ArrayList<>(entidades);
            }

            if (entidade != null && !entidade.isBlank()) {
                entidades.add(normalizarEntidadeGraphQL(entidade));
                return new ArrayList<>(entidades);
            }

            entidades.add(ConstantesEntidades.USUARIOS_SISTEMA);
            entidades.add(ConstantesEntidades.COLETAS);
            entidades.add(ConstantesEntidades.FRETES);
            if (incluirFaturasGraphQL) {
                entidades.add(ConstantesEntidades.FATURAS_GRAPHQL);
            }
            return new ArrayList<>(entidades);
        }

        if ("dataexport".equals(tipo)) {
            if (entidade != null && !entidade.isBlank()) {
                entidades.add(normalizarEntidadeDataExport(entidade));
                return new ArrayList<>(entidades);
            }
            entidades.add(ConstantesEntidades.MANIFESTOS);
            entidades.add(ConstantesEntidades.COTACOES);
            entidades.add(ConstantesEntidades.LOCALIZACAO_CARGAS);
            entidades.add(ConstantesEntidades.CONTAS_A_PAGAR);
            entidades.add(ConstantesEntidades.FATURAS_POR_CLIENTE);
        }

        return new ArrayList<>(entidades);
    }

    private String normalizarEntidadeGraphQL(final String entidade) {
        final String valor = entidade == null ? "" : entidade.trim().toLowerCase();
        return switch (valor) {
            case "faturas", "faturasgraphql" -> ConstantesEntidades.FATURAS_GRAPHQL;
            case "usuarios" -> ConstantesEntidades.USUARIOS_SISTEMA;
            default -> valor;
        };
    }

    private String normalizarEntidadeDataExport(final String entidade) {
        final String valor = entidade == null ? "" : entidade.trim().toLowerCase();
        return switch (valor) {
            case "localizacao_carga", "localizacao_de_carga" -> ConstantesEntidades.LOCALIZACAO_CARGAS;
            case "contasapagar" -> ConstantesEntidades.CONTAS_A_PAGAR;
            case "faturasporcliente" -> ConstantesEntidades.FATURAS_POR_CLIENTE;
            default -> valor;
        };
    }
}
