/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/auditoria/servicos/AuditoriaService.java
Classe  : AuditoriaService (class)
Pacote  : br.com.extrator.auditoria.servicos
Modulo  : Servico de auditoria
Papel   : Implementa responsabilidade de auditoria service.

Conecta com:
- StatusAuditoria (auditoria.enums)
- StatusValidacao (auditoria.enums)
- ResultadoAuditoria (auditoria.modelos)
- ResultadoValidacaoEntidade (auditoria.modelos)
- AuditoriaRelatorio (auditoria.relatorios)
- AuditoriaValidator (auditoria.validacao)
- GerenciadorConexao (util.banco)
- ConstantesEntidades (util.validacao)

Fluxo geral:
1) Executa regras de validacao de qualidade/ETL.
2) Consolida indicadores e status de auditoria.
3) Publica resultado para relatorio tecnico.

Estrutura interna:
Metodos principais:
- AuditoriaService(): realiza operacao relacionada a "auditoria service".
- executarAuditoriaCompleta(): executa o fluxo principal desta responsabilidade.
- executarAuditoriaPorPeriodo(...2 args): executa o fluxo principal desta responsabilidade.
- executarAuditoriaRapida(): executa o fluxo principal desta responsabilidade.
- validarEntidadeEspecifica(...1 args): aplica regras de validacao e consistencia.
Atributos-chave:
- logger: logger da classe para diagnostico.
- validator: campo de estado para "validator".
- relatorio: campo de estado para "relatorio".
[DOC-FILE-END]============================================================== */

package br.com.extrator.auditoria.servicos;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.auditoria.enums.StatusAuditoria;
import br.com.extrator.auditoria.enums.StatusValidacao;
import br.com.extrator.auditoria.modelos.ResultadoAuditoria;
import br.com.extrator.auditoria.modelos.ResultadoValidacaoEntidade;
import br.com.extrator.auditoria.relatorios.AuditoriaRelatorio;
import br.com.extrator.auditoria.validacao.AuditoriaValidator;
import br.com.extrator.util.banco.GerenciadorConexao;
import br.com.extrator.util.validacao.ConstantesEntidades;

/**
 * Serviço principal de auditoria que coordena a validação da completude
 * dos dados extraídos das APIs do ESL Cloud.
 * 
 * Verifica se todas as entidades foram extraídas corretamente e gera
 * relatórios de auditoria para identificar possíveis inconsistências.
 */
public class AuditoriaService {
    private static final Logger logger = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaValidator validator;
    private final AuditoriaRelatorio relatorio;

    public AuditoriaService() {
        this.validator = new AuditoriaValidator();
        this.relatorio = new AuditoriaRelatorio();
    }

    /**
     * Executa auditoria completa dos dados extraídos nas últimas 24 horas.
     * 
     * @return ResultadoAuditoria com o resultado da auditoria
     */
    public ResultadoAuditoria executarAuditoriaCompleta() {
        logger.info("🔍 Iniciando auditoria completa dos dados extraídos");

        final Instant agora = Instant.now();
        final Instant inicio24h = agora.minusSeconds(24 * 60 * 60); // 24 horas em segundos

        return executarAuditoriaPorPeriodo(inicio24h, agora);
    }

    /**
     * Executa auditoria dos dados extraídos em um período específico.
     * 
     * @param dataInicio Data de início do período
     * @param dataFim    Data de fim do período
     * @return ResultadoAuditoria com o resultado da auditoria
     */
    public ResultadoAuditoria executarAuditoriaPorPeriodo(final Instant dataInicio, final Instant dataFim) {
        final ResultadoAuditoria resultado = new ResultadoAuditoria();
        resultado.setDataInicio(dataInicio);
        resultado.setDataFim(dataFim);
        resultado.setDataExecucao(Instant.now());

        try (Connection conexao = GerenciadorConexao.obterConexao()) {
            // ✅ FASE 1: Verificar que todas as tabelas existem (NÃO criar - schema deve ser gerenciado via scripts SQL)
            logger.info("🔍 Verificando que todas as tabelas existem...");
            validator.verificarTodasTabelasExistem(conexao);
            
            // ✅ FASE 2: Validar usando dados do log_extracoes (não buscar API atual)
            // CORREÇÃO: Usar dados do log_extracoes ao invés de buscar totais atuais da API
            // Isso evita falsos-positivos quando a API foi populada após a extração
            final List<String> ents = List.of(
                ConstantesEntidades.COTACOES,
                ConstantesEntidades.COLETAS,
                ConstantesEntidades.CONTAS_A_PAGAR,
                ConstantesEntidades.FATURAS_POR_CLIENTE,
                ConstantesEntidades.FRETES,
                ConstantesEntidades.FATURAS_GRAPHQL,
                ConstantesEntidades.MANIFESTOS,
                ConstantesEntidades.LOCALIZACAO_CARGAS
            );

            for (final String e : ents) {
                try {
                    // Sempre usar validator.validarEntidade que já usa dados do log_extracoes
                    final ResultadoValidacaoEntidade v = validator.validarEntidade(conexao, e, dataInicio, dataFim);
                    resultado.adicionarValidacao(e, v);
                } catch (RuntimeException ex) {
                    resultado.adicionarValidacao(e, ResultadoValidacaoEntidade.erro(e, 0, ex.getMessage()));
                }
            }

            // ✅ SEMPRE gerar relatório
            resultado.determinarStatusGeral();
            relatorio.gerarRelatorio(resultado);

        } catch (SQLException | RuntimeException ex) {
            resultado.setErro(ex.getMessage());
            resultado.setStatusGeral(StatusAuditoria.ERRO);
            try {
                relatorio.gerarRelatorio(resultado);
            } catch (final Exception e) {
            }
        }

        return resultado;
    }


    /**
     * Executa auditoria rápida apenas verificando se existem dados recentes.
     * 
     * @return true se existem dados das últimas 24 horas, false caso contrário
     */
    public boolean executarAuditoriaRapida() {
        logger.info("⚡ Executando auditoria rápida");

        try (final Connection conexao = GerenciadorConexao.obterConexao()) {
            final Instant agora = Instant.now();
            final Instant inicio24h = agora.minusSeconds(24 * 60 * 60); // 24 horas em segundos

            return validator.verificarExistenciaDadosRecentes(conexao, inicio24h, agora);

        } catch (final SQLException e) {
            logger.error("❌ Erro durante auditoria rápida: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Valida uma entidade específica.
     * 
     * @param nomeEntidade Nome da entidade a ser validada
     * @return ResultadoValidacaoEntidade com o resultado da validação
     */
    public ResultadoValidacaoEntidade validarEntidadeEspecifica(final String nomeEntidade) {
        logger.info("🔍 Validando entidade específica: {}", nomeEntidade);

        try (final Connection conexao = GerenciadorConexao.obterConexao()) {
            final Instant agora = Instant.now();
            final Instant inicio24h = agora.minusSeconds(24 * 60 * 60);

            return validator.validarEntidade(conexao, nomeEntidade, inicio24h, agora);

        } catch (final SQLException e) {
            logger.error("❌ Erro ao validar entidade {}: {}", nomeEntidade, e.getMessage(), e);
            final ResultadoValidacaoEntidade resultado = new ResultadoValidacaoEntidade();
            resultado.setNomeEntidade(nomeEntidade);
            resultado.setErro("Erro de conexão: " + e.getMessage());
            resultado.setStatus(StatusValidacao.ERRO);
            return resultado;
        }
    }
}
