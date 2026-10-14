package br.gov.agu.virgo_back.consulta.domain;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

public enum EstadoExecucao {
    PENDENTE, EM_ANDAMENTO, CONCLUIDA, CANCELADA, INTERROMPIDA;

    void validarPeriodo(@Nullable Instant inicio, @Nullable Instant fim) {
        if (this == PENDENTE && (inicio != null || fim != null)) {
            throw new IllegalArgumentException("Execucao pendente nao possui inicio nem fim");
        }
        if (this == EM_ANDAMENTO && (inicio == null || fim != null)) {
            throw new IllegalArgumentException("Execucao em andamento exige inicio e nenhum fim");
        }
        if (this == CONCLUIDA && (inicio == null || fim == null)) {
            throw new IllegalArgumentException("Execucao concluida exige inicio e fim");
        }
        if ((this == CANCELADA || this == INTERROMPIDA) && fim == null) {
            throw new IllegalArgumentException("Execucao encerrada exige fim");
        }
        if (inicio != null && fim != null && fim.isBefore(inicio)) {
            throw new IllegalArgumentException("Fim nao pode anteceder inicio");
        }
    }
}
