package br.gov.agu.virgo_back.identidade.session;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SessaoVirgo(UUID id, IdConsultante usuario, Instant ultimaAtividade, int consultasAtivas) {
    public SessaoVirgo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(ultimaAtividade);
        if (consultasAtivas < 0) throw new IllegalArgumentException("Contagem de consultas invalida");
    }

    @Override
    public @NonNull String toString() {
        return "SessaoVirgo[consultasAtivas=" + consultasAtivas + "]";
    }
}
