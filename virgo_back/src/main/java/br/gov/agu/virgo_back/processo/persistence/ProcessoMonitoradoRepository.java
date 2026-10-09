package br.gov.agu.virgo_back.processo.persistence;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.processo.domain.ProcessoMonitorado;

import java.util.Optional;
import java.util.UUID;

public interface ProcessoMonitoradoRepository {

    void salvar(ProcessoMonitorado processo);

    Optional<ProcessoMonitorado> buscarPorId(IdConsultante proprietario, UUID id);

    Optional<ProcessoMonitorado> buscarPorNumero(IdConsultante proprietario, NumeroProcesso numero);
}
