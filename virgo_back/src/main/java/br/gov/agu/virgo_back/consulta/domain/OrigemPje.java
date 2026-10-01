package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.Tribunal;

import java.util.Objects;

public record OrigemPje(Tribunal tribunal, GrauJurisdicao grau) {
    public OrigemPje {
        Objects.requireNonNull(tribunal, "Tribunal não pode ser nulo");
        Objects.requireNonNull(grau, "Grau de jurisdição não pode ser nulo");
    }
}
