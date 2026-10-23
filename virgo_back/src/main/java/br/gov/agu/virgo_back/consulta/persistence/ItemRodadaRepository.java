package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.ItemRodada;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRodadaRepository {
    /** busca os itens da rodada do proprietário, ordenados pela posição e com suas consultas */
    List<ItemRodada> buscarPorRodada(IdConsultante proprietario, UUID rodadaId);

    /** busca um item do proprietário com suas consultas e resultados atuais */
    Optional<ItemRodada> buscarPorId(IdConsultante proprietario, UUID id);
}
