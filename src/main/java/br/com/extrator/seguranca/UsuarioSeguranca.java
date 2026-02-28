/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/seguranca/UsuarioSeguranca.java
Classe  : UsuarioSeguranca (record)
Pacote  : br.com.extrator.seguranca
Modulo  : Modulo de seguranca
Papel   : Implementa responsabilidade de usuario seguranca.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela usuarios, perfis e acoes autorizadas.
2) Implementa regras de autenticacao e senha.
3) Gerencia repositorio de seguranca local.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import java.time.LocalDateTime;

/**
 * Modelo de usuario para autenticacao operacional.
 */
public record UsuarioSeguranca(
    long id,
    String username,
    String displayName,
    PerfilAcesso perfilAcesso,
    boolean ativo,
    int tentativasFalhas,
    LocalDateTime bloqueadoAte,
    String senhaHashBase64,
    String senhaSaltBase64
) {
}
