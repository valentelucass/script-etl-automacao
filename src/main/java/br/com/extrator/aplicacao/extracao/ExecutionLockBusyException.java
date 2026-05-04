package br.com.extrator.aplicacao.extracao;

import java.sql.SQLException;

public class ExecutionLockBusyException extends SQLException {
    private final String resourceName;
    private final int lockCode;

    public ExecutionLockBusyException(final String resourceName, final int lockCode) {
        super(
            "Outra execucao do Extrator ESL aparenta estar em andamento e esta segurando o lock global '"
                + resourceName
                + "'. Aguarde a conclusao, pare o loop daemon pelo menu ou cancele a nova execucao. Codigo="
                + lockCode
        );
        this.resourceName = resourceName;
        this.lockCode = lockCode;
    }

    public String getResourceName() {
        return resourceName;
    }

    public int getLockCode() {
        return lockCode;
    }
}
