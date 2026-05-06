package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import br.com.extrator.persistencia.entidade.RasterViagemParadaEntity;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class RasterViagemParadaRepository extends AbstractRepository<RasterViagemParadaEntity> {
    private static final String NOME_TABELA = ConstantesEntidades.RASTER_VIAGEM_PARADAS;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected int executarMerge(final Connection conexao, final RasterViagemParadaEntity parada) throws SQLException {
        if (parada.getCodSolicitacao() == null || parada.getOrdem() == null) {
            throw new SQLException("Nao e possivel executar MERGE Raster parada sem cod_solicitacao e ordem.");
        }

        final String sql = """
            MERGE dbo.raster_viagem_paradas WITH (HOLDLOCK) AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (
                    cod_solicitacao, ordem, tipo, cod_ibge_cidade, cnpj_cliente, codigo_cliente,
                    data_hora_prev_chegada, data_hora_prev_saida, data_hora_real_chegada,
                    data_hora_real_saida, latitude, longitude, dentro_prazo_raster,
                    diferenca_tempo_raster, km_percorrido_entrega, km_restante_entrega,
                    chegou_na_entrega, data_hora_ultima_posicao, latitude_ultima_posicao,
                    longitude_ultima_posicao, referencia_ultima_posicao, metadata, data_extracao
                )
            ON target.cod_solicitacao = source.cod_solicitacao
               AND target.ordem = source.ordem
            WHEN MATCHED THEN
                UPDATE SET
                    tipo = source.tipo,
                    cod_ibge_cidade = source.cod_ibge_cidade,
                    cnpj_cliente = source.cnpj_cliente,
                    codigo_cliente = source.codigo_cliente,
                    data_hora_prev_chegada = source.data_hora_prev_chegada,
                    data_hora_prev_saida = source.data_hora_prev_saida,
                    data_hora_real_chegada = source.data_hora_real_chegada,
                    data_hora_real_saida = source.data_hora_real_saida,
                    latitude = source.latitude,
                    longitude = source.longitude,
                    dentro_prazo_raster = source.dentro_prazo_raster,
                    diferenca_tempo_raster = source.diferenca_tempo_raster,
                    km_percorrido_entrega = source.km_percorrido_entrega,
                    km_restante_entrega = source.km_restante_entrega,
                    chegou_na_entrega = source.chegou_na_entrega,
                    data_hora_ultima_posicao = source.data_hora_ultima_posicao,
                    latitude_ultima_posicao = source.latitude_ultima_posicao,
                    longitude_ultima_posicao = source.longitude_ultima_posicao,
                    referencia_ultima_posicao = source.referencia_ultima_posicao,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (
                    cod_solicitacao, ordem, tipo, cod_ibge_cidade, cnpj_cliente, codigo_cliente,
                    data_hora_prev_chegada, data_hora_prev_saida, data_hora_real_chegada,
                    data_hora_real_saida, latitude, longitude, dentro_prazo_raster,
                    diferenca_tempo_raster, km_percorrido_entrega, km_restante_entrega,
                    chegou_na_entrega, data_hora_ultima_posicao, latitude_ultima_posicao,
                    longitude_ultima_posicao, referencia_ultima_posicao, metadata, data_extracao
                )
                VALUES (
                    source.cod_solicitacao, source.ordem, source.tipo, source.cod_ibge_cidade, source.cnpj_cliente,
                    source.codigo_cliente, source.data_hora_prev_chegada, source.data_hora_prev_saida,
                    source.data_hora_real_chegada, source.data_hora_real_saida, source.latitude, source.longitude,
                    source.dentro_prazo_raster, source.diferenca_tempo_raster, source.km_percorrido_entrega,
                    source.km_restante_entrega, source.chegou_na_entrega, source.data_hora_ultima_posicao,
                    source.latitude_ultima_posicao, source.longitude_ultima_posicao, source.referencia_ultima_posicao,
                    source.metadata, source.data_extracao
                );
            """;

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            int index = 1;
            setLongParameter(statement, index++, parada.getCodSolicitacao());
            setIntegerParameter(statement, index++, parada.getOrdem());
            setStringParameter(statement, index++, parada.getTipo());
            setLongParameter(statement, index++, parada.getCodIbgeCidade());
            setStringParameter(statement, index++, parada.getCnpjCliente());
            setStringParameter(statement, index++, parada.getCodigoCliente());
            setOffsetDateTimeParameter(statement, index++, parada.getDataHoraPrevChegada());
            setOffsetDateTimeParameter(statement, index++, parada.getDataHoraPrevSaida());
            setOffsetDateTimeParameter(statement, index++, parada.getDataHoraRealChegada());
            setOffsetDateTimeParameter(statement, index++, parada.getDataHoraRealSaida());
            setBigDecimalParameter(statement, index++, parada.getLatitude());
            setBigDecimalParameter(statement, index++, parada.getLongitude());
            setStringParameter(statement, index++, parada.getDentroPrazoRaster());
            setStringParameter(statement, index++, parada.getDiferencaTempoRaster());
            setBigDecimalParameter(statement, index++, parada.getKmPercorridoEntrega());
            setBigDecimalParameter(statement, index++, parada.getKmRestanteEntrega());
            setStringParameter(statement, index++, parada.getChegouNaEntrega());
            setOffsetDateTimeParameter(statement, index++, parada.getDataHoraUltimaPosicao());
            setBigDecimalParameter(statement, index++, parada.getLatitudeUltimaPosicao());
            setBigDecimalParameter(statement, index++, parada.getLongitudeUltimaPosicao());
            setStringParameter(statement, index++, parada.getReferenciaUltimaPosicao());
            setStringParameter(statement, index++, parada.getMetadata());
            setInstantParameter(statement, index++, parada.getDataExtracao());
            if (index != 24) {
                throw new SQLException("Numero incorreto de parametros Raster paradas: " + (index - 1));
            }
            return statement.executeUpdate();
        }
    }
}
