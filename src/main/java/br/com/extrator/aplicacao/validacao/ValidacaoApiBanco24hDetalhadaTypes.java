/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/validacao/ValidacaoApiBanco24hDetalhadaTypes.java
Classe  : ValidacaoApiBanco24hDetalhadaTypes (final class)
Pacote  : br.com.extrator.aplicacao.validacao
Modulo  : Use Case - Validacao

Papel   : Holder de tipos aninhados (records + interface funcional) para validacao API 24h (chaves, janelas, comparacoes).

Conecta com:
- Nenhuma (tipos puros)

Fluxo geral:
1) Contem 5 tipos internos: ResultadoApiChavesSupplier, EntidadeValidacao, JanelaExecucao, ResultadoApiChaves, ResultadoComparacao, ResumoExecucao.
2) Records imutaveis para passagem de dados entre componentes.
3) Funcional supplier para lazy eval de dados de API.

Estrutura interna:
Tipos aninhados:
- ResultadoApiChavesSupplier: @FunctionalInterface throws Exception.
- EntidadeValidacao(entidade, fornecedor): record com supplier lazy.
- JanelaExecucao(inicio, fim, alinhadaAoPeriodo): record de intervalo com flag.
- ResultadoApiChaves: contas (bruto, unico, invalidos), chaves, hashes, detalhe, chaves_toleradas.
- ResultadoComparacao: entidade, contas API, contas Banco, discrepancias, detalhe, metodo ok().
- ResumoExecucao: ok, falhas (consolidacao).
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.validacao;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

final class ValidacaoApiBanco24hDetalhadaTypes {
    private ValidacaoApiBanco24hDetalhadaTypes() {
    }

    @FunctionalInterface
    interface ResultadoApiChavesSupplier {
        ResultadoApiChaves get() throws Exception;
    }

    record EntidadeValidacao(
        String entidade,
        ResultadoApiChavesSupplier fornecedor
    ) { }

    record JanelaExecucao(
        LocalDateTime inicio,
        LocalDateTime fim,
        boolean alinhadaAoPeriodo
    ) { }

    record ResultadoApiChaves(
        int apiBruto,
        int apiUnico,
        int invalidos,
        Set<String> chaves,
        Map<String, String> hashesPorChave,
        Map<String, Set<String>> hashesAceitosPorChave,
        String detalhe,
        Set<String> chavesToleradasNoBanco,
        Map<String, String> metadataPorChave,
        boolean extracaoCompleta,
        String motivoInterrupcao,
        int paginasProcessadas,
        Set<String> caminhosEstruturais
    ) { }

    record ResultadoComparacao(
        String entidade,
        int apiBruto,
        int apiUnico,
        int invalidos,
        int banco,
        int faltantes,
        int excedentes,
        int divergenciasDados,
        boolean apiCompleta,
        String motivoInterrupcaoApi,
        String detalhe
    ) {
        boolean ok() {
            return apiCompleta && faltantes == 0 && excedentes == 0 && divergenciasDados == 0;
        }

        boolean falhaCompletude() {
            return !apiCompleta || faltantes > 0 || excedentes > 0;
        }

        boolean falhaConteudo() {
            return divergenciasDados > 0;
        }
    }

    record ResumoExecucao(int ok, int falhas) { }
}
