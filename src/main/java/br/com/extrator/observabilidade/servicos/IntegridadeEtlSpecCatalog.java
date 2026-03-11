package br.com.extrator.observabilidade.servicos;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/servicos/IntegridadeEtlSpecCatalog.java
Classe  :  (class)
Pacote  : br.com.extrator.observabilidade.servicos
Modulo  : Observabilidade - Servico
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import br.com.extrator.suporte.validacao.ConstantesEntidades;

final class IntegridadeEtlSpecCatalog {
    private IntegridadeEtlSpecCatalog() {
    }

    static Map<String, IntegridadeEtlSpec> carregarSpecs() {
        final Map<String, IntegridadeEtlSpec> specs = new LinkedHashMap<>();
        specs.put(ConstantesEntidades.USUARIOS_SISTEMA, new IntegridadeEtlSpec(
            ConstantesEntidades.USUARIOS_SISTEMA,
            "dim_usuarios",
            "data_atualizacao",
            List.of("user_id"),
            List.of("user_id", "nome", "data_atualizacao")
        ));
        specs.put(ConstantesEntidades.COLETAS, new IntegridadeEtlSpec(
            ConstantesEntidades.COLETAS,
            ConstantesEntidades.COLETAS,
            "data_extracao",
            List.of("id"),
            List.of("id", "sequence_code", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.FRETES, new IntegridadeEtlSpec(
            ConstantesEntidades.FRETES,
            ConstantesEntidades.FRETES,
            "data_extracao",
            List.of("id"),
            List.of("id", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.MANIFESTOS, new IntegridadeEtlSpec(
            ConstantesEntidades.MANIFESTOS,
            ConstantesEntidades.MANIFESTOS,
            "data_extracao",
            List.of("sequence_code", "identificador_unico"),
            List.of("sequence_code", "identificador_unico", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.COTACOES, new IntegridadeEtlSpec(
            ConstantesEntidades.COTACOES,
            ConstantesEntidades.COTACOES,
            "data_extracao",
            List.of("sequence_code"),
            List.of("sequence_code", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.LOCALIZACAO_CARGAS, new IntegridadeEtlSpec(
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            ConstantesEntidades.LOCALIZACAO_CARGAS,
            "data_extracao",
            List.of("sequence_number"),
            List.of("sequence_number", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.CONTAS_A_PAGAR, new IntegridadeEtlSpec(
            ConstantesEntidades.CONTAS_A_PAGAR,
            ConstantesEntidades.CONTAS_A_PAGAR,
            "data_extracao",
            List.of("sequence_code"),
            List.of("sequence_code", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.FATURAS_POR_CLIENTE, new IntegridadeEtlSpec(
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            ConstantesEntidades.FATURAS_POR_CLIENTE,
            "data_extracao",
            List.of("unique_id"),
            List.of("unique_id", "metadata", "data_extracao")
        ));
        specs.put(ConstantesEntidades.FATURAS_GRAPHQL, new IntegridadeEtlSpec(
            ConstantesEntidades.FATURAS_GRAPHQL,
            ConstantesEntidades.FATURAS_GRAPHQL,
            "data_extracao",
            List.of("id"),
            List.of("id", "metadata", "data_extracao")
        ));
        return Map.copyOf(specs);
    }
}
