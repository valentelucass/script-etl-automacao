package br.com.extrator.suporte.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigSourceTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limparOverrides() {
        System.clearProperty("extrator.env.file");
        System.clearProperty("DOTENV_TEST_VALUE");
        System.clearProperty("DOTENV_TEST_REQUIRED");
        System.clearProperty("DOTENV_TEST_PRIMARY");
        System.clearProperty("DOTENV_TEST_ALIAS");
    }

    @Test
    void devePriorizarSystemPropertyDaVariavelAntesDoArquivo() {
        System.setProperty("TESTE_CONFIG_OVERRIDE", "123");
        try {
            assertEquals(
                "123",
                ConfigSource.obterConfiguracao(new String[] {"TESTE_CONFIG_OVERRIDE"}, "chave.ausente")
            );
        } finally {
            System.clearProperty("TESTE_CONFIG_OVERRIDE");
        }
    }

    @Test
    void deveAceitarSystemPropertyPeloNomeDaChaveProperties() {
        System.setProperty("api.graphql.max.paginas", "11");
        try {
            assertEquals("11", ConfigSource.obterConfiguracao(new String[] {"AMBIENTE_INEXISTENTE"}, "api.graphql.max.paginas"));
        } finally {
            System.clearProperty("api.graphql.max.paginas");
        }
    }

    @Test
    void deveLerConfiguracaoObrigatoriaDoArquivoDotEnv() throws Exception {
        final Path dotEnv = tempDir.resolve(".env");
        Files.writeString(dotEnv, "DOTENV_TEST_REQUIRED=valor-do-dotenv\n");
        System.setProperty("extrator.env.file", dotEnv.toString());

        assertEquals("valor-do-dotenv", ConfigSource.obterConfiguracaoObrigatoria("DOTENV_TEST_REQUIRED"));
    }

    @Test
    void devePriorizarSystemPropertyAntesDoArquivoDotEnv() throws Exception {
        final Path dotEnv = tempDir.resolve(".env");
        Files.writeString(dotEnv, "DOTENV_TEST_VALUE=valor-do-dotenv\n");
        System.setProperty("extrator.env.file", dotEnv.toString());
        System.setProperty("DOTENV_TEST_VALUE", "valor-system-property");

        assertEquals(
            "valor-system-property",
            ConfigSource.obterConfiguracao(new String[] {"DOTENV_TEST_VALUE"}, "chave.ausente")
        );
    }

    @Test
    void deveAceitarAliasObrigatorioNoArquivoDotEnv() throws Exception {
        final Path dotEnv = tempDir.resolve(".env");
        Files.writeString(dotEnv, "DOTENV_TEST_ALIAS=valor-alias\n");
        System.setProperty("extrator.env.file", dotEnv.toString());

        assertEquals(
            "valor-alias",
            ConfigSource.obterConfiguracaoObrigatoria(new String[] {"DOTENV_TEST_PRIMARY", "DOTENV_TEST_ALIAS"})
        );
    }

    @Test
    void deveFalharQuandoObrigatoriaNaoExisteEmNenhumaFonte() {
        System.setProperty("extrator.env.file", tempDir.resolve(".env").toString());

        assertThrows(
            IllegalStateException.class,
            () -> ConfigSource.obterConfiguracaoObrigatoria("DOTENV_TEST_REQUIRED")
        );
    }
}
