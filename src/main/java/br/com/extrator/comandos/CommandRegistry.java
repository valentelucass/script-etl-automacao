/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/CommandRegistry.java
Classe  : CommandRegistry (class)
Pacote  : br.com.extrator.comandos
Modulo  : Comando CLI
Papel   : Implementa comando relacionado a command registry.

Conecta com:
- AuditarEstruturaApiComando (comandos.auditoria)
- ExecutarAuditoriaComando (comandos.auditoria)
- Comando (comandos.base)
- ExibirAjudaComando (comandos.console)
- ExecutarExtracaoPorIntervaloComando (comandos.extracao)
- ExecutarFluxoCompletoComando (comandos.extracao)
- LoopDaemonComando (comandos.extracao)
- LoopExtracaoComando (comandos.extracao)

Fluxo geral:
1) Recebe parametros de execucao.
2) Delega operacao para servicos internos.
3) Retorna codigo/estado final do processo.

Estrutura interna:
Metodos principais:
- CommandRegistry(): realiza operacao relacionada a "command registry".
- criarMapaComandos(): instancia ou monta estrutura de dados.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import br.com.extrator.comandos.auditoria.AuditarEstruturaApiComando;
import br.com.extrator.comandos.auditoria.ExecutarAuditoriaComando;
import br.com.extrator.comandos.base.Comando;
import br.com.extrator.comandos.console.ExibirAjudaComando;
import br.com.extrator.comandos.extracao.ExecutarExtracaoPorIntervaloComando;
import br.com.extrator.comandos.extracao.ExecutarFluxoCompletoComando;
import br.com.extrator.comandos.extracao.LoopDaemonComando;
import br.com.extrator.comandos.extracao.LoopExtracaoComando;
import br.com.extrator.comandos.seguranca.AuthBootstrapComando;
import br.com.extrator.comandos.seguranca.AuthCheckComando;
import br.com.extrator.comandos.seguranca.AuthCreateUserComando;
import br.com.extrator.comandos.seguranca.AuthDisableUserComando;
import br.com.extrator.comandos.seguranca.AuthInfoComando;
import br.com.extrator.comandos.seguranca.AuthResetPasswordComando;
import br.com.extrator.comandos.utilitarios.ExportarCsvComando;
import br.com.extrator.comandos.utilitarios.LimparTabelasComando;
import br.com.extrator.comandos.utilitarios.RealizarIntrospeccaoGraphQLComando;
import br.com.extrator.comandos.utilitarios.TestarApiComando;
import br.com.extrator.comandos.validacao.ValidarAcessoComando;
import br.com.extrator.comandos.validacao.ValidarApiVsBanco24hComando;
import br.com.extrator.comandos.validacao.ValidarApiVsBanco24hDetalhadoComando;
import br.com.extrator.comandos.validacao.ValidarDadosCompletoComando;
import br.com.extrator.comandos.validacao.ValidarManifestosComando;
import br.com.extrator.comandos.validacao.VerificarTimestampsComando;
import br.com.extrator.comandos.validacao.VerificarTimezoneComando;

public final class CommandRegistry {
    private CommandRegistry() {
    }

    public static Map<String, Comando> criarMapaComandos() {
        final Map<String, Comando> comandos = new HashMap<>();
        comandos.put("--fluxo-completo", new ExecutarFluxoCompletoComando());
        comandos.put("--extracao-intervalo", new ExecutarExtracaoPorIntervaloComando());
        comandos.put("--loop", new LoopExtracaoComando());
        comandos.put("--validar", new ValidarAcessoComando());
        comandos.put("--ajuda", new ExibirAjudaComando());
        comandos.put("--help", new ExibirAjudaComando());
        comandos.put("--introspeccao", new RealizarIntrospeccaoGraphQLComando());
        comandos.put("--auditoria", new ExecutarAuditoriaComando());
        comandos.put("--auditar-api", new AuditarEstruturaApiComando());
        comandos.put("--testar-api", new TestarApiComando());
        comandos.put("--limpar-tabelas", new LimparTabelasComando());
        comandos.put("--verificar-timestamps", new VerificarTimestampsComando());
        comandos.put("--verificar-timezone", new VerificarTimezoneComando());
        comandos.put("--validar-manifestos", new ValidarManifestosComando());
        comandos.put("--validar-dados", new ValidarDadosCompletoComando());
        comandos.put("--validar-api-banco-24h", new ValidarApiVsBanco24hComando());
        comandos.put("--validar-api-banco-24h-detalhado", new ValidarApiVsBanco24hDetalhadoComando());
        comandos.put("--exportar-csv", new ExportarCsvComando());

        comandos.put("--auth-check", new AuthCheckComando());
        comandos.put("--auth-bootstrap", new AuthBootstrapComando());
        comandos.put("--auth-create-user", new AuthCreateUserComando());
        comandos.put("--auth-reset-password", new AuthResetPasswordComando());
        comandos.put("--auth-disable-user", new AuthDisableUserComando());
        comandos.put("--auth-info", new AuthInfoComando());

        comandos.put("--loop-daemon-start", new LoopDaemonComando(LoopDaemonComando.Modo.START));
        comandos.put("--loop-daemon-stop", new LoopDaemonComando(LoopDaemonComando.Modo.STOP));
        comandos.put("--loop-daemon-status", new LoopDaemonComando(LoopDaemonComando.Modo.STATUS));
        comandos.put("--loop-daemon-run", new LoopDaemonComando(LoopDaemonComando.Modo.RUN));
        return Collections.unmodifiableMap(comandos);
    }
}
