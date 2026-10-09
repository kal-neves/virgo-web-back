package br.gov.agu.virgo_back.processo.persistence;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.processo.domain.ProcessoMonitorado;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcProcessoMonitoradoRepo implements ProcessoMonitoradoRepository {
    private final JdbcClient jdbc;

    public JdbcProcessoMonitoradoRepo(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void salvar(ProcessoMonitorado processo) {
        int alterados = jdbc.sql("""
                INSERT INTO processo_monitorado
                    (id, proprietario_id, numero_cnj, tribunal_confirmado,
                     primeiro_grau_observado, segundo_grau_observado, anotacao, atencao)
                VALUES (:id, :proprietario, :numero, :tribunal, :primeiro, :segundo, :anotacao, :atencao)
                ON CONFLICT (id) DO UPDATE SET
                    tribunal_confirmado = EXCLUDED.tribunal_confirmado,
                    primeiro_grau_observado = EXCLUDED.primeiro_grau_observado,
                    segundo_grau_observado = EXCLUDED.segundo_grau_observado,
                    anotacao = EXCLUDED.anotacao,
                    atencao = EXCLUDED.atencao
                WHERE processo_monitorado.proprietario_id = EXCLUDED.proprietario_id
                  AND processo_monitorado.numero_cnj = EXCLUDED.numero_cnj
                """)
                .param("id", processo.id())
                .param("proprietario", processo.proprietario().valor())
                .param("numero", processo.numeroProcesso().valor())
                .param("tribunal", processo.tribunalConfirmado() == null
                        ? null : processo.tribunalConfirmado().name(), Types.VARCHAR)
                .param("primeiro", processo.grausObservados().contains(GrauJurisdicao.PRIMEIRO_GRAU))
                .param("segundo", processo.grausObservados().contains(GrauJurisdicao.SEGUNDO_GRAU))
                .param("anotacao", processo.anotacao())
                .param("atencao", processo.atencao())
                .update();
        if (alterados != 1) {
            throw new DataIntegrityViolationException("Nao e permitido alterar o proprietario ou CNJ do processo");
        }
    }

    @Override
    public Optional<ProcessoMonitorado> buscarPorId(IdConsultante proprietario, UUID id) {
        return jdbc.sql("SELECT * FROM processo_monitorado WHERE proprietario_id = :proprietario AND id = :id")
                .param("proprietario", proprietario.valor())
                .param("id", id)
                .query(JdbcProcessoMonitoradoRepo::mapear)
                .optional();
    }

    @Override
    public Optional<ProcessoMonitorado> buscarPorNumero(IdConsultante proprietario, NumeroProcesso numero) {
        return jdbc.sql("SELECT * FROM processo_monitorado WHERE proprietario_id = :proprietario AND numero_cnj = :numero")
                .param("proprietario", proprietario.valor())
                .param("numero", numero.valor())
                .query(JdbcProcessoMonitoradoRepo::mapear)
                .optional();
    }

    private static ProcessoMonitorado mapear(ResultSet rs, int linha) throws SQLException {
        var graus = EnumSet.noneOf(GrauJurisdicao.class);
        if (rs.getBoolean("primeiro_grau_observado")) graus.add(GrauJurisdicao.PRIMEIRO_GRAU);
        if (rs.getBoolean("segundo_grau_observado")) graus.add(GrauJurisdicao.SEGUNDO_GRAU);
        String tribunal = rs.getString("tribunal_confirmado");
        return new ProcessoMonitorado(
                rs.getObject("id", UUID.class),
                new IdConsultante(rs.getString("proprietario_id")),
                new NumeroProcesso(rs.getString("numero_cnj")),
                tribunal == null ? null : Tribunal.valueOf(tribunal),
                graus, rs.getString("anotacao"), rs.getBoolean("atencao"));
    }
}
