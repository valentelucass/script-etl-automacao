/* ==[DOC-FILE]===============================================================
Arquivo : src/main/java/br/com/extrator/util/console/BannerUtil.java
Classe  : BannerUtil (class)
Pacote  : br.com.extrator.suporte.console
Modulo  : Utilitario compartilhado
Papel   : Implementa responsabilidade de banner util.

Conecta com:
- Sem dependencia interna explicita (classe isolada ou foco em libs externas).

Fluxo geral:
1) Centraliza funcoes auxiliares reutilizaveis.
2) Evita repeticao de logica transversal.
3) Apoia configuracao, formatacao e infraestrutura.

Estrutura interna:
Metodos principais:
- exibirBannerExtracaoCompleta(): realiza operacao relacionada a "exibir banner extracao completa".
- exibirBannerApiRest(): realiza operacao relacionada a "exibir banner api rest".
- exibirBannerApiGraphQL(): realiza operacao relacionada a "exibir banner api graph ql".
- exibirBannerApiDataExport(): realiza operacao relacionada a "exibir banner api data export".
- exibirBannerSucesso(): realiza operacao relacionada a "exibir banner sucesso".
- exibirBannerErro(): realiza operacao relacionada a "exibir banner erro".
- exibirBanner(...1 args): realiza operacao relacionada a "exibir banner".
- exibirEstatisticas(...3 args): realiza operacao relacionada a "exibir estatisticas".
- exibirSeparador(): realiza operacao relacionada a "exibir separador".
- exibirProgresso(...1 args): realiza operacao relacionada a "exibir progresso".
- exibirSucessoMensagem(...1 args): realiza operacao relacionada a "exibir sucesso mensagem".
- exibirErroMensagem(...1 args): realiza operacao relacionada a "exibir erro mensagem".
- exibirAvisoMensagem(...1 args): realiza operacao relacionada a "exibir aviso mensagem".
Atributos-chave:
- logger: logger da classe para diagnostico.
[DOC-FILE-END]============================================================== */

package br.com.extrator.suporte.console;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utilitário para exibir banners estilizados no console.
 * 
 * Por que banners bonitos? Porque código não precisa ser apenas funcional,
 * também pode ser uma experiência agradável. Cada detalhe importa.
 * 
 * @author Lucas Andrade (@valentelucass) - lucasmac.dev@gmail.com
 */
public class BannerUtil {

    private static final Logger logger = LoggerFactory.getLogger(BannerUtil.class);

    /**
     * Exibe o banner de extração completa
     */
    public static void exibirBannerExtracaoCompleta() {
        exibirBanner("banners/banner-extracao-completa.txt");
    }

    /**
     * Exibe o banner da API REST
     */
    public static void exibirBannerApiRest() {
        exibirBanner("banners/banner-api-rest.txt");
    }

    /**
     * Exibe o banner da API GraphQL
     */
    public static void exibirBannerApiGraphQL() {
        exibirBanner("banners/banner-api-graphql.txt");
    }

    /**
     * Exibe o banner da API Data Export
     */
    public static void exibirBannerApiDataExport() {
        exibirBanner("banners/banner-api-dataexport.txt");
    }

    /**
     * Exibe o banner de sucesso
     */
    public static void exibirBannerSucesso() {
        exibirBanner("banners/banner-sucesso.txt");
    }

    /**
     * Exibe o banner de erro
     */
    public static void exibirBannerErro() {
        exibirBanner("banners/banner-erro.txt");
    }

    /**
     * Exibe um banner personalizado (método público para uso em comandos específicos)
     * 
     * @param caminhoArquivo Caminho do arquivo de banner em resources
     */
    public static void exibirBanner(final String caminhoArquivo) {
        try (final InputStream inputStream = BannerUtil.class.getClassLoader()
                .getResourceAsStream(caminhoArquivo)) {

            if (inputStream == null) {
                logger.warn("Banner não encontrado: {}", caminhoArquivo);
                return;
            }

            try (final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                
                final String banner = reader.lines()
                        .collect(Collectors.joining("\n"));

                System.out.println(banner);
            }

        } catch (final Exception e) {
            logger.warn("Erro ao exibir banner: {}", e.getMessage());
        }
    }

    /**
     * Exibe estatísticas de extração formatadas
     * 
     * @param nomeApi Nome da API
     * @param registros Número de registros extraídos
     * @param tempoSegundos Tempo de execução em segundos
     */
    public static void exibirEstatisticas(final String nomeApi, final int registros, final long tempoSegundos) {
        System.out.println();
        System.out.println("  📊 " + nomeApi);
        System.out.println("     ├─ Registros: " + registros);
        System.out.println("     ├─ Tempo: " + tempoSegundos + "s");
        if (tempoSegundos > 0) {
            System.out.println("     └─ Taxa: " + (registros / tempoSegundos) + " reg/s");
        }
        System.out.println();
    }

    /**
     * Exibe uma linha separadora
     */
    public static void exibirSeparador() {
        System.out.println("═══════════════════════════════════════════════════════════════════════════");
    }

    /**
     * Exibe uma mensagem de progresso
     * 
     * @param mensagem Mensagem a exibir
     */
    public static void exibirProgresso(final String mensagem) {
        System.out.println("  ⏳ " + mensagem + "...");
    }

    /**
     * Exibe uma mensagem de sucesso
     * 
     * @param mensagem Mensagem a exibir
     */
    public static void exibirSucessoMensagem(final String mensagem) {
        System.out.println("  ✅ " + mensagem);
    }

    /**
     * Exibe uma mensagem de erro
     * 
     * @param mensagem Mensagem a exibir
     */
    public static void exibirErroMensagem(final String mensagem) {
        System.out.println("  ❌ " + mensagem);
    }

    /**
     * Exibe uma mensagem de aviso
     * 
     * @param mensagem Mensagem a exibir
     */
    public static void exibirAvisoMensagem(final String mensagem) {
        System.out.println("  ⚠️  " + mensagem);
    }
}
