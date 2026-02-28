/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/seguranca/CaminhoBancoSegurancaResolverTest.java
Classe  : CaminhoBancoSegurancaResolverTest (class)
Pacote  : br.com.extrator.seguranca
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade CaminhoBancoSegurancaResolver.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de CaminhoBancoSegurancaResolver.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- devePriorizarCaminhoDefinidoViaSystemProperty(): verifica comportamento esperado em teste automatizado.
- restaurarSystemProperty(...1 args): realiza operacao relacionada a "restaurar system property".
Atributos-chave:
- PROP_DB_PATH: campo de estado para "prop db path".
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CaminhoBancoSegurancaResolverTest {

    private static final String PROP_DB_PATH = "extrator.security.db.path";

    @TempDir
    Path tempDir;

    @Test
    void devePriorizarCaminhoDefinidoViaSystemProperty() {
        final Path caminhoEsperado = tempDir.resolve("seguranca").resolve("users.db").toAbsolutePath().normalize();
        final String valorAnterior = System.getProperty(PROP_DB_PATH);
        System.setProperty(PROP_DB_PATH, caminhoEsperado.toString());
        try {
            final Path resolvido = CaminhoBancoSegurancaResolver.resolver();
            assertEquals(caminhoEsperado, resolvido);
            assertTrue(Files.isDirectory(resolvido.getParent()));
        } finally {
            restaurarSystemProperty(valorAnterior);
        }
    }

    private void restaurarSystemProperty(final String valorAnterior) {
        if (valorAnterior == null) {
            System.clearProperty(PROP_DB_PATH);
        } else {
            System.setProperty(PROP_DB_PATH, valorAnterior);
        }
    }
}
