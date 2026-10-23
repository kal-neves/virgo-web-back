package br.gov.agu.virgo_back.processo.persistence;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.processo.domain.ProcessoMonitorado;

import java.util.Optional;
import java.util.UUID;

public interface ProcessoMonitoradoRepository {

    /**
     * salva um novo processo ou atualiza seus metadados e graus observados
     * o proprietário e o número de um processo existente não podem ser alterados
     */
    void salvar(ProcessoMonitorado processo);

    /** busca um processo pelo ID dentro do histórico do proprietário */
    Optional<ProcessoMonitorado> buscarPorId(IdConsultante proprietario, UUID id);

    /** busca um processo pelo número CNJ dentro do histórico do proprietário */
    Optional<ProcessoMonitorado> buscarPorNumero(IdConsultante proprietario, NumeroProcesso numero);
}
