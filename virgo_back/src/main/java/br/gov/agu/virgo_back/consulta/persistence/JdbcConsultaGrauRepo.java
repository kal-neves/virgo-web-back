package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
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
        int alterados = jdbc.sql("UPDATE tentativa_consulta_grau SET novas_movimentacoes = :quantidade WHERE id = :id")
                .param("quantidade", quantidade).param("id", id).update();
        if (alterados != 1) throw new IllegalStateException("Consulta de grau inexistente");
    }
}
