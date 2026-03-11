/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/CommandRegistry.java
Classe  : CommandRegistry (class)
Pacote  : br.com.extrator.comandos.cli
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

package br.com.extrator.comandos.cli;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import br.com.extrator.comandos.cli.auditoria.AuditarEstruturaApiComando;
import br.com.extrator.comandos.cli.auditoria.ExecutarAuditoriaComando;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.comandos.cli.console.ExibirAjudaComando;
import br.com.extrator.comandos.cli.extracao.ExecutarExtracaoPorIntervaloComando;
import br.com.extrator.comandos.cli.extracao.ExecutarFluxoCompletoComando;
import br.com.extrator.comandos.cli.extracao.LoopDaemonComando;
import br.com.extrator.comandos.cli.extracao.LoopExtracaoComando;
import br.com.extrator.comandos.cli.extracao.recovery.RecoveryComando;
import br.com.extrator.comandos.cli.seguranca.AuthBootstrapComando;
import br.com.extrator.comandos.cli.seguranca.AuthCheckComando;
import br.com.extrator.comandos.cli.seguranca.AuthCreateUserComando;
import br.com.extrator.comandos.cli.seguranca.AuthDisableUserComando;
import br.com.extrator.comandos.cli.seguranca.AuthInfoComando;
import br.com.extrator.comandos.cli.seguranca.AuthResetPasswordComando;
import br.com.extrator.comandos.cli.utilitarios.ExportarCsvComando;
import br.com.extrator.comandos.cli.utilitarios.LimparTabelasComando;
import br.com.extrator.comandos.cli.utilitarios.RealizarIntrospeccaoGraphQLComando;
import br.com.extrator.comandos.cli.utilitarios.TestarApiComando;
import br.com.extrator.comandos.cli.validacao.ValidarAcessoComando;
import br.com.extrator.comandos.cli.validacao.ValidarApiVsBanco24hComando;
import br.com.extrator.comandos.cli.validacao.ValidarApiVsBanco24hDetalhadoComando;
import br.com.extrator.comandos.cli.validacao.ValidarDadosCompletoComando;
import br.com.extrator.comandos.cli.validacao.ValidarEtlExtremoComando;
import br.com.extrator.comandos.cli.validacao.ValidarManifestosComando;
import br.com.extrator.comandos.cli.validacao.VerificarTimestampsComando;
import br.com.extrator.comandos.cli.validacao.VerificarTimezoneComando;

public final class CommandRegistry {
    private CommandRegistry() {
    }

    public static Map<String, Comando> criarMapaComandos() {
        final Map<String, Comando> comandos = new HashMap<>();
        registrarComandosPadrao(comandos);
        return Collections.unmodifiableMap(comandos);
    }

    static Comando lazy(final Supplier<Comando> factory) {
        return new LazyComando(factory);
    }

    private static void registrarComandosPadrao(final Map<String, Comando> comandos) {
        comandos.put("--fluxo-completo", lazy(ExecutarFluxoCompletoComando::new));
        comandos.put("--extracao-intervalo", lazy(ExecutarExtracaoPorIntervaloComando::new));
        comandos.put("--recovery", lazy(RecoveryComando::new));
        comandos.put("--loop", lazy(LoopExtracaoComando::new));
        comandos.put("--validar", lazy(ValidarAcessoComando::new));
        comandos.put("--ajuda", lazy(ExibirAjudaComando::new));
        comandos.put("--help", lazy(ExibirAjudaComando::new));
        comandos.put("--introspeccao", lazy(RealizarIntrospeccaoGraphQLComando::new));
        comandos.put("--auditoria", lazy(ExecutarAuditoriaComando::new));
        comandos.put("--auditar-api", lazy(AuditarEstruturaApiComando::new));
        comandos.put("--testar-api", lazy(TestarApiComando::new));
        comandos.put("--limpar-tabelas", lazy(LimparTabelasComando::new));
        comandos.put("--verificar-timestamps", lazy(VerificarTimestampsComando::new));
        comandos.put("--verificar-timezone", lazy(VerificarTimezoneComando::new));
        comandos.put("--validar-manifestos", lazy(ValidarManifestosComando::new));
        comandos.put("--validar-dados", lazy(ValidarDadosCompletoComando::new));
        comandos.put("--validar-api-banco-24h", lazy(ValidarApiVsBanco24hComando::new));
        comandos.put("--validar-api-banco-24h-detalhado", lazy(ValidarApiVsBanco24hDetalhadoComando::new));
        comandos.put("--validar-etl-extremo", lazy(ValidarEtlExtremoComando::new));
        comandos.put("--exportar-csv", lazy(ExportarCsvComando::new));

        comandos.put("--auth-check", lazy(AuthCheckComando::new));
        comandos.put("--auth-bootstrap", lazy(AuthBootstrapComando::new));
        comandos.put("--auth-create-user", lazy(AuthCreateUserComando::new));
        comandos.put("--auth-reset-password", lazy(AuthResetPasswordComando::new));
        comandos.put("--auth-disable-user", lazy(AuthDisableUserComando::new));
        comandos.put("--auth-info", lazy(AuthInfoComando::new));

        comandos.put("--loop-daemon-start", lazy(() -> new LoopDaemonComando(LoopDaemonComando.Modo.START)));
        comandos.put("--loop-daemon-stop", lazy(() -> new LoopDaemonComando(LoopDaemonComando.Modo.STOP)));
        comandos.put("--loop-daemon-status", lazy(() -> new LoopDaemonComando(LoopDaemonComando.Modo.STATUS)));
        comandos.put("--loop-daemon-run", lazy(() -> new LoopDaemonComando(LoopDaemonComando.Modo.RUN)));
    }

    private static final class LazyComando implements Comando {
        private final Supplier<Comando> factory;
        private volatile Comando delegate;

        private LazyComando(final Supplier<Comando> factory) {
            this.factory = factory;
        }

        @Override
        public void executar(final String[] args) throws Exception {
            resolver().executar(args);
        }

        private Comando resolver() {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = factory.get();
                    }
                }
            }
            return delegate;
        }
    }
}
