import React, { useMemo } from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';
import '../estilos/Dashboard.css';

// Cores para as diferentes APIs
const CORES_APIS = {
  apiRest: '#3b82f6',
  apiGraphQL: '#10b981', 
  apiDataExport: '#f59e0b'
};

const GraficoPerformance = ({ dados }) => {
  // Processar dados usando useMemo para evitar recálculos desnecessários
  const dadosProcessados = useMemo(() => {
    if (!dados || !Array.isArray(dados)) {
      return [];
    }

    return dados.map(item => ({
      timestamp: new Date(item.timestamp).toLocaleTimeString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit'
      }),
      timestampCompleto: item.timestamp,
      apiRest: item.apiRest || 0,
      apiGraphQL: item.apiGraphQL || 0,
      apiDataExport: item.apiDataExport || 0
    }));
  }, [dados]);

  // Calcular estatísticas usando useMemo
  const estatisticas = useMemo(() => {
    const apis = ['apiRest', 'apiGraphQL', 'apiDataExport'];
    const stats = {};

    apis.forEach(api => {
      const valores = dadosProcessados.map(item => item[api]).filter(val => val > 0);
      if (valores.length > 0) {
        const soma = valores.reduce((acc, val) => acc + val, 0);
        const media = soma / valores.length;
        const min = Math.min(...valores);
        const max = Math.max(...valores);
        
        stats[api] = {
          media: Math.round(media),
          min,
          max,
          total: valores.length
        };
      } else {
        stats[api] = {
          media: 0,
          min: 0,
          max: 0,
          total: 0
        };
      }
    });

    return stats;
  }, [dadosProcessados]);

  // Verificação de dados após os hooks
  if (!dados || !Array.isArray(dados) || dados.length === 0) {
    return (
      <div className="grafico-performance-container">
        <div className="grafico-header">
          <h3 className="grafico-titulo">
            <i className="fas fa-chart-line"></i>
            Histórico de Performance
          </h3>
        </div>
        <div className="grafico-sem-dados">
          <i className="fas fa-chart-line fa-3x"></i>
          <p>Nenhum dado de performance disponível</p>
        </div>
      </div>
    );
  }

  // Tooltip customizado
  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      return (
        <div className="grafico-tooltip">
          <p className="tooltip-label">{`Horário: ${label}`}</p>
          {payload.map((entry, index) => (
            <p key={index} className="tooltip-value" style={{ color: entry.color }}>
              {`${formatarNomeApi(entry.dataKey)}: ${entry.value}ms`}
            </p>
          ))}
        </div>
      );
    }
    return null;
  };

  // Função para formatar os nomes das APIs na legenda
  const formatarNomeApi = (nome) => {
    const nomes = {
      'apiRest': 'API REST',
      'apiGraphQL': 'API GraphQL', 
      'apiDataExport': 'API Data Export'
    };
    return nomes[nome] || nome;
  };

  // Se não há dados, mostrar mensagem
  if (!dadosProcessados.length) {
    return (
      <div className="grafico-container">
        <div className="grafico-header">
          <h3 className="grafico-titulo">
            <i className="fas fa-chart-line"></i>
            Histórico de Performance
          </h3>
        </div>
        <div className="grafico-sem-dados">
          <i className="fas fa-chart-line fa-3x"></i>
          <p>Nenhum dado de performance disponível</p>
        </div>
      </div>
    );
  }

  return (
    <div className="grafico-container">
      <div className="grafico-header">
        <h3 className="grafico-titulo">
          <i className="fas fa-chart-line"></i>
          Histórico de Performance
        </h3>
        <p className="grafico-descricao">
          Evolução dos tempos de resposta das APIs ao longo do tempo
        </p>
      </div>
      
      <div className="grafico-content">
        <ResponsiveContainer width="100%" height={300}>
          <LineChart
            data={dadosProcessados}
            margin={{
              top: 5,
              right: 30,
              left: 20,
              bottom: 5,
            }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255, 255, 255, 0.1)" />
            <XAxis 
               dataKey="timestamp" 
               tick={{ fill: '#ffffff', fontSize: 12 }}
               axisLine={{ stroke: '#ffffff' }}
             />
            <YAxis 
              tick={{ fill: '#ffffff', fontSize: 12 }}
              axisLine={{ stroke: '#ffffff' }}
              label={{ value: 'Tempo (ms)', angle: -90, position: 'insideLeft', style: { textAnchor: 'middle', fill: '#ffffff' } }}
            />
            <Tooltip content={<CustomTooltip />} />
            <Legend 
              formatter={(value) => formatarNomeApi(value)}
              wrapperStyle={{ color: '#ffffff' }}
            />
            
            <Line
              type="monotone"
              dataKey="apiRest"
              stroke={CORES_APIS.apiRest}
              strokeWidth={2}
              dot={{ fill: CORES_APIS.apiRest, strokeWidth: 2, r: 4 }}
              activeDot={{ r: 6, stroke: CORES_APIS.apiRest, strokeWidth: 2 }}
              connectNulls={false}
            />
            
            <Line
              type="monotone"
              dataKey="apiGraphQL"
              stroke={CORES_APIS.apiGraphQL}
              strokeWidth={2}
              dot={{ fill: CORES_APIS.apiGraphQL, strokeWidth: 2, r: 4 }}
              activeDot={{ r: 6, stroke: CORES_APIS.apiGraphQL, strokeWidth: 2 }}
              connectNulls={false}
            />
            
            <Line
              type="monotone"
              dataKey="apiDataExport"
              stroke={CORES_APIS.apiDataExport}
              strokeWidth={2}
              dot={{ fill: CORES_APIS.apiDataExport, strokeWidth: 2, r: 4 }}
              activeDot={{ r: 6, stroke: CORES_APIS.apiDataExport, strokeWidth: 2 }}
              connectNulls={false}
            />
          </LineChart>
        </ResponsiveContainer>
      </div>
      
      <div className="grafico-estatisticas">
        <h4 className="estatisticas-titulo">
          <i className="fas fa-calculator"></i>
          Estatísticas de Performance
        </h4>
        
        <div className="estatisticas-grid">
          {Object.entries(estatisticas).map(([api, stats]) => (
            <div key={api} className="estatistica-card">
              <div className="estatistica-header">
                <span 
                  className="estatistica-cor" 
                  style={{ backgroundColor: CORES_APIS[api] }}
                ></span>
                <span className="estatistica-nome">{stats.nome}</span>
              </div>
              
              <div className="estatistica-valores">
                <div className="estatistica-item">
                  <span className="estatistica-label">Média:</span>
                  <span className="estatistica-valor">{stats.media}ms</span>
                </div>
                
                <div className="estatistica-item">
                  <span className="estatistica-label">Mín:</span>
                  <span className="estatistica-valor">{stats.min}ms</span>
                </div>
                
                <div className="estatistica-item">
                  <span className="estatistica-label">Máx:</span>
                  <span className="estatistica-valor">{stats.max}ms</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
      
      <div className="grafico-insights">
        <div className="insight-item">
          <i className="fas fa-info-circle"></i>
          <span>
            Monitore picos de latência que podem indicar problemas de performance ou sobrecarga nas APIs
          </span>
        </div>
      </div>
    </div>
  );
};

export default GraficoPerformance;