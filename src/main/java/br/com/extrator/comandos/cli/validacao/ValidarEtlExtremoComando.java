package br.com.extrator.comandos.cli.validacao;

import br.com.extrator.aplicacao.validacao.ValidacaoEtlExtremaRequest;
import br.com.extrator.aplicacao.validacao.ValidacaoEtlExtremaUseCase;
import br.com.extrator.comandos.cli.base.Comando;
import br.com.extrator.suporte.tempo.RelogioSistema;

public class ValidarEtlExtremoComando implements Comando {
    private final ValidacaoEtlExtremaUseCase useCase;

    public ValidarEtlExtremoComando() {
        this(new ValidacaoEtlExtremaUseCase());
    }

    ValidarEtlExtremoComando(final ValidacaoEtlExtremaUseCase useCase) {
        this.useCase = useCase;
    }

    @Override
    public void executar(final String[] args) throws Exception {
        useCase.executar(
            new ValidacaoEtlExtremaRequest(
                !possuiFlag(args, "--sem-faturas-graphql"),
                possuiFlag(args, "--periodo-fechado"),
                possuiFlag(args, "--permitir-fallback-janela"),
                resolverStressRepeticoes(args),
                possuiFlag(args, "--executar-idempotencia"),
                possuiFlag(args, "--executar-hidratacao-orfaos"),
                RelogioSistema.hoje()
            )
        );
    }

    private boolean possuiFlag(final String[] args, final String flag) {
        if (args == null || flag == null) {
            return false;
        }
        for (final String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private int resolverStressRepeticoes(final String[] args) {
        if (args == null) {
            return 3;
        }
        for (int i = 0; i < args.length; i++) {
            if (!"--stress-repeticoes".equalsIgnoreCase(args[i]) || i + 1 >= args.length) {
                continue;
            }
            try {
                return Math.max(1, Integer.parseInt(args[i + 1]));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Valor invalido para --stress-repeticoes: " + args[i + 1]);
            }
        }
        return 3;
    }
}
