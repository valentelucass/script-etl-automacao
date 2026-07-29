package br.com.extrator.aplicacao.expurgo;

import java.time.LocalDate;

public interface ColetaSourceSnapshotPort {
    ColetaSourceSnapshot carregar(LocalDate dataInicio, LocalDate dataFim);
}
