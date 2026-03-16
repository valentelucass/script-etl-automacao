package br.com.extrator.aplicacao.validacao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import br.com.extrator.suporte.validacao.ConstantesEntidades;

class ValidacaoEtlExtremaUseCaseTest {

    @Test
    void devePularStressRepetitivoParaUsuariosSistema() {
        assertFalse(ValidacaoEtlExtremaUseCase.deveExecutarStressApi(ConstantesEntidades.USUARIOS_SISTEMA));
        assertFalse(ValidacaoEtlExtremaUseCase.deveExecutarReplayIdempotencia(ConstantesEntidades.USUARIOS_SISTEMA));
        assertFalse(ValidacaoEtlExtremaUseCase.deveExecutarStressApi(ConstantesEntidades.FATURAS_GRAPHQL));
        assertTrue(ValidacaoEtlExtremaUseCase.deveExecutarReplayIdempotencia(ConstantesEntidades.FATURAS_GRAPHQL));
    }

    @Test
    void deveManterStressParaEntidadesTemporais() {
        assertTrue(ValidacaoEtlExtremaUseCase.deveExecutarStressApi(ConstantesEntidades.FRETES));
        assertTrue(ValidacaoEtlExtremaUseCase.deveExecutarStressApi(ConstantesEntidades.COLETAS));
        assertTrue(ValidacaoEtlExtremaUseCase.deveExecutarReplayIdempotencia(ConstantesEntidades.FRETES));
    }
}
