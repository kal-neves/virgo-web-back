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
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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

    /**
     * salva a rodada, seus itens e os dois graus de cada item na mesma transação
     * se alguma gravação falhar, a criação inteira é desfeita
     * usa a transação do chamador ou abre uma nova quando não houver uma
     */
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

    /**
     * inicia uma rodada pendente dentro da transação do chamador ou de uma nova transação
     * usa o mesmo bloqueio dos graus para impedir que início e cancelamento se sobreponham
     */
    @Override
    @Transactional
    public boolean iniciar(IdConsultante proprietario, IdConsultante executor, UUID id, Instant inicio) {
        var instante = instanteBanco(inicio);
        if (buscarEBloquear(proprietario, executor, id).isEmpty()) return false;
        return jdbc.sql("""
                UPDATE rodada SET estado = 'EM_ANDAMENTO', inicio = :inicio
                WHERE id = :id AND estado = 'PENDENTE' AND NOT cancelamento_solicitado
                """).param("inicio", instante).param("id", id).update() == 1;
    }

    /**
     * salva o pedido de cancelamento sem alterar uma chamada em andamento
     * mantém o bloqueio da rodada até a transação terminar
     * repetir o pedido não muda o resultado de um cancelamento já solicitado
     */
    @Override
    @Transactional
    public boolean solicitarCancelamento(IdConsultante proprietario, IdConsultante executor, UUID id) {
        if (buscarEBloquear(proprietario, executor, id).isEmpty()) return false;
        return jdbc.sql("""
                UPDATE rodada SET cancelamento_solicitado = true
                WHERE id = :id AND estado IN ('PENDENTE', 'EM_ANDAMENTO', 'CANCELADA')
                """).param("id", id).update() == 1;
    }

    @Override
    public boolean cancelamentoSolicitado(IdConsultante proprietario, IdConsultante executor, UUID id) {
        validarIdentidade(proprietario, executor, id);
        return jdbc.sql("""
                SELECT cancelamento_solicitado FROM rodada
                WHERE id = :id AND proprietario_id = :proprietario AND executor_id = :executor
                """).param("id", id).param("proprietario", proprietario.valor()).param("executor", executor.valor())
                .query(Boolean.class).optional().orElse(false);
    }

    /**
     * confirma o término da rodada apenas quando todos os graus estão concluídos
     * verifica o cancelamento sob o mesmo bloqueio para respeitar o pedido que chegar primeiro
     */
    @Override
    @Transactional
    public boolean concluir(IdConsultante proprietario, IdConsultante executor, UUID id, Instant fim) {
        return encerrar(proprietario, executor, id, fim, EstadoExecucao.CONCLUIDA);
    }

    /**
     * confirma o cancelamento e marca os graus restantes na mesma transação
     * retorna false enquanto houver grau em andamento e preserva as tentativas concluídas
     */
    @Override
    @Transactional
    public boolean cancelar(IdConsultante proprietario, IdConsultante executor, UUID id, Instant fim) {
        return encerrar(proprietario, executor, id, fim, EstadoExecucao.CANCELADA);
    }

    /**
     * marca a rodada e os graus incompletos como interrompidos na mesma transação
     * deve ser chamado somente depois que o trabalhador parar
     * preserva resultados concluídos e não inventa respostas para chamadas sem resultado
     */
    @Override
    @Transactional
    public boolean interromper(IdConsultante proprietario, IdConsultante executor, UUID id, Instant fim) {
        return encerrar(proprietario, executor, id, fim, EstadoExecucao.INTERROMPIDA);
    }

    private boolean encerrar(IdConsultante proprietario, IdConsultante executor, UUID id,
                             Instant fim, EstadoExecucao destino) {
        var instante = instanteBanco(fim);
        if (buscarEBloquear(proprietario, executor, id).isEmpty()) return false;
        var estado = jdbc.sql("SELECT estado FROM rodada WHERE id = ?").param(id).query(String.class).single();
        if (estado.equals(destino.name())) return true;
        if (!estado.equals("PENDENTE") && !estado.equals("EM_ANDAMENTO")) return false;
        boolean permitido = jdbc.sql("""
                SELECT (inicio IS NULL OR :fim >= inicio)
                    AND NOT EXISTS (SELECT 1 FROM consulta_grau c JOIN item_rodada i ON i.id = c.item_rodada_id
                        WHERE i.rodada_id = r.id AND (c.inicio > :fim OR c.fim > :fim))
                    AND CASE :destino
                        WHEN 'CONCLUIDA' THEN r.estado = 'EM_ANDAMENTO' AND NOT r.cancelamento_solicitado
                            AND NOT EXISTS (SELECT 1 FROM consulta_grau c JOIN item_rodada i ON i.id = c.item_rodada_id
                                WHERE i.rodada_id = r.id AND c.estado <> 'CONCLUIDA')
                        WHEN 'CANCELADA' THEN r.cancelamento_solicitado
                            AND NOT EXISTS (SELECT 1 FROM consulta_grau c JOIN item_rodada i ON i.id = c.item_rodada_id
                                WHERE i.rodada_id = r.id AND c.estado = 'EM_ANDAMENTO')
                        ELSE true END
                FROM rodada r WHERE r.id = :id
                """).param("fim", instante).param("destino", destino.name()).param("id", id)
                .query(Boolean.class).single();
        if (!permitido) return false;
        if (destino != EstadoExecucao.CONCLUIDA) {
            jdbc.sql("""
                    UPDATE consulta_grau c SET estado = :estado, fim = :fim
                    FROM item_rodada i WHERE c.item_rodada_id = i.id AND i.rodada_id = :id
                        AND c.estado <> 'CONCLUIDA'
                    """).param("estado", destino.name()).param("fim", instante).param("id", id).update();
        }
        jdbc.sql("UPDATE rodada SET estado = :estado, fim = :fim WHERE id = :id")
                .param("estado", destino.name()).param("fim", instante).param("id", id).update();
        return true;
    }

    private Optional<UUID> buscarEBloquear(IdConsultante proprietario, IdConsultante executor, UUID id) {
        validarIdentidade(proprietario, executor, id);
        return jdbc.sql("""
                SELECT id FROM rodada WHERE id = :id AND proprietario_id = :proprietario
                    AND executor_id = :executor FOR UPDATE
                """).param("id", id).param("proprietario", proprietario.valor()).param("executor", executor.valor())
                .query(UUID.class).optional();
    }

    private static void validarIdentidade(IdConsultante proprietario, IdConsultante executor, UUID id) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(id);
    }

    private static OffsetDateTime instanteBanco(Instant instante) {
        return Objects.requireNonNull(instante).truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    /**
     * busca a rodada do proprietário com seus itens e resultados sem alterar os dados
     * ao abrir uma transação, mantém a mesma visão dos dados durante as leituras
     * se o chamador já tiver uma transação, usa as configurações dela
     */
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
