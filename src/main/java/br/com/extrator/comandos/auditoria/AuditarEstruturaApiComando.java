/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/comandos/auditoria/AuditarEstruturaApiComando.java
Classe  : AuditarEstruturaApiComando (class)
Pacote  : br.com.extrator.comandos.auditoria
Modulo  : Comando CLI (auditoria)
Papel   : Implementa responsabilidade de auditar estrutura api comando.

Conecta com:
- AuditorEstruturaApi (auditoria.validacao)
- Comando (comandos.base)

Fluxo geral:
1) Aciona auditorias de estrutura e integridade.
2) Executa validadores e consolida evidencias.
3) Produz saida para analise tecnica.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos.auditoria;

import br.com.extrator.auditoria.validacao.AuditorEstruturaApi;
import br.com.extrator.comandos.base.Comando;

/**
 * Comando para auditar a estrutura das APIs e gerar relatorio CSV.
 */
public class AuditarEstruturaApiComando implements Comando {

    @Override
    public void executar(final String[] args) throws Exception {
        final int exitCode = AuditorEstruturaApi.executar();
        if (exitCode != 0) {
            throw new IllegalStateException("Auditoria de estrutura finalizada com erro (codigo " + exitCode + ").");
        }
    }
}
