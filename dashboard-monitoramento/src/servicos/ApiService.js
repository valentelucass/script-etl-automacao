/**
 * Serviço para gerenciar conexões com o backend de forma resiliente
 * Inclui retry automático, fallback de dados e tratamento de erros
 */

class ApiService {
  constructor() {
    this.baseUrl = 'http://localhost:7070/api';
    this.maxRetries = 3;
    this.retryDelay = 1000; // 1 segundo
    this.timeout = 10000; // 10 segundos
    this.isOnline = true;
    this.lastSuccessfulFetch = null;
    this.cachedData = null;
  }

  /**
   * Realiza uma requisição com retry automático
   */
  async fetchWithRetry(url, options = {}, retryCount = 0) {
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeout);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          'Accept': 'application/json',
          ...options.headers
        }
      });

      clearTimeout(timeoutId);

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      
      // Sucesso - atualiza status e cache
      this.isOnline = true;
      this.lastSuccessfulFetch = new Date();
      this.cachedData = data;
      
      return data;

    } catch (error) {
      clearTimeout(timeoutId);
      
      // Se ainda há tentativas restantes, tenta novamente
      if (retryCount < this.maxRetries) {
        console.warn(`Tentativa ${retryCount + 1}/${this.maxRetries + 1} falhou. Tentando novamente em ${this.retryDelay}ms...`, error.message);
        
        await this.delay(this.retryDelay * (retryCount + 1)); // Backoff exponencial
        return this.fetchWithRetry(url, options, retryCount + 1);
      }

      // Todas as tentativas falharam
      this.isOnline = false;
      console.error(`Todas as ${this.maxRetries + 1} tentativas falharam para ${url}:`, error.message);
      
      throw error;
    }
  }

  /**
   * Busca dados do status com fallback inteligente
   */
  async buscarDados() {
    try {
      const dados = await this.fetchWithRetry(`${this.baseUrl}/status`);
      return dados;
    } catch (error) {
      console.warn('Backend indisponível, usando estratégia de fallback:', error.message);
      
      // Estratégia 1: Usar dados em cache se disponíveis e recentes (< 5 minutos)
      if (this.cachedData && this.lastSuccessfulFetch) {
        const tempoDecorrido = Date.now() - this.lastSuccessfulFetch.getTime();
        if (tempoDecorrido < 5 * 60 * 1000) { // 5 minutos
          console.info('Usando dados em cache (idade: ' + Math.round(tempoDecorrido / 1000) + 's)');
          return {
            ...this.cachedData,
            _isFromCache: true,
            _cacheAge: Math.round(tempoDecorrido / 1000)
          };
        }
      }

      // Estratégia 2: Dados mockados para demonstração
      console.info('Usando dados mockados para demonstração');
      return this.getDadosMockados();
    }
  }

  /**
   * Dados mockados para quando o backend não está disponível
   */
  getDadosMockados() {
    return {
      statusGeral: "SIMULAÇÃO",
      ultimaAtualizacao: new Date().toISOString(),
      versaoSistema: "2.1.0-DEMO",
      tempoOnline: "Demo Mode",
      _isMocked: true,
      
      statusAPIs: {
        "API REST": {
          status: "SIMULACAO",
          tempoResposta: 1200,
          totalRegistros: 1500,
          taxaSucesso: 95,
          ultimaExecucao: new Date(Date.now() - 30 * 60 * 1000).toISOString()
        },
        "API GraphQL": {
          status: "SIMULACAO", 
          tempoResposta: 800,
          totalRegistros: 2300,
          taxaSucesso: 98,
          ultimaExecucao: new Date(Date.now() - 25 * 60 * 1000).toISOString()
        },
        "API Data Export": {
          status: "SIMULACAO",
          tempoResposta: 2100,
          totalRegistros: 890,
          taxaSucesso: 92,
          ultimaExecucao: new Date(Date.now() - 40 * 60 * 1000).toISOString()
        }
      },

      metricas: {
        duracaoTotalSegundos: 45,
        totalRegistrosProcessados: 4690,
        taxaSucessoGeral: 95
      },

      dadosGraficos: {
        registrosPorAPI: {
          "API REST": 1500,
          "API GraphQL": 2300,
          "API Data Export": 890
        },
        
        temposResposta: [
          { timestamp: "2025-10-02T10:00:00", apiRest: 1200, apiGraphQL: 800, apiDataExport: 2100 },
          { timestamp: "2025-10-02T11:00:00", apiRest: 1150, apiGraphQL: 750, apiDataExport: 2050 },
          { timestamp: "2025-10-02T12:00:00", apiRest: 1300, apiGraphQL: 850, apiDataExport: 2200 },
          { timestamp: "2025-10-02T13:00:00", apiRest: 1100, apiGraphQL: 700, apiDataExport: 1950 }
        ],
        
        execucoesPorHora: [
          { hora: "09:00", sucessos: 45, falhas: 2 },
          { hora: "10:00", sucessos: 52, falhas: 1 },
          { hora: "11:00", sucessos: 48, falhas: 3 },
          { hora: "12:00", sucessos: 55, falhas: 0 },
          { hora: "13:00", sucessos: 42, falhas: 1 }
        ]
      },

      consumoApis: {
        apiRest: 1500,
        apiGraphQL: 2300,
        apiDataExport: 890,
        totalRequisicoes: 4690,
        detalhes: {
          descricao: "Dados simulados - Backend indisponível"
        }
      }
    };
  }

  /**
   * Verifica se o backend está online
   */
  async verificarConexao() {
    try {
      await this.fetchWithRetry(`${this.baseUrl}/health`, {}, 0); // Sem retry para verificação rápida
      return true;
    } catch (error) {
      return false;
    }
  }

  /**
   * Obtém informações sobre o status da conexão
   */
  getStatusConexao() {
    return {
      isOnline: this.isOnline,
      lastSuccessfulFetch: this.lastSuccessfulFetch,
      hasCachedData: !!this.cachedData,
      cacheAge: this.lastSuccessfulFetch ? Date.now() - this.lastSuccessfulFetch.getTime() : null
    };
  }

  /**
   * Utilitário para delay
   */
  delay(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
  }

  /**
   * Limpa o cache
   */
  clearCache() {
    this.cachedData = null;
    this.lastSuccessfulFetch = null;
  }
}

// Singleton para uso global
const apiService = new ApiService();

export default apiService;