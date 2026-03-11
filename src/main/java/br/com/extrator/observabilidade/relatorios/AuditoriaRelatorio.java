/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/relatorios/AuditoriaRelatorio.java
Classe  : AuditoriaRelatorio (class)
Pacote  : br.com.extrator.observabilidade.relatorios
Modulo  : Modulo de auditoria
Papel   : Gera relatorio textual consolidado da auditoria.

Conecta com:
- ResultadoAuditoria (auditoria.modelos)
- ResultadoValidacaoEntidade (auditoria.modelos)

Fluxo geral:
1) Recebe resultado da auditoria.
2) Monta conteudo markdown consolidado.
3) Persiste arquivo em disco na pasta de relatorios.

Estrutura interna:
Metodos principais:
- gerarRelatorio(...1 args): escreve relatorio final no disco.
- montarConteudo(...1 args): monta texto detalhado do relatorio.
Atributos-chave:
- logger: logger da classe para diagnostico.
- NOME_ARQUIVO_FMT: formato de timestamp para nome de arquivo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.observabilidade.relatorios;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.observabilidade.modelos.ResultadoAuditoria;
import br.com.extrator.observabilidade.modelos.ResultadoValidacaoEntidade;

/**
 * Gera relatorio textual de auditoria em disco.
 */
public class AuditoriaRelatorio {
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaRelatorio.class);
    private static final DateTimeFormatter NOME_ARQUIVO_FMT =
        DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").withZone(ZoneId.systemDefault());

    public void gerarRelatorio(final ResultadoAuditoria resultado) {
        if (resultado == null) {
            logger.warn("Resultado de auditoria nulo; relatorio nao sera gerado.");
            return;
        }

        final Path diretorio = Paths.get("runtime/reports");
        final String timestamp = NOME_ARQUIVO_FMT.format(Instant.now());
        final Path arquivo = diretorio.resolve("auditoria_dados_" + timestamp + ".md");

        try {
            Files.createDirectories(diretorio);
            Files.writeString(arquivo, montarConteudo(resultado), StandardCharsets.UTF_8);
            logger.info("Relatorio de auditoria gerado em {}", arquivo.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Falha ao gerar relatorio de auditoria: {}", e.getMessage(), e);
        }
    }

    private String montarConteudo(final ResultadoAuditoria resultado) {
        final StringBuilder sb = new StringBuilder();
        sb.append("# Relatorio de Auditoria\n\n");
        sb.append("- Data geracao: ").append(Instant.now()).append('\n');
        sb.append("- Periodo inicio: ").append(resultado.getDataInicio()).append('\n');
        sb.append("- Periodo fim: ").append(resultado.getDataFim()).append('\n');
        sb.append("- Status geral: ").append(resultado.getStatusGeral()).append('\n');
        if (resultado.getErro() != null && !resultado.getErro().isBlank()) {
            sb.append("- Erro geral: ").append(resultado.getErro()).append('\n');
        }
        sb.append('\n');

        final Map<String, ResultadoValidacaoEntidade> mapa = resultado.getResultadosValidacaoMap();
        if (mapa.isEmpty()) {
            sb.append("## Resultado consolidado\n\n");
            final List<String> linhas = resultado.getResultadosValidacao();
            if (linhas == null || linhas.isEmpty()) {
                sb.append("Sem detalhes de validacao disponiveis.\n");
            } else {
                for (String linha : linhas) {
                    sb.append("- ").append(linha).append('\n');
                }
            }
            return sb.toString();
        }

        sb.append("## Detalhes por Entidade\n\n");
        for (Map.Entry<String, ResultadoValidacaoEntidade> entry : mapa.entrySet()) {
            final String entidade = entry.getKey();
            final ResultadoValidacaoEntidade validacao = entry.getValue();

            sb.append("### ").append(entidade).append('\n');
            sb.append("- Status: ").append(validacao.getStatus()).append('\n');
            sb.append("- Registros banco: ").append(validacao.getTotalRegistros()).append('\n');

            if (validacao.getRegistrosEsperadosApi() > 0) {
                sb.append("- Registros API: ").append(validacao.getRegistrosEsperadosApi()).append('\n');
                sb.append("- Diferenca: ").append(validacao.getDiferencaRegistros()).append('\n');
                sb.append("- Completude: ").append(String.format("%.1f%%", validacao.getPercentualCompletude())).append('\n');
            }

            if (validacao.getErro() != null && !validacao.getErro().isBlank()) {
                sb.append("- Erro: ").append(validacao.getErro()).append('\n');
            }

            final List<String> observacoes = validacao.getObservacoes();
            if (observacoes != null && !observacoes.isEmpty()) {
                sb.append("- Observacoes:\n");
                for (String observacao : observacoes) {
                    sb.append("  - ").append(observacao).append('\n');
                }
            }

            sb.append('\n');
        }

        return sb.toString();
    }
}
