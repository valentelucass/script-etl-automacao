package br.com.extrator.integracao.raster;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.dominio.raster.RasterParadaDTO;
import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.suporte.configuracao.ConfigRaster;
import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;
import br.com.extrator.suporte.log.SensitiveDataSanitizer;
import br.com.extrator.suporte.mapeamento.MapperUtil;

public class ClienteApiRaster {
    private static final Logger logger = LoggerFactory.getLogger(ClienteApiRaster.class);
    private static final int LIMITE_ALERTA_REGISTROS = 500;
    private static final String MOTIVO_LOTE_RASTER_500_REGISTROS = "LOTE_RASTER_500_REGISTROS";
    private static final String MOTIVO_RESULTADO_RASTER_NULO = "RESULTADO_RASTER_NULO";
    private static final String MOTIVO_INCOMPLETO_RASTER = "INCOMPLETO_RASTER";
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String METODO_EVENTO_FIM_VIAGEM = "%22getEventoFimViagem%22";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ClienteApiRaster() {
        this(
            HttpClient.newBuilder()
                .connectTimeout(ConfigRaster.obterTimeout())
                .build(),
            MapperUtil.sharedJson()
        );
    }

    ClienteApiRaster(final HttpClient httpClient, final ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    public ResultadoExtracao<RasterViagemDTO> buscarEventoFimViagem(final LocalDate dataInicio,
                                                                    final LocalDate dataFim) {
        final LocalDate inicioResolvido = resolverData(dataInicio);
        final LocalDate fimResolvido = resolverData(dataFim);
        final int maxDiasPorJanela = ConfigRaster.obterMaxDiasPorJanela();
        return buscarEventoFimViagemParticionado(inicioResolvido, fimResolvido, maxDiasPorJanela);
    }

    private ResultadoExtracao<RasterViagemDTO> buscarEventoFimViagemParticionado(final LocalDate dataInicio,
                                                                                 final LocalDate dataFim,
                                                                                 final int maxDiasPorJanela) {
        if (deveParticionarPorTamanho(dataInicio, dataFim, maxDiasPorJanela)) {
            logger.info(
                "Particionando consulta Raster antes da chamada HTTP: {} a {} excede {} dia(s)",
                dataInicio,
                dataFim,
                maxDiasPorJanela
            );
            return consultarPartes(dataInicio, dataFim, maxDiasPorJanela);
        }

        final ResultadoExtracao<RasterViagemDTO> resultado = buscarEventoFimViagemUmaJanela(dataInicio, dataFim);
        if (!deveParticionar(resultado, dataInicio, dataFim)) {
            return resultado;
        }

        logger.warn(
            "Particionando consulta Raster apos lote limite de {} registros: {} a {}",
            LIMITE_ALERTA_REGISTROS,
            dataInicio,
            dataFim
        );
        return consultarPartes(dataInicio, dataFim, maxDiasPorJanela);
    }

    private ResultadoExtracao<RasterViagemDTO> consultarPartes(final LocalDate dataInicio,
                                                               final LocalDate dataFim,
                                                               final int maxDiasPorJanela) {
        final LocalDate meio = dataInicio.plusDays(ChronoUnit.DAYS.between(dataInicio, dataFim) / 2);
        final ResultadoExtracao<RasterViagemDTO> primeiraMetade =
            buscarEventoFimViagemParticionado(dataInicio, meio, maxDiasPorJanela);
        final ResultadoExtracao<RasterViagemDTO> segundaMetade =
            buscarEventoFimViagemParticionado(meio.plusDays(1), dataFim, maxDiasPorJanela);
        return combinarResultados(primeiraMetade, segundaMetade);
    }

    private boolean deveParticionarPorTamanho(final LocalDate dataInicio,
                                              final LocalDate dataFim,
                                              final int maxDiasPorJanela) {
        return dataInicio != null
            && dataFim != null
            && dataInicio.isBefore(dataFim)
            && ChronoUnit.DAYS.between(dataInicio, dataFim) + 1L > maxDiasPorJanela;
    }

    ResultadoExtracao<RasterViagemDTO> buscarEventoFimViagemUmaJanela(final LocalDate dataInicio,
                                                                      final LocalDate dataFim) {
        try {
            final String body = montarPayload(dataInicio, dataFim);
            final HttpRequest request = HttpRequest.newBuilder(URI.create(montarUrlEventoFimViagem()))
                .timeout(ConfigRaster.obterTimeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

            final HttpResponse<String> response = GerenciadorRequisicaoHttp.getInstance()
                .executarRequisicaoEstrita(httpClient, request, "raster");
            return parseResponse(response.body());
        } catch (final IOException e) {
            throw new IllegalStateException("Falha ao montar payload Raster: " + e.getMessage(), e);
        } catch (final RuntimeException e) {
            final String mensagem = SensitiveDataSanitizer.sanitize(e.getMessage());
            throw new IllegalStateException(mensagem, e);
        }
    }

    private boolean deveParticionar(final ResultadoExtracao<RasterViagemDTO> resultado,
                                    final LocalDate dataInicio,
                                    final LocalDate dataFim) {
        return resultado != null
            && !resultado.isCompleto()
            && MOTIVO_LOTE_RASTER_500_REGISTROS.equals(resultado.getMotivoInterrupcao())
            && dataInicio != null
            && dataFim != null
            && dataInicio.isBefore(dataFim);
    }

    private ResultadoExtracao<RasterViagemDTO> combinarResultados(
        final ResultadoExtracao<RasterViagemDTO> primeiraMetade,
        final ResultadoExtracao<RasterViagemDTO> segundaMetade
    ) {
        final List<RasterViagemDTO> viagens = new ArrayList<>();
        int paginasProcessadas = 0;
        int registrosExtraidos = 0;
        String motivoIncompletude = null;

        final List<ResultadoExtracao<RasterViagemDTO>> resultados = new ArrayList<>();
        resultados.add(primeiraMetade);
        resultados.add(segundaMetade);
        for (final ResultadoExtracao<RasterViagemDTO> resultado : resultados) {
            if (resultado == null) {
                motivoIncompletude = primeiroMotivo(motivoIncompletude, MOTIVO_RESULTADO_RASTER_NULO);
                continue;
            }
            viagens.addAll(resultado.getDados());
            paginasProcessadas += resultado.getPaginasProcessadas();
            registrosExtraidos += resultado.getRegistrosExtraidos();
            if (!resultado.isCompleto()) {
                motivoIncompletude = primeiroMotivo(
                    motivoIncompletude,
                    motivoOuPadrao(resultado.getMotivoInterrupcao())
                );
            }
        }

        if (motivoIncompletude == null) {
            return ResultadoExtracao.completo(viagens, paginasProcessadas, registrosExtraidos);
        }
        return ResultadoExtracao.incompleto(viagens, motivoIncompletude, paginasProcessadas, registrosExtraidos);
    }

    private String primeiroMotivo(final String motivoAtual, final String novoMotivo) {
        return motivoAtual != null && !motivoAtual.isBlank() ? motivoAtual : novoMotivo;
    }

    private String motivoOuPadrao(final String motivo) {
        return motivo != null && !motivo.isBlank() ? motivo : MOTIVO_INCOMPLETO_RASTER;
    }

    ResultadoExtracao<RasterViagemDTO> parseResponse(final String responseBody) {
        try {
            final JsonNode root = objectMapper.readTree(responseBody == null ? "{}" : responseBody);
            if (root.hasNonNull("error") || root.hasNonNull("Erro")) {
                throw new IllegalStateException(
                    "Raster retornou erro: " + SensitiveDataSanitizer.sanitize(root.toString())
                );
            }

            final List<RasterViagemDTO> viagens = new ArrayList<>();
            final JsonNode resultNode = obterFilhoCaseInsensitive(root, "result");
            if (resultNode != null && !resultNode.isMissingNode() && !resultNode.isNull()) {
                extrairViagensDeResult(resultNode, viagens);
            } else {
                extrairViagensDeResult(root, viagens);
            }

            if (viagens.size() >= LIMITE_ALERTA_REGISTROS) {
                return ResultadoExtracao.incompleto(
                    viagens,
                    MOTIVO_LOTE_RASTER_500_REGISTROS,
                    1,
                    viagens.size()
                );
            }
            return ResultadoExtracao.completo(viagens, 1, viagens.size());
        } catch (final IOException e) {
            throw new IllegalStateException("Resposta Raster invalida: " + e.getMessage(), e);
        }
    }

    private LocalDate resolverData(final LocalDate data) {
        return data != null ? data : LocalDate.now();
    }

    private String montarPayload(final LocalDate dataInicio, final LocalDate dataFim) throws IOException {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("Ambiente", ConfigRaster.obterAmbiente());
        payload.put("Login", ConfigRaster.obterLoginObrigatorio());
        payload.put("Senha", ConfigRaster.obterSenhaObrigatoria());
        payload.put("TipoRetorno", "JSON");
        payload.put("DataInicial", formatarData(dataInicio));
        payload.put("DataFinal", formatarData(dataFim));
        payload.put("StatusViagem", ConfigRaster.obterStatusViagem());
        return objectMapper.writeValueAsString(payload);
    }

    private String montarUrlEventoFimViagem() {
        final String base = ConfigRaster.obterBaseUrl();
        final String normalizada = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalizada + "/" + METODO_EVENTO_FIM_VIAGEM;
    }

    private String formatarData(final LocalDate data) {
        return resolverData(data).format(FORMATO_DATA);
    }

    private void extrairViagensDeResult(final JsonNode node, final List<RasterViagemDTO> viagens) throws IOException {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (final JsonNode item : node) {
                extrairViagensDeResult(item, viagens);
            }
            return;
        }
        final JsonNode viagensNode = obterFilhoCaseInsensitive(node, "Viagens");
        if (viagensNode == null || viagensNode.isMissingNode() || viagensNode.isNull()) {
            if (node.has("CodSolicitacao")) {
                viagens.add(converterViagem(node));
            }
            return;
        }
        if (viagensNode.isArray()) {
            for (final JsonNode viagemNode : viagensNode) {
                viagens.add(converterViagem(viagemNode));
            }
            return;
        }
        if (viagensNode.isObject()) {
            viagens.add(converterViagem(viagensNode));
        }
    }

    private RasterViagemDTO converterViagem(final JsonNode viagemNode) throws IOException {
        final RasterViagemDTO viagem = objectMapper.treeToValue(viagemNode, RasterViagemDTO.class);
        viagem.setMetadata(viagemNode.toString());

        final JsonNode paradasNode = obterFilhoCaseInsensitive(viagemNode, "ColetasEntregas");
        if (paradasNode != null && paradasNode.isArray()) {
            final List<RasterParadaDTO> paradas = viagem.getColetasEntregas();
            for (int i = 0; i < paradas.size() && i < paradasNode.size(); i++) {
                paradas.get(i).setMetadata(paradasNode.get(i).toString());
            }
        }
        return viagem;
    }

    private JsonNode obterFilhoCaseInsensitive(final JsonNode node, final String nome) {
        if (node == null || nome == null || !node.isObject()) {
            return null;
        }
        final JsonNode direto = node.get(nome);
        if (direto != null) {
            return direto;
        }
        final java.util.Iterator<String> nomes = node.fieldNames();
        while (nomes.hasNext()) {
            final String atual = nomes.next();
            if (nome.equalsIgnoreCase(atual)) {
                return node.get(atual);
            }
        }
        return null;
    }
}
