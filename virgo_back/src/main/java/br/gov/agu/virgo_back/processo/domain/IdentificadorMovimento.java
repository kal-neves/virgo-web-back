package br.gov.agu.virgo_back.processo.domain;

import java.util.Objects;

public record IdentificadorMovimento(String valor) {
    public IdentificadorMovimento {
        Objects.requireNonNull(valor, "O identificador da movimentação é obrigatório");
        if (valor.isBlank()) {
            throw new IllegalArgumentException("O identificador da movimentação não pode ser vazio");
        }
    }
}
