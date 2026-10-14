package br.gov.agu.virgo_back.movimentacao.persistence;

import br.gov.agu.virgo_back.movimentacao.domain.MovimentacaoDetectada;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;

import java.util.Set;
import java.util.UUID;

public interface HistoricoMovimentacaoRepository {
    Set<IdentificadorMovimento> buscarIdsConhecidos(UUID processoId, GrauJurisdicao grau);

    boolean inserirSeAusente(MovimentacaoDetectada deteccao);
}
