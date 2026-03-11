package br.com.extrator.persistencia.repositorio;

/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/persistencia/repositorio/RepositoryParameterBindingSupport.java
Classe  :  (class)
Pacote  : br.com.extrator.persistencia.repositorio
Modulo  : Persistencia - Repositorio
Papel   : [DESC PENDENTE]
Conecta com: Sem dependencia interna
Fluxo geral:
1) [PENDENTE]
Estrutura interna:
Metodos: [PENDENTE]
Atributos: [PENDENTE]
[DOC-FILE-END]============================================================== */


import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Centraliza helpers de bind de parametros SQL compartilhados pelos repositorios.
 */
abstract class RepositoryParameterBindingSupport {
    protected void setParameter(
        final PreparedStatement statement,
        final int index,
        final Object value,
        final int sqlType
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, sqlType);
        } else {
            statement.setObject(index, value, sqlType);
        }
    }

    protected void setStringParameter(
        final PreparedStatement statement,
        final int index,
        final String value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    protected void setIntegerParameter(
        final PreparedStatement statement,
        final int index,
        final Integer value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    protected void setDoubleParameter(
        final PreparedStatement statement,
        final int index,
        final Double value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DOUBLE);
        } else {
            statement.setDouble(index, value);
        }
    }

    protected void setBooleanParameter(
        final PreparedStatement statement,
        final int index,
        final Boolean value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BOOLEAN);
        } else {
            statement.setBoolean(index, value);
        }
    }

    protected void setBigDecimalParameter(
        final PreparedStatement statement,
        final int index,
        final java.math.BigDecimal value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DECIMAL);
        } else {
            statement.setBigDecimal(index, value);
        }
    }

    protected void setDateTimeParameter(
        final PreparedStatement statement,
        final int index,
        final LocalDateTime value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.valueOf(value));
        }
    }

    protected void setInstantParameter(
        final PreparedStatement statement,
        final int index,
        final Instant value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setTimestamp(index, Timestamp.from(value));
        }
    }

    protected void setOffsetDateTimeParameter(
        final PreparedStatement statement,
        final int index,
        final java.time.OffsetDateTime value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE);
        } else {
            // setObject com OffsetDateTime é mapeado pelo driver JDBC 4.2+ diretamente
            // para DATETIMEOFFSET no SQL Server, preservando o offset original da API.
            statement.setObject(index, value);
        }
    }

    protected void setLongParameter(
        final PreparedStatement statement,
        final int index,
        final Long value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.BIGINT);
        } else {
            statement.setLong(index, value);
        }
    }

    protected void setDateParameter(
        final PreparedStatement statement,
        final int index,
        final LocalDate value
    ) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.DATE);
        } else {
            statement.setDate(index, Date.valueOf(value));
        }
    }
}
