package br.com.extrator.suporte.configuracao;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/suporte/configuracao/ConfigSource.java
Classe  :  (class)
Pacote  : br.com.extrator.suporte.configuracao
Modulo  : Suporte - Config
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConfigSource {
    private static final Logger logger = LoggerFactory.getLogger(ConfigSource.class);
    private static final String ARQUIVO_CONFIG = "config.properties";
    private static final String ARQUIVO_DOTENV = ".env";
    private static final String PROP_DOTENV_FILE = "extrator.env.file";
    private static final String ENV_DOTENV_FILE = "EXTRATOR_ENV_FILE";
    private static final Path[] CAMINHOS_FALLBACK = {
        Path.of(ARQUIVO_CONFIG),
        Path.of("config", ARQUIVO_CONFIG),
        Path.of("src", "main", "resources", ARQUIVO_CONFIG)
    };

    private static final class PropertiesHolder {
        private static final Properties INSTANCE = carregarPropriedadesInterno();

        private static Properties carregarPropriedadesInterno() {
            final Properties props = new Properties();
            if (carregarDoClasspath(props)) {
                logger.info("Arquivo de configuracao carregado com sucesso do classpath");
                return props;
            }

            final Path caminhoFallback = localizarArquivoConfiguracao();
            if (caminhoFallback != null) {
                try (InputStream input = Files.newInputStream(caminhoFallback)) {
                    props.load(input);
                    logger.info(
                        "Arquivo de configuracao carregado com sucesso do disco: {}",
                        caminhoFallback.toAbsolutePath()
                    );
                } catch (final IOException ex) {
                    logger.error(
                        "Erro ao carregar o arquivo de configuracao do disco: {}",
                        caminhoFallback.toAbsolutePath(),
                        ex
                    );
                }
                return props;
            }

            logger.warn(
                "Arquivo de configuracao '{}' nao encontrado no classpath nem em disco. Continuando com variaveis de ambiente.",
                ARQUIVO_CONFIG
            );
            return props;
        }

        private static boolean carregarDoClasspath(final Properties props) {
            try (InputStream input = ConfigSource.class.getClassLoader().getResourceAsStream(ARQUIVO_CONFIG)) {
                if (input == null) {
                    return false;
                }
                props.load(input);
                return true;
            } catch (final IOException ex) {
                logger.error("Erro ao carregar o arquivo de configuracao do classpath", ex);
                return false;
            }
        }

        private static Path localizarArquivoConfiguracao() {
            for (final Path caminho : CAMINHOS_FALLBACK) {
                if (Files.isRegularFile(caminho)) {
                    return caminho;
                }
            }
            return null;
        }
    }

    private ConfigSource() {
    }

    static Properties carregarPropriedades() {
        return PropertiesHolder.INSTANCE;
    }

    static String obterConfiguracaoObrigatoria(final String nomeVariavelAmbiente) {
        return obterConfiguracaoObrigatoria(new String[] { nomeVariavelAmbiente });
    }

    static String obterConfiguracaoObrigatoria(final String[] nomesVariaveisAmbiente) {
        final String valorSystemProperty = obterConfiguracaoSystemProperty(nomesVariaveisAmbiente, true);
        if (valorSystemProperty != null) {
            return valorSystemProperty;
        }

        final String valorDotEnv = obterConfiguracaoDotEnv(nomesVariaveisAmbiente, true);
        if (valorDotEnv != null) {
            return valorDotEnv;
        }

        final String valor = obterConfiguracaoVariavelAmbiente(nomesVariaveisAmbiente, true);
        if (valor != null) {
            return valor;
        }

        final String nomes = nomesVariaveisAmbiente == null ? "<nenhuma>" : String.join(", ", nomesVariaveisAmbiente);
        if (nomesVariaveisAmbiente == null || nomesVariaveisAmbiente.length == 0) {
            final String mensagem = String.format(
                "Variavel de ambiente obrigatoria '%s' nao encontrada ou esta vazia. Configure esta variavel de ambiente antes de executar a aplicacao.",
                nomes
            );
            logger.error(mensagem);
            throw new IllegalStateException(mensagem);
        }

        final String mensagem = String.format(
            "Variavel de ambiente obrigatoria '%s' nao encontrada ou esta vazia. Configure no ambiente do processo, no Windows ou no arquivo .env da raiz do projeto.",
            nomes
        );
        logger.error(mensagem);
        throw new IllegalStateException(mensagem);
    }

    static String obterConfiguracao(final String nomeVariavelAmbiente, final String nomeChaveProperties) {
        return obterConfiguracao(new String[] { nomeVariavelAmbiente }, nomeChaveProperties);
    }

    static String obterConfiguracao(final String[] nomesVariaveisAmbiente, final String nomeChaveProperties) {
        final String valorSystemPropertyAmbiente = obterConfiguracaoSystemProperty(nomesVariaveisAmbiente, false);
        if (valorSystemPropertyAmbiente != null) {
            return valorSystemPropertyAmbiente;
        }

        final String valorPropertyKey = System.getProperty(nomeChaveProperties);
        if (valorPropertyKey != null && !valorPropertyKey.trim().isEmpty()) {
            logger.debug("Configuracao '{}' obtida da system property com chave de propriedade", nomeChaveProperties);
            return valorPropertyKey;
        }

        final String valorDotEnv = obterConfiguracaoDotEnv(nomesVariaveisAmbiente, false);
        if (valorDotEnv != null) {
            return valorDotEnv;
        }

        final String valorAmbiente = obterConfiguracaoVariavelAmbiente(nomesVariaveisAmbiente, false);
        if (valorAmbiente != null) {
            return valorAmbiente;
        }

        final Properties props = carregarPropriedades();
        final String valorProperties = props.getProperty(nomeChaveProperties);
        if (valorProperties == null) {
            logger.warn(
                "Configuracao '{}' nao encontrada em variavel de ambiente ({}) nem no arquivo de configuracao '{}'",
                nomeChaveProperties,
                nomesVariaveisAmbiente == null ? "<nenhuma>" : String.join(", ", nomesVariaveisAmbiente),
                ARQUIVO_CONFIG
            );
        } else {
            logger.debug("Configuracao '{}' obtida do arquivo config.properties", nomeChaveProperties);
        }
        return valorProperties;
    }

    static String obterPropriedade(final String chave) {
        final Properties props = carregarPropriedades();
        final String valor = props.getProperty(chave);
        if (valor == null) {
            logger.warn("Propriedade '{}' nao encontrada no arquivo de configuracao", chave);
        }
        return valor;
    }

    private static String obterConfiguracaoSystemProperty(final String[] nomesVariaveisAmbiente, final boolean sensivel) {
        if (nomesVariaveisAmbiente == null) {
            return null;
        }

        for (int i = 0; i < nomesVariaveisAmbiente.length; i++) {
            final String nomeVariavelAmbiente = nomesVariaveisAmbiente[i];
            if (nomeVariavelAmbiente == null || nomeVariavelAmbiente.isBlank()) {
                continue;
            }

            final String valorSystemProperty = System.getProperty(nomeVariavelAmbiente);
            if (valorSystemProperty != null && !valorSystemProperty.trim().isEmpty()) {
                registrarOrigemAmbiente(nomesVariaveisAmbiente, i, "system property", sensivel);
                return valorSystemProperty;
            }
        }
        return null;
    }

    private static String obterConfiguracaoVariavelAmbiente(final String[] nomesVariaveisAmbiente, final boolean sensivel) {
        if (nomesVariaveisAmbiente == null) {
            return null;
        }
        for (int i = 0; i < nomesVariaveisAmbiente.length; i++) {
            final String nomeVariavelAmbiente = nomesVariaveisAmbiente[i];
            if (nomeVariavelAmbiente == null || nomeVariavelAmbiente.isBlank()) {
                continue;
            }
            final String valorAmbiente = System.getenv(nomeVariavelAmbiente);
            if (valorAmbiente != null && !valorAmbiente.trim().isEmpty()) {
                registrarOrigemAmbiente(nomesVariaveisAmbiente, i, "variavel de ambiente", sensivel);
                return valorAmbiente;
            }
        }
        return null;
    }

    private static void registrarOrigemAmbiente(final String[] nomesVariaveisAmbiente,
                                                final int indice,
                                                final String origem,
                                                final boolean sensivel) {
        final String nome = nomesVariaveisAmbiente[indice];
        if (indice > 0) {
            logger.warn(
                "Configuracao{} obtida por alias de {} '{}'. Prefira '{}'.",
                sensivel ? " sensivel" : "",
                origem,
                nome,
                nomesVariaveisAmbiente[0]
            );
        } else {
            logger.debug(
                "Configuracao{} '{}' obtida da {}",
                sensivel ? " sensivel" : "",
                nome,
                origem
            );
        }
    }

    private static String obterConfiguracaoDotEnv(final String[] nomesVariaveisAmbiente, final boolean sensivel) {
        if (nomesVariaveisAmbiente == null) {
            return null;
        }

        final Properties props = carregarDotEnv();
        if (props.isEmpty()) {
            return null;
        }

        for (int i = 0; i < nomesVariaveisAmbiente.length; i++) {
            final String nome = nomesVariaveisAmbiente[i];
            if (nome == null || nome.isBlank()) {
                continue;
            }

            final String valor = props.getProperty(nome);
            if (valor != null && !valor.trim().isEmpty()) {
                if (i > 0) {
                    logger.warn(
                        "Configuracao{} obtida por alias do arquivo .env '{}'. Prefira '{}'.",
                        sensivel ? " sensivel" : "",
                        nome,
                        nomesVariaveisAmbiente[0]
                    );
                } else {
                    logger.debug(
                        "Configuracao{} '{}' obtida do arquivo .env",
                        sensivel ? " sensivel" : "",
                        nome
                    );
                }
                return valor;
            }
        }
        return null;
    }

    private static Properties carregarDotEnv() {
        final Properties props = new Properties();
        final Path caminho = localizarArquivoDotEnv();
        if (caminho == null) {
            return props;
        }

        try {
            for (final String linha : Files.readAllLines(caminho, StandardCharsets.UTF_8)) {
                carregarLinhaDotEnv(props, linha);
            }
        } catch (final IOException ex) {
            logger.warn("Nao foi possivel carregar arquivo .env: {}", caminho.toAbsolutePath(), ex);
        }
        return props;
    }

    private static void carregarLinhaDotEnv(final Properties props, final String linhaOriginal) {
        if (linhaOriginal == null) {
            return;
        }

        String linha = linhaOriginal.strip();
        if (linha.startsWith("\uFEFF")) {
            linha = linha.substring(1).strip();
        }
        if (linha.isEmpty() || linha.startsWith("#")) {
            return;
        }
        if (linha.startsWith("export ")) {
            linha = linha.substring("export ".length()).strip();
        }

        final int separador = linha.indexOf('=');
        if (separador <= 0) {
            return;
        }

        final String chave = linha.substring(0, separador).strip();
        if (!chave.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return;
        }

        String valor = linha.substring(separador + 1).strip();
        if (valor.length() >= 2) {
            final char primeiro = valor.charAt(0);
            final char ultimo = valor.charAt(valor.length() - 1);
            if ((primeiro == '"' && ultimo == '"') || (primeiro == '\'' && ultimo == '\'')) {
                valor = valor.substring(1, valor.length() - 1);
            }
        }
        props.setProperty(chave, valor);
    }

    private static Path localizarArquivoDotEnv() {
        final String caminhoExplicito = primeiroNaoVazio(
            System.getProperty(PROP_DOTENV_FILE),
            System.getenv(ENV_DOTENV_FILE)
        );
        if (caminhoExplicito != null) {
            final Path path = Path.of(caminhoExplicito);
            if (Files.isRegularFile(path)) {
                return path;
            }
            logger.warn("Arquivo .env configurado em {} nao encontrado: {}", PROP_DOTENV_FILE, path.toAbsolutePath());
            return null;
        }

        final Set<Path> candidatos = new LinkedHashSet<>();
        final Path baseDir = resolverBaseDir();
        if (baseDir != null) {
            candidatos.add(baseDir.resolve(ARQUIVO_DOTENV));
            candidatos.add(baseDir.resolve("config").resolve(ARQUIVO_DOTENV));
        }
        candidatos.add(Path.of(ARQUIVO_DOTENV));
        candidatos.add(Path.of("config", ARQUIVO_DOTENV));

        for (final Path candidato : candidatos) {
            if (Files.isRegularFile(candidato)) {
                return candidato;
            }
        }
        return null;
    }

    private static Path resolverBaseDir() {
        final String base = primeiroNaoVazio(
            System.getProperty("etl.base.dir"),
            System.getProperty("ETL_BASE_DIR"),
            System.getenv("ETL_BASE_DIR")
        );
        return base == null ? null : Path.of(base);
    }

    private static String primeiroNaoVazio(final String... valores) {
        if (valores == null) {
            return null;
        }
        for (final String valor : valores) {
            if (valor != null && !valor.trim().isEmpty()) {
                return valor.trim();
            }
        }
        return null;
    }
}
