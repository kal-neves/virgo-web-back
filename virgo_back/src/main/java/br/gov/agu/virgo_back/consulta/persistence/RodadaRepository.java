package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.Rodada;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * operações de ciclo de vida conferem proprietário e executor persistidos
 * o chamador continua responsável pela autenticação e pela autorização de acesso
 * retornam false quando a rodada não pertence às identidades ou a transição não é permitida
 * repetir um encerramento no mesmo estado retorna true sem alterar a data final
 */
public interface RodadaRepository {
    /**
     * cria uma rodada pendente com seus itens, graus e endpoints na mesma transação
     * uma rodada existente não pode ser sobrescrita
     */
    void criar(Rodada rodada, Map<OrigemPje, URI> endpoints);

    /** busca a rodada do proprietário com seus itens e resultados atuais */
    Optional<Rodada> buscarPorId(IdConsultante proprietario, UUID id);

    /** inicia uma rodada pendente se não houver pedido de cancelamento */
    boolean iniciar(IdConsultante proprietario, IdConsultante executor, UUID id, Instant inicio);

    /** solicita cancelamento sem interromper uma chamada já enviada */
    boolean solicitarCancelamento(IdConsultante proprietario, IdConsultante executor, UUID id);

    /** consulta o pedido persistido de cancelamento da rodada */
    boolean cancelamentoSolicitado(IdConsultante proprietario, IdConsultante executor, UUID id);

    /** conclui quando todos os graus terminaram e não há cancelamento solicitado */
    boolean concluir(IdConsultante proprietario, IdConsultante executor, UUID id, Instant fim);

    /** confirma o cancelamento solicitado quando não há grau em andamento */
    boolean cancelar(IdConsultante proprietario, IdConsultante executor, UUID id, Instant fim);

    /**
     * encerra a rodada e os graus incompletos preservando resultados concluídos
     * o chamador deve garantir que o trabalhador parou e não há chamada ao PJe em curso
     */
    boolean interromper(IdConsultante proprietario, IdConsultante executor, UUID id, Instant fim);
}
