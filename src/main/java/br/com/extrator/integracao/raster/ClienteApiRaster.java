package br.com.extrator.integracao.raster;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.extrator.dominio.raster.RasterParadaDTO;
import br.com.extrator.dominio.raster.RasterViagemDTO;
import br.com.extrator.integracao.ResultadoExtracao;
import br.com.extrator.suporte.configuracao.ConfigRaster;
import br.com.extrator.suporte.http.GerenciadorRequisicaoHttp;
import br.com.extrator.suporte.log.SensitiveDataSanitizer;
import br.com.extrator.suporte.mapeamento.MapperUtil;

public class ClienteApiRaster {
    private static final int LIMITE_ALERTA_REGISTROS = 500;
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
                    "LOTE_RASTER_500_REGISTROS",
                    1,
                    viagens.size()
                );
            }
            return ResultadoExtracao.completo(viagens, 1, viagens.size());
        } catch (final IOException e) {
            throw new IllegalStateException("Resposta Raster invalida: " + e.getMessage(), e);
        }
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
        final LocalDate resolvida = data != null ? data : LocalDate.now();
        return resolvida.format(FORMATO_DATA);
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
