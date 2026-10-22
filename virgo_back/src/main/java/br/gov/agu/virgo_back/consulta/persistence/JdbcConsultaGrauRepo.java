package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Repository
public class JdbcConsultaGrauRepo implements ConsultaGrauRepository {
    private final JdbcClient jdbc;

    public JdbcConsultaGrauRepo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(UUID id, UUID processoId, RespostaConsultaOrigem resposta, Instant instante) {
        jdbc.sql("""
                INSERT INTO tentativa_consulta_grau (id, processo_id, tribunal, grau, status, erro, registrada_em)
                VALUES (:id, :processo, :tribunal, :grau, :status, :erro, :instante)
                """)
                .param("id", id).param("processo", processoId)
                .param("tribunal", resposta.origem().tribunal().name())
                .param("grau", resposta.origem().grau().name())
                .param("status", resposta.status().name())
                .param("erro", resposta.erro(), Types.VARCHAR)
                .param("instante", instante.atOffset(ZoneOffset.UTC)).update();
    }

    @Override
    public void registrarQuantidadeDetectada(UUID id, int quantidade) {
        int alterados = jdbc.sql("UPDATE tentativa_consulta_grau SET novas_movimentacoes = :quantidade WHERE id = :id AND consulta_planejada_id IS NULL")
                .param("quantidade", quantidade).param("id", id).update();
        if (alterados != 1) throw new IllegalStateException("Consulta de grau inexistente");
    }

    @Override
    @Transactional
    public boolean iniciar(IdConsultante proprietario, UUID consultaId, Instant inicio) {
        Objects.requireNonNull(inicio);
        var rodadaId = bloquearRodada(proprietario, consultaId);
        int alterados = jdbc.sql("""
                UPDATE consulta_grau c SET estado = 'EM_ANDAMENTO', inicio = :inicio,
                    fim = NULL, tentativa_atual_id = NULL
                FROM item_rodada i, rodada r
                WHERE c.id = :consulta AND c.item_rodada_id = i.id AND i.rodada_id = r.id
                  AND r.id = :rodada AND r.estado = 'EM_ANDAMENTO' AND NOT r.cancelamento_solicitado
                  AND :inicio >= r.inicio
                  AND (c.inicio IS NULL OR :inicio > c.inicio)
                  AND (c.fim IS NULL OR :inicio >= c.fim)
                  AND c.estado IN ('PENDENTE', 'CANCELADA', 'INTERROMPIDA', 'CONCLUIDA')
                  AND NOT EXISTS (SELECT 1 FROM tentativa_consulta_grau t
                      WHERE t.consulta_planejada_id = c.id AND t.status = 'ENCONTRADO')
                  AND NOT EXISTS (SELECT 1 FROM consulta_grau ativa JOIN item_rodada ai ON ai.id = ativa.item_rodada_id
                      WHERE ai.rodada_id = r.id AND ativa.estado = 'EM_ANDAMENTO')
                """)
                .param("inicio", micros(inicio).atOffset(ZoneOffset.UTC))
                .param("consulta", consultaId).param("rodada", rodadaId).update();
        return alterados == 1;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarTentativa(IdConsultante proprietario, UUID consultaId, UUID tentativaId,
                                   Instant inicioEsperado, Instant fim, RespostaConsultaOrigem resposta) {
        Objects.requireNonNull(tentativaId);
        Objects.requireNonNull(inicioEsperado);
        Objects.requireNonNull(fim);
        Objects.requireNonNull(resposta);
        if (fim.isBefore(inicioEsperado)) {
            throw new IllegalArgumentException("Periodo invalido");
        }
        boolean sucesso = resposta.status() == StatusConsulta.ENCONTRADO;
        if (sucesso ? resposta.erro() != null : resposta.erro() == null || resposta.erro().isBlank()
                || !resposta.movimentacoes().isEmpty()) {
            throw new IllegalArgumentException("Resposta inconsistente");
        }
        bloquearRodada(proprietario, consultaId);
        int inseridos = jdbc.sql("""
                INSERT INTO tentativa_consulta_grau (id, processo_id, tribunal, grau, status, erro,
                    registrada_em, novas_movimentacoes, movimentacoes_exibidas, consulta_planejada_id, numero_tentativa)
                SELECT :tentativa, c.processo_id, c.tribunal, c.grau, :status, :erro,
                    :fim, 0, NULL, c.id,
                    (SELECT COALESCE(MAX(t.numero_tentativa), 0) + 1 FROM tentativa_consulta_grau t
                     WHERE t.consulta_planejada_id = c.id)
                FROM consulta_grau c
                WHERE c.id = :consulta AND c.estado = 'EM_ANDAMENTO' AND c.inicio = :inicio
                    AND c.tribunal = :tribunal AND c.grau = :grau
                    AND NOT EXISTS (SELECT 1 FROM tentativa_consulta_grau aberta
                        WHERE aberta.consulta_planejada_id = c.id AND aberta.movimentacoes_exibidas IS NULL)
                """)
                .param("tentativa", tentativaId).param("consulta", consultaId)
                .param("status", resposta.status().name()).param("erro", resposta.erro(), Types.VARCHAR)
                .param("fim", micros(fim).atOffset(ZoneOffset.UTC))
                .param("inicio", micros(inicioEsperado).atOffset(ZoneOffset.UTC))
                .param("tribunal", resposta.origem().tribunal().name()).param("grau", resposta.origem().grau().name()).update();
        if (inseridos != 1) throw new IllegalStateException("Consulta nao esta em execucao ou origem/inicio divergente");
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void concluir(IdConsultante proprietario, UUID consultaId, UUID tentativaId, int detectadas, int exibidas) {
        Objects.requireNonNull(tentativaId);
        if (detectadas < 0 || exibidas < 0 || exibidas > detectadas) {
            throw new IllegalArgumentException("Contagens invalidas");
        }
        bloquearRodada(proprietario, consultaId);
        int alterados = jdbc.sql("""
                UPDATE tentativa_consulta_grau t SET novas_movimentacoes = :detectadas, movimentacoes_exibidas = :exibidas
                FROM consulta_grau c
                WHERE t.id = :tentativa AND t.consulta_planejada_id = c.id AND c.id = :consulta
                  AND c.estado = 'EM_ANDAMENTO' AND t.movimentacoes_exibidas IS NULL
                  AND t.registrada_em >= c.inicio
                """).param("tentativa", tentativaId).param("consulta", consultaId)
                .param("detectadas", detectadas).param("exibidas", exibidas).update();
        if (alterados != 1) throw new IllegalStateException("Tentativa inexistente ou ja finalizada");
        jdbc.sql("""
                UPDATE consulta_grau c SET estado = 'CONCLUIDA', fim = t.registrada_em, tentativa_atual_id = t.id
                FROM tentativa_consulta_grau t WHERE c.id = :consulta AND t.id = :tentativa
                """)
                .param("tentativa", tentativaId).param("consulta", consultaId).update();
    }

    @Override
    public List<Tentativa> buscarTentativas(IdConsultante proprietario, UUID consultaId) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(consultaId);
        return jdbc.sql("""
                SELECT t.* FROM tentativa_consulta_grau t
                JOIN consulta_grau c ON c.id = t.consulta_planejada_id
                JOIN item_rodada i ON i.id = c.item_rodada_id
                WHERE c.id = :consulta AND i.proprietario_id = :proprietario ORDER BY t.numero_tentativa
                """).param("consulta", consultaId).param("proprietario", proprietario.valor())
                .query((rs, row) -> new Tentativa(rs.getObject("id", UUID.class), rs.getInt("numero_tentativa"),
                        rs.getObject("registrada_em", OffsetDateTime.class).toInstant(),
                        StatusConsulta.valueOf(rs.getString("status")), rs.getString("erro"),
                        rs.getInt("novas_movimentacoes"), rs.getObject("movimentacoes_exibidas", Integer.class))).list();
    }

    private UUID bloquearRodada(IdConsultante proprietario, UUID consultaId) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(consultaId);
        return jdbc.sql("""
                SELECT r.id FROM rodada r JOIN item_rodada i ON i.rodada_id = r.id
                JOIN consulta_grau c ON c.item_rodada_id = i.id
                WHERE c.id = :consulta AND r.proprietario_id = :proprietario FOR UPDATE OF r
                """).param("consulta", consultaId).param("proprietario", proprietario.valor())
                .query(UUID.class).optional().orElseThrow(() -> new IllegalArgumentException("Consulta inexistente para o proprietario"));
    }

    private static Instant micros(Instant instante) {
        return instante.truncatedTo(ChronoUnit.MICROS);
    }
}
