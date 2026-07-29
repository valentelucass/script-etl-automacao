/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/console/ExibirAjudaComando.java
Classe  : ExibirAjudaComando (class)
Pacote  : br.com.extrator.comandos.cli.console
Modulo  : Componente Java
Papel   : Implementa comportamento de exibir ajuda comando.

Conecta com:
- Comando (comandos.base)

Fluxo geral:
1) Define comportamento principal deste modulo.
2) Interage com camadas relacionadas do sistema.
3) Entrega resultado para o fluxo chamador.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.cli.console;

import br.com.extrator.comandos.cli.base.Comando;

/**
 * Comando responsavel por exibir a ajuda do sistema.
 */
public class ExibirAjudaComando implements Comando {

    @Override
    public void executar(final String[] args) throws Exception {
        System.out.println("=".repeat(80));
        System.out.println("SISTEMA DE EXTRACAO DE DADOS - ESL CLOUD");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.println("COMANDOS DISPONIVEIS:");
        System.out.println();
        System.out.println("  (sem argumentos)      Executa extracao completa de todas as APIs");
        System.out.println("  --extracao-intervalo  Executa extracao por intervalo");
        System.out.println("                        Uso: --extracao-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--retrofit]");
        System.out.println("  --fechamento-mensal   Reprocessa automaticamente o mes anterior fechado");
        System.out.println("  --recovery            Replay/backfill idempotente por intervalo");
        System.out.println("                        Uso: --recovery YYYY-MM-DD YYYY-MM-DD [--api graphql|dataexport] [--entidade nome]");
        System.out.println("  --expurgo-orfaos      Executa reconciliacao noturna Sweep and Prune para DataExport e Coletas");
        System.out.println("                        Uso: --expurgo-orfaos [--periodo YYYY-MM-DD YYYY-MM-DD] [--entidade coletas|nome] [--dry-run]");
        System.out.println("  --validar             Valida configuracoes e conectividade");
        System.out.println("  --introspeccao        Realiza introspeccao do schema GraphQL");
        System.out.println("  --auditoria           Executa auditoria dos dados (janela operacional recente D-1..D)");
        System.out.println("  --auditoria --periodo YYYY-MM-DD YYYY-MM-DD");
        System.out.println("                        Executa auditoria para periodo especifico");
        System.out.println("  --auditar-api         Audita estrutura das APIs e gera CSV");
        System.out.println("  --testar-api [tipo]   Testa API especifica (graphql|dataexport|raster)");
        System.out.println("                        Uso: --testar-api [tipo] [entidade]");
        System.out.println("  --sincronizar-usuarios");
        System.out.println("                        Diagnostico incremental de dim_usuarios; o ciclo principal ja executa automaticamente");
        System.out.println("  --validar-api-banco-24h");
        System.out.println("                        Compara API ao vivo x banco (janela da ultima extracao COMPLETA; nao necessariamente 24h corridas)");
        System.out.println("                        Opcional: --permitir-fallback-janela");
        System.out.println("  --validar-api-banco-24h-detalhado");
        System.out.println("                        Compara chave a chave por entidade (API x banco na janela da ultima extracao)");
        System.out.println("                        Opcional: --periodo-fechado | --permitir-fallback-janela");
        System.out.println("  --validar-etl-extremo");
        System.out.println("                        Executa a bateria extrema do ETL com API x banco x logs x paginacao");
        System.out.println("                        Opcional: --periodo-fechado | --permitir-fallback-janela");
        System.out.println("                                  --stress-repeticoes N | --executar-idempotencia | --executar-hidratacao-orfaos");
        System.out.println("  --validar-etl-resiliencia");
        System.out.println("                        Executa bateria de resiliencia/chaos do daemon e do pipeline");
        System.out.println("                        Opcional: --auto-chaos | --ciclos N | --duracao-segundos N");
        System.out.println("                                  --stress-concorrencia N | --seed N | --sem-cenarios-http");
        System.out.println("  --loop                Console interativo de loop");
        System.out.println("  --loop-daemon-start   Inicia loop em segundo plano");
        System.out.println("  --loop-daemon-stop    Para loop em segundo plano");
        System.out.println("  --loop-daemon-status  Consulta status do loop em segundo plano");
        System.out.println("  --materializar-fatos-bi");
        System.out.println("                        Executa uma carga das fatos BI financeiras");
        System.out.println("  --materializar-fatos-bi-scheduler");
        System.out.println("                        Legado: scheduler isolado; loop daemon ja materializa intradia");
        System.out.println("  --auth-bootstrap      Cria o primeiro usuario ADMIN");
        System.out.println("  --auth-check ACAO     Solicita autenticacao para acao sensivel");
        System.out.println("  --auth-create-user    Cria usuario (requer ADMIN)");
        System.out.println("  --auth-reset-password Redefine senha de usuario (requer ADMIN)");
        System.out.println("  --auth-disable-user   Desativa usuario (requer ADMIN)");
        System.out.println("  --auth-info           Exibe info do banco de seguranca");
        System.out.println("  --ajuda, --help       Exibe esta ajuda");
        System.out.println();
        System.out.println("EXEMPLOS:");
        System.out.println("  java -jar extrator.jar --auth-bootstrap");
        System.out.println("  java -jar extrator.jar --auth-check RUN_EXTRACAO_COMPLETA");
        System.out.println("  java -jar extrator.jar --fluxo-completo");
        System.out.println("  java -jar extrator.jar --extracao-intervalo 2026-01-01 2026-01-31");
        System.out.println("  java -jar extrator.jar --fechamento-mensal");
        System.out.println("  java -jar extrator.jar --extracao-intervalo 2026-01-01 2026-01-31 --retrofit");
        System.out.println("  java -jar extrator.jar --recovery 2026-01-01 2026-01-31 --api graphql --entidade coletas");
        System.out.println("  java -jar extrator.jar --expurgo-orfaos --periodo 2026-05-29 2026-05-30 --dry-run");
        System.out.println("  java -jar extrator.jar --validar-api-banco-24h-detalhado --periodo-fechado");
        System.out.println("  java -jar extrator.jar --validar-etl-extremo --periodo-fechado --stress-repeticoes 3");
        System.out.println("  java -jar extrator.jar --validar-etl-resiliencia --auto-chaos --ciclos 12 --duracao-segundos 120");
        System.out.println("  java -jar extrator.jar --loop-daemon-start");
        System.out.println("  java -jar extrator.jar --materializar-fatos-bi-scheduler");
        System.out.println();
        System.out.println("=".repeat(80));
    }
}
