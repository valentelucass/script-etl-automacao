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
    void deveIgnorarDriftOperacionalEmColetas() {
        final String baseline = """
            {
              "id":"COLETA-1",
              "sequenceCode":123,
              "requestDate":"2026-03-09",
              "serviceDate":"2026-03-10",
              "requestHour":"08:00",
              "serviceStartHour":"09:00",
              "finishDate":"2026-03-10",
              "serviceEndHour":"17:00",
              "status":"open",
              "customer":{"id":"CLI-1","cnpj":"12345678000100"},
              "pickAddress":{"postalCode":"01001000","number":"10","city":{"name":"Sao Paulo","state":{"code":"SP"}}}
            }
            """;
        final String drift = """
            {
              "id":"COLETA-1",
              "sequenceCode":123,
              "requestDate":"2026-03-09",
              "serviceDate":"2026-03-10",
              "requestHour":"08:00",
              "serviceStartHour":"10:30",
              "finishDate":null,
              "serviceEndHour":null,
              "status":"finished",
              "customer":{"id":"CLI-1","cnpj":"12345678000100"},
              "pickAddress":{"postalCode":"01001000","number":"10","city":{"name":"Sao Paulo","state":{"code":"SP"}}}
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.COLETAS, baseline),
            hasher.hashMetadata(ConstantesEntidades.COLETAS, drift)
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
    void deveIgnorarLocalizacaoAtualVolatilEmLocalizacaoCargas() {
        final String baseline = """
            {
              "sequence_number":334087,
              "corporation_sequence_number":455,
              "service_at":"2026-03-09T10:15:30Z",
              "service_type":"NORMAL",
              "total":"1500.00",
              "fit_fln_cln_nickname":"SPO",
              "fit_fhe_cte_number":123,
              "fit_fhe_cte_key":"3526"
            }
            """;
        final String drift = """
            {
              "sequence_number":334087,
              "corporation_sequence_number":455,
              "service_at":"2026-03-09T10:15:30Z",
              "service_type":"NORMAL",
              "total":"1500.00",
              "fit_fln_cln_nickname":"RJR",
              "fit_fhe_cte_number":123,
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
    void deveIgnorarDriftOperacionalEmInventario() {
        final String baseline = """
            {
              "sequence_code":2270,
              "type":"CheckIn::Order::Unloading",
              "started_at":"2026-04-13T06:09:00.000-03:00",
              "finished_at":"2026-04-13T14:43:00.000-03:00",
              "status":"finished",
              "cnr_c_s_read_volumes":4,
              "cnr_c_s_fit_corporation_sequence_number":355519,
              "cnr_c_s_fit_invoices_mapping":["258442"],
              "cnr_c_s_fit_invoices_value":"948.95",
              "cnr_c_s_fit_real_weight":"37.4",
              "cnr_c_s_fit_total_cubic_volume":"0.4044",
              "cnr_c_s_fit_taxed_weight":"80.88",
              "cnr_c_s_fit_invoices_volumes":4,
              "cnr_c_s_fit_dpn_delivery_prediction_at":"2026-04-14T23:59:59.999-03:00",
              "cnr_c_s_fit_dpn_performance_finished_at":"2026-04-13T18:00:00.000-03:00",
              "cnr_c_s_fit_fte_lce_occurrence_at":"2026-04-14T15:22:54.734-03:00",
              "cnr_c_s_fit_fte_lce_ore_description":"Comprovante de Entrega Anexado",
              "cnr_c_s_fit_dyn_name":"RJR - RJR - RIO DE JANEIRO - POLO",
              "cnr_c_s_fit_dyn_drt_nickname":"RJR - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
              "cnr_c_s_fit_pyr_nickname":"IMPPAR (Umuarama)",
              "cnr_c_s_fit_rpt_nickname":"ATAC-FIRE SEGURANCA CONTRA INCENDIO",
              "cnr_c_s_fit_rpt_ads_cty_name":"Rio de Janeiro",
              "cnr_c_s_fit_sdr_nickname":"IMPPAR (Umuarama)",
              "cnr_c_s_fit_sdr_ads_cty_name":"Umuarama",
              "cnr_cis_eoe_psn_name":"LUCAS DE LIMA RAMOS",
              "cnr_crn_psn_nickname":"RJR - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
            }
            """;
        final String drift = """
            {
              "sequence_code":2270,
              "type":"CheckIn::Order::Unloading",
              "started_at":"2026-04-13T06:09:00.000-03:00",
              "finished_at":null,
              "status":"pending",
              "cnr_c_s_read_volumes":5,
              "cnr_c_s_fit_corporation_sequence_number":355519,
              "cnr_c_s_fit_invoices_mapping":["258442"],
              "cnr_c_s_fit_invoices_value":"948.95",
              "cnr_c_s_fit_real_weight":"37.4",
              "cnr_c_s_fit_total_cubic_volume":"0.4044",
              "cnr_c_s_fit_taxed_weight":"80.88",
              "cnr_c_s_fit_invoices_volumes":4,
              "cnr_c_s_fit_dpn_delivery_prediction_at":"2026-04-15T23:59:59.999-03:00",
              "cnr_c_s_fit_dpn_performance_finished_at":null,
              "cnr_c_s_fit_fte_lce_occurrence_at":"2026-04-15T10:39:00.000-03:00",
              "cnr_c_s_fit_fte_lce_ore_description":"Saida para Entrega",
              "cnr_c_s_fit_dyn_name":"RJR - RJR - RIO DE JANEIRO - POLO",
              "cnr_c_s_fit_dyn_drt_nickname":"RJR - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA",
              "cnr_c_s_fit_pyr_nickname":"IMPPAR (Umuarama)",
              "cnr_c_s_fit_rpt_nickname":"ATAC-FIRE SEGURANCA CONTRA INCENDIO",
              "cnr_c_s_fit_rpt_ads_cty_name":"Rio de Janeiro",
              "cnr_c_s_fit_sdr_nickname":"IMPPAR (Umuarama)",
              "cnr_c_s_fit_sdr_ads_cty_name":"Umuarama",
              "cnr_cis_eoe_psn_name":"Outro Conferente",
              "cnr_crn_psn_nickname":"RJR - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA"
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.INVENTARIO, baseline),
            hasher.hashMetadata(ConstantesEntidades.INVENTARIO, drift)
        );
    }

    @Test
    void deveDetectarMudancaEstavelEmSinistros() {
        final String baseline = """
            {
              "sequence_code":9981,
              "opening_at_date":"2026-04-22",
              "occurrence_at_date":"2026-04-22",
              "insurance_occurrence_number":4321,
              "corporation_sequence_number":7788,
              "occurrence_code":"A01",
              "occurrence_description":"Avaria parcial"
            }
            """;
        final String alterado = """
            {
              "sequence_code":9981,
              "opening_at_date":"2026-04-22",
              "occurrence_at_date":"2026-04-22",
              "insurance_occurrence_number":4321,
              "corporation_sequence_number":7788,
              "occurrence_code":"A02",
              "occurrence_description":"Extravio"
            }
            """;

        assertNotEquals(
            hasher.hashMetadata(ConstantesEntidades.SINISTROS, baseline),
            hasher.hashMetadata(ConstantesEntidades.SINISTROS, alterado)
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

    @Test
    void deveIgnorarDriftOperacionalEmFaturasPorCliente() {
        final String baseline = """
            {
              "fit_nse_number":123,
              "nfse_number":"123",
              "fit_fhe_cte_number":456,
              "fit_fhe_cte_issued_at":"2026-03-09T10:00:00Z",
              "fit_fhe_cte_key":"CTE-1",
              "fit_fhe_cte_status":"authorized",
              "fit_fhe_cte_status_result":"ok",
              "fit_ant_document":"FAT-100",
              "fit_ant_issue_date":"2026-03-09",
              "fit_ant_value":"1500.75",
              "fit_ant_ils_due_date":"2026-03-20",
              "fit_ant_ils_original_due_date":"2026-03-20",
              "fit_ant_ils_atn_transaction_date":"2026-03-21",
              "total":"1300.50",
              "third_party_ctes_value":"200.25",
              "type":"Freight::Normal",
              "fit_crn_psn_nickname":"SPO",
              "fit_diy_sae_name":"SP",
              "fit_fsn_name":"DISTRIBUICAO",
              "fit_pyr_name":"Pagador A",
              "fit_pyr_document":"12345678000100",
              "fit_rpt_name":"Remetente A",
              "fit_rpt_document":"22345678000100",
              "fit_sdr_name":"Destinatario A",
              "fit_sdr_document":"32345678000100",
              "fit_sps_slr_psn_name":"Vendedor A",
              "invoices_mapping":["NF-1","NF-2"],
              "fit_fte_invoices_order_number":["PED-1","PED-2"]
            }
            """;
        final String drift = """
            {
              "fit_nse_number":123,
              "nfse_number":"123",
              "fit_fhe_cte_number":456,
              "fit_fhe_cte_issued_at":"2026-03-09T10:00:00Z",
              "fit_fhe_cte_key":"CTE-1",
              "fit_fhe_cte_status":"cancelled",
              "fit_fhe_cte_status_result":"changed",
              "fit_ant_document":"FAT-100",
              "fit_ant_issue_date":"2026-03-09",
              "fit_ant_value":"1500.75",
              "fit_ant_ils_due_date":"2026-03-20",
              "fit_ant_ils_original_due_date":"2026-03-20",
              "fit_ant_ils_atn_transaction_date":"2026-03-25",
              "total":"1300.50",
              "third_party_ctes_value":"200.25",
              "type":"Freight::Normal",
              "fit_crn_psn_nickname":"SPO",
              "fit_diy_sae_name":"SP",
              "fit_fsn_name":"DISTRIBUICAO",
              "fit_pyr_name":"Pagador B",
              "fit_pyr_document":"12345678000100",
              "fit_rpt_name":"Remetente B",
              "fit_rpt_document":"22345678000100",
              "fit_sdr_name":"Destinatario B",
              "fit_sdr_document":"32345678000100",
              "fit_sps_slr_psn_name":"Vendedor B",
              "invoices_mapping":["NF-2","NF-1"],
              "fit_fte_invoices_order_number":["PED-2","PED-1"]
            }
            """;

        assertEquals(
            hasher.hashMetadata(ConstantesEntidades.FATURAS_POR_CLIENTE, baseline),
            hasher.hashMetadata(ConstantesEntidades.FATURAS_POR_CLIENTE, drift)
        );
    }

    @Test
    void deveDetectarMudancaEstavelEmFaturasPorCliente() {
        final String baseline = """
            {
              "fit_nse_number":123,
              "fit_fhe_cte_number":456,
              "fit_fhe_cte_issued_at":"2026-03-09T10:00:00Z",
              "fit_fhe_cte_key":"CTE-1",
              "fit_ant_document":"FAT-100",
              "fit_ant_issue_date":"2026-03-09",
              "fit_ant_value":"1500.75",
              "fit_ant_ils_due_date":"2026-03-20",
              "fit_ant_ils_original_due_date":"2026-03-20",
              "total":"1300.50",
              "third_party_ctes_value":"200.25",
              "type":"Freight::Normal",
              "fit_crn_psn_nickname":"SPO",
              "fit_diy_sae_name":"SP",
              "fit_fsn_name":"DISTRIBUICAO",
              "fit_pyr_document":"12345678000100",
              "fit_rpt_document":"22345678000100",
              "fit_sdr_document":"32345678000100"
            }
            """;
        final String alterado = """
            {
              "fit_nse_number":123,
              "fit_fhe_cte_number":456,
              "fit_fhe_cte_issued_at":"2026-03-09T10:00:00Z",
              "fit_fhe_cte_key":"CTE-1",
              "fit_ant_document":"FAT-101",
              "fit_ant_issue_date":"2026-03-09",
              "fit_ant_value":"1500.75",
              "fit_ant_ils_due_date":"2026-03-20",
              "fit_ant_ils_original_due_date":"2026-03-20",
              "total":"1300.50",
              "third_party_ctes_value":"200.25",
              "type":"Freight::Normal",
              "fit_crn_psn_nickname":"SPO",
              "fit_diy_sae_name":"SP",
              "fit_fsn_name":"DISTRIBUICAO",
              "fit_pyr_document":"12345678000100",
              "fit_rpt_document":"22345678000100",
              "fit_sdr_document":"32345678000100"
            }
            """;

        assertNotEquals(
            hasher.hashMetadata(ConstantesEntidades.FATURAS_POR_CLIENTE, baseline),
            hasher.hashMetadata(ConstantesEntidades.FATURAS_POR_CLIENTE, alterado)
        );
    }
}
