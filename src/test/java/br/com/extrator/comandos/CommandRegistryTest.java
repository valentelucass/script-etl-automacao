/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/comandos/CommandRegistryTest.java
Classe  : CommandRegistryTest (class)
Pacote  : br.com.extrator.comandos
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade CommandRegistry.

Conecta com:
- Comando (comandos.base)

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de CommandRegistry.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- deveConterComandosEssenciais(): verifica comportamento esperado em teste automatizado.
- mapaDeveSerImutavel(): realiza operacao relacionada a "mapa deve ser imutavel".
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.comandos;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import br.com.extrator.comandos.base.Comando;

class CommandRegistryTest {

    @Test
    void deveConterComandosEssenciais() {
        final Map<String, Comando> comandos = CommandRegistry.criarMapaComandos();
        assertTrue(comandos.containsKey("--fluxo-completo"));
        assertTrue(comandos.containsKey("--loop-daemon-run"));
        assertTrue(comandos.containsKey("--auth-check"));
    }

    @Test
    void mapaDeveSerImutavel() {
        final Map<String, Comando> comandos = CommandRegistry.criarMapaComandos();
        assertThrows(UnsupportedOperationException.class, () -> comandos.put("--novo", null));
    }
}
