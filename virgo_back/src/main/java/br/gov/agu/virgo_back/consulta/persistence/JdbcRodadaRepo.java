package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.sql.Types;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcRodadaRepo implements RodadaRepository {
    private final JdbcClient jdbc;
    private final ItemRodadaRepository itens;

    public JdbcRodadaRepo(JdbcClient jdbc, ItemRodadaRepository itens) {
        this.jdbc = jdbc;
        this.itens = itens;
    }

    @Override
    @Transactional
    public void criar(Rodada rodada, Map<OrigemPje, URI> endpoints) {
        Objects.requireNonNull(rodada);
        var destinos = Map.copyOf(endpoints);
        if (rodada.estado() != EstadoExecucao.PENDENTE) {
            throw new IllegalArgumentException("Criacao exige rodada pendente");
        }
        for (var item : rodada.itens()) {
            for (var consulta : item.consultas()) {
                var endpoint = destinos.get(consulta.origem());
                if (endpoint == null || endpoint.getHost() == null
                        || !("https".equalsIgnoreCase(endpoint.getScheme()) || "http".equalsIgnoreCase(endpoint.getScheme()))
                        || endpoint.getUserInfo() != null || endpoint.getFragment() != null) {
                    throw new IllegalArgumentException("Endpoint HTTP(S) sem credenciais e obrigatorio para cada origem");
                }
            }
        }
        jdbc.sql("""
                INSERT INTO rodada (id, proprietario_id, executor_id, escopo, lista_id, nome_lista,
                                    janela_dias, data_referencia, zona_referencia)
                VALUES (:id, :proprietario, :executor, :escopo, :lista, :nome, :janela, :data, :zona)
                """)
                .param("id", rodada.id()).param("proprietario", rodada.proprietario().valor())
                .param("executor", rodada.executor().valor()).param("escopo", rodada.escopo().name())
                .param("lista", rodada.listaId(), Types.OTHER).param("nome", rodada.nomeLista(), Types.VARCHAR)
                .param("janela", rodada.janela().dias()).param("data", rodada.dataReferencia())
                .param("zona", rodada.zonaReferencia().getId()).update();
        for (var item : rodada.itens()) {

            int inseridos = jdbc.sql("""
                    INSERT INTO item_rodada (id, rodada_id, proprietario_id, processo_id, numero_cnj, ordem)
                    SELECT :id, :rodada, proprietario_id, id, numero_cnj, :ordem
                    FROM processo_monitorado
                    WHERE id = :processo AND proprietario_id = :proprietario AND numero_cnj = :numero
                    """)
                    .param("id", item.id()).param("rodada", rodada.id()).param("ordem", item.ordem())
                    .param("processo", item.processoMonitoradoId()).param("proprietario", rodada.proprietario().valor())
                    .param("numero", item.numeroProcesso().valor()).update();
            if (inseridos != 1) throw new IllegalArgumentException("Processo, proprietario ou CNJ do item divergente");
            for (var consulta : item.consultas()) {
                jdbc.sql("""
                        INSERT INTO consulta_grau (id, item_rodada_id, processo_id, tribunal, grau, endpoint)
                        VALUES (:id, :item, :processo, :tribunal, :grau, :endpoint)
                        """)
                        .param("id", consulta.id()).param("item", item.id()).param("processo", item.processoMonitoradoId())
                        .param("tribunal", consulta.origem().tribunal().name()).param("grau", consulta.origem().grau().name())
                        .param("endpoint", destinos.get(consulta.origem()).toString()).update();
            }
        }
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Rodada> buscarPorId(IdConsultante proprietario, UUID id) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(id);
        return jdbc.sql("SELECT * FROM rodada WHERE id = :id AND proprietario_id = :proprietario")
                .param("id", id).param("proprietario", proprietario.valor())
                .query((rs, row) -> {
                    var inicio = rs.getObject("inicio", OffsetDateTime.class);
                    var fim = rs.getObject("fim", OffsetDateTime.class);
                    return new Rodada(id, proprietario, new IdConsultante(rs.getString("executor_id")),
                            Rodada.Escopo.valueOf(rs.getString("escopo")), rs.getObject("lista_id", UUID.class),
                            rs.getString("nome_lista"), new JanelaExibicao(rs.getInt("janela_dias")),
                            rs.getObject("data_referencia", LocalDate.class), ZoneId.of(rs.getString("zona_referencia")),
                            EstadoExecucao.valueOf(rs.getString("estado")),
                            inicio == null ? null : inicio.toInstant(), fim == null ? null : fim.toInstant(),
                            itens.buscarPorRodada(proprietario, id));
                }).optional();
    }
}
