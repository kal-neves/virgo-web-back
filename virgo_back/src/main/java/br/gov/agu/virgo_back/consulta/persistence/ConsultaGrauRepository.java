package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;

import java.time.Instant;
import java.util.UUID;

public interface ConsultaGrauRepository {
    void registrar(UUID id, UUID processoId, RespostaConsultaOrigem resposta, Instant instante);

    void registrarQuantidadeDetectada(UUID id, int quantidade);
}
