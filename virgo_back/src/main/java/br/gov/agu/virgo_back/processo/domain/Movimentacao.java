package br.gov.agu.virgo_back.processo.domain;

import java.time.LocalDateTime;
import java.util.Objects;

public record Movimentacao(IdentificadorMovimento id, LocalDateTime dataHora,
                           TipoMovimentacao tipo, int codigo, String descricao) {
    public Movimentacao {
        Objects.requireNonNull(id, "O identificador é obrigatório");
        Objects.requireNonNull(dataHora, "A data/hora é obrigatória");
        Objects.requireNonNull(tipo, "O tipo da movimentação é obrigatório");
        Objects.requireNonNull(descricao, "A descrição é obrigatória");
    }
}
