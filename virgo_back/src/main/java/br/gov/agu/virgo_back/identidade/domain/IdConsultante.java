package br.gov.agu.virgo_back.identidade.domain;

import java.util.Objects;

public record IdConsultante(String valor) {

    public IdConsultante {
        Objects.requireNonNull(valor, "CPF do consultante é obrigatório");

        String normalizado = valor.replaceAll("[.\\s-]", "");

        if (!normalizado.matches("[0-9]{11}")) {
            throw new IllegalArgumentException(
                    "CPF do consultante deve conter 11 dígitos"
            );
        }

        valor = normalizado;
    }
}