/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/modelo/dataexport/faturaporcliente/FaturaPorClienteMapperTest.java
Classe  : FaturaPorClienteMapperTest (class)
Pacote  : br.com.extrator.modelo.dataexport.faturaporcliente
Modulo  : Teste automatizado
Papel   : Valida comportamento da unidade FaturaPorClienteMapper.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Prepara cenarios e dados de teste.
2) Executa casos para validar comportamento de FaturaPorClienteMapper.
3) Assegura regressao controlada nas regras principais.

Estrutura interna:
Metodos principais:
- deveUsarNfseQuandoDisponivel(): verifica comportamento esperado em teste automatizado.
- deveUsarChaveCteQuandoNaoHaNfse(): verifica comportamento esperado em teste automatizado.
- deveGerarHashDeterministicoQuandoNaoHaChaveNatural(): verifica comportamento esperado em teste automatizado.
- deveAlterarHashQuandoCampoCanonicoMuda(): verifica comportamento esperado em teste automatizado.
- criarDtoSemChaveNatural(): instancia ou monta estrutura de dados.
Atributos-chave:
- mapper: apoio de mapeamento de dados.
[DOC-FILE-END]============================================================== */

package br.com.extrator.modelo.dataexport.faturaporcliente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class FaturaPorClienteMapperTest {

    private final FaturaPorClienteMapper mapper = new FaturaPorClienteMapper();

    @Test
    void deveUsarNfseQuandoDisponivel() {
        final FaturaPorClienteDTO dto = new FaturaPorClienteDTO();
        dto.setNfseNumber(123456L);
        dto.setCteKey("35100000000000000000000000000000000000000000");

        final String uniqueId = mapper.calcularIdentificadorUnico(dto);

        assertEquals("NFSE-123456", uniqueId);
    }

    @Test
    void deveUsarChaveCteQuandoNaoHaNfse() {
        final FaturaPorClienteDTO dto = new FaturaPorClienteDTO();
        dto.setCteKey("35100000000000000000000000000000000000000000");

        final String uniqueId = mapper.calcularIdentificadorUnico(dto);

        assertEquals("35100000000000000000000000000000000000000000", uniqueId);
    }

    @Test
    void deveGerarHashDeterministicoQuandoNaoHaChaveNatural() {
        final FaturaPorClienteDTO dto = criarDtoSemChaveNatural();

        final String uniqueIdPrimeiraExecucao = mapper.calcularIdentificadorUnico(dto);
        final String uniqueIdSegundaExecucao = mapper.calcularIdentificadorUnico(dto);

        assertEquals(uniqueIdPrimeiraExecucao, uniqueIdSegundaExecucao);
        assertTrue(uniqueIdPrimeiraExecucao.startsWith("FPC-HASH-"));
        assertTrue(uniqueIdPrimeiraExecucao.length() <= 100);
    }

    @Test
    void deveAlterarHashQuandoCampoCanonicoMuda() {
        final FaturaPorClienteDTO dtoA = criarDtoSemChaveNatural();
        final FaturaPorClienteDTO dtoB = criarDtoSemChaveNatural();
        dtoB.setValorFrete("1020.75");

        final String uniqueIdA = mapper.calcularIdentificadorUnico(dtoA);
        final String uniqueIdB = mapper.calcularIdentificadorUnico(dtoB);

        assertNotEquals(uniqueIdA, uniqueIdB);
    }

    private FaturaPorClienteDTO criarDtoSemChaveNatural() {
        final FaturaPorClienteDTO dto = new FaturaPorClienteDTO();
        dto.setNfseNumber(null);
        dto.setCteKey(null);
        dto.setFaturaDocument(null);
        dto.setBillingId(null);
        dto.setValorFrete("1000.50");
        dto.setThirdPartyCtesValue("0.00");
        dto.setTipoFrete("Freight::Normal");
        dto.setFilial("Matriz");
        dto.setEstado("SP");
        dto.setClassificacao("Padrao");
        dto.setPagadorNome("Cliente Teste");
        dto.setPagadorDocumento("12345678000190");
        dto.setRemetenteNome("Remetente");
        dto.setRemetenteDocumento("11111111000191");
        dto.setDestinatarioNome("Destinatario");
        dto.setDestinatarioDocumento("22222222000192");
        dto.setVendedorNome("Vendedor");
        dto.setNotasFiscais(List.of("NF-1", "NF-2"));
        dto.setPedidosCliente(List.of("PED-1", "PED-2"));
        return dto;
    }
}
