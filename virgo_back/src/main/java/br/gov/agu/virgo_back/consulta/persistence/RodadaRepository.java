package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.Rodada;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;

import java.net.URI;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface RodadaRepository {
    /**
     * cria uma rodada pendente com seus itens, graus e endpoints na mesma transação
     * uma rodada existente não pode ser sobrescrita
     */
    void criar(Rodada rodada, Map<OrigemPje, URI> endpoints);

    /** busca a rodada do proprietário com seus itens e resultados atuais */
    Optional<Rodada> buscarPorId(IdConsultante proprietario, UUID id);
}
