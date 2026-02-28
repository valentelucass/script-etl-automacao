/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/seguranca/PerfilAcesso.java
Classe  : PerfilAcesso (enum)
Pacote  : br.com.extrator.seguranca
Modulo  : Modulo de seguranca
Papel   : Implementa responsabilidade de perfil acesso.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela usuarios, perfis e acoes autorizadas.
2) Implementa regras de autenticacao e senha.
3) Gerencia repositorio de seguranca local.

Estrutura interna:
Metodos principais:
- fromString(...1 args): realiza operacao relacionada a "from string".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import java.util.Locale;

/**
 * Perfis de acesso do modulo de seguranca operacional.
 */
public enum PerfilAcesso {
    ADMIN,
    OPERADOR,
    VISUALIZADOR;

    public static PerfilAcesso fromString(final String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            throw new IllegalArgumentException("Perfil de acesso nao informado.");
        }
        try {
            return PerfilAcesso.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            throw new IllegalArgumentException("Perfil de acesso invalido: " + valor);
        }
    }
}
