package br.com.extrator.aplicacao.extracao;

@FunctionalInterface
public interface ExecutionLockManager {
    AutoCloseable acquire(String resourceName) throws Exception;
}
