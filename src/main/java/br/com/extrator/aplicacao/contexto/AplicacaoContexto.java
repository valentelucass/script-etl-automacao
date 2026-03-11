/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/contexto/AplicacaoContexto.java
Classe  : AplicacaoContexto (class)
Pacote  : br.com.extrator.aplicacao.contexto
Modulo  : Contexto DI - Aplicacao

Papel   : Registro estatico e centralizador de portas (interfaces) da aplicacao.

Conecta com:
- PipelineOrchestratorFactory
- PipelineStepsFactory
- GraphQLGateway
- DataExportGateway
- ExtractionLogQueryPort
- CompletudePort
- IntegridadeEtlPort

Fluxo geral:
1) Use cases e componentes chamam getters staticos para obter portas.
2) Bootstrap (PipelineCompositionRoot) registra implementacoes via setters.
3) Context fornece fallback no-op ou lanca IllegalStateException se ausente.

Estrutura interna:
Metodos principais:
- registrar(): sobrecargado para cada tipo de porta (acumula nas vars static).
- orchestratorFactory(), stepsFactory(), etc.: getters com lazy fallback/exception.
Atributos-chave:
- orchestratorFactory: PipelineOrchestratorFactory (volatile).
- stepsFactory: PipelineStepsFactory (volatile).
- graphQLGateway, dataExportGateway: portas de integracao HTTP (volatile).
- completudePort, integridadeEtlPort: portas de validacao (volatile).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.contexto;

import br.com.extrator.aplicacao.portas.CompletudePort;
import br.com.extrator.aplicacao.portas.DataExportGateway;
import br.com.extrator.aplicacao.portas.ExtractionLogQueryPort;
import br.com.extrator.aplicacao.portas.GraphQLGateway;
import br.com.extrator.aplicacao.portas.IntegridadeEtlPort;
import br.com.extrator.aplicacao.portas.LimpezaBancoPort;
import br.com.extrator.aplicacao.portas.ManifestoOrfaoQueryPort;
import br.com.extrator.aplicacao.portas.PipelineOrchestratorFactory;
import br.com.extrator.aplicacao.portas.PipelineStepsFactory;
import br.com.extrator.aplicacao.portas.VerificacaoTimestampPort;
import br.com.extrator.aplicacao.portas.VerificacaoTimezonePort;

/**
 * Contexto de portas da aplicacao.
 * Bootstrap inicializa este contexto antes de qualquer execucao.
 * Use cases de producao obtém suas portas daqui quando criados sem injecao explicita.
 */
public final class AplicacaoContexto {

    private static volatile PipelineOrchestratorFactory orchestratorFactory;
    private static volatile PipelineStepsFactory stepsFactory;
    private static volatile GraphQLGateway graphQLGateway;
    private static volatile DataExportGateway dataExportGateway;
    private static volatile ExtractionLogQueryPort extractionLogQueryPort;
    private static volatile CompletudePort completudePort;
    private static volatile IntegridadeEtlPort integridadeEtlPort;
    private static volatile LimpezaBancoPort limpezaBancoPort;
    private static volatile VerificacaoTimestampPort verificacaoTimestampPort;
    private static volatile VerificacaoTimezonePort verificacaoTimezonePort;
    private static volatile ManifestoOrfaoQueryPort manifestoOrfaoQueryPort;

    private AplicacaoContexto() {
    }

    public static void registrar(final PipelineOrchestratorFactory factory) {
        orchestratorFactory = factory;
    }

    public static void registrar(final PipelineStepsFactory factory) {
        stepsFactory = factory;
    }

    public static void registrar(final GraphQLGateway gateway) {
        graphQLGateway = gateway;
    }

    public static void registrar(final DataExportGateway gateway) {
        dataExportGateway = gateway;
    }

    public static void registrar(final ExtractionLogQueryPort port) {
        extractionLogQueryPort = port;
    }

    public static void registrar(final CompletudePort port) {
        completudePort = port;
    }

    public static void registrar(final IntegridadeEtlPort port) {
        integridadeEtlPort = port;
    }

    public static void registrar(final LimpezaBancoPort port) {
        limpezaBancoPort = port;
    }

    public static void registrar(final VerificacaoTimestampPort port) {
        verificacaoTimestampPort = port;
    }

    public static void registrar(final VerificacaoTimezonePort port) {
        verificacaoTimezonePort = port;
    }

    public static void registrar(final ManifestoOrfaoQueryPort port) {
        manifestoOrfaoQueryPort = port;
    }

    public static PipelineOrchestratorFactory orchestratorFactory() {
        return orchestratorFactory != null ? orchestratorFactory : () -> {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: PipelineOrchestratorFactory ausente.");
        };
    }

    public static PipelineStepsFactory stepsFactory() {
        return stepsFactory != null ? stepsFactory : (inc, qual) -> {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: PipelineStepsFactory ausente.");
        };
    }

    public static ExtractionLogQueryPort extractionLogQueryPort() {
        return extractionLogQueryPort != null ? extractionLogQueryPort : new NoOpExtractionLogQueryPort();
    }

    public static CompletudePort completudePort() {
        if (completudePort == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: CompletudePort ausente.");
        }
        return completudePort;
    }

    public static GraphQLGateway graphQLGateway() {
        if (graphQLGateway == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: GraphQLGateway ausente.");
        }
        return graphQLGateway;
    }

    public static DataExportGateway dataExportGateway() {
        if (dataExportGateway == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: DataExportGateway ausente.");
        }
        return dataExportGateway;
    }

    public static IntegridadeEtlPort integridadeEtlPort() {
        if (integridadeEtlPort == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: IntegridadeEtlPort ausente.");
        }
        return integridadeEtlPort;
    }

    public static LimpezaBancoPort limpezaBancoPort() {
        if (limpezaBancoPort == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: LimpezaBancoPort ausente.");
        }
        return limpezaBancoPort;
    }

    public static VerificacaoTimestampPort verificacaoTimestampPort() {
        if (verificacaoTimestampPort == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: VerificacaoTimestampPort ausente.");
        }
        return verificacaoTimestampPort;
    }

    public static VerificacaoTimezonePort verificacaoTimezonePort() {
        if (verificacaoTimezonePort == null) {
            throw new IllegalStateException("AplicacaoContexto nao inicializado: VerificacaoTimezonePort ausente.");
        }
        return verificacaoTimezonePort;
    }

    public static ManifestoOrfaoQueryPort manifestoOrfaoQueryPort() {
        return manifestoOrfaoQueryPort != null ? manifestoOrfaoQueryPort : () -> java.util.Optional.empty();
    }
}
