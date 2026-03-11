package br.com.extrator.observabilidade.pipeline;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/observabilidade/pipeline/JsonStructuredExtractionLogger.java
Classe  : ExtractionLoggerPort (class)
Pacote  : br.com.extrator.observabilidade.pipeline
Modulo  : Observabilidade - Pipeline
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import br.com.extrator.aplicacao.portas.ExtractionLoggerPort;
import br.com.extrator.suporte.log.SensitiveDataSanitizer;

public final class JsonStructuredExtractionLogger implements ExtractionLoggerPort {
    private static final Logger logger = LoggerFactory.getLogger(JsonStructuredExtractionLogger.class);
    private final ObjectMapper objectMapper;

    public JsonStructuredExtractionLogger() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    @Override
    public void logarEstruturado(final String eventName, final Map<String, Object> fields) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", eventName);
        if (fields != null) {
            payload.putAll(fields);
        }
        try {
            final String json = objectMapper.writeValueAsString(payload);
            logger.info(SensitiveDataSanitizer.sanitize(json));
        } catch (JsonProcessingException e) {
            logger.warn("Falha ao serializar log estruturado event={}: {}", eventName, e.getMessage());
        }
    }
}


