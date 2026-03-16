/* ==[DOC-FILE]===============================================================
Arquivo : src/test/java/br/com/extrator/modelo/dataexport/faturaporcliente/FaturaPorClienteMapperTest.java
Classe  : FaturaPorClienteMapperTest (class)
Pacote  : br.com.extrator.dominio.dataexport.faturaporcliente
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

package br.com.extrator.dominio.dataexport.faturaporcliente;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import br.com.extrator.integracao.mapeamento.dataexport.faturaporcliente.FaturaPorClienteMapper;

class FaturaPorClienteMapperTest {

    private final FaturaPorClienteMapper mapper = new FaturaPorClienteMapper();

    @Test
    void deveGerarHashCanonicoMesmoQuandoNfseDisponivel() {
        final FaturaPorClienteDTO dto = new FaturaPorClienteDTO();
        dto.setNfseNumber(123456L);
        dto.setCteKey("35100000000000000000000000000000000000000000");
        dto.setCteNumber(9876L);
        dto.setCteIssuedAt("2026-03-09T10:15:30-03:00");
        dto.setFaturaDocument("DOC-001");
        dto.setFaturaIssueDate("2026-03-09");
        dto.setFaturaDueDate("2026-03-10");
        dto.setFaturaValue("125.50");
        dto.setValorFrete("125.50");

        final String uniqueId = mapper.calcularIdentificadorUnico(dto);

        assertTrue(uniqueId.startsWith("FPC-HASH-"));
    }

    @Test
    void deveGerarHashCanonicoMesmoQuandoNfseRawDisponivel() {
        final FaturaPorClienteDTO dto = new FaturaPorClienteDTO();
        dto.setNfseNumber(null);
        dto.setNfseNumberRaw("123456");
        dto.setCteKey("35100000000000000000000000000000000000000000");
        dto.setCteNumber(9876L);
        dto.setCteIssuedAt("2026-03-09T10:15:30-03:00");
        dto.setFaturaDocument("DOC-001");
        dto.setFaturaIssueDate("2026-03-09");
        dto.setFaturaDueDate("2026-03-10");
        dto.setFaturaValue("125.50");
        dto.setValorFrete("125.50");

        final String uniqueId = mapper.calcularIdentificadorUnico(dto);
        final var entity = mapper.toEntity(dto);

        assertTrue(uniqueId.startsWith("FPC-HASH-"));
        assertEquals(123456L, entity.getNumeroNfse());
        assertTrue(entity.getMetadata().contains("\"nfse_number\":\"123456\""));
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
        dtoB.setPagadorDocumento("99887766000155");

        final String uniqueIdA = mapper.calcularIdentificadorUnico(dtoA);
        final String uniqueIdB = mapper.calcularIdentificadorUnico(dtoB);

        assertNotEquals(uniqueIdA, uniqueIdB);
    }

    @Test
    void deveManterMesmoUniqueIdQuandoCteKeyApareceDepois() {
        final FaturaPorClienteDTO semCteKey = criarDtoSemChaveNatural();
        semCteKey.setCteNumber(12345L);
        semCteKey.setCteIssuedAt("2026-03-09T10:15:30-03:00");
        semCteKey.setFaturaDocument("DOC-7788");
        semCteKey.setFaturaIssueDate("2026-03-09");
        semCteKey.setFaturaDueDate("2026-03-15");

        final FaturaPorClienteDTO comCteKey = criarDtoSemChaveNatural();
        comCteKey.setCteNumber(12345L);
        comCteKey.setCteIssuedAt("2026-03-09T10:15:30-03:00");
        comCteKey.setCteKey("35260360960473000243570030001067161982016073");
        comCteKey.setFaturaDocument("DOC-7788");
        comCteKey.setFaturaIssueDate("2026-03-09");
        comCteKey.setFaturaDueDate("2026-03-15");

        final String uniqueIdSemCteKey = mapper.calcularIdentificadorUnico(semCteKey);
        final String uniqueIdComCteKey = mapper.calcularIdentificadorUnico(comCteKey);

        assertEquals(uniqueIdSemCteKey, uniqueIdComCteKey);
    }

    @Test
    void deveManterMesmoUniqueIdQuandoStatusOuBaixaMudam() {
        final FaturaPorClienteDTO original = criarDtoSemChaveNatural();
        original.setCteNumber(12345L);
        original.setCteIssuedAt("2026-03-09T10:15:30-03:00");
        original.setFaturaDocument("DOC-7788");
        original.setFaturaIssueDate("2026-03-09");
        original.setFaturaDueDate("2026-03-15");
        original.setCteStatus("authorized");
        original.setCteStatusResult("Autorizado o uso do CT-e");
        original.setFaturaBaixaDate("2026-03-10");

        final FaturaPorClienteDTO atualizado = criarDtoSemChaveNatural();
        atualizado.setCteNumber(12345L);
        atualizado.setCteIssuedAt("2026-03-09T10:15:30-03:00");
        atualizado.setFaturaDocument("DOC-7788");
        atualizado.setFaturaIssueDate("2026-03-09");
        atualizado.setFaturaDueDate("2026-03-15");
        atualizado.setCteStatus("cancelled");
        atualizado.setCteStatusResult("Cancelado");
        atualizado.setFaturaBaixaDate("2026-03-12");

        final String uniqueIdOriginal = mapper.calcularIdentificadorUnico(original);
        final String uniqueIdAtualizado = mapper.calcularIdentificadorUnico(atualizado);

        assertEquals(uniqueIdOriginal, uniqueIdAtualizado);
    }

    @Test
    void deveManterMesmoUniqueIdQuandoCamposComplementaresMudamMasCtePermanece() {
        final FaturaPorClienteDTO original = criarDtoSemChaveNatural();
        original.setCteNumber(12345L);
        original.setNotasFiscais(List.of("NF-1"));
        original.setPedidosCliente(List.of("PED-1"));

        final FaturaPorClienteDTO atualizado = criarDtoSemChaveNatural();
        atualizado.setCteNumber(12345L);
        atualizado.setNotasFiscais(List.of("NF-1", "NF-2", "NF-3"));
        atualizado.setPedidosCliente(List.of("PED-9"));
        atualizado.setFaturaDocument("DOC-ADICIONAL");
        atualizado.setFaturaIssueDate("2026-03-10");
        atualizado.setBillingId("BILL-7788");

        final String uniqueIdOriginal = mapper.calcularIdentificadorUnico(original);
        final String uniqueIdAtualizado = mapper.calcularIdentificadorUnico(atualizado);

        assertEquals(uniqueIdOriginal, uniqueIdAtualizado);
    }

    @Test
    void deveExporAliasesLegadosParaReconciliacao() {
        final FaturaPorClienteDTO dto = criarDtoSemChaveNatural();
        dto.setNfseNumber(123456L);
        dto.setCteKey("35260360960473000243570030001067161982016073");
        dto.setFaturaDocument("DOC-7788");
        dto.setBillingId("9988");

        final List<String> aliases = mapper.calcularIdentificadoresLegados(dto);

        assertEquals(
            List.of(
                "NFSE-123456",
                "35260360960473000243570030001067161982016073",
                "FATURA-DOC-7788",
                "BILLING-9988"
            ),
            aliases
        );
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
