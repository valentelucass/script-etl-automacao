import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts';

// Definindo as cores para o gráfico
const CORES = {
  execucoes: '#3b82f6',
  sucessos: '#10b981',
  falhas: '#ef4444',
  total: '#8b5cf6'
};

const GraficoExecucoes24h = ({ dados }) => {
  // Função para processar os dados
  const processarDados = () => {
    if (!dados || !Array.isArray(dados)) {
      return [];
    }
    
    return dados.map(item => ({
      hora: `${item.hora}h`,
      execucoes: (item.sucessos || 0) + (item.falhas || 0),
      sucessos: item.sucessos || 0,
      falhas: item.falhas || 0
    }));
  };

  const dadosProcessados = processarDados();

  // Se não há dados, mostrar mensagem
  if (!dadosProcessados.length) {
    return (
      <div className="grafico-container">
        <div className="grafico-header">
          <h3 className="grafico-titulo">
            <i className="fas fa-chart-bar"></i>
            Execuções vs. Falhas (24h)
          </h3>
        </div>
        <div className="grafico-sem-dados">
          <i className="fas fa-chart-bar fa-3x"></i>
          <p>Nenhum dado de execução disponível</p>
        </div>
      </div>
    );
  }

  // Função customizada para renderizar o tooltip
  const renderTooltip = (props) => {
    if (props.active && props.payload && props.payload.length) {
      const data = props.payload[0].payload;
      return (
        <div className="grafico-tooltip">
          <p className="tooltip-label">{`Horário: ${props.label}`}</p>
          <p className="tooltip-value">
            <span className="tooltip-color" style={{ backgroundColor: CORES.execucoes }}></span>
            Total de Execuções: {data.execucoes}
          </p>
          <p className="tooltip-value">
            <span className="tooltip-color" style={{ backgroundColor: CORES.sucessos }}></span>
            Sucessos: {data.sucessos}
          </p>
          <p className="tooltip-value">
            <span className="tooltip-color" style={{ backgroundColor: CORES.falhas }}></span>
            Falhas: {data.falhas}
          </p>
          <p className="tooltip-percentage">
            Taxa de Sucesso: {data.execucoes > 0 ? ((data.sucessos / data.execucoes) * 100).toFixed(1) : 0}%
          </p>
        </div>
      );
    }
    return null;
  };

  // Se não há dados, mostrar mensagem
  if (!dadosProcessados.length) {
    return (
      <div className="grafico-container">
        <div className="grafico-header">
          <h3 className="grafico-titulo">
            <i className="fas fa-chart-bar"></i>
            Execuções vs. Falhas (24h)
          </h3>
        </div>
        <div className="grafico-sem-dados">
          <i className="fas fa-chart-bar fa-3x"></i>
          <p>Nenhum dado de execução disponível</p>
        </div>
      </div>
    );
  }

  // Calcular estatísticas gerais
  const calcularEstatisticas = () => {
    const totalExecucoes = dadosProcessados.reduce((acc, item) => acc + item.execucoes, 0);
    const totalSucessos = dadosProcessados.reduce((acc, item) => acc + item.sucessos, 0);
    const totalFalhas = dadosProcessados.reduce((acc, item) => acc + item.falhas, 0);
    const taxaSucessoGeral = totalExecucoes > 0 ? ((totalSucessos / totalExecucoes) * 100).toFixed(1) : 0;
    
    // Encontrar horário com mais falhas
    const horarioMaisFalhas = dadosProcessados.reduce((max, item) => 
      item.falhas > max.falhas ? item : max, dadosProcessados[0]
    );

    // Encontrar horário com mais execuções
    const horarioMaisExecucoes = dadosProcessados.reduce((max, item) => 
      item.execucoes > max.execucoes ? item : max, dadosProcessados[0]
    );

    return {
      totalExecucoes,
      totalSucessos,
      totalFalhas,
      taxaSucessoGeral,
      horarioMaisFalhas,
      horarioMaisExecucoes
    };
  };

  const estatisticas = calcularEstatisticas();

  return (
    <div className="grafico-container">
      <div className="grafico-header">
        <h3 className="grafico-titulo">
          <i className="fas fa-chart-bar"></i>
          Execuções vs. Falhas (24h)
        </h3>
        <p className="grafico-descricao">
          Distribuição de execuções e falhas por horário nas últimas 24 horas
        </p>
      </div>
      
      <div className="grafico-content">
        <ResponsiveContainer width="100%" height={300}>
          <BarChart
            data={dadosProcessados}
            margin={{
              top: 20,
              right: 30,
              left: 20,
              bottom: 5,
            }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255, 255, 255, 0.1)" />
            <XAxis 
              dataKey="hora" 
              tick={{ fill: '#ffffff', fontSize: 12 }}
              axisLine={{ stroke: '#ffffff' }}
            />
            <YAxis 
              tick={{ fill: '#ffffff', fontSize: 12 }}
              axisLine={{ stroke: '#ffffff' }}
              label={{ value: 'Quantidade', angle: -90, position: 'insideLeft', style: { textAnchor: 'middle', fill: '#ffffff' } }}
            />
            <Tooltip content={renderTooltip} />
            <Legend 
              wrapperStyle={{ color: '#ffffff' }}
              formatter={(value) => {
                const nomes = {
                  'execucoes': 'Total de Execuções',
                  'sucessos': 'Sucessos',
                  'falhas': 'Falhas'
                };
                return nomes[value] || value;
              }}
            />
            
            <Bar 
              dataKey="execucoes" 
              fill={CORES.execucoes}
              name="execucoes"
              radius={[2, 2, 0, 0]}
            />
            <Bar 
              dataKey="sucessos" 
              fill={CORES.sucessos}
              name="sucessos"
              radius={[2, 2, 0, 0]}
            />
            <Bar 
              dataKey="falhas" 
              fill={CORES.falhas}
              name="falhas"
              radius={[2, 2, 0, 0]}
            />
          </BarChart>
        </ResponsiveContainer>
      </div>
      
      <div className="grafico-resumo-24h">
        <div className="resumo-cards-24h">
          <div className="resumo-card-24h">
            <div className="resumo-icon">
              <i className="fas fa-play-circle" style={{ color: CORES.execucoes }}></i>
            </div>
            <div className="resumo-info">
              <div className="resumo-valor">{estatisticas.totalExecucoes}</div>
              <div className="resumo-label">Total de Execuções</div>
            </div>
          </div>
          
          <div className="resumo-card-24h">
            <div className="resumo-icon">
              <i className="fas fa-check-circle" style={{ color: CORES.sucessos }}></i>
            </div>
            <div className="resumo-info">
              <div className="resumo-valor">{estatisticas.totalSucessos}</div>
              <div className="resumo-label">Sucessos</div>
            </div>
          </div>
          
          <div className="resumo-card-24h">
            <div className="resumo-icon">
              <i className="fas fa-times-circle" style={{ color: CORES.falhas }}></i>
            </div>
            <div className="resumo-info">
              <div className="resumo-valor">{estatisticas.totalFalhas}</div>
              <div className="resumo-label">Falhas</div>
            </div>
          </div>
          
          <div className="resumo-card-24h">
            <div className="resumo-icon">
              <i className="fas fa-percentage" style={{ color: CORES.total }}></i>
            </div>
            <div className="resumo-info">
              <div className="resumo-valor">{estatisticas.taxaSucessoGeral}%</div>
              <div className="resumo-label">Taxa de Sucesso</div>
            </div>
          </div>
        </div>
      </div>
      
      <div className="grafico-insights-24h">
        <div className="insights-grid">
          <div className="insight-item">
            <i className="fas fa-clock"></i>
            <div className="insight-content">
              <span className="insight-label">Pico de Atividade:</span>
              <span className="insight-valor">{estatisticas.horarioMaisExecucoes.hora} ({estatisticas.horarioMaisExecucoes.execucoes} execuções)</span>
            </div>
          </div>
          
          <div className="insight-item">
            <i className="fas fa-exclamation-triangle"></i>
            <div className="insight-content">
              <span className="insight-label">Horário com Mais Falhas:</span>
              <span className="insight-valor">{estatisticas.horarioMaisFalhas.hora} ({estatisticas.horarioMaisFalhas.falhas} falhas)</span>
            </div>
          </div>
          
          <div className="insight-item">
            <i className="fas fa-info-circle"></i>
            <div className="insight-content">
              <span className="insight-label">Dica:</span>
              <span className="insight-valor">
                {estatisticas.totalFalhas > 0 ? 
                  'Monitore os horários com mais falhas para identificar padrões' : 
                  'Excelente! Nenhuma falha registrada no período'
                }
              </span>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default GraficoExecucoes24h;