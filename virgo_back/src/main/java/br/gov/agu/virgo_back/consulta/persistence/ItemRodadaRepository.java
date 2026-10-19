package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.ItemRodada;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRodadaRepository {
    List<ItemRodada> buscarPorRodada(IdConsultante proprietario, UUID rodadaId);

    Optional<ItemRodada> buscarPorId(IdConsultante proprietario, UUID id);
}
