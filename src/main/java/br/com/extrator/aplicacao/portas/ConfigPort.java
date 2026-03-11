/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/portas/ConfigPort.java
Classe  : ConfigPort (interface)
Pacote  : br.com.extrator.aplicacao.portas
Modulo  : Porta (Interface)

Papel   : Porta para leitura de configuracoes (chave-valor com defaults).

Conecta com:
- ConfigEtl (implementacao em suporte/configuracao)

Fluxo geral:
1) obterTexto/Inteiro/Longo/Decimal/Booleano(key, defaultValue).
2) Retorna valor configurado ou default se ausente.
3) Abstrai origem (properties, env, BD).

Estrutura interna:
Metodos principais:
- obterTexto(String, String): String com default.
- obterInteiro(String, int): int com default.
- obterLongo(String, long): long com default.
- obterDecimal(String, double): double com default.
- obterBooleano(String, boolean): boolean com default.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.portas;

public interface ConfigPort {
    String obterTexto(String key, String defaultValue);

    int obterInteiro(String key, int defaultValue);

    long obterLongo(String key, long defaultValue);

    double obterDecimal(String key, double defaultValue);

    boolean obterBooleano(String key, boolean defaultValue);
}
