package br.gov.agu.virgo_back.movimentacao.persistence;

import br.gov.agu.virgo_back.movimentacao.domain.MovimentacaoDetectada;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcHistoricoMovimentacaoRepo implements HistoricoMovimentacaoRepository {
    private final JdbcClient jdbc;

    public JdbcHistoricoMovimentacaoRepo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<IdentificadorMovimento> buscarIdsConhecidos(UUID processoId, GrauJurisdicao grau) {
        return Set.copyOf(jdbc.sql("""
                SELECT id_movimentacao FROM movimentacao_detectada
                WHERE processo_id = :processo AND grau = :grau
                """)
                .param("processo", processoId).param("grau", grau.name())
                .query((rs, linha) -> new IdentificadorMovimento(rs.getString("id_movimentacao"))).list());
    }

    @Override
    public boolean inserirSeAusente(MovimentacaoDetectada deteccao) {
        var movimento = deteccao.movimentacao();
        return jdbc.sql("""
                INSERT INTO movimentacao_detectada
                    (processo_id, grau, id_movimentacao, tribunal, data_hora, tipo, codigo,
                     descricao, primeira_deteccao, consulta_grau_id)
                VALUES (:processo, :grau, :movimento, :tribunal, :data, :tipo, :codigo,
                        :descricao, :instante, :consulta)
                ON CONFLICT (processo_id, grau, id_movimentacao) DO NOTHING
                """)
                .param("processo", deteccao.processoMonitoradoId())
                .param("grau", deteccao.origem().grau().name())
                .param("movimento", movimento.id().valor())
                .param("tribunal", deteccao.origem().tribunal().name())
                .param("data", movimento.dataHora())
                .param("tipo", movimento.tipo().name()).param("codigo", movimento.codigo())
                .param("descricao", movimento.descricao())
                .param("instante", deteccao.primeiraDeteccao().atOffset(ZoneOffset.UTC))
                .param("consulta", deteccao.consultaGrauId()).update() == 1;
    }
}
