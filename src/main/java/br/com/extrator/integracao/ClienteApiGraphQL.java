package br.com.extrator.integracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/integracao/ClienteApiGraphQL.java
Classe  : ClienteApiGraphQL (class)
Pacote  : br.com.extrator.integracao
Modulo  : Integracao HTTP
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.extrator.integracao.constantes.ConstantesApiGraphQL;
import br.com.extrator.integracao.graphql.GraphQLQueries;
import br.com.extrator.dominio.graphql.bancos.BankAccountNodeDTO;
import br.com.extrator.dominio.graphql.coletas.ColetaNodeDTO;
import br.com.extrator.dominio.graphql.fretes.FreteNodeDTO;
import br.com.extrator.suporte.configuracao.ConfigApi;
import br.com.extrator.suporte.formatacao.FormatadorData;
import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;
import br.com.extrator.suporte.tempo.RelogioSistema;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class ClienteApiGraphQL {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiGraphQL.class);
    private static final int INTERVALO_LOG_PROGRESSO = 50;
    private static final int MAX_FALHAS_CONSECUTIVAS = 5;
    private static final Duration JANELA_REABERTURA_CIRCUITO = Duration.ofMinutes(10);

    private final Map<String, Integer> contadorFalhasConsecutivas = new ConcurrentHashMap<>();
    private final Set<String> entidadesComCircuitAberto = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> circuitosAbertosDesde = new ConcurrentHashMap<>();
    private final String urlBase;
    private final String endpointGraphQL;
    private final String token;
    private final HttpClient clienteHttp;
    private final ObjectMapper mapeadorJson;
    private final GerenciadorRequisicaoHttp gerenciadorRequisicao;
    private final Duration timeoutRequisicao;
    private final GraphQLRequestFactory requestFactory;
    private final GraphQLConnectivityValidator connectivityValidator;
    private final GraphQLLookupSupport lookupSupport;
    private final GraphQLPaginator paginator;
    private final GraphQLColetaSupport coletaSupport;
    private String executionUuid;

    public ClienteApiGraphQL() {
        this.urlBase = ConfigApi.obterUrlBaseApi();
        this.endpointGraphQL = ConfigApi.obterEndpointGraphQL();
        this.token = ConfigApi.obterTokenApiGraphQL();
        this.timeoutRequisicao = ConfigApi.obterTimeoutApiRest();
        this.clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.mapeadorJson = new ObjectMapper();
        this.gerenciadorRequisicao = GerenciadorRequisicaoHttp.getInstance();
        this.requestFactory = new GraphQLRequestFactory(
            this.mapeadorJson,
            this.urlBase,
            this.endpointGraphQL,
            this.token,
            this.timeoutRequisicao
        );

        final GraphQLTypedResponseParser typedResponseParser = new GraphQLTypedResponseParser(this.mapeadorJson);
        final GraphQLHttpExecutor httpExecutor = new GraphQLHttpExecutor(
            logger,
            this.urlBase,
            this.endpointGraphQL,
            this.token,
            this.clienteHttp,
            this.mapeadorJson,
            this.gerenciadorRequisicao,
            typedResponseParser,
            this.requestFactory
        );
        this.paginator = new GraphQLPaginator(
            logger,
            INTERVALO_LOG_PROGRESSO,
            MAX_FALHAS_CONSECUTIVAS,
            JANELA_REABERTURA_CIRCUITO,
            this.contadorFalhasConsecutivas,
            this.entidadesComCircuitAberto,
            this.circuitosAbertosDesde,
            httpExecutor::executarQueryGraphQLTipado
        );
        final GraphQLSchemaInspector schemaInspector = new GraphQLSchemaInspector(
            logger,
            this.clienteHttp,
            this.mapeadorJson,
            this.gerenciadorRequisicao,
            this.requestFactory
        );
        this.coletaSupport = new GraphQLColetaSupport(logger, schemaInspector, this.paginator);
        this.connectivityValidator = new GraphQLConnectivityValidator(
            this.clienteHttp,
            this.mapeadorJson,
            this.requestFactory,
            (cliente, requisicao, operacao) -> this.gerenciadorRequisicao.executarRequisicao(cliente, requisicao, operacao),
            logger
        );
        this.lookupSupport = new GraphQLLookupSupport(httpExecutor::executarQueryGraphQLTipado, logger);
        this.executionUuid = java.util.UUID.randomUUID().toString();
    }

    public void setExecutionUuid(final String uuid) {
        this.executionUuid = uuid;
    }

    public ResultadoExtracao<ColetaNodeDTO> buscarColetas(final LocalDate dataReferencia) {
        return buscarColetas(dataReferencia.minusDays(1), dataReferencia);
    }

    public ResultadoExtracao<FreteNodeDTO> buscarFretes(final LocalDate dataReferencia) {
        return buscarFretes(dataReferencia.minusDays(1), dataReferencia);
    }

    public ResultadoExtracao<ColetaNodeDTO> buscarColetas(final LocalDate dataInicio, final LocalDate dataFim) {
        return coletaSupport.buscarColetas(this.executionUuid, dataInicio, dataFim);
    }

    public ResultadoExtracao<ColetaNodeDTO> buscarColetas(final LocalDate dataInicio,
                                                          final LocalDate dataFim,
                                                          final PageChunkConsumer<ColetaNodeDTO> chunkConsumer) {
        return coletaSupport.buscarColetas(this.executionUuid, dataInicio, dataFim, chunkConsumer);
    }

    public ResultadoExtracao<FreteNodeDTO> buscarFretes(final LocalDate dataInicio, final LocalDate dataFim) {
        return buscarFretes(dataInicio, dataFim, null);
    }

    public ResultadoExtracao<FreteNodeDTO> buscarFretes(final LocalDate dataInicio,
                                                        final LocalDate dataFim,
                                                        final PageChunkConsumer<FreteNodeDTO> chunkConsumer) {
        logger.info("🔍 Buscando fretes via GraphQL - Período: {} a {}", dataInicio, dataFim);
        final String intervaloServiceAt = formatarDataParaApiGraphQL(dataInicio) + " - " + formatarDataParaApiGraphQL(dataFim);
        final Map<String, Object> variaveis = Map.of("params", Map.of("serviceAt", intervaloServiceAt));
        return paginator.executarQueryPaginada(
            this.executionUuid,
            GraphQLQueries.QUERY_FRETES,
            ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.FRETES),
            variaveis,
            FreteNodeDTO.class,
            chunkConsumer
        );
    }

    /**
     * Busca usuários do sistema na API legada sem filtro temporal em Individual.
     * A janela recebida e mantida apenas para telemetria/auditoria; o hard-limit
     * de paginacao de usuarios evita full load historico prolongado.
     */
    public ResultadoExtracao<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> buscarUsuariosSistema(
            final LocalDate dataInicio, final LocalDate dataFim) {
        return buscarUsuariosSistema(dataInicio, dataFim, null);
    }

    public ResultadoExtracao<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> buscarUsuariosSistema(
            final LocalDate dataInicio,
            final LocalDate dataFim,
            final PageChunkConsumer<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> chunkConsumer) {
        final LocalDate inicio = dataInicio != null ? dataInicio : RelogioSistema.hoje().minusDays(90);
        final LocalDate fim = dataFim != null ? dataFim : inicio;
        return buscarUsuariosSistema(inicio.atStartOfDay(), fim.atTime(23, 59), chunkConsumer);
    }

    public ResultadoExtracao<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> buscarUsuariosSistema(
            final LocalDateTime atualizadoApos,
            final LocalDateTime atualizadoAte) {
        return buscarUsuariosSistema(atualizadoApos, atualizadoAte, null);
    }

    public ResultadoExtracao<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> buscarUsuariosSistema(
            final LocalDateTime atualizadoApos,
            final LocalDateTime atualizadoAte,
            final PageChunkConsumer<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> chunkConsumer) {
        try {
            final LocalDateTime inicio = atualizadoApos != null
                ? atualizadoApos
                : RelogioSistema.agora().minusDays(90);
            final LocalDateTime fim = atualizadoAte != null ? atualizadoAte : RelogioSistema.agora();
            final Map<String, Object> variaveis = new HashMap<>();
            variaveis.put("params", Map.of("enabled", true));
            logger.info(
                "Buscando Usuarios do Sistema via GraphQL legado (enabled: true, sem filtro temporal; janela informativa: {} a {})",
                inicio,
                fim
            );
            return paginator.executarQueryPaginada(
                this.executionUuid,
                GraphQLQueries.QUERY_USUARIOS_SISTEMA,
                ConstantesApiGraphQL.obterNomeEntidadeApi(ConstantesEntidades.USUARIOS_SISTEMA),
                variaveis,
                br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO.class,
                chunkConsumer
            );
        } catch (final RuntimeException e) {
            logger.warn("Falha ao buscar Usuários do Sistema (incremental): {}", e.getMessage());
            return ResultadoExtracao.incompleto(new ArrayList<>(), ResultadoExtracao.MotivoInterrupcao.ERRO_API, 0, 0);
        }
    }

    /**
     * Compatibilidade para chamadas legadas: usa a query legada sem filtro temporal,
     * protegida pelo hard-limit de paginacao de usuarios no cliente Java.
     *
     * @return Resultado da extracao com usuarios habilitados dentro do limite de paginas
     */
    public ResultadoExtracao<br.com.extrator.dominio.graphql.usuarios.IndividualNodeDTO> buscarUsuariosSistema() {
        final LocalDateTime fim = RelogioSistema.agora();
        return buscarUsuariosSistema(fim.minusDays(90), fim);
    }

    public ResultadoExtracao<br.com.extrator.dominio.graphql.fretes.nfse.NfseNodeDTO> buscarNfseDireta(final LocalDate dataReferencia) {
        try {
            final LocalDate dataInicio = dataReferencia.minusDays(1);
            final String intervaloIssuedAt = dataInicio.format(FormatadorData.ISO_DATE) + " - " + dataReferencia.format(FormatadorData.ISO_DATE);
            final Map<String, Object> variaveis = Map.of("params", Map.of("issuedAt", intervaloIssuedAt));
            logger.info("Buscando NFSe via GraphQL - Período: {}", intervaloIssuedAt);
            return paginator.executarQueryPaginada(
                this.executionUuid,
                GraphQLQueries.QUERY_NFSE,
                ConstantesApiGraphQL.obterNomeEntidadeApi("nfse"),
                variaveis,
                br.com.extrator.dominio.graphql.fretes.nfse.NfseNodeDTO.class
            );
        } catch (final RuntimeException e) {
            logger.warn("Falha ao buscar NFSe direta: {}", e.getMessage());
            return ResultadoExtracao.incompleto(new ArrayList<>(), ResultadoExtracao.MotivoInterrupcao.ERRO_API, 0, 0);
        }
    }

    public boolean validarAcessoApi() {
        return connectivityValidator.validarAcessoApi();
    }

    public java.util.Optional<BankAccountNodeDTO> buscarDetalhesBanco(final Integer bankAccountId) {
        return lookupSupport.buscarDetalhesBanco(bankAccountId);
    }

    private String formatarDataParaApiGraphQL(final LocalDate data) {
        return data.format(FormatadorData.ISO_DATE);
    }
}
