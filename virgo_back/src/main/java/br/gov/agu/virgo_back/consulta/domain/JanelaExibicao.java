package br.gov.agu.virgo_back.consulta.domain;

public record JanelaExibicao (int dias) {

    public JanelaExibicao {
        if (dias < 0) {
            throw new IllegalArgumentException("Valor dias não pode ser negativo");
        }
    }
}