/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/dataexport/DataExportRunner.java
Classe  : DataExportRunner (class)
Pacote  : br.com.extrator.integracao.dataexport
Modulo  : Componente Java
Papel   : Implementa comportamento de data export runner.

Conecta com:
- DataExportExtractionService (runners.dataexport.services)
- LoggerConsole (util.console)

Fluxo geral:
1) Define comportamento principal deste modulo.
2) Interage com camadas relacionadas do sistema.
3) Entrega resultado para o fluxo chamador.

Estrutura interna:
Metodos principais:
- DataExportRunner(): realiza operacao relacionada a "data export runner".
- validarIntervalo(...2 args): aplica regras de validacao e consistencia.
Atributos-chave:
- log: campo de estado para "log".
[DOC-FILE-END]============================================================== */

package br.com.extrator.integracao.dataexport;

import java.time.LocalDate;
import java.util.Objects;

import br.com.extrator.integracao.dataexport.services.DataExportExtractionService;
import br.com.extrator.suporte.console.LoggerConsole;

/**
 * Runner independente para a API Data Export (Manifestos, Cotações e Localização de Carga).
 * Refatorado para usar serviços de orquestração.
 * 
 * CORREÇÃO ALTO #2: Validação de parâmetros NULL adicionada
 */
@Deprecated(since = "2026-03-06", forRemoval = false)
public final class DataExportRunner {
    private static final LoggerConsole log = LoggerConsole.getLogger(DataExportRunner.class);

    @Deprecated(since = "2026-03-06", forRemoval = false)
    private DataExportRunner() {}

    /**
     * Executa extração de todas as entidades Data Export.
     *
     * @param dataInicio Data de início para filtro (não pode ser null)
     * @throws IllegalArgumentException Se dataInicio for null
     * @throws Exception Se houver falha na extração
     */
    @Deprecated(since = "2026-03-06", forRemoval = false)
    public static void executar(final LocalDate dataInicio) throws Exception {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser null");
        executar(dataInicio, null);
    }

    /**
     * Executa extração de todas as entidades Data Export para um intervalo de datas.
     *
     * @param dataInicio Data de início do período (não pode ser null)
     * @param dataFim Data de fim do período (não pode ser null)
     * @throws IllegalArgumentException Se dataInicio ou dataFim forem null, ou se dataFim < dataInicio
     * @throws Exception Se houver falha na extração
     */
    @Deprecated(since = "2026-03-06", forRemoval = false)
    public static void executarPorIntervalo(final LocalDate dataInicio, final LocalDate dataFim) throws Exception {
        validarIntervalo(dataInicio, dataFim);
        executarPorIntervalo(dataInicio, dataFim, null);
    }

    /**
     * Executa extração de entidade(s) Data Export específica(s) para um intervalo de datas.
     *
     * @param dataInicio Data de início do período (não pode ser null)
     * @param dataFim Data de fim do período (não pode ser null)
     * @param entidade Nome da entidade (null = todas)
     * @throws IllegalArgumentException Se dataInicio ou dataFim forem null, ou se dataFim < dataInicio
     * @throws Exception Se houver falha na extração
     */
    @Deprecated(since = "2026-03-06", forRemoval = false)
    public static void executarPorIntervalo(final LocalDate dataInicio, final LocalDate dataFim, final String entidade) throws Exception {
        validarIntervalo(dataInicio, dataFim);
        
        log.info("🔄 Executando runner DataExport - Período: {} a {}", dataInicio, dataFim);
        
        final DataExportExtractionService service = new DataExportExtractionService();
        service.execute(dataInicio, dataFim, entidade);
    }

    /**
     * Executa extração de entidade(s) Data Export específica(s).
     *
     * @param dataInicio Data de início para filtro (não pode ser null)
     * @param entidade Nome da entidade (null = todas)
     * @throws IllegalArgumentException Se dataInicio for null
     * @throws Exception Se houver falha na extração
     */
    @Deprecated(since = "2026-03-06", forRemoval = false)
    public static void executar(final LocalDate dataInicio, final String entidade) throws Exception {
        Objects.requireNonNull(dataInicio, "dataInicio não pode ser null");
        
        log.info("🔄 Executando runner DataExport...");
        
        final DataExportExtractionService service = new DataExportExtractionService();
        service.execute(dataInicio, dataInicio, entidade);
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
