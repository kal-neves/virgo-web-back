package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.Rodada;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RodadaRepository {
    void criar(Rodada rodada, Map<OrigemPje, URI> endpoints);

    Optional<Rodada> buscarPorId(IdConsultante proprietario, UUID id);
}
