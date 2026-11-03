package br.gov.agu.virgo_back.movimentacao.application;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.consulta.persistence.ConsultaGrauRepository;
import br.gov.agu.virgo_back.consulta.persistence.RodadaRepository;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.movimentacao.domain.MovimentacaoDetectada;
import br.gov.agu.virgo_back.movimentacao.persistence.HistoricoMovimentacaoRepository;
import br.gov.agu.virgo_back.processo.domain.*;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.net.URI;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
class RegistrarDeteccoesServiceTest {
    private static final IdConsultante DONO = new IdConsultante("22222222222");
    private static final IdConsultante OUTRO = new IdConsultante("33333333333");
    private static final OrigemPje ORIGEM = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU);
    private static final NumeroProcesso CNJ = new NumeroProcesso("00000010020264010000");

    @Autowired RegistrarDeteccoesService service;
    @Autowired ProcessoMonitoradoRepository processos;
    @Autowired HistoricoMovimentacaoRepository historico;
    @Autowired JdbcClient jdbc;
    @Autowired RodadaRepository rodadas;
    @Autowired ConsultaGrauRepository consultas;
    private UUID processoId;

    @BeforeEach
    void preparar() {
        jdbc.sql("INSERT INTO usuario (id_consultante) VALUES (?), (?)")
                .params(DONO.valor(), OUTRO.valor()).update();
        processoId = criarProcesso(DONO);
    }

    @AfterEach
    void limpar() {
        // O service confirma transacoes proprias; limpar somente os dados destes proprietarios.
        jdbc.sql("""
                DELETE FROM movimentacao_detectada WHERE processo_id IN
                    (SELECT id FROM processo_monitorado WHERE proprietario_id IN (?, ?))
                """).params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("""
                UPDATE consulta_grau SET estado = 'PENDENTE', inicio = NULL, fim = NULL, tentativa_atual_id = NULL
                WHERE processo_id IN (SELECT id FROM processo_monitorado WHERE proprietario_id IN (?, ?))
                """).params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("""
                DELETE FROM tentativa_consulta_grau WHERE processo_id IN
                    (SELECT id FROM processo_monitorado WHERE proprietario_id IN (?, ?))
                """).params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("DELETE FROM consulta_grau WHERE processo_id IN (SELECT id FROM processo_monitorado WHERE proprietario_id IN (?, ?))")
                .params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("DELETE FROM item_rodada WHERE proprietario_id IN (?, ?)").params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("DELETE FROM rodada WHERE proprietario_id IN (?, ?)").params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("DELETE FROM processo_monitorado WHERE proprietario_id IN (?, ?)")
                .params(DONO.valor(), OUTRO.valor()).update();
        jdbc.sql("DELETE FROM usuario WHERE id_consultante IN (?, ?)")
                .params(DONO.valor(), OUTRO.valor()).update();
    }

    @Test
    void registraRetroativasDuplicadasEIdsOpacosSemFiltroDeData() {
        UUID consulta = UUID.randomUUID();
        var antigo = movimento("-8", "Movimento antigo");
        var local = new Movimentacao(new IdentificadorMovimento("legado-A"),
                LocalDateTime.of(2026, 10, 9, 13, 45), TipoMovimentacao.LOCAL, 123, "Descrição d'água");
        Instant antes = Instant.now();
        var inseridas = service.registrar(processoId, consulta, sucesso(ORIGEM, List.of(local, antigo, antigo)));
        assertEquals(2, inseridas.size());
        assertEquals(2, quantidade(consulta));
        assertEquals(Set.of(antigo.id(), local.id()), historico.buscarIdsConhecidos(processoId, ORIGEM.grau()));
        assertThrows(UnsupportedOperationException.class, inseridas::clear);
        for (var deteccao : inseridas) {
            assertEquals(processoId, deteccao.processoMonitoradoId());
            assertEquals(consulta, deteccao.consultaGrauId());
            assertFalse(deteccao.primeiraDeteccao().isBefore(antes));
            var row = jdbc.sql("SELECT * FROM movimentacao_detectada WHERE processo_id = ? AND id_movimentacao = ?")
                    .params(processoId, deteccao.movimentacao().id().valor()).query().singleRow();
            assertEquals(deteccao.movimentacao().descricao(), row.get("descricao"));
            assertEquals(deteccao.movimentacao().tipo().name(), row.get("tipo"));
            assertEquals(deteccao.movimentacao().codigo(), row.get("codigo"));
            assertEquals(consulta, row.get("consulta_grau_id"));
        }
        assertEquals(local.dataHora(), jdbc.sql("SELECT data_hora FROM movimentacao_detectada WHERE id_movimentacao = ?")
                .param(local.id().valor()).query(LocalDateTime.class).single());
    }

    @Test
    void reconsultaPreservaPrimeiraDeteccaoESeusDados() {
        UUID primeira = UUID.randomUUID();
        service.registrar(processoId, primeira, sucesso(ORIGEM, List.of(movimento("7", "Original"))));
        var instante = jdbc.sql("SELECT primeira_deteccao FROM movimentacao_detectada WHERE processo_id = ?")
                .param(processoId).query(OffsetDateTime.class).single();
        UUID segunda = UUID.randomUUID();
        assertTrue(service.registrar(processoId, segunda,
                sucesso(ORIGEM, List.of(movimento("7", "Texto alterado")))).isEmpty());
        assertEquals(0, quantidade(segunda));
        assertEquals(1, quantidade(primeira));
        var row = jdbc.sql("SELECT descricao, consulta_grau_id FROM movimentacao_detectada WHERE processo_id = ?")
                .param(processoId).query().singleRow();
        assertEquals("Original", row.get("descricao"));
        assertEquals(primeira, row.get("consulta_grau_id"));
        assertEquals(instante, jdbc.sql("SELECT primeira_deteccao FROM movimentacao_detectada WHERE processo_id = ?")
                .param(processoId).query(OffsetDateTime.class).single());
    }

    @Test
    void separaProprietariosEGrausMasNaoTribunaisNaChave() {
        var resposta = sucesso(ORIGEM, List.of(movimento("1", "Movimento")));
        assertEquals(1, service.registrar(processoId, UUID.randomUUID(), resposta).size());
        var segundo = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU);
        assertEquals(1, service.registrar(processoId, UUID.randomUUID(), sucesso(segundo, resposta.movimentacoes())).size());
        assertEquals(1, service.registrar(criarProcesso(OUTRO), UUID.randomUUID(), resposta).size());
        var outroTribunal = new OrigemPje(Tribunal.TRF6, ORIGEM.grau());
        assertTrue(service.registrar(processoId, UUID.randomUUID(), sucesso(outroTribunal, resposta.movimentacoes())).isEmpty());
    }

    @Test
    void persisteSucessoVazioEFalhaSemInventarDeteccoes() {
        UUID sucesso = UUID.randomUUID();
        UUID falha = UUID.randomUUID();
        assertTrue(service.registrar(processoId, sucesso, sucesso(ORIGEM, List.of())).isEmpty());
        assertTrue(service.registrar(processoId, falha,
                new RespostaConsultaOrigem(ORIGEM, StatusConsulta.INDISPONIVEL, List.of(), "Tempo esgotado")).isEmpty());
        assertEquals(0, quantidade(sucesso));
        assertEquals(0, quantidade(falha));
        assertEquals("INDISPONIVEL", jdbc.sql("SELECT status FROM tentativa_consulta_grau WHERE id = ?")
                .param(falha).query(String.class).single());
        assertEquals("Tempo esgotado", jdbc.sql("SELECT erro FROM tentativa_consulta_grau WHERE id = ?")
                .param(falha).query(String.class).single());
    }

    @Test
    void falhaDeEscritaReverteConsultaEMovimentosJaInseridos() {
        UUID consulta = UUID.randomUUID();
        // PostgreSQL nao aceita NUL em text; a segunda insercao falha depois da primeira.
        var resposta = sucesso(ORIGEM, List.of(movimento("a", "Valido"), movimento("z", "Invalido\u0000")));
        assertThrows(DataIntegrityViolationException.class, () -> service.registrar(processoId, consulta, resposta));
        assertEquals(0, contarConsultas());
        assertTrue(historico.buscarIdsConhecidos(processoId, ORIGEM.grau()).isEmpty());
    }

    @Test
    void rejeitaRespostaInconsistenteEProcessoInexistente() {
        var movimento = movimento("1", "Movimento");
        var falhaComMovimentos = new RespostaConsultaOrigem(ORIGEM, StatusConsulta.RESPOSTA_INVALIDA,
                List.of(movimento), "XML invalido");
        assertThrows(IllegalArgumentException.class,
                () -> service.registrar(processoId, UUID.randomUUID(), falhaComMovimentos));
        assertThrows(IllegalArgumentException.class, () -> service.registrar(processoId, UUID.randomUUID(),
                new RespostaConsultaOrigem(ORIGEM, StatusConsulta.ENCONTRADO, List.of(), "Erro")));
        assertThrows(DataIntegrityViolationException.class,
                () -> service.registrar(UUID.randomUUID(), UUID.randomUUID(), sucesso(ORIGEM, List.of(movimento))));
        assertEquals(0, contarConsultas());
    }

    @Test
    void falhaNoSegundoGrauPreservaDeteccoesConfirmadasDoPrimeiro() {
        UUID primeira = UUID.randomUUID();
        service.registrar(processoId, primeira, sucesso(ORIGEM, List.of(movimento("1", "Primeiro grau"))));
        var segundo = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU);
        assertThrows(DataIntegrityViolationException.class, () -> service.registrar(processoId, UUID.randomUUID(),
                sucesso(segundo, List.of(movimento("a", "Valido"), movimento("z", "Invalido\u0000")))));
        assertEquals(1, contarConsultas());
        assertEquals(1, quantidade(primeira));
        assertEquals(Set.of(new IdentificadorMovimento("1")), historico.buscarIdsConhecidos(processoId, ORIGEM.grau()));
        assertTrue(historico.buscarIdsConhecidos(processoId, segundo.grau()).isEmpty());
    }

    @Test
    void bancoRejeitaAtribuicaoAOutroGrauOuConsultaDeFalha() {
        UUID sucesso = UUID.randomUUID();
        UUID falha = UUID.randomUUID();
        service.registrar(processoId, sucesso, sucesso(ORIGEM, List.of()));
        service.registrar(processoId, falha,
                new RespostaConsultaOrigem(ORIGEM, StatusConsulta.NAO_ENCONTRADO, List.of(), "Nao encontrado"));
        var segundo = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU);
        assertThrows(DataIntegrityViolationException.class, () -> historico.inserirSeAusente(
                new MovimentacaoDetectada(processoId, segundo, movimento("1", "Movimento"), Instant.now(), sucesso)));
        assertThrows(DataIntegrityViolationException.class, () -> historico.inserirSeAusente(
                new MovimentacaoDetectada(processoId, ORIGEM, movimento("1", "Movimento"), Instant.now(), falha)));
        assertTrue(historico.buscarIdsConhecidos(processoId, ORIGEM.grau()).isEmpty());
    }

    @Test
    void reutilizarIdDaConsultaNaoSobrescreveResultadoNemAdicionaMovimentos() {
        UUID consulta = UUID.randomUUID();
        service.registrar(processoId, consulta, sucesso(ORIGEM, List.of(movimento("1", "Primeiro"))));
        assertThrows(DataIntegrityViolationException.class, () -> service.registrar(processoId, consulta,
                sucesso(ORIGEM, List.of(movimento("2", "Segundo")))));
        assertEquals(1, quantidade(consulta));
        assertEquals(Set.of(new IdentificadorMovimento("1")), historico.buscarIdsConhecidos(processoId, ORIGEM.grau()));
    }

    @Test
    void consultasConcorrentesNaoContamAMesmaDeteccaoDuasVezes() throws Exception {
        var movimentos = new ArrayList<Movimentacao>();
        for (int i = 0; i < 30; i++) movimentos.add(movimento("id-" + i, "Movimento"));
        var inversos = new ArrayList<>(movimentos);
        Collections.reverse(inversos);
        UUID primeira = UUID.randomUUID();
        UUID segunda = UUID.randomUUID();
        var largada = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> {
                assertTrue(largada.await(5, TimeUnit.SECONDS));
                return service.registrar(processoId, primeira, sucesso(ORIGEM, movimentos));
            });
            var b = executor.submit(() -> {
                assertTrue(largada.await(5, TimeUnit.SECONDS));
                return service.registrar(processoId, segunda, sucesso(ORIGEM, inversos));
            });
            largada.countDown();
            List<MovimentacaoDetectada> resultadoA = a.get(15, TimeUnit.SECONDS);
            List<MovimentacaoDetectada> resultadoB = b.get(15, TimeUnit.SECONDS);
            assertEquals(30, resultadoA.size() + resultadoB.size());
            assertEquals(resultadoA.size(), quantidade(primeira));
            assertEquals(resultadoB.size(), quantidade(segunda));
            assertEquals(30, historico.buscarIdsConhecidos(processoId, ORIGEM.grau()).size());
        }
    }

    @Test
    void planejadaPersisteTodasAsNovasEContaExibidasPelaDataCapturada() {
        var conhecido = movimento("conhecido", "Ja detectado");
        service.registrar(processoId, UUID.randomUUID(), sucesso(ORIGEM, List.of(conhecido)));
        var plano = planejar();
        var antigo = movimento("antigo", "Retroativo");
        var limite = new Movimentacao(new IdentificadorMovimento("limite"), LocalDateTime.of(2026, 10, 19, 0, 0),
                TipoMovimentacao.NACIONAL, 85, "No limite");
        var futuro = new Movimentacao(new IdentificadorMovimento("futuro"), LocalDateTime.of(2030, 1, 1, 0, 0),
                TipoMovimentacao.NACIONAL, 85, "Futuro");
        var tentativa = UUID.randomUUID();
        var inseridas = service.registrarPlanejada(DONO, plano.rodadaId(), plano.consultaId(), tentativa,
                plano.inicio(), sucesso(ORIGEM, List.of(conhecido, antigo, limite, futuro, limite)));
        assertEquals(3, inseridas.size());
        assertTrue(inseridas.stream().allMatch(d -> d.consultaGrauId().equals(tentativa)
                && d.processoMonitoradoId().equals(processoId)));
        assertThrows(UnsupportedOperationException.class, inseridas::clear);
        var registro = consultas.buscarTentativas(DONO, plano.consultaId()).getFirst();
        assertEquals(3, registro.detectadas());
        assertEquals(2, registro.exibidas());
        assertEquals(tentativa, registro.id());
        var rodada = rodadas.buscarPorId(DONO, plano.rodadaId()).orElseThrow();
        assertEquals(EstadoExecucao.CONCLUIDA, rodada.itens().getFirst().consultas().getFirst().estado());
        assertEquals(EstadoExecucao.PENDENTE, rodada.itens().getFirst().consultas().getLast().estado());
        assertEquals(4, historico.buscarIdsConhecidos(processoId, ORIGEM.grau()).size());
        assertThrows(IllegalStateException.class, () -> service.registrarPlanejada(DONO, plano.rodadaId(),
                plano.consultaId(), UUID.randomUUID(), plano.inicio(), sucesso(ORIGEM, List.of())));
        assertEquals(1, consultas.buscarTentativas(DONO, plano.consultaId()).size());
    }

    @Test
    void planejadaConfirmaRespostaEmCursoMesmoComCancelamentoSolicitado() {
        var plano = planejar();
        jdbc.sql("UPDATE rodada SET cancelamento_solicitado = true WHERE id = ?").param(plano.rodadaId()).update();
        assertTrue(service.registrarPlanejada(DONO, plano.rodadaId(), plano.consultaId(), UUID.randomUUID(),
                plano.inicio(), sucesso(ORIGEM, List.of())).isEmpty());
        var tentativa = consultas.buscarTentativas(DONO, plano.consultaId()).getFirst();
        assertEquals(StatusConsulta.ENCONTRADO, tentativa.status());
        assertEquals(0, tentativa.detectadas());
        assertEquals(0, tentativa.exibidas());
    }

    @Test
    void planejadaRegistraFalhaEContinuaExplicitamenteSemPerderTentativaAnterior() {
        var plano = planejar();
        assertTrue(service.registrarPlanejada(DONO, plano.rodadaId(), plano.consultaId(), UUID.randomUUID(), plano.inicio(),
                new RespostaConsultaOrigem(ORIGEM, StatusConsulta.INDISPONIVEL, List.of(), "Indisponivel")).isEmpty());
        var anterior = consultas.buscarTentativas(DONO, plano.consultaId()).getFirst();
        var novoInicio = Instant.now();
        assertTrue(consultas.iniciar(DONO, plano.consultaId(), novoInicio));
        service.registrarPlanejada(DONO, plano.rodadaId(), plano.consultaId(), UUID.randomUUID(), novoInicio,
                sucesso(ORIGEM, List.of(movimento("novo", "Novo"))));
        var tentativas = consultas.buscarTentativas(DONO, plano.consultaId());
        assertEquals(2, tentativas.size());
        assertEquals(anterior, tentativas.getFirst());
        assertEquals(2, tentativas.getLast().numero());
        assertEquals(1, tentativas.getLast().detectadas());
    }

    @Test
    void planejadaReverteTentativaEDeteccoesSeUmaInsercaoFalhar() {
        var plano = planejar();
        assertThrows(DataIntegrityViolationException.class, () -> service.registrarPlanejada(DONO,
                plano.rodadaId(), plano.consultaId(), UUID.randomUUID(), plano.inicio(),
                sucesso(ORIGEM, List.of(movimento("a", "Valido"), movimento("z", "Invalido\u0000")))));
        assertTrue(consultas.buscarTentativas(DONO, plano.consultaId()).isEmpty());
        assertTrue(historico.buscarIdsConhecidos(processoId, ORIGEM.grau()).isEmpty());
        assertEquals(EstadoExecucao.EM_ANDAMENTO, rodadas.buscarPorId(DONO, plano.rodadaId()).orElseThrow()
                .itens().getFirst().consultas().getFirst().estado());
    }

    @Test
    void planejadaRejeitaProprietarioConsultaOrigemEInicioIncorretos() {
        var plano = planejar();
        var resposta = sucesso(ORIGEM, List.of(movimento("1", "Movimento")));
        assertThrows(IllegalArgumentException.class, () -> service.registrarPlanejada(OUTRO,
                plano.rodadaId(), plano.consultaId(), UUID.randomUUID(), plano.inicio(), resposta));
        assertThrows(IllegalArgumentException.class, () -> service.registrarPlanejada(DONO,
                plano.rodadaId(), UUID.randomUUID(), UUID.randomUUID(), plano.inicio(), resposta));
        assertThrows(IllegalStateException.class, () -> service.registrarPlanejada(DONO,
                plano.rodadaId(), plano.consultaId(), UUID.randomUUID(), plano.inicio().minusSeconds(1), resposta));
        assertThrows(IllegalStateException.class, () -> service.registrarPlanejada(DONO,
                plano.rodadaId(), plano.consultaId(), UUID.randomUUID(), plano.inicio(),
                sucesso(new OrigemPje(Tribunal.TRF6, GrauJurisdicao.PRIMEIRO_GRAU), resposta.movimentacoes())));
        assertTrue(consultas.buscarTentativas(DONO, plano.consultaId()).isEmpty());
        assertTrue(historico.buscarIdsConhecidos(processoId, ORIGEM.grau()).isEmpty());
    }

    private Plano planejar() {
        var rodadaId = UUID.randomUUID();
        var itemId = UUID.randomUUID();
        var primeiro = ConsultaGrau.planejar(UUID.randomUUID(), itemId, ORIGEM);
        var segundo = ConsultaGrau.planejar(UUID.randomUUID(), itemId,
                new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU));
        var rodada = new Rodada(rodadaId, DONO, OUTRO, Rodada.Escopo.PROCESSO, null, null,
                new JanelaExibicao(1), LocalDate.of(2026, 10, 20), ZoneId.of("America/Sao_Paulo"),
                EstadoExecucao.PENDENTE, null, null,
                List.of(new ItemRodada(itemId, rodadaId, processoId, CNJ, 0, List.of(primeiro, segundo))));
        rodadas.criar(rodada, Map.of(ORIGEM, URI.create("https://example.test/pje1"),
                segundo.origem(), URI.create("https://example.test/pje2")));
        var inicio = Instant.now().minusSeconds(2);
        jdbc.sql("UPDATE rodada SET estado = 'EM_ANDAMENTO', inicio = ? WHERE id = ?")
                .params(inicio.minusSeconds(1).atOffset(java.time.ZoneOffset.UTC), rodadaId).update();
        assertTrue(consultas.iniciar(DONO, primeiro.id(), inicio));
        return new Plano(rodadaId, primeiro.id(), inicio);
    }

    private record Plano(UUID rodadaId, UUID consultaId, Instant inicio) {}

    private UUID criarProcesso(IdConsultante dono) {
        UUID id = UUID.randomUUID();
        processos.salvar(new ProcessoMonitorado(id, dono, CNJ, null, Set.of(), "", false));
        return id;
    }

    private int quantidade(UUID consulta) {
        return jdbc.sql("SELECT novas_movimentacoes FROM tentativa_consulta_grau WHERE id = ?")
                .param(consulta).query(Integer.class).single();
    }

    private long contarConsultas() {
        return jdbc.sql("SELECT count(*) FROM tentativa_consulta_grau WHERE processo_id = ?")
                .param(processoId).query(Long.class).single();
    }

    private static Movimentacao movimento(String id, String descricao) {
        return new Movimentacao(new IdentificadorMovimento(id), LocalDateTime.of(2020, 1, 2, 10, 30),
                TipoMovimentacao.NACIONAL, 85, descricao);
    }

    private static RespostaConsultaOrigem sucesso(OrigemPje origem, List<Movimentacao> movimentos) {
        return new RespostaConsultaOrigem(origem, StatusConsulta.ENCONTRADO, movimentos, null);
    }
}
