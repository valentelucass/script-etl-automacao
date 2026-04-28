package br.com.extrator.observabilidade;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class LogbackConsoleAppenderTest {

    @Test
    void configuracaoPrincipalNaoUsaConsoleAppenderSincrono() throws Exception {
        final var document = carregarLogback();
        final var appenders = document.getElementsByTagName("appender");

        for (int i = 0; i < appenders.getLength(); i++) {
            final Node node = appenders.item(i);
            if (node instanceof Element element) {
                assertNotEquals(
                    "ch.qos.logback.core.ConsoleAppender",
                    element.getAttribute("class"),
                    "Logging de producao nao deve depender de stdout/stderr sincrono"
                );
            }
        }
    }

    @Test
    void rootAppenderNaoReferenciaConsole() throws Exception {
        final var document = carregarLogback();
        final var refs = document.getElementsByTagName("appender-ref");

        for (int i = 0; i < refs.getLength(); i++) {
            final Node node = refs.item(i);
            if (node instanceof Element element) {
                assertNotEquals(
                    "CONSOLE",
                    element.getAttribute("ref"),
                    "Root logger nao deve escrever no console em execucoes longas"
                );
            }
        }
    }

    @Test
    void mantemAppenderDeArquivoRuntime() throws Exception {
        final var document = carregarLogback();
        final var appenders = document.getElementsByTagName("appender");
        boolean encontrouFile = false;

        for (int i = 0; i < appenders.getLength(); i++) {
            final Node node = appenders.item(i);
            if (node instanceof Element element
                && "FILE".equals(element.getAttribute("name"))
                && "ch.qos.logback.core.rolling.RollingFileAppender".equals(element.getAttribute("class"))) {
                encontrouFile = true;
                break;
            }
        }

        assertTrue(encontrouFile, "Appender FILE deve continuar ativo para auditoria em arquivo");
    }

    private static org.w3c.dom.Document carregarLogback() throws Exception {
        final Path config = Path.of("src", "main", "resources", "logback.xml");
        assertTrue(Files.exists(config), "logback.xml deve existir em src/main/resources");

        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(config.toFile());
    }
}
