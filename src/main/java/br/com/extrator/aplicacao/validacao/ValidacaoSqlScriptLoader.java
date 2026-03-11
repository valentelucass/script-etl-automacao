/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoSqlScriptLoader.java
Classe  : ValidacaoSqlScriptLoader (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Loader de scripts SQL do classpath (/sql/ resources).

Conecta com:
- Nenhuma (utilidade pura)

Fluxo geral:
1) carregar(String scriptName) lê arquivo /sql/{scriptName} do classpath.
2) Retorna conteudo como String com linhas juntadas por newline.
3) Throws Exception se arquivo nao encontrado.

Estrutura interna:
Metodos principais:
- carregar(String): carrega e retorna conteudo script.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

final class ValidacaoSqlScriptLoader {
    String carregar(final String scriptName) throws Exception {
        final String resourcePath = "/sql/" + scriptName;
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new Exception("Script SQL nao encontrado: " + resourcePath);
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        }
    }
}
