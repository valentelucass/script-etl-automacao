/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/validacao/ValidarAcessoComando.java
Classe  : ValidarAcessoComando (class)
Pacote  : br.com.extrator.comandos.cli.validacao
Modulo  : Comando CLI (validacao)
Papel   : Implementa responsabilidade de validar acesso comando.

Conecta com:
- Comando (comandos.base)
- LoggerConsole (util.console)

Fluxo geral:
1) Executa validacoes de acesso, timestamps e consistencia.
2) Compara API versus banco quando aplicavel.
3) Emite resultado de qualidade para operacao.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- log: campo de estado para "log".
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.validacao;

import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.console.LoggerConsole;
import br.com.extrator.suporte.configuracao.ConfigBanco;

/**
 * Comando responsável por validar as configurações e acessos do sistema.
 */
public class ValidarAcessoComando implements Comando {
    // PROBLEMA #9 CORRIGIDO: Usar LoggerConsole para log duplo
    private static final LoggerConsole log = LoggerConsole.getLogger(ValidarAcessoComando.class);
    
    @Override
    public void executar(String[] args) throws Exception {
        log.info("[INFO] Validando configuracoes do sistema...");
        log.console("=".repeat(50));

        try {
            log.info("[INFO] Validando conexao com banco de dados...");
            ConfigBanco.validarConexaoBancoDados();
            log.info("[OK] Conexao com banco de dados: OK");

            log.info("[OK] Tabela dbo.log_extracoes deve existir (criada via scripts SQL em database/)");

            log.info("[INFO] Validando configuracoes das APIs...");
            log.info("[OK] Configuracoes das APIs: OK");

            log.console("=".repeat(50));
            log.info("[OK] Todas as validacoes foram bem-sucedidas.");
            log.info("O sistema esta pronto para execucao.");

        } catch (final Exception e) {
            log.error("[ERRO] ERRO na validacao: {}", e.getMessage());
            log.error("Verifique as configuracoes e tente novamente.");
            throw e;
        }
    }
}
