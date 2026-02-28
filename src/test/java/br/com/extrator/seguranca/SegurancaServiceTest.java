/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/seguranca/SegurancaServiceTest.java
Classe  : SegurancaServiceTest (class)
Pacote  : br.com.extrator.seguranca
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade SegurancaService.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de SegurancaService.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- deveInicializarSqliteEBootstraparAutenticarUsuario(): verifica comportamento esperado em teste automatizado.
- restaurarSystemProperty(...1 args): realiza operacao relacionada a "restaurar system property".
Atributos-chave:
- PROP_DB_PATH: campo de estado para "prop db path".
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SegurancaServiceTest {

    private static final String PROP_DB_PATH = "extrator.security.db.path";

    @TempDir
    Path tempDir;

    @Test
    void deveInicializarSqliteEBootstraparAutenticarUsuario() {
        final Path dbPath = tempDir.resolve("security").resolve("users.db").toAbsolutePath().normalize();
        final String valorAnterior = System.getProperty(PROP_DB_PATH);
        System.setProperty(PROP_DB_PATH, dbPath.toString());

        final char[] senha = "Senha123".toCharArray();
        try {
            final SegurancaService service = new SegurancaService();
            service.bootstrapAdmin("admin_teste", "Admin Teste", senha);
            service.autenticarEAutorizar("admin_teste", senha, AcaoSeguranca.RUN_AJUDA, "teste automatizado");

            final SegurancaService.ResumoSeguranca resumo = service.obterResumo();
            assertTrue(Files.exists(dbPath), "Banco SQLite de seguranca deve ser criado.");
            assertEquals(1L, resumo.usuariosAtivos(), "Bootstrap deve criar um usuario admin ativo.");
            assertTrue(resumo.eventosAuditoria() >= 2L, "Fluxo deve registrar auditoria de bootstrap e autenticacao.");
        } finally {
            Arrays.fill(senha, '\0');
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
