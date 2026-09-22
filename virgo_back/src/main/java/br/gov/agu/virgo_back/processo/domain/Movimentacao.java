package br.gov.agu.virgo_back.processo.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record Movimentacao (LocalDateTime dataHora, String descricao) {
    public Movimentacao {
        Objects.requireNonNull(dataHora, "A data/hora é obrigatória");
        Objects.requireNonNull(descricao, "A descrição é obrigatória");
    }
}
