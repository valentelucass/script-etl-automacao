/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/validacao/ConstantesEntidades.java
Classe  : ConstantesEntidades (class)
Pacote  : br.com.extrator.util.validacao
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de constantes entidades.

Conecta com:
- ThreadUtil (util)
- CarregadorConfig (util.configuracao)

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- obterDelayEntreExtracoes(): recupera dados configurados ou calculados.
- ConstantesEntidades(): realiza operacao relacionada a "constantes entidades".
Atributos-chave:
- COLETAS: campo de estado para "coletas".
- FRETES: campo de estado para "fretes".
- FATURAS_GRAPHQL: campo de estado para "faturas graphql".
- USUARIOS_SISTEMA: campo de estado para "usuarios sistema".
- MANIFESTOS: campo de estado para "manifestos".
- COTACOES: campo de estado para "cotacoes".
- LOCALIZACAO_CARGAS: campo de estado para "localizacao cargas".
- CONTAS_A_PAGAR: campo de estado para "contas a pagar".
- FATURAS_POR_CLIENTE: campo de estado para "faturas por cliente".
- ALIASES_COTACOES: campo de estado para "aliases cotacoes".
- ALIASES_LOCALIZACAO: campo de estado para "aliases localizacao".
- ALIASES_CONTAS_PAGAR: campo de estado para "aliases contas pagar".
- ALIASES_FATURAS_CLIENTE: campo de estado para "aliases faturas cliente".
- ALIASES_FATURAS_GRAPHQL: campo de estado para "aliases faturas graphql".
[DOC-FILE-END]============================================================== */

package br.com.extrator.util.validacao;

import br.com.extrator.util.ThreadUtil;
import br.com.extrator.util.configuracao.CarregadorConfig;

/**
 * Constantes centralizadas para nomes de entidades utilizadas no sistema.
 * Evita duplicação de strings "magic" espalhadas pelo código.
 * 
 * @author Sistema de Extração ESL Cloud
 * @version 1.0
 */
public final class ConstantesEntidades {
    
    // ========== ENTIDADES GRAPHQL ==========
    public static final String COLETAS = "coletas";
    public static final String FRETES = "fretes";
    public static final String FATURAS_GRAPHQL = "faturas_graphql";
    public static final String USUARIOS_SISTEMA = "usuarios_sistema";
    
    // ========== ENTIDADES DATA EXPORT ==========
    public static final String MANIFESTOS = "manifestos";
    public static final String COTACOES = "cotacoes";
    public static final String LOCALIZACAO_CARGAS = "localizacao_cargas";
    public static final String CONTAS_A_PAGAR = "contas_a_pagar";
    public static final String FATURAS_POR_CLIENTE = "faturas_por_cliente";
    
    // ========== ALIASES PARA COMPATIBILIDADE ==========
    /** Aliases para input do usuário (múltiplas formas aceitas) */
    public static final String[] ALIASES_COTACOES = {"cotacoes", "cotacao"};
    public static final String[] ALIASES_LOCALIZACAO = {"localizacao_carga", "localizacao_de_carga", "localizacao-carga", "localizacao de carga"};
    public static final String[] ALIASES_CONTAS_PAGAR = {"contas_a_pagar", "contasapagar", "contas a pagar", "contas-a-pagar"};
    public static final String[] ALIASES_FATURAS_CLIENTE = {"faturas_por_cliente", "faturasporcliente", "faturas por cliente", "faturas-por-cliente"};
    public static final String[] ALIASES_FATURAS_GRAPHQL = {"faturas_graphql", "faturas"};
    
    // ========== STATUS DE EXTRAÇÃO ==========
    public static final String STATUS_COMPLETO = "COMPLETO";
    /**
     * Status legado genérico. Mantido para compatibilidade com históricos antigos.
     */
    public static final String STATUS_INCOMPLETO = "INCOMPLETO";
    /**
     * Extração interrompida por proteção de paginação/volume/circuit breaker.
     */
    public static final String STATUS_INCOMPLETO_LIMITE = "INCOMPLETO_LIMITE";
    /**
     * Dados inválidos recebidos da origem (campos críticos nulos/ilegais).
     */
    public static final String STATUS_INCOMPLETO_DADOS = "INCOMPLETO_DADOS";
    /**
     * Divergência entre volume único esperado e volume efetivamente persistido.
     */
    public static final String STATUS_INCOMPLETO_DB = "INCOMPLETO_DB";
    public static final String STATUS_ERRO_API = "ERRO_API";
    
    // ========== DELAY ENTRE EXTRAÇÕES (configurável) ==========
    /** 
     * Obtém o delay padrão entre extrações de entidades.
     * Utiliza a configuração de extracao.delay.ms do CarregadorConfig.
     * @return delay em milissegundos
     */
    public static long obterDelayEntreExtracoes() {
        return CarregadorConfig.obterDelayEntreExtracoes();
    }
    
    /**
     * Aplica delay entre extrações de entidades.
     * Usa valor configurável em vez de hardcoded.
     * @throws InterruptedException se a thread for interrompida
     */
    public static void aplicarDelayEntreExtracoes() throws InterruptedException {
        ThreadUtil.aguardar(obterDelayEntreExtracoes());
    }
    
    private ConstantesEntidades() {
        // Impede instanciação
    }
}
