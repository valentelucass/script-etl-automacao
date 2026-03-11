/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/bootstrap/pipeline/CarregadorConfigAdapter.java
Classe  : CarregadorConfigAdapter (class)
Pacote  : br.com.extrator.bootstrap.pipeline
Modulo  : Bootstrap - Wiring

Papel   : Adapter que implementa ConfigPort, resolvendo valores de configuracao
          primeiro por variavel de ambiente e depois por arquivo config.properties.

Conecta com:
- ConfigPort (aplicacao.portas) — interface de porta que esta classe implementa
- CarregadorConfig (suporte.configuracao) — acesso ao config.properties em disco

Fluxo geral:
1) Para cada chave solicitada, converte o nome para formato de variavel de ambiente (upper-case, ponto -> underscore).
2) Se a variavel de ambiente estiver definida e nao vazia, retorna seu valor.
3) Caso contrario, delega ao CarregadorConfig para ler o config.properties.
4) Retorna o valor padrao (defaultValue) caso nenhuma fonte tenha o valor.

Estrutura interna:
Metodos principais:
- obterTexto(key, defaultValue): resolve String com prioridade env > properties > default.
- obterInteiro(key, defaultValue): converte resultado de obterTexto para int.
- obterLongo(key, defaultValue): converte resultado de obterTexto para long.
- obterDecimal(key, defaultValue): converte resultado de obterTexto para double.
- obterBooleano(key, defaultValue): converte resultado de obterTexto para boolean.
[DOC-FILE-END]============================================================== */
package br.com.extrator.bootstrap.pipeline;

import br.com.extrator.aplicacao.portas.ConfigPort;
import br.com.extrator.suporte.configuracao.CarregadorConfig;

public final class CarregadorConfigAdapter implements ConfigPort {
    @Override
    public String obterTexto(final String key, final String defaultValue) {
        final String envAlias = key.toUpperCase().replace('.', '_');
        final String env = System.getenv(envAlias);
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        final String property = CarregadorConfig.obterPropriedade(key);
        return property == null || property.isBlank() ? defaultValue : property.trim();
    }

    @Override
    public int obterInteiro(final String key, final int defaultValue) {
        try {
            return Integer.parseInt(obterTexto(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public long obterLongo(final String key, final long defaultValue) {
        try {
            return Long.parseLong(obterTexto(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public double obterDecimal(final String key, final double defaultValue) {
        try {
            return Double.parseDouble(obterTexto(key, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public boolean obterBooleano(final String key, final boolean defaultValue) {
        final String value = obterTexto(key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }
}


