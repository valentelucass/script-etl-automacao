package br.com.extrator.servicos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serviço singleton para coleta e gerenciamento de métricas de execução
 * Monitora sucessos, falhas, registros processados e tempos de execução
 * Inclui persistência diária dos dados em arquivo JSON
 */
public class MetricasService {
    private static final Logger logger = LoggerFactory.getLogger(MetricasService.class);
    private static volatile MetricasService instancia;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DIRETORIO_METRICAS = "metricas";
    
    // Contadores de sucessos e falhas por API
    private final Map<String, Integer> sucessosPorApi = new ConcurrentHashMap<>();
    private final Map<String, Integer> falhasPorApi = new ConcurrentHashMap<>();
    
    // Registros processados por entidade
    private final Map<String, Integer> registrosPorEntidade = new ConcurrentHashMap<>();
    
    // Contadores de requisições HTTP por API
    private final Map<String, Integer> requisicoesHttpPorApi = new ConcurrentHashMap<>();
    
    // Controle de timers para operações
    private final Map<String, LocalDateTime> timersInicio = new ConcurrentHashMap<>();
    private final Map<String, Duration> duracaoOperacoes = new ConcurrentHashMap<>();
    
    // Timestamp de início da coleta de métricas
    private final LocalDateTime inicioColeta;
    
    /**
     * Construtor privado para implementação singleton
     */
    private MetricasService() {
        this.inicioColeta = LocalDateTime.now();
        logger.info("MetricasService inicializado em {}", 
                   inicioColeta.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));
        
        // Criar diretório de métricas se não existir
        criarDiretorioMetricas();
    }
    
    /**
     * Cria o diretório de métricas se não existir
     */
    private void criarDiretorioMetricas() {
        try {
            Path diretorio = Paths.get(DIRETORIO_METRICAS);
            if (!Files.exists(diretorio)) {
                Files.createDirectories(diretorio);
                logger.info("Diretório de métricas criado: {}", diretorio.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Erro ao criar diretório de métricas: {}", e.getMessage());
        }
    }
    
    /**
     * Gera o nome do arquivo de métricas para uma data específica
     * 
     * @param data Data para gerar o nome do arquivo
     * @return Nome do arquivo (ex: "metricas-2025-01-02.json")
     */
    private String gerarNomeArquivoMetricas(LocalDate data) {
        return String.format("%s/metricas-%s.json", DIRETORIO_METRICAS, 
                           data.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
    }
    
    /**
     * Salva as métricas do dia atual em arquivo JSON
     */
    public void salvarMetricasDoDia() {
        try {
            String nomeArquivo = gerarNomeArquivoMetricas(LocalDate.now());
            
            Map<String, Object> dadosMetricas = new HashMap<>();
            dadosMetricas.put("data", LocalDate.now().toString());
            dadosMetricas.put("inicioColeta", inicioColeta.toString());
            dadosMetricas.put("sucessosPorApi", new HashMap<>(sucessosPorApi));
            dadosMetricas.put("falhasPorApi", new HashMap<>(falhasPorApi));
            dadosMetricas.put("registrosPorEntidade", new HashMap<>(registrosPorEntidade));
            dadosMetricas.put("requisicoesHttpPorApi", new HashMap<>(requisicoesHttpPorApi));
            
            // Converter durações para milissegundos para serialização
            Map<String, Long> duracoesMilis = new HashMap<>();
            duracaoOperacoes.forEach((operacao, duracao) -> 
                duracoesMilis.put(operacao, duracao.toMillis()));
            dadosMetricas.put("duracaoOperacoesMilis", duracoesMilis);
            
            objectMapper.writerWithDefaultPrettyPrinter()
                       .writeValue(new File(nomeArquivo), dadosMetricas);
            
            logger.info("Métricas do dia salvas em: {}", nomeArquivo);
            
        } catch (IOException e) {
            logger.error("Erro ao salvar métricas do dia: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Carrega as métricas do dia atual do arquivo JSON
     */
    public void carregarMetricasDoDia() {
        try {
            String nomeArquivo = gerarNomeArquivoMetricas(LocalDate.now());
            File arquivo = new File(nomeArquivo);
            
            if (!arquivo.exists()) {
                logger.info("Arquivo de métricas do dia não encontrado: {}. Iniciando com métricas zeradas.", nomeArquivo);
                return;
            }
            
            JsonNode dadosJson = objectMapper.readTree(arquivo);
            
            // Carregar sucessos por API
            if (dadosJson.has("sucessosPorApi")) {
                Map<String, Integer> sucessos = objectMapper.convertValue(
                    dadosJson.get("sucessosPorApi"), 
                    new TypeReference<Map<String, Integer>>() {}
                );
                sucessosPorApi.putAll(sucessos);
            }
            
            // Carregar falhas por API
            if (dadosJson.has("falhasPorApi")) {
                Map<String, Integer> falhas = objectMapper.convertValue(
                    dadosJson.get("falhasPorApi"), 
                    new TypeReference<Map<String, Integer>>() {}
                );
                falhasPorApi.putAll(falhas);
            }
            
            // Carregar registros por entidade
            if (dadosJson.has("registrosPorEntidade")) {
                Map<String, Integer> registros = objectMapper.convertValue(
                    dadosJson.get("registrosPorEntidade"), 
                    new TypeReference<Map<String, Integer>>() {}
                );
                registrosPorEntidade.putAll(registros);
            }
            
            // Carregar requisições HTTP por API
            if (dadosJson.has("requisicoesHttpPorApi")) {
                Map<String, Integer> requisicoes = objectMapper.convertValue(
                    dadosJson.get("requisicoesHttpPorApi"), 
                    new TypeReference<Map<String, Integer>>() {}
                );
                requisicoesHttpPorApi.putAll(requisicoes);
            }
            
            // Carregar durações de operações
            if (dadosJson.has("duracaoOperacoesMilis")) {
                Map<String, Long> duracoesMilis = objectMapper.convertValue(
                    dadosJson.get("duracaoOperacoesMilis"), 
                    new TypeReference<Map<String, Long>>() {}
                );
                duracoesMilis.forEach((operacao, milis) -> 
                    duracaoOperacoes.put(operacao, Duration.ofMillis(milis)));
            }
            
            logger.info("Métricas do dia carregadas de: {}", nomeArquivo);
            logger.info("Dados carregados - Sucessos: {}, Falhas: {}, Registros: {}, Requisições: {}", 
                       getTotalSucessos(), getTotalFalhas(), getTotalRegistrosProcessados(), getTotalRequisicoesHttp());
            
        } catch (IOException e) {
            logger.error("Erro ao carregar métricas do dia: {}", e.getMessage(), e);
        }
    }

    /**
     * Obtém a instância singleton do MetricasService
     * Thread-safe usando double-checked locking
     * 
     * @return Instância única do MetricasService
     */
    public static MetricasService getInstance() {
        if (instancia == null) {
            synchronized (MetricasService.class) {
                if (instancia == null) {
                    instancia = new MetricasService();
                }
            }
        }
        return instancia;
    }
    
    /**
     * Registra um sucesso para uma API específica
     * 
     * @param nomeApi Nome da API (ex: "REST", "GraphQL", "DataExport")
     */
    public void registrarSucesso(String nomeApi) {
        sucessosPorApi.merge(nomeApi, 1, Integer::sum);
        logger.debug("Sucesso registrado para API: {}", nomeApi);
    }
    
    /**
     * Registra uma falha para uma API específica
     * 
     * @param nomeApi Nome da API (ex: "REST", "GraphQL", "DataExport")
     */
    public void registrarFalha(String nomeApi) {
        falhasPorApi.merge(nomeApi, 1, Integer::sum);
        logger.debug("Falha registrada para API: {}", nomeApi);
    }
    
    /**
     * Adiciona registros processados para uma entidade específica
     * 
     * @param nomeEntidade Nome da entidade (ex: "faturas", "ocorrencias", "coletas")
     * @param quantidade Quantidade de registros processados
     */
    public void adicionarRegistrosProcessados(String nomeEntidade, int quantidade) {
        registrosPorEntidade.merge(nomeEntidade, quantidade, Integer::sum);
        logger.debug("Registrados {} registros para entidade: {}", quantidade, nomeEntidade);
    }
    
    /**
     * Registra uma requisição HTTP para uma API específica
     * 
     * @param nomeApi Nome da API (ex: "REST", "GraphQL", "DataExport")
     */
    public void registrarRequisicaoApi(String nomeApi) {
        requisicoesHttpPorApi.merge(nomeApi, 1, Integer::sum);
        logger.debug("Requisição HTTP registrada para API: {}", nomeApi);
    }
    
    /**
     * Obtém o número de requisições HTTP para uma API específica
     * 
     * @param nomeApi Nome da API
     * @return Número de requisições HTTP realizadas
     */
    public int getRequisicoesApi(String nomeApi) {
        return requisicoesHttpPorApi.getOrDefault(nomeApi, 0);
    }
    
    /**
     * Obtém o mapa completo de requisições HTTP por API
     * 
     * @return Map com o número de requisições por API
     */
    public Map<String, Integer> getRequisicoesHttpPorApi() {
        return new HashMap<>(requisicoesHttpPorApi);
    }
    
    /**
     * Obtém o total de requisições HTTP realizadas
     * 
     * @return Soma de todas as requisições HTTP
     */
    public int getTotalRequisicoesHttp() {
        return requisicoesHttpPorApi.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Inicia um timer para uma operação específica
     * 
     * @param nomeOperacao Nome da operação (ex: "extracao_faturas_receber")
     */
    public void iniciarTimer(String nomeOperacao) {
        timersInicio.put(nomeOperacao, LocalDateTime.now());
        logger.debug("Timer iniciado para operação: {}", nomeOperacao);
    }
    
    /**
     * Para um timer e calcula a duração da operação
     * 
     * @param nomeOperacao Nome da operação
     */
    public void pararTimer(String nomeOperacao) {
        LocalDateTime inicio = timersInicio.remove(nomeOperacao);
        if (inicio != null) {
            Duration duracao = Duration.between(inicio, LocalDateTime.now());
            duracaoOperacoes.put(nomeOperacao, duracao);
            logger.debug("Timer parado para operação: {} - Duração: {}ms", 
                        nomeOperacao, duracao.toMillis());
        } else {
            logger.warn("Tentativa de parar timer não iniciado: {}", nomeOperacao);
        }
    }
    
    /**
     * Obtém a duração total da coleta de métricas
     * 
     * @return Duração desde o início da coleta
     */
    public Duration getDuracaoTotal() {
        return Duration.between(inicioColeta, LocalDateTime.now());
    }
    
    /**
     * Obtém o total de sucessos registrados
     * 
     * @return Soma de todos os sucessos
     */
    public int getTotalSucessos() {
        return sucessosPorApi.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Obtém o total de falhas registradas
     * 
     * @return Soma de todas as falhas
     */
    public int getTotalFalhas() {
        return falhasPorApi.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Obtém o total de registros processados
     * 
     * @return Soma de todos os registros processados
     */
    public int getTotalRegistrosProcessados() {
        return registrosPorEntidade.values().stream().mapToInt(Integer::intValue).sum();
    }
    
    /**
     * Obtém o mapa completo de sucessos por API
     * 
     * @return Map com o número de sucessos por API
     */
    public Map<String, Integer> getSucessosPorApi() {
        return new HashMap<>(sucessosPorApi);
    }

    /**
     * Obtém o mapa completo de falhas por API
     * 
     * @return Map com o número de falhas por API
     */
    public Map<String, Integer> getFalhasPorApi() {
        return new HashMap<>(falhasPorApi);
    }

    /**
     * Obtém o mapa completo de registros processados por entidade
     * 
     * @return Map com o número de registros por entidade
     */
    public Map<String, Integer> getRegistrosPorEntidade() {
        return new HashMap<>(registrosPorEntidade);
    }

    /**
     * Obtém o mapa completo de durações de operações
     * 
     * @return Map com as durações das operações
     */
    public Map<String, Duration> getDuracaoOperacoes() {
        return new HashMap<>(duracaoOperacoes);
    }

    /**
     * Obtém a taxa de sucesso geral
     * 
     * @return Taxa de sucesso em porcentagem (0-100)
     */
    public double getTaxaSucessoGeral() {
        int totalOperacoes = getTotalSucessos() + getTotalFalhas();
        if (totalOperacoes == 0) return 0.0;
        return (getTotalSucessos() * 100.0) / totalOperacoes;
    }

    /**
     * Obtém o tempo médio de execução das operações
     * 
     * @return Tempo médio em milissegundos
     */
    public long getTempoMedioExecucao() {
        if (duracaoOperacoes.isEmpty()) return 0;
        long totalMilis = duracaoOperacoes.values().stream()
            .mapToLong(Duration::toMillis)
            .sum();
        return totalMilis / duracaoOperacoes.size();
    }
    
    /**
     * Gera um relatório completo das métricas coletadas
     * 
     * @return String formatada com o relatório de métricas
     */
    public String gerarRelatorio() {
        StringBuilder relatorio = new StringBuilder();
        
        relatorio.append("=".repeat(60)).append("\n");
        relatorio.append("MÉTRICAS DE EXECUÇÃO").append("\n");
        relatorio.append("=".repeat(60)).append("\n");
        
        // Informações gerais
        Duration duracaoTotal = getDuracaoTotal();
        relatorio.append("Duração Total: ").append(formatarDuracao(duracaoTotal)).append("\n");
        relatorio.append("Início da Coleta: ").append(inicioColeta.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        relatorio.append("Total de Operações: ").append(getTotalSucessos() + getTotalFalhas()).append("\n");
        relatorio.append("\n");
        
        // Sucessos e Falhas por API
        if (!sucessosPorApi.isEmpty() || !falhasPorApi.isEmpty()) {
            relatorio.append("SUCESSOS E FALHAS POR API:").append("\n");
            
            // Coleta todas as APIs mencionadas
            Map<String, String> statusPorApi = new HashMap<>();
            sucessosPorApi.forEach((api, sucessos) -> {
                int falhas = falhasPorApi.getOrDefault(api, 0);
                double taxaSucesso = (sucessos * 100.0) / (sucessos + falhas);
                statusPorApi.put(api, String.format("   %-12s: %d sucessos, %d falhas (%.1f%% sucesso)", 
                                                   api, sucessos, falhas, taxaSucesso));
            });
            
            falhasPorApi.forEach((api, falhas) -> {
                if (!sucessosPorApi.containsKey(api)) {
                    statusPorApi.put(api, String.format("   %-12s: %d sucessos, %d falhas (%.1f%% sucesso)", 
                                                       api, 0, falhas, 0.0));
                }
            });
            
            statusPorApi.values().forEach(linha -> relatorio.append(linha).append("\n"));
            relatorio.append("\n");
        }
        
        // Registros processados por entidade
        if (!registrosPorEntidade.isEmpty()) {
            relatorio.append("REGISTROS PROCESSADOS POR ENTIDADE:").append("\n");
            registrosPorEntidade.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> relatorio.append(String.format("   %-20s: %,d registros%n", 
                                                                 entry.getKey(), entry.getValue())));
            relatorio.append(String.format("   %-20s: %,d registros%n", "TOTAL", getTotalRegistrosProcessados()));
            relatorio.append("\n");
        }
        
        // Tempos de execução por operação
        if (!duracaoOperacoes.isEmpty()) {
            relatorio.append("TEMPOS DE EXECUÇÃO POR OPERAÇÃO:").append("\n");
            duracaoOperacoes.entrySet().stream()
                .sorted(Map.Entry.<String, Duration>comparingByValue().reversed())
                .forEach(entry -> relatorio.append(String.format("   %-25s: %s%n", 
                                                                 entry.getKey(), formatarDuracao(entry.getValue()))));
            relatorio.append("\n");
        }
        
        // Resumo de performance
        relatorio.append("RESUMO DE PERFORMANCE:").append("\n");
        if (getTotalRegistrosProcessados() > 0 && duracaoTotal.toMillis() > 0) {
            double registrosPorSegundo = getTotalRegistrosProcessados() / (duracaoTotal.toMillis() / 1000.0);
            relatorio.append(String.format("   Taxa de Processamento: %.2f registros/segundo%n", registrosPorSegundo));
        }
        
        if (getTotalSucessos() + getTotalFalhas() > 0) {
            double taxaSucessoGeral = (getTotalSucessos() * 100.0) / (getTotalSucessos() + getTotalFalhas());
            relatorio.append(String.format("   Taxa de Sucesso Geral: %.1f%%%n", taxaSucessoGeral));
        }
        
        relatorio.append("=".repeat(60)).append("\n");
        
        return relatorio.toString();
    }
    
    /**
     * Formata uma duração para exibição amigável
     * 
     * @param duracao Duração a ser formatada
     * @return String formatada (ex: "2m 30s", "45s", "1.2s")
     */
    private String formatarDuracao(Duration duracao) {
        long totalSegundos = duracao.getSeconds();
        long minutos = totalSegundos / 60;
        long segundos = totalSegundos % 60;
        long milissegundos = duracao.toMillis() % 1000;
        
        if (minutos > 0) {
            return String.format("%dm %ds", minutos, segundos);
        } else if (segundos > 0) {
            return String.format("%ds", segundos);
        } else {
            return String.format("%.1fs", milissegundos / 1000.0);
        }
    }
    
    /**
     * Reseta todas as métricas coletadas
     * Útil para testes ou reinicializações
     */
    public void resetar() {
        sucessosPorApi.clear();
        falhasPorApi.clear();
        registrosPorEntidade.clear();
        requisicoesHttpPorApi.clear();
        timersInicio.clear();
        duracaoOperacoes.clear();
        logger.info("Métricas resetadas");
    }
}