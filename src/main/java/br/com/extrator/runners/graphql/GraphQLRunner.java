/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/graphql/GraphQLRunner.java
Classe  : GraphQLRunner (class)
Pacote  : br.com.extrator.runners.graphql
Modulo  : Componente Java
Papel   : Implementa comportamento de graph qlrunner.

Conecta com:
- GraphQLExtractionService (runners.graphql.services)
- LoggerConsole (util.console)

Fluxo geral:
1) Define comportamento principal deste modulo.
2) Interage com camadas relacionadas do sistema.
3) Entrega resultado para o fluxo chamador.

Estrutura interna:
Metodos principais:
- GraphQLRunner(): realiza operacao relacionada a "graph qlrunner".
- validarIntervalo(...2 args): aplica regras de validacao e consistencia.
Atributos-chave:
- log: campo de estado para "log".
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.graphql;

import java.time.LocalDate;
import java.util.Objects;

import br.com.extrator.runners.graphql.services.GraphQLExtractionService;
import br.com.extrator.util.console.LoggerConsole;

/**
 * Runner independente para a API GraphQL (Coletas, Fretes e Faturas GraphQL).
 * Refatorado para usar serviços de orquestração.
 * 
 * CORREÇÃO ALTO #2: Validação de parâmetros NULL adicionada
 */
public final class GraphQLRunner {

    private static final LoggerConsole log = LoggerConsole.getLogger(GraphQLRunner.class);

    private GraphQLRunner() {}

    /**
     * Executa extração de todas as entidades GraphQL.
     * 
     * @param dataInicio Data de início para filtro (não pode ser null)
     * @throws IllegalArgumentException Se dataInicio for null
     * @throws Exception Se houver falha na extração
     */
    public static void executar(final LocalDate dataInicio) throws Exception {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser null");
        executar(dataInicio, (String) null);
    }

    /**
     * Executa extração de todas as entidades GraphQL para um intervalo de datas.
     * 
     * @param dataInicio Data de início do período (não pode ser null)
     * @param dataFim Data de fim do período (não pode ser null)
     * @throws IllegalArgumentException Se dataInicio ou dataFim forem null, ou se dataFim < dataInicio
     * @throws Exception Se houver falha na extração
     */
    public static void executarPorIntervalo(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
        validarIntervalo(dataInicio, dataFim);
        executarPorIntervalo(dataInicio, dataFim, null);
    }

    /**
     * Executa extração de entidade(s) GraphQL específica(s) para um intervalo de datas.
     * 
     * @param dataInicio Data de início do período (não pode ser null)
     * @param dataFim Data de fim do período (não pode ser null)
     * @param entidade Nome da entidade (null = todas)
     * @throws IllegalArgumentException Se dataInicio ou dataFim forem null, ou se dataFim < dataInicio
     * @throws Exception Se houver falha na extração
     */
    public static void executarPorIntervalo(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) throws Exception {
        validarIntervalo(dataInicio, dataFim);
        
        log.info("🔄 Executando runner GraphQL - Período: {} a {}", dataInicio, dataFim);
        
        final GraphQLExtractionService service = new GraphQLExtractionService();
        service.execute(dataInicio, dataFim, entidade);
    }

    /**
     * Executa extração de entidade(s) GraphQL específica(s).
     * 
     * @param dataInicio Data de início para filtro (não pode ser null)
     * @param entidade Nome da entidade (null = todas)
     * @throws IllegalArgumentException Se dataInicio for null
     * @throws Exception Se houver falha na extração
     */
    public static void executar(final LocalDate dataInicio, final String entidade) throws Exception {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser null");
        
        log.info("🔄 Executando runner GraphQL...");
        
        final GraphQLExtractionService service = new GraphQLExtractionService();
        service.execute(dataInicio, dataInicio, entidade);
    }
    
    /**
     * FASE 3: Executa extração APENAS de Faturas GraphQL para um intervalo de datas.
     * Este método é chamado APÓS todas as outras entidades serem extraídas.
     * 
     * Motivo: O enriquecimento de faturas_graphql é muito demorado (50+ minutos),
     * então as outras entidades são priorizadas para garantir dados parciais atualizados no BI.
     * 
     * @param dataInicio Data de início do período (não pode ser null)
     * @param dataFim Data de fim do período (não pode ser null)
     * @throws IllegalArgumentException Se dataInicio ou dataFim forem null, ou se dataFim < dataInicio
     * @throws Exception Se houver falha na extração
     */
    public static void executarFaturasGraphQLPorIntervalo(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
        validarIntervalo(dataInicio, dataFim);
        
        log.info("🔄 [FASE 3] Executando extração de Faturas GraphQL por último...");
        log.info("📅 Período: {} a {}", dataInicio, dataFim);
        
        final GraphQLExtractionService service = new GraphQLExtractionService();
        service.execute(dataInicio, dataFim, br.com.extrator.util.validacao.ConstantesEntidades.FATURAS_GRAPHQL);
    }
    
    /**
     * Valida intervalo de datas.
     * 
     * @param dataInicio Data de início
     * @param dataFim Data de fim
     * @throws IllegalArgumentException Se parâmetros forem inválidos
     */
    private static void validarIntervalo(final LocalDate dataInicio, final LocalDate dataFim) {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser null");
        Objects.requireNonNull(dataFim, "dataFim não pode ser null");
        
        if (dataFim.isBefore(dataInicio)) {
            throw new IllegalArgumentException(
                String.format("dataFim (%s) não pode ser anterior a dataInicio (%s)", dataFim, dataInicio)
            );
        }
    }
}
