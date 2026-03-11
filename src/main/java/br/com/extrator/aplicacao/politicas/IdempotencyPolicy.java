/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/aplicacao/politicas/IdempotencyPolicy.java
Classe  : IdempotencyPolicy (class)
Pacote  : br.com.extrator.aplicacao.politicas
Modulo  : Politicas - Resiliencia

Papel   : Valida idempotencia de operacoes (chave SHA256 + janela temporal + schema version).

Conecta com:
- RecoveryUseCase (usa para validar replay)

Fluxo geral:
1) gerarChave(payload) calcula hash SHA256 de naturalKeys (ex: data_inicio|data_fim|api|entidade).
2) dentroDaJanela() valida se evento e recente o suficiente (within Duration).
3) schemaCompativel() verifica se versao schema e consistente.
4) Use: prevention de duplicated replays (recovery) dentro de intervalo de tempo.

Estrutura interna:
Atributos-chave:
- naturalKeys: List<String> (campos que identific am unicidade, ex: [data_inicio, data_fim, api]).
- janelaTemporal: Duration (janela de idempotencia, ex: 365 dias).
- schemaVersion: String (versao schema para migracoes, ex: v2).
Metodos principais:
- gerarChave(payload): SHA256 hash de natural keys + schema.
- dentroDaJanela(evento, referencia): valida timestamp dentro janela.
- schemaCompativel(incomingVersion): verifica compatibilidade de schema.
[DOC-FILE-END]============================================================== */
package br.com.extrator.aplicacao.politicas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public class IdempotencyPolicy {
    private final List<String> naturalKeys;
    private final Duration janelaTemporal;
    private final String schemaVersion;

    public IdempotencyPolicy(final List<String> naturalKeys, final Duration janelaTemporal, final String schemaVersion) {
        this.naturalKeys = naturalKeys;
        this.janelaTemporal = janelaTemporal;
        this.schemaVersion = schemaVersion == null ? "v1" : schemaVersion;
    }

    public String gerarChave(final Map<String, Object> payload) {
        final StringJoiner joiner = new StringJoiner("|");
        for (String key : naturalKeys) {
            final Object value = payload == null ? null : payload.get(key);
            joiner.add(key + "=" + (value == null ? "" : value.toString()));
        }
        joiner.add("schema=" + schemaVersion);
        return sha256(joiner.toString());
    }

    public boolean dentroDaJanela(final LocalDateTime evento, final LocalDateTime referencia) {
        if (evento == null || referencia == null || janelaTemporal == null) {
            return true;
        }
        return !evento.isBefore(referencia.minus(janelaTemporal));
    }

    public boolean schemaCompativel(final String incomingSchemaVersion) {
        if (incomingSchemaVersion == null || incomingSchemaVersion.isBlank()) {
            return false;
        }
        return schemaVersion.equalsIgnoreCase(incomingSchemaVersion.trim());
    }

    private String sha256(final String plain) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] bytes = digest.digest(plain.getBytes(StandardCharsets.UTF_8));
            final StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Falha ao gerar hash idempotente", e);
        }
    }
}


