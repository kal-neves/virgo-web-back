package br.gov.agu.virgo_back.processo.domain;

import java.time.LocalDate;
import java.util.Objects;

public record PeriodoConsulta(LocalDate inicio, LocalDate fim) {

    public PeriodoConsulta {
        Objects.requireNonNull(inicio, "A data inicial é obrigatória");
        Objects.requireNonNull(fim, "A data final é obrigatória");
        if (inicio.isAfter(fim)) {
            throw new IllegalArgumentException("A data inicial não pode ser posterior à data final");
        }
    }
}
