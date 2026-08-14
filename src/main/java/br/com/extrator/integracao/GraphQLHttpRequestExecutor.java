package br.com.extrator.integracao;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@FunctionalInterface
interface GraphQLHttpRequestExecutor {

    HttpResponse<String> executar(HttpClient cliente, HttpRequest requisicao, String operacao);
}
