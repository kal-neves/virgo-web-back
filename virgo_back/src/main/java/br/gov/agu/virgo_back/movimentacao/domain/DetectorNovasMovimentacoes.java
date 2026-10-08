package br.gov.agu.virgo_back.movimentacao.domain;

import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;
import br.gov.agu.virgo_back.processo.domain.Movimentacao;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;

public class DetectorNovasMovimentacoes {

    public List<Movimentacao> detectar(List<Movimentacao> recebidas, Set<IdentificadorMovimento> idsConhecidos) {
        Objects.requireNonNull(recebidas, "As movimentações recebidas são obrigatórias");
        Objects.requireNonNull(idsConhecidos, "Os IDs conhecidos são obrigatórios");

        var idsVistos = new HashSet<>(idsConhecidos);
        var novasMovimentacoes = new ArrayList<Movimentacao>();
        for (Movimentacao movimento : recebidas) {
            if (idsVistos.add(movimento.id())) {
                novasMovimentacoes.add(movimento);
            }
        }
        return List.copyOf(novasMovimentacoes);
    }

}
