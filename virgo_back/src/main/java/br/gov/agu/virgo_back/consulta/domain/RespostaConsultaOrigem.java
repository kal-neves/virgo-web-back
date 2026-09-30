package br.gov.agu.virgo_back.consulta.domain;
import br.gov.agu.virgo_back.processo.domain.Movimentacao;

import java.util.List;
import java.util.Objects;

public record RespostaConsultaOrigem(OrigemPje origem, StatusConsulta status, List<Movimentacao> movimentacoes, String erro) {

    public RespostaConsultaOrigem {
        Objects.requireNonNull(origem, "Origem da resposta é obrigatória");
        Objects.requireNonNull(status, "Status da consulta é obrigatório");

        movimentacoes = movimentacoes == null ? List.of() : List.copyOf(movimentacoes);
    }
}
