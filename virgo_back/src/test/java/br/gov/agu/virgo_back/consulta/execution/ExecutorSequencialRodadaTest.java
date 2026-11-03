package br.gov.agu.virgo_back.consulta.execution;

import br.gov.agu.virgo_back.consulta.application.ConsultarProcessoGateway;
import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.consulta.persistence.RodadaRepository;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.*;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import br.gov.agu.virgo_back.support.PostgresTestConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Import(PostgresTestConfig.class)
class ExecutorSequencialRodadaTest {
    private static final IdConsultante DONO = new IdConsultante("88888888888");
    private static final IdConsultante EXECUTOR = new IdConsultante("99999999999");
    private static final IdConsultante OUTRO = new IdConsultante("10101010101");
    private static final Map<OrigemPje, URI> ENDPOINTS = Map.of(
            new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU), URI.create("https://capturado.invalid/pje1"),
            new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU), URI.create("https://capturado.invalid/pje2"));
    @Autowired ExecutorSequencialRodada executor;
    @Autowired RodadaRepository rodadas;
    @Autowired ProcessoMonitoradoRepository processos;
    @Autowired JdbcClient jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ConsultarProcessoGateway gateway;
    private int proximoNumero;

    @BeforeEach
    void preparar() {
        jdbc.sql("INSERT INTO usuario (id_consultante) VALUES (?), (?), (?)")
                .params(DONO.valor(), EXECUTOR.valor(), OUTRO.valor()).update();
    }

    @AfterEach
    void limpar() {
        jdbc.sql("DELETE FROM movimentacao_detectada WHERE processo_id IN (SELECT id FROM processo_monitorado WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("UPDATE consulta_grau SET estado = 'PENDENTE', inicio = NULL, fim = NULL, tentativa_atual_id = NULL WHERE item_rodada_id IN (SELECT id FROM item_rodada WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("DELETE FROM tentativa_consulta_grau WHERE processo_id IN (SELECT id FROM processo_monitorado WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("DELETE FROM consulta_grau WHERE item_rodada_id IN (SELECT id FROM item_rodada WHERE proprietario_id = ?)")
                .param(DONO.valor()).update();
        jdbc.sql("DELETE FROM item_rodada WHERE proprietario_id = ?").param(DONO.valor()).update();
        jdbc.sql("DELETE FROM rodada WHERE proprietario_id = ?").param(DONO.valor()).update();
        jdbc.sql("DELETE FROM processo_monitorado WHERE proprietario_id = ?").param(DONO.valor()).update();
        jdbc.sql("DELETE FROM usuario WHERE id_consultante IN (?, ?, ?)").params(DONO.valor(), EXECUTOR.valor(), OUTRO.valor()).update();
    }

    @ParameterizedTest
    @EnumSource(value = StatusConsulta.class, names = {"NAO_ENCONTRADO", "INDISPONIVEL", "RESPOSTA_INVALIDA"})
    void executaNaOrdemComEndpointCapturadoEConfirmaCadaGrauAntesDoProximo(StatusConsulta primeiraFalha) {
        var rodada = novaRodada(EXECUTOR, 2);
        var chamadas = new AtomicInteger();
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            int ordem = chamadas.getAndIncrement();
            assertEquals(ordem, tentativas(rodada));
            assertEquals(rodada.itens().get(ordem / 2).numeroProcesso(), i.getArgument(3));
            OrigemPje origem = i.getArgument(0);
            assertEquals(ordem % 2 == 0 ? GrauJurisdicao.PRIMEIRO_GRAU : GrauJurisdicao.SEGUNDO_GRAU, origem.grau());
            assertEquals(ENDPOINTS.get(origem), i.getArgument(1));
            assertEquals(EXECUTOR, ((CredenciaisPje) i.getArgument(2)).login());
            return resposta(origem, ordem == 0 ? primeiraFalha : StatusConsulta.ENCONTRADO, "Movimento");
        });
        assertTrue(executar(rodada));
        var salva = ler(rodada);
        assertEquals(EstadoExecucao.CONCLUIDA, salva.estado());
        assertEquals(ResultadoExecucao.SUCESSO, salva.resultado().orElseThrow());
        assertEquals(3, salva.detectadas());
        assertEquals(3, salva.exibidas());
        assertEquals(4, chamadas.get());
        assertEquals(primeiraFalha, salva.itens().getFirst().consultas().getFirst().status());
        assertFalse(executar(rodada));
        assertEquals(4, chamadas.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4})
    void cancelamentoDuranteChamadaConfirmaResultadoEParaAntesDoProximoGrau(int cancelarNaChamada) {
        var rodada = novaRodada(EXECUTOR, 2);
        var chamadas = new AtomicInteger();
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i -> {
            if (chamadas.incrementAndGet() == cancelarNaChamada) {
                assertTrue(rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id()));
            }
            return resposta(i.getArgument(0), StatusConsulta.ENCONTRADO, "Movimento");
        });
        assertTrue(executar(rodada));
        var salva = ler(rodada);
        assertEquals(EstadoExecucao.CANCELADA, salva.estado());
        assertEquals(cancelarNaChamada, tentativas(rodada));
        assertEquals(cancelarNaChamada, salva.detectadas());
        assertEquals(4 - cancelarNaChamada, salva.itens().stream().flatMap(i -> i.consultas().stream())
                .filter(c -> c.estado() == EstadoExecucao.CANCELADA && !c.tentada()).count());
        verify(gateway, times(cancelarNaChamada)).consultarProcesso(any(), any(URI.class), any(), any());
    }

    @Test
    void cancelamentoAnteriorAoInicioNaoEnviaSoap() {
        var rodada = novaRodada(EXECUTOR, 1);
        rodadas.solicitarCancelamento(DONO, EXECUTOR, rodada.id());
        assertFalse(executar(rodada));
        assertEquals(EstadoExecucao.CANCELADA, ler(rodada).estado());
        assertEquals(0, tentativas(rodada));
        verifyNoInteractions(gateway);
    }

    @Test
    void acessoNegadoInterrompeRodadaDepoisDeRegistrarFalha() {
        var rodada = novaRodada(EXECUTOR, 2);
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i ->
                resposta(i.getArgument(0), StatusConsulta.ACESSO_NEGADO, ""));
        assertTrue(executar(rodada));
        var salva = ler(rodada);
        assertEquals(EstadoExecucao.INTERROMPIDA, salva.estado());
        assertEquals(StatusConsulta.ACESSO_NEGADO, salva.itens().getFirst().consultas().getFirst().status());
        assertEquals(1, tentativas(rodada));
        assertFalse(executar(rodada));
        verify(gateway, times(1)).consultarProcesso(any(), any(URI.class), any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falhaNoSegundoGrauPreservaPrimeiroSemRepetirChamadas(boolean falhaNoBanco) {
        var rodada = novaRodada(EXECUTOR, 2);
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i -> {
            OrigemPje origem = i.getArgument(0);
            if (origem.grau() == GrauJurisdicao.SEGUNDO_GRAU) {
                if (!falhaNoBanco) throw new IllegalStateException("Falha inesperada");
                return resposta(origem, StatusConsulta.ENCONTRADO, "Invalido\u0000");
            }
            return resposta(origem, StatusConsulta.ENCONTRADO, "Valido");
        });
        if (falhaNoBanco) assertThrows(DataIntegrityViolationException.class, () -> executar(rodada));
        else assertThrows(IllegalStateException.class, () -> executar(rodada));
        assertEquals(EstadoExecucao.INTERROMPIDA, ler(rodada).estado());
        assertEquals(1, tentativas(rodada));
        assertEquals(1, ler(rodada).detectadas());
        verify(gateway, times(2)).consultarProcesso(any(), any(URI.class), any(), any());
    }

    @Test
    void respostaDeOutraOrigemInterrompeSemRegistrarResultadoIncorreto() {
        var rodada = novaRodada(EXECUTOR, 1);
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenReturn(
                resposta(new OrigemPje(Tribunal.TRF6, GrauJurisdicao.PRIMEIRO_GRAU), StatusConsulta.ENCONTRADO, "Movimento"));
        assertThrows(IllegalStateException.class, () -> executar(rodada));
        assertEquals(EstadoExecucao.INTERROMPIDA, ler(rodada).estado());
        assertEquals(0, tentativas(rodada));
    }

    @Test
    void duasExecucoesDaMesmaRodadaNaoDuplicamChamadas() throws Exception {
        var rodada = novaRodada(EXECUTOR, 1);
        var entrou = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i -> {
            entrou.countDown();
            assertTrue(liberar.await(15, TimeUnit.SECONDS));
            return resposta(i.getArgument(0), StatusConsulta.ENCONTRADO, "Movimento");
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            var primeira = pool.submit(() -> executar(rodada));
            try {
                assertTrue(entrou.await(15, TimeUnit.SECONDS));
                assertFalse(executar(rodada));
            } finally {
                liberar.countDown();
            }
            assertTrue(primeira.get(15, TimeUnit.SECONDS));
        }
        assertEquals(2, tentativas(rodada));
        verify(gateway, times(2)).consultarProcesso(any(), any(URI.class), any(), any());
    }

    @Test
    void executoresDiferentesPodemConsultarAoMesmoTempo() throws Exception {
        var primeira = novaRodada(EXECUTOR, 1);
        var segunda = novaRodada(OUTRO, 1);
        var chegaram = new CountDownLatch(2);
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i -> {
            OrigemPje origem = i.getArgument(0);
            if (origem.grau() == GrauJurisdicao.PRIMEIRO_GRAU) {
                chegaram.countDown();
                assertTrue(chegaram.await(15, TimeUnit.SECONDS));
            }
            return resposta(origem, StatusConsulta.ENCONTRADO, "Movimento");
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var a = pool.submit(() -> executar(primeira));
            var b = pool.submit(() -> executar(segunda));
            assertTrue(a.get(20, TimeUnit.SECONDS));
            assertTrue(b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(EstadoExecucao.CONCLUIDA, ler(primeira).estado());
        assertEquals(EstadoExecucao.CONCLUIDA, ler(segunda).estado());
    }

    @Test
    void rejeitaProprietarioExecutorOuCredenciaisIncorretosAntesDeIniciar() {
        var rodada = novaRodada(EXECUTOR, 1);
        assertThrows(IllegalArgumentException.class, () -> executor.executar(OUTRO, EXECUTOR, rodada.id(), credenciais(EXECUTOR)));
        assertThrows(IllegalArgumentException.class, () -> executor.executar(DONO, OUTRO, rodada.id(), credenciais(OUTRO)));
        assertThrows(IllegalArgumentException.class, () -> executor.executar(DONO, EXECUTOR, rodada.id(), credenciais(OUTRO)));
        assertEquals(EstadoExecucao.PENDENTE, ler(rodada).estado());
        verifyNoInteractions(gateway);
    }

    @Test
    void suspendeTransacaoExternaESalvaSucessoSemMovimentacoes() {
        var rodada = novaRodada(EXECUTOR, 1);
        when(gateway.consultarProcesso(any(), any(URI.class), any(), any())).thenAnswer(i -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return new RespostaConsultaOrigem(i.getArgument(0), StatusConsulta.ENCONTRADO, List.of(), null);
        });
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            assertTrue(executar(rodada));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            tx.setRollbackOnly();
        });
        var salva = ler(rodada);
        assertEquals(EstadoExecucao.CONCLUIDA, salva.estado());
        assertEquals(ResultadoExecucao.SUCESSO, salva.resultado().orElseThrow());
        assertEquals(0, salva.detectadas());
        assertEquals(2, tentativas(rodada));
    }

    private Rodada novaRodada(IdConsultante executorId, int quantidade) {
        var id = UUID.randomUUID();
        var itens = new ArrayList<ItemRodada>();
        for (int ordem = 0; ordem < quantidade; ordem++) {
            var processoId = UUID.randomUUID();
            var numero = new NumeroProcesso(String.format("%020d", ++proximoNumero));
            processos.salvar(new ProcessoMonitorado(processoId, DONO, numero, null, Set.of(), "", false));
            var itemId = UUID.randomUUID();
            var primeiro = ConsultaGrau.planejar(UUID.randomUUID(), itemId, new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU));
            var segundo = ConsultaGrau.planejar(UUID.randomUUID(), itemId, new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU));
            itens.addFirst(new ItemRodada(itemId, id, processoId, numero, ordem, List.of(segundo, primeiro)));
        }
        var rodada = new Rodada(id, DONO, executorId, quantidade == 1 ? Rodada.Escopo.PROCESSO : Rodada.Escopo.LISTA,
                quantidade == 1 ? null : UUID.randomUUID(), quantidade == 1 ? null : "Lista",
                new JanelaExibicao(1), LocalDate.of(2026, 10, 20), ZoneId.of("America/Sao_Paulo"),
                EstadoExecucao.PENDENTE, null, null, itens);
        rodadas.criar(rodada, ENDPOINTS);
        return rodada;
    }

    private boolean executar(Rodada rodada) {
        return executor.executar(DONO, rodada.executor(), rodada.id(), credenciais(rodada.executor()));
    }

    private CredenciaisPje credenciais(IdConsultante login) {
        return new CredenciaisPje("senha-ficticia", login);
    }

    private Rodada ler(Rodada rodada) {
        return rodadas.buscarPorId(DONO, rodada.id()).orElseThrow();
    }

    private int tentativas(Rodada rodada) {
        return jdbc.sql("""
                SELECT count(*) FROM tentativa_consulta_grau t JOIN consulta_grau c ON c.id = t.consulta_planejada_id
                JOIN item_rodada i ON i.id = c.item_rodada_id WHERE i.rodada_id = ?
                """).param(rodada.id()).query(Integer.class).single();
    }

    private RespostaConsultaOrigem resposta(OrigemPje origem, StatusConsulta status, String descricao) {
        var movimento = new Movimentacao(new IdentificadorMovimento("mov-1"), LocalDateTime.of(2026, 10, 20, 10, 0),
                TipoMovimentacao.NACIONAL, 85, descricao);
        return new RespostaConsultaOrigem(origem, status, status == StatusConsulta.ENCONTRADO ? List.of(movimento) : List.of(),
                status == StatusConsulta.ENCONTRADO ? null : "Falha simulada");
    }
}
