package br.gov.agu.virgo_back.identidade.application;


import br.gov.agu.virgo_back.identidade.domain.IdConsultante;

import java.util.Objects;

public record CredenciaisPje(String senha, IdConsultante login) {

    public CredenciaisPje {
        Objects.requireNonNull(login, "Identificação do consultante é obrigatória");
        Objects.requireNonNull(senha, "Senha é obrigatória");
        if (senha.isBlank()) {
            throw new IllegalArgumentException("Senha não pode estar em branco");
        }
    }

    @Override
    public String toString() {
        return "CredenciaisPje[idConsultante=" + login + "]";
    }
}
