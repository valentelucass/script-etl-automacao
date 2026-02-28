/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/servicos/ValidadorLimiteExtracao.java
Classe  : ValidadorLimiteExtracao (class)
Pacote  : br.com.extrator.servicos
Modulo  : Servico de negocio
Papel   : Implementa responsabilidade de validador limite extracao.

Conecta com:
- LogExtracaoEntity (db.entity)
- LogExtracaoRepository (db.repository)
- LoggerConsole (util.console)

Fluxo geral:
1) Encapsula regras de processo.
2) Coordena validacoes e limites operacionais.
3) Expone API interna para comandos/runners.

Estrutura interna:
Metodos principais:
- ValidadorLimiteExtracao(): realiza operacao relacionada a "validador limite extracao".
- validarLimiteExtracao(...3 args): aplica regras de validacao e consistencia.
- validarLimiteExtracaoPorPeriodoTotal(...4 args): aplica regras de validacao e consistencia.
- calcularDuracaoPeriodo(...2 args): realiza operacao relacionada a "calcular duracao periodo".
- obterLimiteHoras(...1 args): recupera dados configurados ou calculados.
Atributos-chave:
- log: campo de estado para "log".
- DIAS_31: campo de estado para "dias 31".
- DIAS_6_MESES: campo de estado para "dias 6 meses".
- logRepository: dependencia de acesso a banco.
[DOC-FILE-END]============================================================== */

package br.com.extrator.servicos;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import br.com.extrator.db.entity.LogExtracaoEntity;
import br.com.extrator.db.repository.LogExtracaoRepository;
import br.com.extrator.util.console.LoggerConsole;

/**
 * Serviço responsável por validar regras de limitação de tempo para extrações
 * baseadas no período consultado.
 * 
 * Regras:
 * - < 31 dias: sem limite de tempo
 * - 31 dias a 6 meses: mínimo 1 hora desde última extração do mesmo período
 * - > 6 meses: mínimo 12 horas desde última extração do mesmo período
 */
public class ValidadorLimiteExtracao {
    
    private static final LoggerConsole log = LoggerConsole.getLogger(ValidadorLimiteExtracao.class);
    
    private static final int DIAS_31 = 31;
    private static final int DIAS_6_MESES = 180; // Aproximadamente 6 meses
    
    private final LogExtracaoRepository logRepository;
    
    public ValidadorLimiteExtracao() {
        this.logRepository = new LogExtracaoRepository();
    }
    
    /**
     * Resultado da validação de limite de extração
     */
    public static class ResultadoValidacao {
        private final boolean permitido;
        private final String motivo;
        private final long horasRestantes;
        private final int limiteHoras;
        
        private ResultadoValidacao(final boolean permitido, final String motivo, final long horasRestantes, final int limiteHoras) {
            this.permitido = permitido;
            this.motivo = motivo;
            this.horasRestantes = horasRestantes;
            this.limiteHoras = limiteHoras;
        }
        
        public static ResultadoValidacao permitido() {
            return new ResultadoValidacao(true, "Extração permitida", 0, 0);
        }
        
        public static ResultadoValidacao bloqueado(final String motivo, final long horasRestantes, final int limiteHoras) {
            return new ResultadoValidacao(false, motivo, horasRestantes, limiteHoras);
        }
        
        public boolean isPermitido() {
            return permitido;
        }
        
        public String getMotivo() {
            return motivo;
        }
        
        public long getHorasRestantes() {
            return horasRestantes;
        }
        
        public int getLimiteHoras() {
            return limiteHoras;
        }
    }
    
    /**
     * Valida se é permitido executar uma extração para o período especificado
     * baseado nas regras de limitação de tempo.
     * 
     * @param entidade Nome da entidade a ser extraída
     * @param dataInicio Data de início do período
     * @param dataFim Data de fim do período
     * @return ResultadoValidacao indicando se é permitido ou bloqueado
     */
    public ResultadoValidacao validarLimiteExtracao(final String entidade, 
                                                     final LocalDate dataInicio, 
                                                     final LocalDate dataFim) {
        // Calcular duração do período em dias
        final long diasPeriodo = calcularDuracaoPeriodo(dataInicio, dataFim);
        
        // Obter limite de horas baseado na duração
        final int limiteHoras = obterLimiteHoras(diasPeriodo);
        
        // Se não há limite (período < 31 dias), permitir imediatamente
        if (limiteHoras == 0) {
            log.debug("Período de {} dias (< 31 dias) - sem limite de tempo", diasPeriodo);
            return ResultadoValidacao.permitido();
        }
        
        // Buscar última extração do mesmo período
        final Optional<LogExtracaoEntity> ultimaExtracao = 
            logRepository.buscarUltimaExtracaoPorPeriodo(entidade, dataInicio, dataFim);
        
        // Se não há extração anterior, permitir
        if (ultimaExtracao.isEmpty()) {
            log.debug("Nenhuma extração anterior encontrada para período {} a {} - permitindo", 
                     dataInicio, dataFim);
            return ResultadoValidacao.permitido();
        }
        
        // Calcular tempo decorrido desde última extração
        final LogExtracaoEntity logExtracao = ultimaExtracao.get();
        final LocalDateTime agora = LocalDateTime.now();
        final LocalDateTime ultimaExtracaoFim = logExtracao.getTimestampFim();
        
        final Duration tempoDecorrido = Duration.between(ultimaExtracaoFim, agora);
        final long horasDecorridas = tempoDecorrido.toHours();
        final long minutosRestantes = tempoDecorrido.toMinutes() % 60;
        
        // Verificar se já passou o tempo mínimo
        if (horasDecorridas >= limiteHoras) {
            log.info("✅ Limite de {} horas já foi atingido (decorridas: {}h {}min) - permitindo extração", 
                    limiteHoras, horasDecorridas, minutosRestantes);
            return ResultadoValidacao.permitido();
        }
        
        // Calcular tempo restante
        final long horasRestantes = limiteHoras - horasDecorridas;
        final long minutosRestantesTotal = (limiteHoras * 60) - tempoDecorrido.toMinutes();
        
        final String motivo = String.format(
            "Extração bloqueada: necessário aguardar %d hora(s) desde última extração (decorridas: %dh %dmin, restam: %dh %dmin)",
            limiteHoras, horasDecorridas, minutosRestantes, horasRestantes, minutosRestantesTotal % 60
        );
        
        log.warn("⏳ {}", motivo);
        
        return ResultadoValidacao.bloqueado(motivo, horasRestantes, limiteHoras);
    }
    
    /**
     * Valida se é permitido executar uma extração para o período especificado,
     * usando o período TOTAL solicitado para determinar a regra de limitação.
     * 
     * ATENÇÃO: Este método NÃO deve ser usado quando o período é dividido em blocos.
     * Para blocos, use {@link #validarLimiteExtracao(String, LocalDate, LocalDate)} que
     * usa o tamanho do bloco (ex: 30 dias) em vez do período total.
     * 
     * Este método pode ser usado apenas em casos muito específicos onde realmente se deseja
     * aplicar a regra baseada no período total, não no tamanho do bloco individual.
     * 
     * @param entidade Nome da entidade a ser extraída
     * @param dataInicio Data de início do período/bloco
     * @param dataFim Data de fim do período/bloco
     * @param diasPeriodoTotal Número total de dias do período completo
     * @return ResultadoValidacao indicando se é permitido ou bloqueado
     * @deprecated Para blocos, use {@link #validarLimiteExtracao(String, LocalDate, LocalDate)}.
     *             Este método viola a estratégia de usar blocos de 30 dias para evitar regra de 12 horas.
     */
    public ResultadoValidacao validarLimiteExtracaoPorPeriodoTotal(final String entidade, 
                                                                    final LocalDate dataInicio, 
                                                                    final LocalDate dataFim,
                                                                    final long diasPeriodoTotal) {
        // Obter limite de horas baseado no período TOTAL (não do bloco)
        final int limiteHoras = obterLimiteHoras(diasPeriodoTotal);
        
        // Se não há limite (período < 31 dias), permitir imediatamente
        if (limiteHoras == 0) {
            log.debug("Período total de {} dias (< 31 dias) - sem limite de tempo", diasPeriodoTotal);
            return ResultadoValidacao.permitido();
        }
        
        // Buscar última extração do mesmo período (do bloco)
        final Optional<LogExtracaoEntity> ultimaExtracao = 
            logRepository.buscarUltimaExtracaoPorPeriodo(entidade, dataInicio, dataFim);
        
        // Se não há extração anterior, permitir
        if (ultimaExtracao.isEmpty()) {
            log.debug("Nenhuma extração anterior encontrada para período {} a {} - permitindo", 
                     dataInicio, dataFim);
            return ResultadoValidacao.permitido();
        }
        
        // Calcular tempo decorrido desde última extração
        final LogExtracaoEntity logExtracao = ultimaExtracao.get();
        final LocalDateTime agora = LocalDateTime.now();
        final LocalDateTime ultimaExtracaoFim = logExtracao.getTimestampFim();
        
        final Duration tempoDecorrido = Duration.between(ultimaExtracaoFim, agora);
        final long horasDecorridas = tempoDecorrido.toHours();
        final long minutosRestantes = tempoDecorrido.toMinutes() % 60;
        
        // Verificar se já passou o tempo mínimo
        if (horasDecorridas >= limiteHoras) {
            log.info("✅ Limite de {} horas já foi atingido (decorridas: {}h {}min) - permitindo extração", 
                    limiteHoras, horasDecorridas, minutosRestantes);
            return ResultadoValidacao.permitido();
        }
        
        // Calcular tempo restante
        final long horasRestantes = limiteHoras - horasDecorridas;
        final long minutosRestantesTotal = (limiteHoras * 60) - tempoDecorrido.toMinutes();
        
        final String motivo = String.format(
            "Extração bloqueada: necessário aguardar %d hora(s) desde última extração (decorridas: %dh %dmin, restam: %dh %dmin)",
            limiteHoras, horasDecorridas, minutosRestantes, horasRestantes, minutosRestantesTotal % 60
        );
        
        log.warn("⏳ {}", motivo);
        
        return ResultadoValidacao.bloqueado(motivo, horasRestantes, limiteHoras);
    }
    
    /**
     * Calcula a duração do período em dias (inclusive).
     * 
     * @param inicio Data de início
     * @param fim Data de fim
     * @return Número de dias do período
     */
    public long calcularDuracaoPeriodo(final LocalDate inicio, final LocalDate fim) {
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException(
                String.format("Data de início (%s) não pode ser posterior à data de fim (%s)", inicio, fim)
            );
        }
        
        // ChronoUnit.DAYS.between retorna dias entre as datas (exclusive)
        // Adicionamos 1 para incluir ambas as datas
        return ChronoUnit.DAYS.between(inicio, fim) + 1;
    }
    
    /**
     * Obtém o limite de horas baseado na duração do período.
     * 
     * @param diasPeriodo Duração do período em dias
     * @return Limite de horas: 0 (sem limite), 1 (1 hora) ou 12 (12 horas)
     */
    public int obterLimiteHoras(final long diasPeriodo) {
        if (diasPeriodo < DIAS_31) {
            return 0; // Sem limite
        } else if (diasPeriodo <= DIAS_6_MESES) {
            return 1; // 1 hora
        } else {
            return 12; // 12 horas
        }
    }
}

