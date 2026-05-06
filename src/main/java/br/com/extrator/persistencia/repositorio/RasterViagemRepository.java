package br.com.extrator.persistencia.repositorio;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import br.com.extrator.persistencia.entidade.RasterViagemEntity;
import br.com.extrator.suporte.validacao.ConstantesEntidades;

public class RasterViagemRepository extends AbstractRepository<RasterViagemEntity> {
    private static final String NOME_TABELA = ConstantesEntidades.RASTER_VIAGENS;

    @Override
    protected String getNomeTabela() {
        return NOME_TABELA;
    }

    @Override
    protected int executarMerge(final Connection conexao, final RasterViagemEntity viagem) throws SQLException {
        if (viagem.getCodSolicitacao() == null) {
            throw new SQLException("Nao e possivel executar MERGE Raster sem cod_solicitacao.");
        }

        final String sql = """
            MERGE dbo.raster_viagens WITH (HOLDLOCK) AS target
            USING (VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?))
                AS source (
                    cod_solicitacao, sequencial, cod_filial, status_viagem, placa_veiculo,
                    placa_carreta1, placa_carreta2, placa_carreta3, cpf_motorista1, cpf_motorista2,
                    cnpj_cliente_orig, cnpj_cliente_dest, cod_ibge_cidade_orig, cod_ibge_cidade_dest,
                    data_hora_prev_ini, data_hora_prev_fim, data_hora_real_ini, data_hora_real_fim,
                    data_hora_identificou_fim_viagem, tempo_total_viagem_min, dentro_prazo_raster,
                    percentual_atraso_raster, rodou_fora_horario, velocidade_media, eventos_velocidade,
                    desvios_de_rota, cod_rota, rota_descricao, link_timeline, metadata, data_extracao
                )
            ON target.cod_solicitacao = source.cod_solicitacao
            WHEN MATCHED THEN
                UPDATE SET
                    sequencial = source.sequencial,
                    cod_filial = source.cod_filial,
                    status_viagem = source.status_viagem,
                    placa_veiculo = source.placa_veiculo,
                    placa_carreta1 = source.placa_carreta1,
                    placa_carreta2 = source.placa_carreta2,
                    placa_carreta3 = source.placa_carreta3,
                    cpf_motorista1 = source.cpf_motorista1,
                    cpf_motorista2 = source.cpf_motorista2,
                    cnpj_cliente_orig = source.cnpj_cliente_orig,
                    cnpj_cliente_dest = source.cnpj_cliente_dest,
                    cod_ibge_cidade_orig = source.cod_ibge_cidade_orig,
                    cod_ibge_cidade_dest = source.cod_ibge_cidade_dest,
                    data_hora_prev_ini = source.data_hora_prev_ini,
                    data_hora_prev_fim = source.data_hora_prev_fim,
                    data_hora_real_ini = source.data_hora_real_ini,
                    data_hora_real_fim = source.data_hora_real_fim,
                    data_hora_identificou_fim_viagem = source.data_hora_identificou_fim_viagem,
                    tempo_total_viagem_min = source.tempo_total_viagem_min,
                    dentro_prazo_raster = source.dentro_prazo_raster,
                    percentual_atraso_raster = source.percentual_atraso_raster,
                    rodou_fora_horario = source.rodou_fora_horario,
                    velocidade_media = source.velocidade_media,
                    eventos_velocidade = source.eventos_velocidade,
                    desvios_de_rota = source.desvios_de_rota,
                    cod_rota = source.cod_rota,
                    rota_descricao = source.rota_descricao,
                    link_timeline = source.link_timeline,
                    metadata = source.metadata,
                    data_extracao = source.data_extracao
            WHEN NOT MATCHED THEN
                INSERT (
                    cod_solicitacao, sequencial, cod_filial, status_viagem, placa_veiculo,
                    placa_carreta1, placa_carreta2, placa_carreta3, cpf_motorista1, cpf_motorista2,
                    cnpj_cliente_orig, cnpj_cliente_dest, cod_ibge_cidade_orig, cod_ibge_cidade_dest,
                    data_hora_prev_ini, data_hora_prev_fim, data_hora_real_ini, data_hora_real_fim,
                    data_hora_identificou_fim_viagem, tempo_total_viagem_min, dentro_prazo_raster,
                    percentual_atraso_raster, rodou_fora_horario, velocidade_media, eventos_velocidade,
                    desvios_de_rota, cod_rota, rota_descricao, link_timeline, metadata, data_extracao
                )
                VALUES (
                    source.cod_solicitacao, source.sequencial, source.cod_filial, source.status_viagem, source.placa_veiculo,
                    source.placa_carreta1, source.placa_carreta2, source.placa_carreta3, source.cpf_motorista1, source.cpf_motorista2,
                    source.cnpj_cliente_orig, source.cnpj_cliente_dest, source.cod_ibge_cidade_orig, source.cod_ibge_cidade_dest,
                    source.data_hora_prev_ini, source.data_hora_prev_fim, source.data_hora_real_ini, source.data_hora_real_fim,
                    source.data_hora_identificou_fim_viagem, source.tempo_total_viagem_min, source.dentro_prazo_raster,
                    source.percentual_atraso_raster, source.rodou_fora_horario, source.velocidade_media, source.eventos_velocidade,
                    source.desvios_de_rota, source.cod_rota, source.rota_descricao, source.link_timeline, source.metadata, source.data_extracao
                );
            """;

        try (PreparedStatement statement = conexao.prepareStatement(sql)) {
            int index = 1;
            setLongParameter(statement, index++, viagem.getCodSolicitacao());
            setLongParameter(statement, index++, viagem.getSequencial());
            setIntegerParameter(statement, index++, viagem.getCodFilial());
            setStringParameter(statement, index++, viagem.getStatusViagem());
            setStringParameter(statement, index++, viagem.getPlacaVeiculo());
            setStringParameter(statement, index++, viagem.getPlacaCarreta1());
            setStringParameter(statement, index++, viagem.getPlacaCarreta2());
            setStringParameter(statement, index++, viagem.getPlacaCarreta3());
            setStringParameter(statement, index++, viagem.getCpfMotorista1());
            setStringParameter(statement, index++, viagem.getCpfMotorista2());
            setStringParameter(statement, index++, viagem.getCnpjClienteOrig());
            setStringParameter(statement, index++, viagem.getCnpjClienteDest());
            setLongParameter(statement, index++, viagem.getCodIbgeCidadeOrig());
            setLongParameter(statement, index++, viagem.getCodIbgeCidadeDest());
            setOffsetDateTimeParameter(statement, index++, viagem.getDataHoraPrevIni());
            setOffsetDateTimeParameter(statement, index++, viagem.getDataHoraPrevFim());
            setOffsetDateTimeParameter(statement, index++, viagem.getDataHoraRealIni());
            setOffsetDateTimeParameter(statement, index++, viagem.getDataHoraRealFim());
            setOffsetDateTimeParameter(statement, index++, viagem.getDataHoraIdentificouFimViagem());
            setIntegerParameter(statement, index++, viagem.getTempoTotalViagemMin());
            setStringParameter(statement, index++, viagem.getDentroPrazoRaster());
            setBigDecimalParameter(statement, index++, viagem.getPercentualAtrasoRaster());
            setStringParameter(statement, index++, viagem.getRodouForaHorario());
            setBigDecimalParameter(statement, index++, viagem.getVelocidadeMedia());
            setIntegerParameter(statement, index++, viagem.getEventosVelocidade());
            setIntegerParameter(statement, index++, viagem.getDesviosDeRota());
            setLongParameter(statement, index++, viagem.getCodRota());
            setStringParameter(statement, index++, viagem.getRotaDescricao());
            setStringParameter(statement, index++, viagem.getLinkTimeline());
            setStringParameter(statement, index++, viagem.getMetadata());
            setInstantParameter(statement, index++, viagem.getDataExtracao());
            if (index != 32) {
                throw new SQLException("Numero incorreto de parametros Raster viagens: " + (index - 1));
            }
            return statement.executeUpdate();
        }
    }
}
