/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/seguranca/CaminhoBancoSegurancaResolver.java
Classe  : CaminhoBancoSegurancaResolver (class)
Pacote  : br.com.extrator.seguranca
Modulo  : Modulo de seguranca
Papel   : Implementa responsabilidade de caminho banco seguranca resolver.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Modela usuarios, perfis e acoes autorizadas.
2) Implementa regras de autenticacao e senha.
3) Gerencia repositorio de seguranca local.

Estrutura interna:
Metodos principais:
- CaminhoBancoSegurancaResolver(): realiza operacao relacionada a "caminho banco seguranca resolver".
- resolver(): realiza operacao relacionada a "resolver".
- criarDiretorioPai(...1 args): instancia ou monta estrutura de dados.
Atributos-chave:
- logger: logger da classe para diagnostico.
- ENV_DB_PATH: campo de estado para "env db path".
- SYS_PROP_DB_PATH: campo de estado para "sys prop db path".
[DOC-FILE-END]============================================================== */

package br.com.extrator.seguranca;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolve caminho do banco SQLite de seguranca.
 */
public final class CaminhoBancoSegurancaResolver {
    private static final Logger logger = LoggerFactory.getLogger(CaminhoBancoSegurancaResolver.class);
    private static final String ENV_DB_PATH = "EXTRATOR_SECURITY_DB_PATH";
    private static final String SYS_PROP_DB_PATH = "extrator.security.db.path";

    private CaminhoBancoSegurancaResolver() {
    }

    public static Path resolver() {
        final String caminhoViaSystemProperty = System.getProperty(SYS_PROP_DB_PATH);
        if (caminhoViaSystemProperty != null && !caminhoViaSystemProperty.trim().isEmpty()) {
            final Path custom = Paths.get(caminhoViaSystemProperty.trim()).toAbsolutePath().normalize();
            criarDiretorioPai(custom);
            return custom;
        }

        final String caminhoCustomizado = System.getenv(ENV_DB_PATH);
        if (caminhoCustomizado != null && !caminhoCustomizado.trim().isEmpty()) {
            final Path custom = Paths.get(caminhoCustomizado.trim()).toAbsolutePath().normalize();
            criarDiretorioPai(custom);
            return custom;
        }

        final String programData = System.getenv("ProgramData");
        if (programData != null && !programData.trim().isEmpty()) {
            final Path prod = Paths.get(programData, "ExtratorESL", "security", "users.db").toAbsolutePath().normalize();
            try {
                criarDiretorioPai(prod);
                return prod;
            } catch (final RuntimeException e) {
                logger.warn("Nao foi possivel usar caminho em ProgramData ({}). Fallback para pasta local.", prod, e);
            }
        }

        final Path fallback = Paths.get("data", "security", "users.db").toAbsolutePath().normalize();
        criarDiretorioPai(fallback);
        return fallback;
    }

    private static void criarDiretorioPai(final Path arquivo) {
        try {
            final Path pai = arquivo.getParent();
            if (pai != null && !Files.exists(pai)) {
                Files.createDirectories(pai);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Falha ao criar diretorio do banco de seguranca: " + arquivo, e);
        }
    }
}
