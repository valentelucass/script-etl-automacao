/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/runners/common/EntityExtractor.java
Classe  : EntityExtractor (interface)
Pacote  : br.com.extrator.integracao.comum
Modulo  : Componente compartilhado de extracao
Papel   : Implementa responsabilidade de entity extractor.

Conecta com:
- ResultadoExtracao (api)

Fluxo geral:
1) Disponibiliza contratos e utilitarios transversais.
2) Padroniza resultado, log e comportamento comum.
3) Reduz duplicacao entre GraphQL e DataExport.

Estrutura interna:
Metodos principais:
- getEmoji(): expone valor atual do estado interno.
Atributos-chave:
- Atributos nao mapeados automaticamente; consulte a implementacao abaixo.
[DOC-FILE-END]============================================================== */

package br.com.extrator.integracao.comum;

import java.time.LocalDate;
import java.util.List;

import br.com.extrator.integracao.ResultadoExtracao;

/**
 * Interface base para extractors de entidades.
 * Define o contrato comum para todas as extrações.
 * 
 * @param <T> Tipo do DTO retornado pela API
 */
public interface EntityExtractor<T> {
    
    /**
     * Extrai dados da API para um intervalo de datas.
     * 
     * @param dataInicio Data de início do período
     * @param dataFim Data de fim do período
     * @return Resultado da extração com lista de DTOs
     */
    ResultadoExtracao<T> extract(LocalDate dataInicio, LocalDate dataFim);
    
    /**
     * Salva entidades no banco de dados.
     * 
     * @param dtos Lista de DTOs a serem salvos
     * @return Número de registros salvos
     * @throws java.sql.SQLException Se houver erro ao salvar
     */
    int save(List<T> dtos) throws java.sql.SQLException;

    /**
     * Salva entidades e retorna métricas de persistência.
     *
     * Implementação padrão:
     * - totalUnicos = tamanho da lista recebida
     * - registrosInvalidos = 0
     */
    default SaveMetrics saveWithMetrics(final List<T> dtos) throws java.sql.SQLException {
        final int totalUnicos = dtos != null ? dtos.size() : 0;
        final int registrosSalvos = save(dtos);
        return new SaveMetrics(registrosSalvos, totalUnicos, 0);
    }
    
    /**
     * Retorna o nome da entidade (usado para logs e identificação).
     * 
     * @return Nome da entidade
     */
    String getEntityName();
    
    /**
     * Retorna o emoji/ícone para logs.
     * 
     * @return Emoji ou símbolo para identificação visual
     */
    default String getEmoji() {
        return "📦";
    }

    /**
     * Métricas de salvamento de uma entidade.
     */
    final class SaveMetrics {
        private final int registrosSalvos;
        private final int totalUnicos;
        private final int registrosInvalidos;
        private final int registrosPersistidos;
        private final int registrosNoOpIdempotente;

        public SaveMetrics(final int registrosSalvos, final int totalUnicos, final int registrosInvalidos) {
            this(registrosSalvos, totalUnicos, registrosInvalidos, registrosSalvos, 0);
        }

        public SaveMetrics(final int registrosSalvos,
                           final int totalUnicos,
                           final int registrosInvalidos,
                           final int registrosPersistidos,
                           final int registrosNoOpIdempotente) {
            this.registrosSalvos = registrosSalvos;
            this.totalUnicos = totalUnicos;
            this.registrosInvalidos = registrosInvalidos;
            this.registrosPersistidos = registrosPersistidos;
            this.registrosNoOpIdempotente = registrosNoOpIdempotente;
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

        public int getRegistrosPersistidos() {
            return registrosPersistidos;
        }

        public int getRegistrosNoOpIdempotente() {
            return registrosNoOpIdempotente;
        }
    }
}
