/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/common/DataExportEntityExtractor.java
Classe  : DataExportEntityExtractor (interface)
Pacote  : br.com.extrator.runners.common
Modulo  : Componente compartilhado de extracao
Papel   : Implementa responsabilidade de data export entity extractor.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Disponibiliza contratos e utilitarios transversais.
2) Padroniza resultado, log e comportamento comum.
3) Reduz duplicacao entre GraphQL e DataExport.

Estrutura interna:
Metodos principais:
- Metodos nao mapeados automaticamente; consulte a implementacao abaixo.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.runners.common;

import java.util.List;

/**
 * Interface especializada para extractors do DataExport que precisam
 * retornar informações sobre deduplicação (totalUnicos).
 * 
 * @param <T> Tipo do DTO retornado pela API
 */
public interface DataExportEntityExtractor<T> extends EntityExtractor<T> {
    
    /**
     * Salva entidades no banco de dados e retorna informações sobre a operação.
     * 
     * @param dtos Lista de DTOs a serem salvos
     * @return Resultado do save com informações de deduplicação
     * @throws java.sql.SQLException Se houver erro ao salvar
     */
    SaveResult saveWithDeduplication(List<T> dtos) throws java.sql.SQLException;
    
    /**
     * Resultado do save com informações de deduplicação.
     */
    class SaveResult {
        private final int registrosSalvos;
        private final int totalUnicos;
        private final int registrosInvalidos;
        
        public SaveResult(final int registrosSalvos, final int totalUnicos) {
            this(registrosSalvos, totalUnicos, 0);
        }

        public SaveResult(final int registrosSalvos, final int totalUnicos, final int registrosInvalidos) {
            this.registrosSalvos = registrosSalvos;
            this.totalUnicos = totalUnicos;
            this.registrosInvalidos = registrosInvalidos;
        }
        
        public int getRegistrosSalvos() {
            return registrosSalvos;
        }
        
        public int getTotalUnicos() {
            return totalUnicos;
        }

        public int getRegistrosInvalidos() {
            return registrosInvalidos;
        }
    }
    
    /**
     * Implementação padrão que delega para saveWithDeduplication().
     * Extractors podem sobrescrever para fornecer informações mais precisas.
     */
    @Override
    default int save(final List<T> dtos) throws java.sql.SQLException {
        final SaveResult result = saveWithDeduplication(dtos);
        return result.getRegistrosSalvos();
    }

    @Override
    default SaveMetrics saveWithMetrics(final List<T> dtos) throws java.sql.SQLException {
        final SaveResult result = saveWithDeduplication(dtos);
        return new SaveMetrics(
            result.getRegistrosSalvos(),
            result.getTotalUnicos(),
            result.getRegistrosInvalidos()
        );
    }
}
