package br.gov.agu.virgo_back.consulta.persistence;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.*;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
class JdbcRodadaRepoTest {
    private static final IdConsultante DONO = new IdConsultante("66666666666");
    private static final IdConsultante EXECUTOR = new IdConsultante("77777777777");
    private static final Map<OrigemPje, URI> ENDPOINTS = Map.of(
            new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU), URI.create("https://example.test/pje1"),
            new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU), URI.create("https://example.test/pje2"));
    @Autowired RodadaRepository rodadas;
    @Autowired ItemRodadaRepository itens;
    @Autowired ProcessoMonitoradoRepository processos;
    @Autowired JdbcClient jdbc;
    @Autowired ConsultaGrauRepository consultas;
    @Autowired PlatformTransactionManager transactions;

    @BeforeEach
    void preparar() {
        jdbc.sql("INSERT INTO usuario (id_consultante) VALUES (?), (?)")
                .params(DONO.valor(), EXECUTOR.valor()).update();
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM movimentacao_detectada WHERE processo_id IN (SELECT id FROM processo_monitorado WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("UPDATE consulta_grau SET tentativa_atual_id = NULL, estado = 'PENDENTE', inicio = NULL, fim = NULL WHERE item_rodada_id IN (SELECT id FROM item_rodada WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("DELETE FROM tentativa_consulta_grau WHERE processo_id IN (SELECT id FROM processo_monitorado WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("DELETE FROM consulta_grau WHERE item_rodada_id IN (SELECT id FROM item_rodada WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("DELETE FROM item_rodada WHERE proprietario_id = ?").param(DONO.valor()).update();
        jdbc.sql("DELETE FROM rodada WHERE proprietario_id = ?").param(DONO.valor()).update();
        jdbc.sql("DELETE FROM processo_monitorado WHERE proprietario_id IN (?, ?)").params(DONO.valor(), EXECUTOR.valor()).update();
        jdbc.sql("DELETE FROM usuario WHERE id_consultante IN (?, ?)").params(DONO.valor(), EXECUTOR.valor()).update();
    }

    @Test
    void preservaSnapshotOrdenadoEEndpointsComExecutorDistinto() {
        var rodada = rodada(2);
        rodadas.criar(rodada, ENDPOINTS);
        assertEquals(rodada, rodadas.buscarPorId(DONO, rodada.id()).orElseThrow());
        assertEquals(rodada.itens(), itens.buscarPorRodada(DONO, rodada.id()));
        for (var item : rodada.itens()) assertEquals(item, itens.buscarPorId(DONO, item.id()).orElseThrow());
        assertEquals(List.of("https://example.test/pje1", "https://example.test/pje1",
                        "https://example.test/pje2", "https://example.test/pje2"),
                jdbc.sql("SELECT endpoint FROM consulta_grau WHERE item_rodada_id IN (SELECT id FROM item_rodada WHERE rodada_id = ?) ORDER BY endpoint")
                        .param(rodada.id()).query(String.class).list());
    }

    @Test
    void consultaPontualSemListaELeiturasIsoladasPorProprietario() {
        var rodada = rodada(1);
        rodadas.criar(rodada, ENDPOINTS);
        assertEquals(rodada, rodadas.buscarPorId(DONO, rodada.id()).orElseThrow());
        assertTrue(rodadas.buscarPorId(EXECUTOR, rodada.id()).isEmpty());
        assertTrue(itens.buscarPorRodada(EXECUTOR, rodada.id()).isEmpty());
        assertTrue(itens.buscarPorId(EXECUTOR, rodada.itens().getFirst().id()).isEmpty());
        assertTrue(rodadas.buscarPorId(DONO, UUID.randomUUID()).isEmpty());
        assertTrue(itens.buscarPorId(DONO, UUID.randomUUID()).isEmpty());
    }

    @Test
    void falhaNoSegundoItemReverteInclusivePrimeiroItemEGraus() {
        var rodada = rodada(2);
        var segundo = rodada.itens().getLast();
        jdbc.sql("DELETE FROM processo_monitorado WHERE id = ?").param(segundo.processoMonitoradoId()).update();
        assertThrows(IllegalArgumentException.class, () -> rodadas.criar(rodada, ENDPOINTS));
        assertTrue(rodadas.buscarPorId(DONO, rodada.id()).isEmpty());
        assertTrue(itens.buscarPorRodada(DONO, rodada.id()).isEmpty());
        assertEquals(0, jdbc.sql("SELECT count(*) FROM consulta_grau WHERE item_rodada_id = ?")
                .param(rodada.itens().getFirst().id()).query(Integer.class).single());
    }

    @Test
    void rejeitaCnjQueNaoCorrespondeAoProcesso() {
        var rodada = rodada(1);
        jdbc.sql("UPDATE processo_monitorado SET numero_cnj = '99999999999999999999' WHERE id = ?")
                .param(rodada.itens().getFirst().processoMonitoradoId()).update();
        assertThrows(IllegalArgumentException.class, () -> rodadas.criar(rodada, ENDPOINTS));
        assertTrue(rodadas.buscarPorId(DONO, rodada.id()).isEmpty());
    }

    @Test
    void rejeitaProcessoDeOutroProprietario() {
        var rodada = rodada(1);
        jdbc.sql("UPDATE processo_monitorado SET proprietario_id = ? WHERE id = ?")
                .params(EXECUTOR.valor(), rodada.itens().getFirst().processoMonitoradoId()).update();
        assertThrows(IllegalArgumentException.class, () -> rodadas.criar(rodada, ENDPOINTS));
        assertTrue(rodadas.buscarPorId(DONO, rodada.id()).isEmpty());
    }

    @Test
    void exigeEndpointsAntesDeGravar() {
        var rodada = rodada(1);
        assertThrows(IllegalArgumentException.class, () -> rodadas.criar(rodada, Map.of()));
        assertTrue(rodadas.buscarPorId(DONO, rodada.id()).isEmpty());
    }

    @Test
    void naoSobrescreveRodadaNemAdmiteDuasAtivasPorExecutor() {
        var primeira = rodada(1);
        rodadas.criar(primeira, ENDPOINTS);
        assertThrows(DataIntegrityViolationException.class, () -> rodadas.criar(primeira, ENDPOINTS));
        var segundaId = UUID.randomUUID();
        var original = primeira.itens().getFirst();
        var item = item(segundaId, original.processoMonitoradoId(), original.numeroProcesso(), 0);
        var segunda = new Rodada(segundaId, DONO, EXECUTOR, Rodada.Escopo.PROCESSO, null, null,
                primeira.janela(), primeira.dataReferencia(), primeira.zonaReferencia(),
                EstadoExecucao.PENDENTE, null, null, List.of(item));
        assertThrows(DataIntegrityViolationException.class, () -> rodadas.criar(segunda, ENDPOINTS));
        assertEquals(primeira, rodadas.buscarPorId(DONO, primeira.id()).orElseThrow());
        assertTrue(rodadas.buscarPorId(DONO, segundaId).isEmpty());
    }

    @Test
    void leResultadoDaTentativaAtualSemSomarTentativasAnteriores() {
        var rodada = rodada(1);
        rodadas.criar(rodada, ENDPOINTS);
        var item = rodada.itens().getFirst();
        var consulta = item.consultas().getFirst();
        var instante = OffsetDateTime.parse("2026-10-19T10:00:00-03:00");
        var atual = UUID.randomUUID();
        for (int numero = 1; numero <= 2; numero++) {
            jdbc.sql("""
                    INSERT INTO tentativa_consulta_grau (id, processo_id, tribunal, grau, status,
                        registrada_em, novas_movimentacoes, consulta_planejada_id, numero_tentativa, movimentacoes_exibidas)
                    VALUES (?, ?, 'TRF1', 'PRIMEIRO_GRAU', 'ENCONTRADO', ?, ?, ?, ?, ?)
                    """).params(numero == 2 ? atual : UUID.randomUUID(), item.processoMonitoradoId(),
                    instante, numero == 2 ? 3 : 8, consulta.id(), numero, numero == 2 ? 1 : 8).update();
        }
        jdbc.sql("UPDATE consulta_grau SET estado = 'CONCLUIDA', inicio = ?, fim = ?, tentativa_atual_id = ? WHERE id = ?")
                .params(instante, instante.plusMinutes(1), atual, consulta.id()).update();
        jdbc.sql("UPDATE rodada SET estado = 'INTERROMPIDA', inicio = ?, fim = ? WHERE id = ?")
                .params(instante, instante.plusMinutes(2), rodada.id()).update();
        var lida = rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
        assertEquals(EstadoExecucao.INTERROMPIDA, lida.estado());
        assertEquals(instante.toInstant(), lida.inicio());
        assertEquals(instante.plusMinutes(2).toInstant(), lida.fim());
        assertEquals(3, lida.detectadas());
        assertEquals(1, lida.exibidas());
        assertEquals(StatusConsulta.ENCONTRADO, lida.itens().getFirst().consultas().getFirst().status());
        assertEquals(1, lida.processosTentados());
        assertEquals(0, lida.processosConcluidos());
    }

    @Test
    void registraTentativasNumeradasEPreservaFalhaAnterior() {
        var rodada = rodadaEmAndamento();
        var consulta = rodada.itens().getFirst().consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00.123456789Z");
        assertTrue(consultas.iniciar(DONO, consulta.id(), inicio));
        finalizar(consulta, inicio, StatusConsulta.INDISPONIVEL);
        var primeira = consultas.buscarTentativas(DONO, consulta.id()).getFirst();
        assertEquals(1, primeira.numero());
        assertEquals(StatusConsulta.INDISPONIVEL, primeira.status());
        assertTrue(consultas.iniciar(DONO, consulta.id(), inicio.plusSeconds(2)));
        finalizar(consulta, inicio.plusSeconds(2), StatusConsulta.ENCONTRADO);
        var historico = consultas.buscarTentativas(DONO, consulta.id());
        assertEquals(2, historico.size());
        assertEquals(primeira, historico.getFirst());
        assertEquals(2, historico.getLast().numero());
        assertFalse(consultas.iniciar(DONO, consulta.id(), inicio.plusSeconds(4)));
        assertEquals(StatusConsulta.ENCONTRADO,
                itens.buscarPorId(DONO, consulta.itemRodadaId()).orElseThrow().consultas().getFirst().status());
        assertTrue(consultas.buscarTentativas(EXECUTOR, consulta.id()).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> consultas.iniciar(EXECUTOR, consulta.id(), inicio));
        assertThrows(IllegalStateException.class,
                () -> consultas.registrarQuantidadeDetectada(historico.getLast().id(), 9));
    }

    @Test
    void cancelamentoBloqueiaNovoEnvioMasPermiteConfirmarChamadaEmCurso() {
        var rodada = rodadaEmAndamento();
        var primeiro = rodada.itens().getFirst().consultas().getFirst();
        var segundo = rodada.itens().getFirst().consultas().getLast();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, primeiro.id(), inicio));
        assertFalse(consultas.iniciar(DONO, segundo.id(), inicio));
        jdbc.sql("UPDATE rodada SET cancelamento_solicitado = true WHERE id = ?").param(rodada.id()).update();
        finalizar(primeiro, inicio, StatusConsulta.ENCONTRADO);
        assertEquals(1, consultas.buscarTentativas(DONO, primeiro.id()).size());
        assertFalse(consultas.iniciar(DONO, segundo.id(), inicio.plusSeconds(2)));
    }

    @Test
    void falhaDepoisDoRegistroReverteTentativaEConclusao() {
        var rodada = rodadaEmAndamento();
        var consulta = rodada.itens().getFirst().consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, consulta.id(), inicio));
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(tx -> {
                    var id = UUID.randomUUID();
                    consultas.registrarTentativa(DONO, consulta.id(), id, inicio, inicio.plusSeconds(1),
                            new RespostaConsultaOrigem(consulta.origem(), StatusConsulta.ENCONTRADO, List.of(), null));
                    consultas.concluir(DONO, consulta.id(), id, 0, 0);
                    throw new IllegalStateException("Falha posterior na mesma transacao");
                }));
        assertTrue(consultas.buscarTentativas(DONO, consulta.id()).isEmpty());
        assertEquals(EstadoExecucao.EM_ANDAMENTO,
                itens.buscarPorId(DONO, consulta.itemRodadaId()).orElseThrow().consultas().getFirst().estado());
        finalizar(consulta, inicio, StatusConsulta.ENCONTRADO);
        assertEquals(1, consultas.buscarTentativas(DONO, consulta.id()).getFirst().numero());
    }

    @Test
    void rejeitaRespostaDeOutraOrigemEInicioObsoletoSemGravar() {
        var rodada = rodadaEmAndamento();
        var consulta = rodada.itens().getFirst().consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, consulta.id(), inicio));
        var tx = new TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> consultas.registrarTentativa(
                DONO, consulta.id(), UUID.randomUUID(), inicio, inicio.plusSeconds(1),
                new RespostaConsultaOrigem(new OrigemPje(Tribunal.TRF6, GrauJurisdicao.PRIMEIRO_GRAU),
                        StatusConsulta.ENCONTRADO, List.of(), null))));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> consultas.registrarTentativa(
                DONO, consulta.id(), UUID.randomUUID(), inicio.minusSeconds(1), inicio.plusSeconds(1),
                new RespostaConsultaOrigem(consulta.origem(), StatusConsulta.ENCONTRADO, List.of(), null))));
        assertTrue(consultas.buscarTentativas(DONO, consulta.id()).isEmpty());
    }

    @Test
    void registroPlanejadoExigeTransacaoENaoPodeFinalizarDuasVezes() {
        var rodada = rodadaEmAndamento();
        var consulta = rodada.itens().getFirst().consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, consulta.id(), inicio));
        assertThrows(IllegalTransactionStateException.class,
                () -> consultas.registrarTentativa(DONO, consulta.id(), UUID.randomUUID(), inicio, inicio,
                        new RespostaConsultaOrigem(consulta.origem(), StatusConsulta.ENCONTRADO, List.of(), null)));
        finalizar(consulta, inicio, StatusConsulta.ENCONTRADO);
        var tentativa = consultas.buscarTentativas(DONO, consulta.id()).getFirst();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions)
                .executeWithoutResult(tx -> consultas.concluir(DONO, consulta.id(), tentativa.id(), 0, 0)));
        assertEquals(tentativa, consultas.buscarTentativas(DONO, consulta.id()).getFirst());
    }

    @Test
    void admissaoConcorrenteIniciaSomenteUmGrau() throws Exception {
        var rodada = rodadaEmAndamento();
        var consultasDoItem = rodada.itens().getFirst().consultas();
        var barreira = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tarefas = consultasDoItem.stream().map(c -> executor.submit(() -> {
                barreira.await(10, TimeUnit.SECONDS);
                return consultas.iniciar(DONO, c.id(), Instant.parse("2026-10-22T13:00:00Z"));
            })).toList();
            int iniciados = 0;
            for (var tarefa : tarefas) if (tarefa.get(15, TimeUnit.SECONDS)) iniciados++;
            assertEquals(1, iniciados);
        }
    }

    private Rodada rodadaEmAndamento() {
        var rodada = rodada(1);
        rodadas.criar(rodada, ENDPOINTS);
        jdbc.sql("UPDATE rodada SET estado = 'EM_ANDAMENTO', inicio = ? WHERE id = ?")
                .params(OffsetDateTime.parse("2026-10-22T12:00:00Z"), rodada.id()).update();
        return rodada;
    }

    @Test
    void registraDeteccaoEntreTentativaEConclusaoNaMesmaTransacao() {
        var rodada = rodadaEmAndamento();
        var item = rodada.itens().getFirst();
        var consulta = item.consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, consulta.id(), inicio));
        var tentativa = UUID.randomUUID();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            consultas.registrarTentativa(DONO, consulta.id(), tentativa, inicio, inicio.plusSeconds(1),
                    new RespostaConsultaOrigem(consulta.origem(), StatusConsulta.ENCONTRADO, List.of(), null));
            jdbc.sql("""
                    INSERT INTO movimentacao_detectada (processo_id, grau, id_movimentacao, tribunal,
                        data_hora, tipo, codigo, descricao, primeira_deteccao, consulta_grau_id)
                    VALUES (?, 'PRIMEIRO_GRAU', 'mov-1', 'TRF1', ?, 'NACIONAL', 1, 'Movimento', ?, ?)
                    """).params(item.processoMonitoradoId(), LocalDateTime.of(2026, 10, 22, 10, 0),
                    inicio.atOffset(ZoneOffset.UTC), tentativa).update();
            consultas.concluir(DONO, consulta.id(), tentativa, 1, 0);
        });
        var resultado = consultas.buscarTentativas(DONO, consulta.id()).getFirst();
        assertEquals(1, resultado.detectadas());
        assertEquals(0, resultado.exibidas());
        assertEquals(1, itens.buscarPorId(DONO, item.id()).orElseThrow().detectadas());
    }

    private void finalizar(ConsultaGrau consulta, Instant inicio, StatusConsulta status) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var id = UUID.randomUUID();
            consultas.registrarTentativa(DONO, consulta.id(), id, inicio, inicio.plusSeconds(1),
                    new RespostaConsultaOrigem(consulta.origem(), status, List.of(),
                            status == StatusConsulta.ENCONTRADO ? null : "Falha simulada"));
            consultas.concluir(DONO, consulta.id(), id, 0, 0);
        });
    }

    @Test
    void cicloPendenteRespeitaIdentidadesECancelamentoIdempotente() {
        var rodada = rodada(1);
        rodadas.criar(rodada, ENDPOINTS);
        var agora = Instant.parse("2026-11-03T12:00:00Z");
        assertFalse(rodadas.iniciar(EXECUTOR, EXECUTOR, rodada.id(), agora));
        assertFalse(rodadas.solicitarCancelamento(DONO, DONO, rodada.id()));
        assertFalse(rodadas.cancelar(DONO, EXECUTOR, rodada.id(), agora));
        assertTrue(rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id()));
        assertTrue(rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id()));
        assertTrue(rodadas.cancelamentoSolicitado(DONO, EXECUTOR, rodada.id()));
        assertFalse(rodadas.cancelamentoSolicitado(DONO, DONO, rodada.id()));
        assertFalse(rodadas.iniciar(DONO, EXECUTOR, rodada.id(), agora));
        assertTrue(rodadas.cancelar(DONO, EXECUTOR, rodada.id(), agora));
        assertTrue(rodadas.cancelar(DONO, EXECUTOR, rodada.id(), agora.plusSeconds(5)));
        var lida = rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
        assertEquals(EstadoExecucao.CANCELADA, lida.estado());
        assertNull(lida.inicio());
        assertEquals(agora, lida.fim());
        assertTrue(lida.itens().getFirst().consultas().stream().allMatch(c -> c.estado() == EstadoExecucao.CANCELADA && !c.tentada()));
        assertFalse(rodadas.interromper(DONO, EXECUTOR, rodada.id(), agora));
    }

    @Test
    void conclusaoExigeTodosOsGrausEPreservaResultadoDeFalha() {
        var rodada = rodada(1);
        rodadas.criar(rodada, ENDPOINTS);
        var inicio = Instant.parse("2026-11-03T12:00:00.123456Z");
        assertFalse(rodadas.concluir(DONO, EXECUTOR, rodada.id(), inicio));
        assertTrue(rodadas.iniciar(DONO, EXECUTOR, rodada.id(), inicio));
        assertFalse(rodadas.iniciar(DONO, EXECUTOR, rodada.id(), inicio));
        assertFalse(rodadas.concluir(DONO, EXECUTOR, rodada.id(), inicio));
        int n = 0;
        for (var consulta : rodada.itens().getFirst().consultas()) {
            var tempo = inicio.plusSeconds(++n * 2L);
            assertTrue(consultas.iniciar(DONO, consulta.id(), tempo));
            finalizar(consulta, tempo, StatusConsulta.INDISPONIVEL);
        }
        assertFalse(rodadas.concluir(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(4)));
        var fim = inicio.plusSeconds(6);
        assertTrue(rodadas.concluir(DONO, EXECUTOR, rodada.id(), fim));
        assertTrue(rodadas.concluir(DONO, EXECUTOR, rodada.id(), fim.plusSeconds(5)));
        assertFalse(rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id()));
        var lida = rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
        assertEquals(fim, lida.fim());
        assertEquals(ResultadoExecucao.FALHA, lida.resultado().orElseThrow());
    }

    @Test
    void cancelamentoEsperaRespostaEmCursoEPreservaResultado() {
        var rodada = rodadaEmAndamento();
        var primeiro = rodada.itens().getFirst().consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, primeiro.id(), inicio));
        assertTrue(rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id()));
        assertFalse(rodadas.cancelar(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(2)));
        finalizar(primeiro, inicio, StatusConsulta.ENCONTRADO);
        var tentativa = consultas.buscarTentativas(DONO, primeiro.id()).getFirst();
        assertTrue(rodadas.cancelar(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(2)));
        var lida = rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
        assertEquals(EstadoExecucao.CONCLUIDA, lida.itens().getFirst().consultas().getFirst().estado());
        assertEquals(EstadoExecucao.CANCELADA, lida.itens().getFirst().consultas().getLast().estado());
        assertEquals(tentativa, consultas.buscarTentativas(DONO, primeiro.id()).getFirst());
    }

    @Test
    void interrupcaoEncerraGrauSemInventarTentativaERejeitaRespostaTardia() {
        var rodada = rodadaEmAndamento();
        var primeiro = rodada.itens().getFirst().consultas().getFirst();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        assertTrue(consultas.iniciar(DONO, primeiro.id(), inicio));
        assertFalse(rodadas.interromper(DONO, EXECUTOR, rodada.id(), inicio.minusSeconds(1)));
        assertTrue(rodadas.interromper(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(1)));
        assertThrows(IllegalStateException.class, () -> finalizar(primeiro, inicio, StatusConsulta.ENCONTRADO));
        assertTrue(consultas.buscarTentativas(DONO, primeiro.id()).isEmpty());
        var lida = rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
        assertEquals(EstadoExecucao.INTERROMPIDA, lida.estado());
        assertTrue(lida.itens().getFirst().consultas().stream().allMatch(c -> c.estado() == EstadoExecucao.INTERROMPIDA));
        assertFalse(rodadas.iniciar(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(2)));
    }

    @Test
    void rollbackDoChamadorDesfazEncerramentoDaRodadaEDosGraus() {
        var rodada = rodadaEmAndamento();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertTrue(rodadas.interromper(DONO, EXECUTOR, rodada.id(), Instant.parse("2026-11-03T12:00:00Z")));
            tx.setRollbackOnly();
        });
        var lida = rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
        assertEquals(EstadoExecucao.EM_ANDAMENTO, lida.estado());
        assertTrue(lida.itens().getFirst().consultas().stream().allMatch(c -> c.estado() == EstadoExecucao.PENDENTE));
    }

    @Test
    void pedidoDeCancelamentoEConclusaoConcorrentesRespeitamPrimeiroBloqueio() throws Exception {
        var rodada = rodadaEmAndamento();
        var inicio = Instant.parse("2026-10-22T13:00:00Z");
        for (var consulta : rodada.itens().getFirst().consultas()) {
            assertTrue(consultas.iniciar(DONO, consulta.id(), inicio));
            finalizar(consulta, inicio, StatusConsulta.ENCONTRADO);
        }
        var barreira = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var pedido = pool.submit(() -> {
                barreira.await(10, TimeUnit.SECONDS);
                return rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id());
            });
            var conclusao = pool.submit(() -> {
                barreira.await(10, TimeUnit.SECONDS);
                return rodadas.concluir(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(2));
            });
            boolean cancelou = pedido.get(15, TimeUnit.SECONDS);
            boolean concluiu = conclusao.get(15, TimeUnit.SECONDS);
            assertNotEquals(cancelou, concluiu);
            if (cancelou) assertTrue(rodadas.cancelar(DONO, EXECUTOR, rodada.id(), inicio.plusSeconds(2)));
            assertEquals(cancelou ? EstadoExecucao.CANCELADA : EstadoExecucao.CONCLUIDA,
                    rodadas.buscarPorId(DONO, rodada.id()).orElseThrow().estado());
        }
    }

    private Rodada rodada(int quantidade) {
        var id = UUID.randomUUID();
        var lista = new ArrayList<ItemRodada>();
        for (int ordem = quantidade - 1; ordem >= 0; ordem--) {
            var processoId = UUID.randomUUID();
            var numero = new NumeroProcesso("0000000002026401000" + ordem);
            processos.salvar(new ProcessoMonitorado(processoId, DONO, numero, null, Set.of(), "", false));
            lista.add(item(id, processoId, numero, ordem));
        }
        return new Rodada(id, DONO, EXECUTOR, quantidade == 1 ? Rodada.Escopo.PROCESSO : Rodada.Escopo.LISTA,
                quantidade == 1 ? null : UUID.randomUUID(), quantidade == 1 ? null : "Lista d'água — revisão",
                new JanelaExibicao(7), LocalDate.of(2026, 10, 19), ZoneId.of("America/Sao_Paulo"),
                EstadoExecucao.PENDENTE, null, null, lista);
    }

    private ItemRodada item(UUID rodadaId, UUID processoId, NumeroProcesso numero, int ordem) {
        var id = UUID.randomUUID();
        return new ItemRodada(id, rodadaId, processoId, numero, ordem,
                Arrays.stream(GrauJurisdicao.values()).map(grau -> ConsultaGrau.planejar(UUID.randomUUID(), id,
                        new OrigemPje(Tribunal.TRF1, grau))).toList());
    }
}
