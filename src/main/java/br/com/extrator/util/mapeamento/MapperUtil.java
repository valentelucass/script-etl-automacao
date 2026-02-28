/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/mapeamento/MapperUtil.java
Classe  : MapperUtil (class)
Pacote  : br.com.extrator.util.mapeamento
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de mapper util.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- MapperUtil(): realiza operacao relacionada a "mapper util".
- createObjectMapper(): realiza operacao relacionada a "create object mapper".
- sharedJson(): realiza operacao relacionada a "shared json".
- toJson(...1 args): realiza operacao relacionada a "to json".
Atributos-chave:
- logger: logger da classe para diagnostico.
- SHARED_MAPPER: apoio de mapeamento de dados.
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.mapeamento;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário centralizado para serialização JSON e ObjectMapper compartilhado.
 * 
 * Elimina a criação repetida de ObjectMapper em múltiplos mappers,
 * garantindo configuração consistente (JavaTimeModule registrado).
 * 
 * @since 2.3.2
 */
public final class MapperUtil {
    private static final Logger logger = LoggerFactory.getLogger(MapperUtil.class);
    private static final ObjectMapper SHARED_MAPPER = createObjectMapper();
    
    private MapperUtil() {
        // Classe utilitária - construtor privado
    }
    
    /**
     * Cria e configura ObjectMapper com JavaTimeModule.
     */
    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
    
    /**
     * Retorna instância compartilhada de ObjectMapper configurado.
     * 
     * @return ObjectMapper com JavaTimeModule registrado
     */
    public static ObjectMapper sharedJson() {
        return SHARED_MAPPER;
    }
    
    /**
     * Serializa objeto para JSON string.
     * 
     * @param obj Objeto a ser serializado
     * @return JSON string ou null em caso de erro
     */
    public static String toJson(final Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return SHARED_MAPPER.writeValueAsString(obj);
        } catch (final Exception e) {
            logger.error("Erro ao serializar objeto para JSON: {}", e.getMessage());
            return null;
        }
    }
}
