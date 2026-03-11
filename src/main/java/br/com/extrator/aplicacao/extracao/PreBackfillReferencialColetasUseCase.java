/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/extracao/PreBackfillReferencialColetasUseCase.java
Classe  : PreBackfillReferencialColetasUseCase (class)
Pacote  : br.com.extrator.aplicacao.extracao
Modulo  : Use Case - Extracao

Papel   : Executa hidratacao referencial de coletas para resolver manifestos orfaos
          sem contaminar a janela principal auditada.

Conecta com:
- ManifestoOrfaoQueryPort (consulta orfaos no banco)
- GraphQLExtractionService (executa extracao auxiliar de coletas)
- ConfigEtl (buffer retroativo e lookahead)

Fluxo geral:
1) executar(dataInicio, dataFim) resolve a janela retroativa e executa backfill auxiliar.
2) executarPosExtracao(dataInicio, dataFim) faz duas tentativas cirurgicas:
   - retroativa: apenas antes do dia principal
   - lookahead: apenas depois do dia principal
3) Em ambos os casos, a execucao auxiliar extrai somente coletas.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.extracao;

import java.time.LocalDate;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import br.com.extrator.aplicacao.contexto.AplicacaoContexto;
import br.com.extrator.integracao.graphql.services.GraphQLExtractionService;
import br.com.extrator.suporte.configuracao.ConfigEtl;

public class PreBackfillReferencialColetasUseCase {

    private static final Logger log = LoggerFactory.getLogger(PreBackfillReferencialColetasUseCase.class);

    public PreBackfillReferencialColetasUseCase() {
    }

    public void executar(final LocalDate dataInicio, final LocalDate dataFim) {
        final Optional<LocalDate> dataOrfao = buscarDataMaisAntigaManifestoOrfao();
        final LocalDate inicioEfetivo = resolverInicioEfetivo(dataInicio, dataOrfao);
        executarColetasReferencial("PRE-BACKFILL", inicioEfetivo, dataFim);
    }

    public void executarPosExtracao(final LocalDate dataInicio, final LocalDate dataFim) {
        final Optional<LocalDate> dataOrfao = buscarDataMaisAntigaManifestoOrfao();
        if (dataOrfao.isEmpty()) {
            log.info(
                "POS-HIDRATACAO | janela_dinamica=false | inicio_estatico={} | fim_estatico={} | motivo=sem_orfaos_no_banco",
                dataInicio,
                dataFim
            );
            return;
        }

        final LocalDate inicioRetroativo = resolverInicioEfetivo(dataInicio, dataOrfao);
        final LocalDate fimRetroativo = resolverFimRetroativoPosExtracao(dataInicio);
        if (!inicioRetroativo.isAfter(fimRetroativo)) {
            executarColetasReferencial("POS-HIDRATACAO-RETROATIVA", inicioRetroativo, fimRetroativo);
        } else {
            log.info(
                "POS-HIDRATACAO-RETROATIVA | inicio_principal={} | orfao_mais_antigo={} | motivo=sem_janela_fora_do_periodo_principal",
                dataInicio,
                dataOrfao.get()
            );
        }

        final Optional<LocalDate> orfaoRemanescente = buscarDataMaisAntigaManifestoOrfao();
        if (orfaoRemanescente.isEmpty()) {
            log.info(
                "POS-HIDRATACAO | inicio_principal={} | fim_principal={} | motivo=orfaos_resolvidos_sem_lookahead",
                dataInicio,
                dataFim
            );
            return;
        }

        final Optional<LocalDate> fimLookahead = resolverFimLookaheadPosExtracao(dataFim);
        if (fimLookahead.isEmpty()) {
            log.info(
                "POS-HIDRATACAO-LOOKAHEAD | fim_principal={} | motivo=lookahead_desabilitado",
                dataFim
            );
            return;
        }

        final LocalDate inicioLookahead = resolverInicioLookaheadPosExtracao(dataFim);
        executarColetasReferencial("POS-HIDRATACAO-LOOKAHEAD", inicioLookahead, fimLookahead.get());
    }

    private Optional<LocalDate> buscarDataMaisAntigaManifestoOrfao() {
        return AplicacaoContexto.manifestoOrfaoQueryPort().buscarDataMaisAntigaManifestoOrfao();
    }

    private LocalDate resolverInicioEfetivo(
        final LocalDate inicioEstatico,
        final Optional<LocalDate> dataOrfao
    ) {
        final int bufferDias = ConfigEtl.obterEtlReferencialColetasBackfillBufferDias();
        if (dataOrfao.isPresent()) {
            final LocalDate inicioDinamico = dataOrfao.get().minusDays(bufferDias);
            if (inicioDinamico.isBefore(inicioEstatico)) {
                log.info(
                    "PRE-BACKFILL | janela_dinamica=true | orfao_mais_antigo={} | buffer_dias={} | inicio_estatico={} | usando inicio_dinamico={}",
                    dataOrfao.get(), bufferDias, inicioEstatico, inicioDinamico
                );
                return inicioDinamico;
            }
            log.info(
                "PRE-BACKFILL | janela_dinamica=false | inicio_estatico={} | orfao_mais_antigo={} | buffer_dias={} | motivo=janela_estatica_ja_cobre_buffer",
                inicioEstatico, dataOrfao.get(), bufferDias
            );
            return inicioEstatico;
        }
        log.info(
            "PRE-BACKFILL | janela_dinamica=false | inicio_estatico={} | motivo=sem_orfaos_no_banco",
            inicioEstatico
        );
        return inicioEstatico;
    }

    private LocalDate resolverFimRetroativoPosExtracao(final LocalDate inicioPrincipal) {
        return inicioPrincipal.minusDays(1);
    }

    private LocalDate resolverInicioLookaheadPosExtracao(final LocalDate fimPrincipal) {
        return fimPrincipal.plusDays(1);
    }

    private Optional<LocalDate> resolverFimLookaheadPosExtracao(final LocalDate fimPrincipal) {
        final int lookaheadDias = ConfigEtl.obterEtlReferencialColetasLookaheadDias();
        if (lookaheadDias <= 0) {
            return Optional.empty();
        }

        final LocalDate fimDinamico = fimPrincipal.plusDays(lookaheadDias);
        log.info(
            "POS-HIDRATACAO-LOOKAHEAD | fim_estatico={} | lookahead_dias={} | usando fim_dinamico={}",
            fimPrincipal,
            lookaheadDias,
            fimDinamico
        );
        return Optional.of(fimDinamico);
    }

    private void executarColetasReferencial(
        final String contexto,
        final LocalDate dataInicio,
        final LocalDate dataFim
    ) {
        log.info(
            "{} | periodo={} a {} | estrategia=somente_coletas",
            contexto,
            dataInicio,
            dataFim
        );
        new GraphQLExtractionService().executarSomenteColetasReferencial(dataInicio, dataFim);
    }
}
