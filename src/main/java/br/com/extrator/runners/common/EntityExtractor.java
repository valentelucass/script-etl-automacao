package br.com.extrator.runners.common;

import java.time.LocalDate;
import java.util.List;

import br.com.extrator.api.ResultadoExtracao;

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

        public SaveMetrics(final int registrosSalvos, final int totalUnicos, final int registrosInvalidos) {
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
}
