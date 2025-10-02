import React, { useState, useEffect } from 'react';
import apiService from '../servicos/ApiService';
import '../estilos/Dashboard.css';
import GraficoVolume from './GraficoVolume';
import GraficoPerformance from './GraficoPerformance';
import GraficoExecucoes24h from './GraficoExecucoes24h';

const Dashboard = () => {
  const [dados, setDados] = useState(null);
  const [proximaAtualizacao, setProximaAtualizacao] = useState(10);
  const [timestampFrontend, setTimestampFrontend] = useState(new Date());
  const [statusConexao, setStatusConexao] = useState({ isOnline: true });

  useEffect(() => {
    // Busca inicial
    buscarDados();

    // Configura intervalo de atualização
    const intervalo = setInterval(() => {
      buscarDados();
      setProximaAtualizacao(10);
      setTimestampFrontend(new Date());
    }, 10000);

    const timer = setInterval(() => {
      setProximaAtualizacao(prev => prev > 0 ? prev - 1 : 10);
    }, 1000);

    return () => {
      clearInterval(intervalo);
      clearInterval(timer);
    };
  }, []);

  const buscarDados = async () => {
    try {
      const dadosJson = await apiService.buscarDados();
      setDados(dadosJson);
      setStatusConexao(apiService.getStatusConexao());
    } catch (erro) {
      console.error('Erro ao buscar dados:', erro);
      setStatusConexao(apiService.getStatusConexao());
    }
  };

  // Função para determinar o status geral
  const determinarStatusGeral = (statusAPIs) => {
    if (!statusAPIs) return { status: 'CARREGANDO', cor: '#6c757d' };
    
    const statuses = Object.values(statusAPIs);
    const sucessos = statuses.filter(api => api.status === 'OPERACIONAL').length;
    const total = statuses.length;
    
    if (sucessos === total) {
      return { status: 'OPERACIONAL', cor: '#28a745', icone: 'fa-circle-check' };
    } else if (sucessos > total / 2) {
      return { status: 'OPERACIONAL COM AVISOS', cor: '#ffc107', icone: 'fa-triangle-exclamation' };
    } else {
      return { status: 'FALHA CRÍTICA', cor: '#dc3545', icone: 'fa-circle-xmark' };
    }
  };

  // Função para obter ícone e cor baseado no status da API
  const obterIconeStatus = (status) => {
    switch (status) {
      case 'OPERACIONAL':
        return { icone: 'fa-circle-check', cor: '#28a745' };
      case 'AVISO_SEM_DADOS':
        return { icone: 'fa-triangle-exclamation', cor: '#ffc107' };
      case 'NAO_AUTORIZADO':
        return { icone: 'fa-lock', cor: '#dc3545' };
      case 'FALHA_500':
        return { icone: 'fa-server', cor: '#dc3545' };
      case 'FALHA_404':
        return { icone: 'fa-question-circle', cor: '#dc3545' };
      default:
        return { icone: 'fa-circle-xmark', cor: '#dc3545' };
    }
  };

  // Função para calcular métricas gerais
  const calcularMetricasGerais = (statusAPIs, metricas) => {
    if (!statusAPIs || !metricas) return { duracaoTotal: 0, totalRegistros: 0, taxaSucesso: '0/0' };
    
    const apis = Object.values(statusAPIs);
    const sucessos = apis.filter(api => api.status === 'OPERACIONAL').length;
    const total = apis.length;
    
    return {
      duracaoTotal: metricas.duracaoTotalSegundos || 0,
      totalRegistros: metricas.totalRegistrosProcessados || 0,
      taxaSucesso: `${sucessos}/${total}`
    };
  };

  // Função para obter a última execução do backend
  const obterUltimaExecucaoBackend = () => {
    if (!dados || !dados.statusAPIs) return null;
    
    // Pega a execução mais recente entre todas as APIs
    const execucoes = Object.values(dados.statusAPIs)
      .map(api => api.ultimaExecucao)
      .filter(exec => exec)
      .sort((a, b) => new Date(b) - new Date(a));
    
    return execucoes.length > 0 ? execucoes[0] : null;
  };

  if (!dados) {
    return (
      <div className="dashboard-container">
        <div className="loading-container">
          <i className="fas fa-spinner fa-spin fa-3x"></i>
          <h2>Carregando Dashboard...</h2>
          <p>Conectando ao servidor de monitoramento...</p>
          {!statusConexao.isOnline && (
            <div className="connection-warning">
              <i className="fas fa-exclamation-triangle"></i>
              <p>Tentando reconectar ao backend...</p>
            </div>
          )}
        </div>
      </div>
    );
  }

  const statusGeral = determinarStatusGeral(dados.statusAPIs);
  const metricasGerais = calcularMetricasGerais(dados.statusAPIs, dados.metricas);
  const ultimaExecucaoBackend = obterUltimaExecucaoBackend();

  // Determina o status da conexão para exibição
  const getStatusDisplay = () => {
    if (dados._isMocked) {
      return { 
        texto: 'MODO DEMONSTRAÇÃO', 
        cor: '#ff9800', 
        icone: 'fa-exclamation-triangle',
        descricao: 'Backend indisponível - Exibindo dados simulados'
      };
    }
    if (dados._isFromCache) {
      return { 
        texto: 'DADOS EM CACHE', 
        cor: '#2196f3', 
        icone: 'fa-database',
        descricao: `Dados do cache (${dados._cacheAge}s atrás)`
      };
    }
    return { 
      texto: 'CONECTADO', 
      cor: '#4caf50', 
      icone: 'fa-wifi',
      descricao: 'Conectado ao backend'
    };
  };

  const statusDisplay = getStatusDisplay();

  return (
    <div className="dashboard-container">
      {/* Componente 1: Cabeçalho Principal com Timestamps Claros */}
      <div className="cabecalho-principal">
        <div className="status-geral-info">
          <i className={`fas ${statusGeral.icone} status-icon`} style={{ color: statusGeral.cor }}></i>
          <h1 className="status-title" style={{ color: statusGeral.cor }}>
            {statusGeral.status}
          </h1>
        </div>
        
        {/* Indicador de Status da Conexão */}
        <div className="connection-status" title={statusDisplay.descricao}>
          <i className={`fas ${statusDisplay.icone}`} style={{ color: statusDisplay.cor }}></i>
          <span style={{ color: statusDisplay.cor, fontSize: '0.9em', fontWeight: 'bold' }}>
            {statusDisplay.texto}
          </span>
        </div>
        
        <div className="timestamps-container">
          <div className="timestamp-principal">
            <i className="fas fa-database"></i>
            <span className="timestamp-label">Última Extração (ETL):</span>
            <span className="timestamp-valor">
              {ultimaExecucaoBackend ? 
                new Date(ultimaExecucaoBackend).toLocaleString('pt-BR', {
                  day: '2-digit',
                  month: '2-digit', 
                  year: 'numeric',
                  hour: '2-digit',
                  minute: '2-digit',
                  second: '2-digit'
                }) : 
                'Não disponível'
              }
            </span>
          </div>
          
          <div className="timestamp-secundario">
            <i className="fas fa-sync-alt"></i>
            <span className="timestamp-label">Status do Dashboard:</span>
            <span className="timestamp-valor">
              (Dados atualizados em: {timestampFrontend.toLocaleTimeString('pt-BR')}, 
              próxima atualização em: <span className="timer">{proximaAtualizacao}s</span>)
            </span>
          </div>
        </div>
      </div>

      {/* Componente 2: Painel de Consumo de API */}
      {dados.consumoApis && (
        <div className="consumo-apis-section">
          <h2 className="section-title">
            <i className="fas fa-chart-bar"></i>
            Consumo de APIs
          </h2>
          
          <div className="consumo-cards-container">
            <div className="consumo-card">
              <div className="consumo-header">
                <i className="fas fa-server consumo-icon"></i>
                <span className="consumo-nome">API REST</span>
              </div>
              <div className="consumo-valor">{dados.consumoApis.apiRest}</div>
              <div className="consumo-unidade">requisições</div>
            </div>
            
            <div className="consumo-card">
              <div className="consumo-header">
                <i className="fas fa-project-diagram consumo-icon"></i>
                <span className="consumo-nome">API GraphQL</span>
              </div>
              <div className="consumo-valor">{dados.consumoApis.apiGraphQL}</div>
              <div className="consumo-unidade">requisições</div>
            </div>
            
            <div className="consumo-card">
              <div className="consumo-header">
                <i className="fas fa-download consumo-icon"></i>
                <span className="consumo-nome">API Data Export</span>
              </div>
              <div className="consumo-valor">{dados.consumoApis.apiDataExport}</div>
              <div className="consumo-unidade">requisições</div>
            </div>
            
            <div className="consumo-card total">
              <div className="consumo-header">
                <i className="fas fa-calculator consumo-icon"></i>
                <span className="consumo-nome">Total Consumido</span>
              </div>
              <div className="consumo-valor">{dados.consumoApis.totalRequisicoes}</div>
              <div className="consumo-unidade">requisições</div>
            </div>
          </div>
          
          <div className="consumo-detalhes">
            <p>
              <i className="fas fa-info-circle"></i>
              {dados.consumoApis.detalhes?.descricao || 'Número de requisições HTTP realizadas por cada cliente de API na última execução'}
            </p>
          </div>
        </div>
      )}

      {/* Componente 3: Painel de Análise Gráfica */}
      <div className="analise-grafica-section">
        <h2 className="section-title">
          <i className="fas fa-chart-line"></i>
          Análise Gráfica
        </h2>
        
        <div className="graficos-container">
          {/* Gráfico de Volume de Dados por API */}
          <div className="grafico-wrapper">
            <GraficoVolume dados={dados.dadosGraficos?.registrosPorAPI} />
          </div>
          
          {/* Gráfico de Performance */}
          <div className="grafico-wrapper">
            <GraficoPerformance dados={dados.dadosGraficos?.temposResposta} />
          </div>
          
          {/* Gráfico de Execuções vs Falhas 24h */}
          <div className="grafico-wrapper">
            <GraficoExecucoes24h dados={dados.dadosGraficos?.execucoesPorHora} />
          </div>
        </div>
      </div>

      {/* Componente 4: Painel de Status Detalhado por API */}
      <div className="apis-section">
        <h2 className="section-title">
          <i className="fas fa-network-wired"></i>
          Status Detalhado das APIs
        </h2>
        <div className="cards-container">
          {Object.entries(dados.statusAPIs).map(([nomeApi, infoApi]) => {
            const iconInfo = obterIconeStatus(infoApi.status);
            return (
              <div key={nomeApi} className={`status-card ${infoApi.status}`}>
                <div className="card-header">
                  <i className={`fas ${iconInfo.icone} card-icon`} style={{ color: iconInfo.cor }}></i>
                  <div className="api-name">{nomeApi}</div>
                </div>
                
                <div className="card-body">
                  <div className="api-status">{infoApi.status.replace(/_/g, ' ')}</div>
                  
                  <div className="metricas">
                    <div className="metrica">
                      <i className="fas fa-stopwatch"></i>
                      <span>Tempo: {infoApi.tempoResposta}ms</span>
                    </div>
                    
                    <div className="metrica">
                      <i className="fas fa-database"></i>
                      <span>Registros: {infoApi.totalRegistros || 0}</span>
                    </div>
                    
                    <div className="metrica">
                      <i className="fas fa-percentage"></i>
                      <span>Sucesso: {infoApi.taxaSucesso}%</span>
                    </div>
                  </div>
                  
                  <div className="ultima-execucao">
                    <i className="fas fa-clock"></i>
                    <span>Última execução: {new Date(infoApi.ultimaExecucao).toLocaleString('pt-BR')}</span>
                  </div>
                  
                  {infoApi.tabelas && (
                    <div className="tabelas-info">
                      <span className="tabelas-count">{infoApi.tabelas.length} tabelas processadas</span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Componente 5: Métricas Gerais */}
      <div className="metricas-gerais-section">
        <h2 className="section-title">
          <i className="fas fa-tachometer-alt"></i>
          Métricas da Última Execução
        </h2>
        
        <div className="metricas-gerais-container">
          <div className="metrica-geral">
            <i className="fas fa-clock metrica-icon"></i>
            <div className="metrica-content">
              <div className="metrica-valor">{metricasGerais.duracaoTotal}s</div>
              <div className="metrica-label">Duração Total</div>
            </div>
          </div>
          
          <div className="metrica-geral">
            <i className="fas fa-database metrica-icon"></i>
            <div className="metrica-content">
              <div className="metrica-valor">{metricasGerais.totalRegistros.toLocaleString('pt-BR')}</div>
              <div className="metrica-label">Registros Salvos</div>
            </div>
          </div>
          
          <div className="metrica-geral">
            <i className="fas fa-check-circle metrica-icon"></i>
            <div className="metrica-content">
              <div className="metrica-valor">{metricasGerais.taxaSucesso}</div>
              <div className="metrica-label">Taxa de Sucesso</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default Dashboard;