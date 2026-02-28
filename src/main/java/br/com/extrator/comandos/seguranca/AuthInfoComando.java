/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/seguranca/AuthInfoComando.java
Classe  : AuthInfoComando (class)
Pacote  : br.com.extrator.comandos.seguranca
Modulo  : Comando CLI (seguranca)
Papel   : Implementa responsabilidade de auth info comando.

Conecta com:
- Comando (comandos.base)
- SegurancaService (seguranca)

Fluxo geral:
1) Orquestra operacoes de autenticacao/autorizacao.
2) Aplica regras de perfil e ciclo de senha.
3) Registra resultado operacional das acoes de seguranca.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.seguranca;

import br.com.extrator.comandos.base.Comando;
import br.com.extrator.seguranca.SegurancaService;

/**
 * Comando para exibir informacoes do modulo de seguranca.
 */
public class AuthInfoComando implements Comando {

    @Override
    public void executar(final String[] args) throws Exception {
        final SegurancaService.ResumoSeguranca resumo = new SegurancaService().obterResumo();
        System.out.println("Banco de seguranca: " + resumo.dbPath());
        System.out.println("Usuarios ativos: " + resumo.usuariosAtivos());
        System.out.println("Eventos de auditoria: " + resumo.eventosAuditoria());
    }
}
