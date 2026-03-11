package br.com.extrator.aplicacao.validacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoApiBanco24hDetalhadaMetadataHasherTest {

    private final ValidacaoApiBanco24hDetalhadaMetadataHasher hasher =
        new ValidacaoApiBanco24hDetalhadaMetadataHasher();

    @Test
    void deveIgnorarCamposVolateisDeFretes() {
        final String baseline = """
            {
              "id":"FRETE-1",
              "accountingCreditId":"99",
              "serviceAt":"2026-03-09T10:15:30Z",
              "serviceDate":"2026-03-09",
              "status":"delivering",
              "deliveryPredictionDate":"2026-03-10",
              "cte":{"key":"3526","number":123,"issuedAt":"2026-03-09T08:00:00Z"}
            }
            """;
        final String drift = """
            {
              "id":"FRETE-1",
              "accountingCreditId":"99",
              "serviceAt":"2026-03-09T10:15:30Z",
              "serviceDate":"2026-03-09",
              "status":"finished",
              "deliveryPredictionDate":"2026-03-11",
              "cte":{"key":"3526","number":123,"issuedAt":"2026-03-09T08:00:00Z"}
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.FRETES, baseline),
            hasher.hashMetadata(ConstantesEntidades.FRETES, drift)
        );
    }

    @Test
    void deveDetectarMudancaEstavelEmColetas() {
        final String baseline = """
            {
              "id":"COLETA-1",
              "sequenceCode":123,
              "requestDate":"2026-03-09",
              "serviceDate":"2026-03-10",
              "invoicesValue":250.30,
              "user":{"id":"USR-1","name":"Operador"}
            }
            """;
        final String alterado = """
            {
              "id":"COLETA-1",
              "sequenceCode":123,
              "requestDate":"2026-03-09",
              "serviceDate":"2026-03-11",
              "invoicesValue":999.99,
              "user":{"id":"USR-1","name":"Outro nome"}
            }
            """;

        assertNotEquals(
            hasher.hashMetadata(ConstantesEntidades.COLETAS, baseline),
            hasher.hashMetadata(ConstantesEntidades.COLETAS, alterado)
        );
    }

    @Test
    void deveIgnorarStatusVolatilEmLocalizacaoCargas() {
        final String baseline = """
            {
              "sequence_number":334087,
              "service_at":"2026-03-09T10:15:30Z",
              "fit_fln_status":"in_transfer",
              "fit_fln_cln_nickname":"SPO",
              "fit_fhe_cte_key":"3526"
            }
            """;
        final String drift = """
            {
              "sequence_number":334087,
              "service_at":"2026-03-09T10:15:30Z",
              "fit_fln_status":"finished",
              "fit_fln_cln_nickname":"SPO",
              "fit_fhe_cte_key":"3526"
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.LOCALIZACAO_CARGAS, baseline),
            hasher.hashMetadata(ConstantesEntidades.LOCALIZACAO_CARGAS, drift)
        );
    }

    @Test
    void deveIgnorarCamposVolateisDeManifestos() {
        final String baseline = """
            {
              "sequence_code":61340,
              "pick_sequence_code":-1,
              "mdfe_number":2529,
              "status":"open",
              "manifest_freights_total":"1450.90",
              "paying_total":"1010.20",
              "distribution_pole":"SPO",
              "vehicle_plate":"ABC1D23",
              "contract_number":"CTR-9"
            }
            """;
        final String drift = """
            {
              "sequence_code":61340,
              "pick_sequence_code":-1,
              "mdfe_number":2529,
              "status":"finished",
              "manifest_freights_total":"9999.99",
              "paying_total":"3000.00",
              "distribution_pole":"SPO",
              "vehicle_plate":"ABC1D23",
              "contract_number":"CTR-9"
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.MANIFESTOS, baseline),
            hasher.hashMetadata(ConstantesEntidades.MANIFESTOS, drift)
        );
    }

    @Test
    void deveDetectarMudancaEstavelEmManifestos() {
        final String baseline = """
            {
              "sequence_code":61340,
              "pick_sequence_code":-1,
              "mdfe_number":2529,
              "distribution_pole":"SPO",
              "vehicle_plate":"ABC1D23",
              "contract_number":"CTR-9"
            }
            """;
        final String alterado = """
            {
              "sequence_code":61340,
              "pick_sequence_code":-1,
              "mdfe_number":2529,
              "distribution_pole":"VCP",
              "vehicle_plate":"ABC1D23",
              "contract_number":"CTR-9"
            }
            """;

        assertNotEquals(
            hasher.hashMetadata(ConstantesEntidades.MANIFESTOS, baseline),
            hasher.hashMetadata(ConstantesEntidades.MANIFESTOS, alterado)
        );
    }

    @Test
    void deveIgnorarDriftOperacionalEmManifestos() {
        final String baseline = """
            {
              "sequence_code":61379,
              "pick_sequence_code":-1,
              "mdfe_number":-1,
              "distribution_pole":"SPO",
              "classification":"DISTRIBUICAO",
              "vehicle_plate":"ABC1D23",
              "vehicle_type":"Truck",
              "vehicle_owner":"Transportadora A",
              "driver_name":"Motorista A",
              "contract_number":"CTR-9",
              "contract_type":"driver",
              "calculation_type":"price_table",
              "cargo_type":"closed",
              "creation_user_name":"Maria",
              "adjustment_user_name":"Joao",
              "programacao_sequence_code":"PGM-1",
              "programacao_cliente":"Cliente A",
              "programacao_tipo_servico":"EXPRESSO"
            }
            """;
        final String drift = """
            {
              "sequence_code":61379,
              "pick_sequence_code":-1,
              "mdfe_number":-1,
              "distribution_pole":"SPO",
              "classification":"DISTRIBUICAO",
              "vehicle_plate":"ABC1D23",
              "vehicle_type":"Truck",
              "vehicle_owner":"Transportadora B",
              "driver_name":"Motorista B",
              "contract_number":"CTR-9",
              "contract_type":"driver",
              "calculation_type":"price_table",
              "cargo_type":"closed",
              "creation_user_name":"Operador 2",
              "adjustment_user_name":"Operador 3",
              "programacao_sequence_code":"PGM-2",
              "programacao_cliente":"Cliente B",
              "programacao_tipo_servico":"PADRAO"
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.MANIFESTOS, baseline),
            hasher.hashMetadata(ConstantesEntidades.MANIFESTOS, drift)
        );
    }

    @Test
    void deveIgnorarCamposVolateisDeContasAPagar() {
        final String baseline = """
            {
              "ant_ils_sequence_code":"107626",
              "document":"NF-10",
              "issue_date":"2026-03-09",
              "type":"Accounting::Debit::Manual",
              "value_to_pay":"1200.55",
              "paid":false,
              "paid_value":"0.0",
              "ant_ils_atn_transaction_date":"2026-03-10",
              "ant_ils_comments":"aberta",
              "ant_rir_name":"Fornecedor A",
              "ant_crn_psn_nickname":"SPO",
              "ant_ils_expense_description":"Combustivel"
            }
            """;
        final String drift = """
            {
              "ant_ils_sequence_code":"107626",
              "document":"NF-10",
              "issue_date":"2026-03-09",
              "type":"Accounting::Debit::Manual",
              "value_to_pay":"0.0",
              "paid":true,
              "paid_value":"1200.55",
              "ant_ils_atn_transaction_date":"2026-03-11",
              "ant_ils_comments":"liquidada",
              "ant_rir_name":"Fornecedor A",
              "ant_crn_psn_nickname":"SPO",
              "ant_ils_expense_description":"Combustivel"
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.CONTAS_A_PAGAR, baseline),
            hasher.hashMetadata(ConstantesEntidades.CONTAS_A_PAGAR, drift)
        );
    }

    @Test
    void deveDetectarMudancaEstavelEmContasAPagar() {
        final String baseline = """
            {
              "ant_ils_sequence_code":"107626",
              "document":"NF-10",
              "issue_date":"2026-03-09",
              "type":"Accounting::Debit::Manual",
              "ant_rir_name":"Fornecedor A",
              "ant_crn_psn_nickname":"SPO",
              "ant_ils_expense_description":"Combustivel"
            }
            """;
        final String alterado = """
            {
              "ant_ils_sequence_code":"107626",
              "document":"NF-99",
              "issue_date":"2026-03-09",
              "type":"Accounting::Debit::Manual",
              "ant_rir_name":"Fornecedor A",
              "ant_crn_psn_nickname":"SPO",
              "ant_ils_expense_description":"Combustivel"
            }
            """;

        assertNotEquals(
            hasher.hashMetadata(ConstantesEntidades.CONTAS_A_PAGAR, baseline),
            hasher.hashMetadata(ConstantesEntidades.CONTAS_A_PAGAR, alterado)
        );
    }
}
