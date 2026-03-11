package br.com.extrator.observabilidade.quality;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/quality/DataQualityQueryPort.java
Classe  : DataQualityQueryPort (class)
Pacote  : br.com.extrator.observabilidade.quality
Modulo  : Observabilidade - Quality
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.time.LocalDate;
import java.time.LocalDateTime;

public interface DataQualityQueryPort {
    long contarDuplicidadesChaveNatural(String entidade, LocalDate dataInicio, LocalDate dataFim);

    long contarLinhasIncompletas(String entidade, LocalDate dataInicio, LocalDate dataFim);

    LocalDateTime buscarTimestampMaisRecente(String entidade);

    long contarQuebrasReferenciais(String entidade);

    String detectarVersaoSchema(String entidade);
}


