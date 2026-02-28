/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/console/ExibirAjudaComando.java
Classe  : ExibirAjudaComando (class)
Pacote  : br.com.extrator.comandos.console
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

package br.com.extrator.comandos.console;

import br.com.extrator.comandos.base.Comando;

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
        System.out.println("                        Opcional: --sem-faturas-graphql (pula Fase 3 de enriquecimento)");
        System.out.println("  --extracao-intervalo  Executa extracao por intervalo");
        System.out.println("                        Uso: --extracao-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--sem-faturas-graphql]");
        System.out.println("  --validar             Valida configuracoes e conectividade");
        System.out.println("  --introspeccao        Realiza introspeccao do schema GraphQL");
        System.out.println("  --auditoria           Executa auditoria dos dados (ultimas 24h)");
        System.out.println("  --auditoria --periodo YYYY-MM-DD YYYY-MM-DD");
        System.out.println("                        Executa auditoria para periodo especifico");
        System.out.println("  --auditar-api         Audita estrutura das APIs e gera CSV");
        System.out.println("  --testar-api [tipo]   Testa API especifica (graphql|dataexport)");
        System.out.println("                        Uso: --testar-api [tipo] [entidade] [--sem-faturas-graphql]");
        System.out.println("  --validar-api-banco-24h");
        System.out.println("                        Compara API ao vivo x banco (janela da ultima extracao COMPLETA)");
        System.out.println("  --validar-api-banco-24h-detalhado");
        System.out.println("                        Compara chave a chave por entidade (API x banco na janela da ultima extracao)");
        System.out.println("  --loop                Console interativo de loop");
        System.out.println("                        Opcional: --sem-faturas-graphql");
        System.out.println("  --loop-daemon-start   Inicia loop em segundo plano");
        System.out.println("                        Opcional: --sem-faturas-graphql");
        System.out.println("  --loop-daemon-stop    Para loop em segundo plano");
        System.out.println("  --loop-daemon-status  Consulta status do loop em segundo plano");
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
        System.out.println("  java -jar extrator.jar --fluxo-completo --sem-faturas-graphql");
        System.out.println("  java -jar extrator.jar --extracao-intervalo 2026-01-01 2026-01-31 --sem-faturas-graphql");
        System.out.println("  java -jar extrator.jar --loop-daemon-start");
        System.out.println("  java -jar extrator.jar --loop-daemon-start --sem-faturas-graphql");
        System.out.println();
        System.out.println("=".repeat(80));
    }
}
