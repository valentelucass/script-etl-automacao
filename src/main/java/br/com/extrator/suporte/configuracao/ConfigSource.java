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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class ConfigSource {
    private static final Logger logger = LoggerFactory.getLogger(ConfigSource.class);
    private static final String ARQUIVO_CONFIG = "config.properties";
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
        final String valor = System.getenv(nomeVariavelAmbiente);
        if (valor == null || valor.trim().isEmpty()) {
            final String mensagem = String.format(
                "Variavel de ambiente obrigatoria '%s' nao encontrada ou esta vazia. Configure esta variavel de ambiente antes de executar a aplicacao.",
                nomeVariavelAmbiente
            );
            logger.error(mensagem);
            throw new IllegalStateException(mensagem);
        }
        logger.debug("Configuracao sensivel '{}' obtida da variavel de ambiente", nomeVariavelAmbiente);
        return valor;
    }

    static String obterConfiguracao(final String nomeVariavelAmbiente, final String nomeChaveProperties) {
        return obterConfiguracao(new String[] { nomeVariavelAmbiente }, nomeChaveProperties);
    }

    static String obterConfiguracao(final String[] nomesVariaveisAmbiente, final String nomeChaveProperties) {
        if (nomesVariaveisAmbiente != null) {
            for (int i = 0; i < nomesVariaveisAmbiente.length; i++) {
                final String nomeVariavelAmbiente = nomesVariaveisAmbiente[i];
                if (nomeVariavelAmbiente == null || nomeVariavelAmbiente.isBlank()) {
                    continue;
                }

                final String valorAmbiente = System.getenv(nomeVariavelAmbiente);
                if (valorAmbiente != null && !valorAmbiente.trim().isEmpty()) {
                    if (i > 0) {
                        logger.warn(
                            "Configuracao '{}' obtida por alias de variavel de ambiente '{}'. Prefira '{}'.",
                            nomeChaveProperties,
                            nomeVariavelAmbiente,
                            nomesVariaveisAmbiente[0]
                        );
                    } else {
                        logger.debug("Configuracao '{}' obtida da variavel de ambiente", nomeVariavelAmbiente);
                    }
                    return valorAmbiente;
                }
            }
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
}
