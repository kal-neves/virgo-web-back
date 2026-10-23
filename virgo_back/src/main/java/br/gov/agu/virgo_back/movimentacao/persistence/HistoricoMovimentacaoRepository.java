package br.gov.agu.virgo_back.movimentacao.persistence;

import br.gov.agu.virgo_back.movimentacao.domain.MovimentacaoDetectada;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;

import java.util.Set;
import java.util.UUID;

public interface HistoricoMovimentacaoRepository {
    /** busca os IDs de movimentações já detectadas para o processo e grau */
    Set<IdentificadorMovimento> buscarIdsConhecidos(UUID processoId, GrauJurisdicao grau);

    /**
     * salva a detecção se ainda não existir para o processo, grau e ID da movimentação
     * retorna false se já existir, preservando a primeira detecção
     */
    boolean inserirSeAusente(MovimentacaoDetectada deteccao);
}
