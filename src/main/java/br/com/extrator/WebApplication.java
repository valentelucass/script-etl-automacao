package br.com.extrator;

import br.com.extrator.servicos.MetricasService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Aplicação Spring Boot para servir o dashboard de monitoramento
 * Executa na porta 7070 para fornecer APIs REST para o frontend React
 */
@SpringBootApplication
@ComponentScan(basePackages = "br.com.extrator")
public class WebApplication {
    
    public static void main(String[] args) {
        // Configura a porta para 7070
        System.setProperty("server.port", "7070");
        
        System.out.println("=== Iniciando Servidor Web do Dashboard ===");
        System.out.println("Porta: 7070");
        System.out.println("Endpoint principal: http://localhost:7070/api/status");
        System.out.println("Health check: http://localhost:7070/api/health");
        System.out.println("==========================================");
        
        // Carrega métricas do dia atual na inicialização
        MetricasService metricasService = MetricasService.getInstance();
        try {
            metricasService.carregarMetricasDoDia();
            System.out.println("Métricas do dia carregadas com sucesso!");
        } catch (Exception e) {
            System.out.println("Aviso: Não foi possível carregar métricas do dia: " + e.getMessage());
        }
        
        // Adiciona shutdown hook para salvar métricas ao encerrar
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Salvando métricas do dia antes de encerrar...");
            try {
                metricasService.salvarMetricasDoDia();
                System.out.println("Métricas do dia salvas com sucesso!");
            } catch (Exception e) {
                System.err.println("Erro ao salvar métricas do dia: " + e.getMessage());
            }
        }));
        
        SpringApplication.run(WebApplication.class, args);
    }
}