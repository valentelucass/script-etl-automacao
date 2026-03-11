/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoDadosCompletoUseCase.java
Classe  : ValidacaoDadosCompletoUseCase (class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Executa suite completa de validacoes SQL (prova dos 9: completude, gaps, integridade, qualidade, metadata).

Conecta com:
- ValidacaoSqlScriptLoader, ValidacaoSqlBatchExecutor (delegacao)

Fluxo geral:
1) executar() carrega 5 scripts SQL de validacao.
2) Executa cada script no BD.
3) Exibe resultados e resumo final.

Estrutura interna:
Atributos-chave:
- SCRIPTS_VALIDACAO: array com caminhos dos 5 scripts.
- scriptLoader, batchExecutor: helpers.
Metodos principais:
- executar(): orquestra suite.
- exibirResumoFinal(), exibirBanner(): exibicao.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import br.com.extrator.suporte.banco.GerenciadorConexao;
import br.com.extrator.suporte.console.BannerUtil;
import br.com.extrator.suporte.console.LoggerConsole;

public class ValidacaoDadosCompletoUseCase {
    private static final LoggerConsole log = LoggerConsole.getLogger(ValidacaoDadosCompletoUseCase.class);
    private static final String[] SCRIPTS_VALIDACAO = {
        "validacao/validar_completude_todas_entidades.sql",
        "validacao/validar_gaps_sequencias.sql",
        "validacao/validar_integridade_chaves.sql",
        "validacao/validar_qualidade_dados.sql",
        "validacao/validar_metadata_backup.sql"
    };

    private final ValidacaoSqlScriptLoader scriptLoader;
    private final ValidacaoSqlBatchExecutor batchExecutor;

    public ValidacaoDadosCompletoUseCase() {
        this(new ValidacaoSqlScriptLoader(), new ValidacaoSqlBatchExecutor(log));
    }

    ValidacaoDadosCompletoUseCase(final ValidacaoSqlScriptLoader scriptLoader,
                                  final ValidacaoSqlBatchExecutor batchExecutor) {
        this.scriptLoader = scriptLoader;
        this.batchExecutor = batchExecutor;
    }

    public void executar() throws Exception {
        exibirBanner();
        log.console("");
        log.info("Este comando executa validacoes profundas para garantir integridade dos dados.");
        log.info("Baseado na 'Prova dos 9' - a paranoia saudavel de todo engenheiro de dados.");
        log.console("");

        try (Connection conn = GerenciadorConexao.obterConexao()) {
            log.info("Conexao com banco de dados estabelecida");
            log.console("");

            int scriptsExecutados = 0;
            int scriptsComErro = 0;
            final List<String> erros = new ArrayList<>();

            for (final String scriptName : SCRIPTS_VALIDACAO) {
                try {
                    log.console("===============================================================================");
                    log.info("Executando: {}", scriptName);
                    log.console("===============================================================================");
                    log.console("");

                    final String sql = scriptLoader.carregar(scriptName);
                    batchExecutor.executar(conn, sql, scriptName);

                    scriptsExecutados++;
                    log.console("");
                    log.info("{} executado com sucesso", scriptName);
                    log.console("");
                } catch (final Exception e) {
                    scriptsComErro++;
                    final String erro = String.format("Erro ao executar %s: %s", scriptName, e.getMessage());
                    log.error(erro, e);
                    erros.add(erro);
                    log.console("");
                }
            }

            exibirResumoFinal(scriptsExecutados, scriptsComErro, erros);
        } catch (final SQLException e) {
            log.error("Erro ao conectar ao banco de dados: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void exibirBanner() {
        try {
            BannerUtil.exibirBanner("banners/banner-validacao.txt");
        } catch (final Exception e) {
            log.console("===============================================================================");
            log.info("             VALIDACAO COMPLETA DOS DADOS - PROVA DOS 9");
            log.console("===============================================================================");
        }
    }

    private void exibirResumoFinal(final int scriptsExecutados,
                                   final int scriptsComErro,
                                   final List<String> erros) {
        log.console("===============================================================================");
        log.info("                        RESUMO DA VALIDACAO");
        log.console("===============================================================================");
        log.console("");
        log.info("Scripts executados: {}/{}", scriptsExecutados, SCRIPTS_VALIDACAO.length);

        if (scriptsComErro > 0) {
            log.error("Scripts com erro: {}", scriptsComErro);
            log.console("");
            log.info("Erros encontrados:");
            for (final String erro : erros) {
                log.error("  {}", erro);
            }
        } else {
            log.info("Todos os scripts foram executados com sucesso");
        }

        log.console("");
        log.console("===============================================================================");
        log.info("INTERPRETACAO DOS RESULTADOS:");
        log.console("===============================================================================");
        log.console("");
        log.info("1. COMPLETUDE: Numeros da API devem coincidir com o Banco");
        log.info("2. GAPS: Pequenos gaps sao normais, gaps grandes podem indicar falhas");
        log.info("3. INTEGRIDADE: Nao deve haver duplicados em chaves primarias");
        log.info("4. QUALIDADE: Campos criticos nao devem estar NULL");
        log.info("5. METADATA: Deve estar 100% preenchido (backup JSON)");
        log.console("");
        log.info("Se todas as validacoes passaram, os dados estao consistentes");
        log.console("");
    }
}
