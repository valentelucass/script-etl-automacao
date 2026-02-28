/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/validacao/AuditorEstruturaApi.java
Classe  : AuditorEstruturaApi (class)
Pacote  : br.com.extrator.auditoria.validacao
Modulo  : Validador de auditoria
Papel   : Implementa responsabilidade de auditor estrutura api.

Conecta com:
- ClienteApiDataExport (api)
- ClienteApiGraphQL (api)
- ResultadoExtracao (api)
- CarregadorConfig (util.configuracao)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Aplica checks tecnicos sobre estrutura e dados.
2) Sinaliza inconformidades por regra.
3) Retorna diagnostico para camadas de relatorio.

Estrutura interna:
Metodos principais:
- main(...1 args): ponto de entrada da execucao.
- executar(): executa o fluxo principal desta responsabilidade.
- coletarCampos(...4 args): realiza operacao relacionada a "coletar campos".
- valor(...1 args): realiza operacao relacionada a "valor".
- escape(...1 args): realiza operacao relacionada a "escape".
Atributos-chave:
- logger: logger da classe para diagnostico.
- NOME_ARQUIVO_FMT: campo de estado para "nome arquivo fmt".
- ENDPOINTS: campo de estado para "endpoints".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.validacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.api.ClienteApiDataExport;
import br.com.extrator.api.ClienteApiGraphQL;
import br.com.extrator.api.ResultadoExtracao;
import br.com.extrator.util.configuracao.CarregadorConfig;
import br.com.extrator.util.validacao.ConstantesEntidades;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class AuditorEstruturaApi {
    private static final Logger logger = LoggerFactory.getLogger(AuditorEstruturaApi.class);
    private static final DateTimeFormatter NOME_ARQUIVO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm");
    private static final Map<String, String> ENDPOINTS = new LinkedHashMap<>();

    static {
        ENDPOINTS.put(ConstantesEntidades.COTACOES, "");
        ENDPOINTS.put(ConstantesEntidades.COLETAS, "");
        ENDPOINTS.put(ConstantesEntidades.CONTAS_A_PAGAR, "");
        ENDPOINTS.put(ConstantesEntidades.FATURAS_POR_CLIENTE, "");
        ENDPOINTS.put(ConstantesEntidades.FRETES, "");
        ENDPOINTS.put(ConstantesEntidades.MANIFESTOS, "");
        ENDPOINTS.put(ConstantesEntidades.LOCALIZACAO_CARGAS, "");
    }

    public static void main(final String[] args) {
        final int exitCode = executar();
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }

    public static int executar() {
        logger.info("Iniciando auditoria de estrutura da API");
        final ObjectMapper mapper = new ObjectMapper();
        final ClienteApiDataExport clienteDataExport = new ClienteApiDataExport();
        final ClienteApiGraphQL clienteGraphQL = new ClienteApiGraphQL();

        final String ts = LocalDateTime.now(ZoneId.systemDefault()).format(NOME_ARQUIVO_FMT);
        final Path dir = Paths.get("relatorios");
        final Path csv = dir.resolve("auditoria_api_" + ts + ".csv");

        try {
            Files.createDirectories(dir);
        } catch (final IOException e) {
            logger.error("Falha ao criar diretório de relatórios: {}", e.getMessage(), e);
            return 2;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(csv, StandardCharsets.UTF_8)) {
            writer.write("Entidade;Nome_Campo_API;Exemplo_Valor\n");

            try (Connection conn = conectar()) {
                prepararTabelaTemp(conn);
                limparTabelaTemp(conn);

                for (final Map.Entry<String, String> entry : ENDPOINTS.entrySet()) {
                    final String entidade = entry.getKey();

                    try {
                        final JsonNode item = obterAmostraEntidade(mapper, clienteDataExport, clienteGraphQL, entidade);
                        if (item == null || item.isNull()) {
                            logger.warn("Resposta sem item analisável para {}", entidade);
                            continue;
                        }

                        final Map<String, String> campos = new LinkedHashMap<>();
                        coletarCampos(item, "", campos);

                        if (campos.isEmpty()) {
                            logger.warn("Nenhum campo encontrado para {}", entidade);
                        }

                        inserirCampos(conn, entidade, campos);
                        for (final Map.Entry<String, String> c : campos.entrySet()) {
                            writer.write(escape(entidade) + ";" + escape(c.getKey()) + ";" + escape(c.getValue()) + "\n");
                        }
                        writer.flush();
                        logger.info("{} campos mapeados para {}", campos.size(), entidade);

                    } catch (final java.io.IOException | java.lang.RuntimeException ex) {
                        logger.error("Erro processando {}: {}", entidade, ex.getMessage());
                    }
                }
            } catch (final SQLException e) {
                logger.error("Erro de banco: {}", e.getMessage(), e);
                return 3;
            }

        } catch (final IOException e) {
            logger.error("Erro de escrita CSV: {}", e.getMessage(), e);
            return 4;
        }

        logger.info("Auditoria concluída: {}", csv.toString());
        return 0;
    }

    private static JsonNode obterAmostraEntidade(final ObjectMapper mapper,
                                                 final ClienteApiDataExport clienteDataExport,
                                                 final ClienteApiGraphQL clienteGraphQL,
                                                 final String entidade) throws IOException {
        return switch (entidade) {
            case ConstantesEntidades.COTACOES -> {
                final ResultadoExtracao<br.com.extrator.modelo.dataexport.cotacao.CotacaoDTO> r = clienteDataExport.buscarCotacoes();
                final java.util.List<br.com.extrator.modelo.dataexport.cotacao.CotacaoDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            case ConstantesEntidades.MANIFESTOS -> {
                final ResultadoExtracao<br.com.extrator.modelo.dataexport.manifestos.ManifestoDTO> r = clienteDataExport.buscarManifestos();
                final java.util.List<br.com.extrator.modelo.dataexport.manifestos.ManifestoDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            case ConstantesEntidades.LOCALIZACAO_CARGAS -> {
                final ResultadoExtracao<br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaDTO> r = clienteDataExport.buscarLocalizacaoCarga();
                final java.util.List<br.com.extrator.modelo.dataexport.localizacaocarga.LocalizacaoCargaDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            case ConstantesEntidades.CONTAS_A_PAGAR -> {
                final ResultadoExtracao<br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarDTO> r = clienteDataExport.buscarContasAPagar();
                final java.util.List<br.com.extrator.modelo.dataexport.contasapagar.ContasAPagarDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            case ConstantesEntidades.FATURAS_POR_CLIENTE -> {
                final ResultadoExtracao<br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO> r = clienteDataExport.buscarFaturasPorCliente();
                final java.util.List<br.com.extrator.modelo.dataexport.faturaporcliente.FaturaPorClienteDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            case ConstantesEntidades.FRETES -> {
                final ResultadoExtracao<br.com.extrator.modelo.graphql.fretes.FreteNodeDTO> r = clienteGraphQL.buscarFretes(LocalDate.now());
                final java.util.List<br.com.extrator.modelo.graphql.fretes.FreteNodeDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            case ConstantesEntidades.COLETAS -> {
                final ResultadoExtracao<br.com.extrator.modelo.graphql.coletas.ColetaNodeDTO> r = clienteGraphQL.buscarColetas(LocalDate.now());
                final java.util.List<br.com.extrator.modelo.graphql.coletas.ColetaNodeDTO> l = r.getDados();
                yield l.isEmpty() ? null : mapper.valueToTree(l.get(0));
            }
            default -> null;
        };
    }

    private static Connection conectar() throws SQLException {
        final String url = CarregadorConfig.obterUrlBancoDados();
        final String user = CarregadorConfig.obterUsuarioBancoDados();
        final String pass = CarregadorConfig.obterSenhaBancoDados();
        return DriverManager.getConnection(url, user, pass);
    }

    private static void prepararTabelaTemp(final Connection conn) throws SQLException {
        final String ddl = """
            IF OBJECT_ID('dbo.sys_auditoria_temp','U') IS NULL BEGIN \
            CREATE TABLE dbo.sys_auditoria_temp (\
            entidade NVARCHAR(100),\
            campo_api NVARCHAR(400),\
            data_auditoria DATETIME2 DEFAULT SYSDATETIME()\
            ) ;\
            CREATE INDEX IX_sys_auditoria_temp_entidade ON dbo.sys_auditoria_temp(entidade);\
            CREATE INDEX IX_sys_auditoria_temp_campo ON dbo.sys_auditoria_temp(campo_api);\
            END""";
        try (Statement st = conn.createStatement()) {
            st.execute(ddl);
        }
    }

    private static void limparTabelaTemp(final Connection conn) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("TRUNCATE TABLE dbo.sys_auditoria_temp");
        }
    }

    private static void inserirCampos(final Connection conn, final String entidade, final Map<String, String> campos) throws SQLException {
        final String sql = "INSERT INTO dbo.sys_auditoria_temp (entidade, campo_api, data_auditoria) VALUES (?, ?, SYSDATETIME())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (final String k : campos.keySet()) {
                ps.setString(1, entidade);
                ps.setString(2, k);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    

    private static void coletarCampos(final JsonNode node, final String prefix, final Map<String, String> out) {
        if (node == null || node.isNull()) return;
        if (node.isObject()) {
            final ObjectNode obj = (ObjectNode) node;
            final java.util.Iterator<String> it = obj.fieldNames();
            while (it.hasNext()) {
                final String name = it.next();
                final String key = prefix.isEmpty() ? name : prefix + "." + name;
                coletarCampos(obj.get(name), key, out);
            }
            return;
        }
        if (node.isArray()) {
            final ArrayNode arr = (ArrayNode) node;
            if (arr.size() > 0) {
                final JsonNode first = arr.get(0);
                if (first.isObject() || first.isArray()) {
                    coletarCampos(first, prefix + "[0]", out);
                } else {
                    out.put(prefix + "[0]", valor(first));
                }
            } else {
                out.put(prefix, "[]");
            }
            return;
        }
        out.put(prefix, valor(node));
    }

    private static String valor(final JsonNode n) {
        if (n == null || n.isNull()) return "";
        if (n.isTextual()) return n.asText();
        if (n.isNumber()) return n.numberValue().toString();
        if (n.isBoolean()) return Boolean.toString(n.booleanValue());
        return n.toString();
    }

    private static String escape(final String s) {
        if (s == null) return "";
        String v = s.replace("\r", " ").replace("\n", " ");
        if (v.contains(";") || v.contains("\"")) {
            v = '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}

