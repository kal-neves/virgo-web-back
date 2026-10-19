package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@Repository
public class JdbcItemRodadaRepo implements ItemRodadaRepository {
    private final JdbcClient jdbc;

    public JdbcItemRodadaRepo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ItemRodada> buscarPorRodada(IdConsultante proprietario, UUID rodadaId) {
        return buscar(proprietario, "i.rodada_id", rodadaId);
    }

    @Override
    public Optional<ItemRodada> buscarPorId(IdConsultante proprietario, UUID id) {
        return buscar(proprietario, "i.id", id).stream().findFirst();
    }

    private List<ItemRodada> buscar(IdConsultante proprietario, String coluna, UUID id) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(id);
        // A coluna vem exclusivamente dos dois metodos acima, nunca da entrada do usuario.
        var linhas = jdbc.sql("""
                SELECT i.id AS item_id, i.rodada_id, i.processo_id, i.numero_cnj, i.ordem,
                       c.id AS consulta_id, c.tribunal, c.grau, c.estado, c.inicio, c.fim,
                       t.status, t.erro, t.novas_movimentacoes, t.movimentacoes_exibidas
                FROM item_rodada i
                JOIN consulta_grau c ON c.item_rodada_id = i.id
                LEFT JOIN tentativa_consulta_grau t ON t.id = c.tentativa_atual_id
                WHERE i.proprietario_id = :proprietario AND
                """ + coluna + " = :id ORDER BY i.ordem, c.grau")
                .param("proprietario", proprietario.valor()).param("id", id)
                .query((rs, row) -> mapear(rs)).list();
        var grupos = new LinkedHashMap<UUID, List<Linha>>();
        for (var linha : linhas) grupos.computeIfAbsent(linha.itemId(), chave -> new ArrayList<>()).add(linha);
        return grupos.values().stream().map(grupo -> {
            var primeira = grupo.getFirst();
            return new ItemRodada(primeira.itemId(), primeira.rodadaId(), primeira.processoId(),
                    primeira.numero(), primeira.ordem(), grupo.stream().map(Linha::consulta).toList());
        }).toList();
    }

    private static Linha mapear(ResultSet rs) throws SQLException {
        var itemId = rs.getObject("item_id", UUID.class);
        var inicio = rs.getObject("inicio", OffsetDateTime.class);
        var fim = rs.getObject("fim", OffsetDateTime.class);
        var status = rs.getString("status");
        var consulta = new ConsultaGrau(rs.getObject("consulta_id", UUID.class), itemId,
                new OrigemPje(Tribunal.valueOf(rs.getString("tribunal")), GrauJurisdicao.valueOf(rs.getString("grau"))),
                EstadoExecucao.valueOf(rs.getString("estado")),
                inicio == null ? null : inicio.toInstant(), fim == null ? null : fim.toInstant(),
                status == null ? null : StatusConsulta.valueOf(status), rs.getString("erro"),
                rs.getInt("novas_movimentacoes"), rs.getInt("movimentacoes_exibidas"));
        return new Linha(itemId, rs.getObject("rodada_id", UUID.class), rs.getObject("processo_id", UUID.class),
                new NumeroProcesso(rs.getString("numero_cnj")), rs.getInt("ordem"), consulta);
    }

    private record Linha(UUID itemId, UUID rodadaId, UUID processoId, NumeroProcesso numero,
                         int ordem, ConsultaGrau consulta) {}
}
