package br.com.extrator.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLSession;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

class GraphQLConnectivityValidatorTest {

    @Test
    void deveValidarAcessoPeloExecutorCentral() {
        final AtomicReference<String> operacaoRecebida = new AtomicReference<>();
        final GraphQLHttpRequestExecutor executor = (cliente, requisicao, operacao) -> {
            operacaoRecebida.set(operacao);
            return new RespostaFalsa(200, "{\"data\":{\"__schema\":{\"queryType\":{\"name\":\"Query\"}}}}");
        };
        final GraphQLConnectivityValidator validator = new GraphQLConnectivityValidator(
            HttpClient.newHttpClient(),
            new ObjectMapper(),
            new GraphQLRequestFactory(new ObjectMapper(), "https://esl.example", "/graphql", "token", Duration.ofSeconds(10)),
            executor,
            LoggerFactory.getLogger(GraphQLConnectivityValidatorTest.class)
        );

        assertTrue(validator.validarAcessoApi());
        assertEquals("GraphQL-connectivity", operacaoRecebida.get());
    }

    private record RespostaFalsa(int statusCode, String body) implements HttpResponse<String> {
        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (nome, valor) -> true);
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return URI.create("https://esl.example/graphql");
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}
