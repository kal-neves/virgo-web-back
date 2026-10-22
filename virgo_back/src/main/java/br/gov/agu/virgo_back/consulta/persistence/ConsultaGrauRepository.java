package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.List;

public interface ConsultaGrauRepository {
    /** caminho legado */
    void registrar(UUID id, UUID processoId, RespostaConsultaOrigem resposta, Instant instante);

    /** atualiza a quantidade detectada apenas em tentativas que não são parte de uma consultação planejada */
    void registrarQuantidadeDetectada(UUID id, int quantidade);

    /** inicia consulta quando solicitado e retorna false se não puder iniciar */
    boolean iniciar(IdConsultante proprietario, UUID consultaId, Instant inicio);

    /**
     * registra uma tentativa dentro de uma transação
     * tentativa, movimentações e conclusão devem ser salvas juntas
     * inicioEsperado = início da execução, em microssegundos
     */
    void registrarTentativa(IdConsultante proprietario, UUID consultaId, UUID tentativaId,
                            Instant inicioEsperado, Instant fim, RespostaConsultaOrigem resposta);

    /** salva as contagens e define a tentativa atual
     * uma tentativa finalizada não pode ser alterada */
    void concluir(IdConsultante proprietario, UUID consultaId, UUID tentativaId, int detectadas, int exibidas);

    List<Tentativa> buscarTentativas(IdConsultante proprietario, UUID consultaId);

    record Tentativa(UUID id, int numero, Instant registradaEm, StatusConsulta status,
                     @Nullable String erro, int detectadas, @Nullable Integer exibidas) {}
}
