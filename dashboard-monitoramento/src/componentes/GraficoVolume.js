import React from 'react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from 'recharts';

const GraficoVolume = ({ dados }) => {
  // Paleta de cores vibrantes para as barras
  const CORES = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7', 
    '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9'
  ];

  // Função para processar e transformar os dados
  const processarDados = () => {
    if (!dados) {
      console.log('GraficoVolume: Nenhum dado fornecido');
      return [];
    }

    console.log('GraficoVolume: Dados recebidos:', dados);

    let dadosArray = [];

    // Se for array, usa diretamente
    if (Array.isArray(dados)) {
      dadosArray = dados;
    } 
    // Se for objeto, converte para array
    else if (typeof dados === 'object') {
      dadosArray = Object.entries(dados).map(([nome, valor]) => ({
        nome,
        valor: Number(valor) || 0
      }));
    }

    // Processa e adiciona cores
    const resultado = dadosArray.map((item, index) => ({
      nome: item.nome || item.name || `API ${index + 1}`,
      valor: Number(item.valor || item.value || 0),
      cor: CORES[index % CORES.length]
    }));

    // Ordena por valor (maior para menor)
    resultado.sort((a, b) => b.valor - a.valor);

    console.log('GraficoVolume: Dados processados:', resultado);
    return resultado;
  };

  const dadosProcessados = processarDados();

  // Tooltip customizado
  const CustomTooltip = ({ active, payload, label }) => {
    if (active && payload && payload.length) {
      const data = payload[0];
      return (
        <div style={{
          backgroundColor: 'rgba(0, 0, 0, 0.9)',
          padding: '12px',
          borderRadius: '8px',
          color: 'white',
          border: `2px solid ${data.payload.cor}`,
          boxShadow: '0 4px 12px rgba(0,0,0,0.3)'
        }}>
          <p style={{ margin: 0, fontWeight: 'bold', fontSize: '14px' }}>
            {label}
          </p>
          <p style={{ margin: '4px 0 0 0', color: data.payload.cor, fontSize: '13px' }}>
            📊 Registros: {data.value.toLocaleString('pt-BR')}
          </p>
        </div>
      );
    }
    return null;
  };

  // Função para formatar labels do eixo Y
  const formatarValor = (valor) => {
    if (valor >= 1000000) {
      return `${(valor / 1000000).toFixed(1)}M`;
    } else if (valor >= 1000) {
      return `${(valor / 1000).toFixed(1)}K`;
    }
    return valor.toString();
  };

  // Se não há dados válidos
  if (!dadosProcessados || dadosProcessados.length === 0) {
    return (
      <div className="grafico-container">
        <h3 className="grafico-titulo">
          <i className="fas fa-chart-bar"></i>
          Volume de Registros por API
        </h3>
        <div className="grafico-sem-dados" style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          height: '300px',
          color: '#7f8c8d',
          backgroundColor: 'rgba(255,255,255,0.05)',
          borderRadius: '10px',
          border: '2px dashed #34495e'
        }}>
          <i className="fas fa-chart-bar fa-4x" style={{ marginBottom: '20px', opacity: 0.5 }}></i>
          <h4 style={{ margin: '0 0 10px 0', color: '#ecf0f1' }}>Nenhum dado disponível</h4>
          <p style={{ margin: 0, fontSize: '14px' }}>Aguardando dados da API...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="grafico-container">
      <h3 className="grafico-titulo">
        <i className="fas fa-chart-bar"></i>
        Volume de Registros por API
      </h3>
      
      <div className="grafico-content" style={{ padding: '20px 10px' }}>
        {/* Estatísticas rápidas */}
        <div style={{
          display: 'flex',
          justifyContent: 'space-around',
          marginBottom: '20px',
          padding: '15px',
          backgroundColor: 'rgba(255,255,255,0.1)',
          borderRadius: '8px',
          border: '1px solid rgba(255,255,255,0.2)'
        }}>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#3498db' }}>
              {dadosProcessados.length}
            </div>
            <div style={{ fontSize: '12px', color: '#bdc3c7' }}>APIs</div>
          </div>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#2ecc71' }}>
              {dadosProcessados.reduce((acc, item) => acc + item.valor, 0).toLocaleString('pt-BR')}
            </div>
            <div style={{ fontSize: '12px', color: '#bdc3c7' }}>Total</div>
          </div>
          <div style={{ textAlign: 'center' }}>
            <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#e74c3c' }}>
              {Math.max(...dadosProcessados.map(item => item.valor)).toLocaleString('pt-BR')}
            </div>
            <div style={{ fontSize: '12px', color: '#bdc3c7' }}>Máximo</div>
          </div>
        </div>

        {/* Gráfico de barras */}
        <ResponsiveContainer width="100%" height={350}>
          <BarChart
            data={dadosProcessados}
            margin={{ top: 20, right: 30, left: 20, bottom: 60 }}
          >
            <CartesianGrid strokeDasharray="3 3" stroke="rgba(255,255,255,0.1)" />
            <XAxis 
              dataKey="nome" 
              tick={{ fill: '#ecf0f1', fontSize: 12 }}
              angle={-45}
              textAnchor="end"
              height={80}
              interval={0}
            />
            <YAxis 
              tick={{ fill: '#ecf0f1', fontSize: 12 }}
              tickFormatter={formatarValor}
            />
            <Tooltip content={<CustomTooltip />} />
            <Bar 
              dataKey="valor" 
              radius={[4, 4, 0, 0]}
              stroke="rgba(255,255,255,0.3)"
              strokeWidth={1}
            >
              {dadosProcessados.map((entry, index) => (
                <Cell key={`cell-${index}`} fill={entry.cor} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>

        {/* Legenda personalizada */}
        <div style={{
          display: 'flex',
          flexWrap: 'wrap',
          justifyContent: 'center',
          gap: '15px',
          marginTop: '20px',
          padding: '15px',
          backgroundColor: 'rgba(0,0,0,0.2)',
          borderRadius: '8px'
        }}>
          {dadosProcessados.slice(0, 5).map((item, index) => (
            <div key={index} style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '5px 10px',
              backgroundColor: 'rgba(255,255,255,0.1)',
              borderRadius: '15px',
              fontSize: '12px'
            }}>
              <div style={{
                width: '12px',
                height: '12px',
                backgroundColor: item.cor,
                borderRadius: '50%'
              }}></div>
              <span style={{ color: '#ecf0f1', fontWeight: 'bold' }}>
                {item.nome}: {item.valor.toLocaleString('pt-BR')}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
};

export default GraficoVolume;