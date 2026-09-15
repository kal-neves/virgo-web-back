package br.gov.agu.virgo_back.processo.domain;
import java.util.List;

public record RespostaConsultaOrigem(OrigemPje origem, StatusConsulta status, List<Movimentacao> movimentacoes, String erro) {

    public RespostaConsultaOrigem {
        movimentacoes = movimentacoes == null ? List.of() : List.copyOf(movimentacoes);
    }
}
