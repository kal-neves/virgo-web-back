package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.consulta.execution.ExecutorSequencialRodada;
import br.gov.agu.virgo_back.consulta.persistence.RodadaRepository;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.identidade.session.CredenciaisJobStore;
import br.gov.agu.virgo_back.pje.ResolverEndpointPje;
import br.gov.agu.virgo_back.processo.domain.*;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.net.URI;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ExecutarRodadaServiceTest {
    private final IdConsultante dono = new IdConsultante("01234567890");
    private final IdConsultante executorId = new IdConsultante("98765432100");
    private final CredenciaisPje credenciais = new CredenciaisPje("senha-ficticia", executorId);
    private final ProcessoMonitorado processo = new ProcessoMonitorado(UUID.randomUUID(), dono,
            new NumeroProcesso("00000000020264010000"), Tribunal.TRF1, Set.of(GrauJurisdicao.PRIMEIRO_GRAU), "", false);
    private final RodadaRepository rodadas = mock(RodadaRepository.class);
    private final ProcessoMonitoradoRepository processos = mock(ProcessoMonitoradoRepository.class);
    private final CredenciaisJobStore store = new CredenciaisJobStore();
    private final VerificarConectividadeGateway prova = mock(VerificarConectividadeGateway.class);
    private final ResolverEndpointPje endpoints = mock(ResolverEndpointPje.class);
    private final ExecutorSequencialRodada executor = mock(ExecutorSequencialRodada.class);
    private final PlatformTransactionManager transacoes = mock(PlatformTransactionManager.class);
    private final ExecutorService trabalhadores = mock(ExecutorService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T02:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final AtomicReference<Runnable> trabalho = new AtomicReference<>();
    private ExecutarRodadaService service;

    @BeforeEach
    void preparar() {
        when(processos.buscarPorId(dono, processo.id())).thenReturn(Optional.of(processo));
        when(prova.verificar(any(), any())).thenReturn(StatusConsulta.NAO_ENCONTRADO);
        when(endpoints.resolver(any())).thenAnswer(i -> URI.create("https://pje.invalid/" + ((OrigemPje) i.getArgument(0)).grau()));
        when(transacoes.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(executor.executar(any(), any(), any(), any())).thenReturn(true);
        doAnswer(i -> { trabalho.set(i.getArgument(0)); return null; }).when(trabalhadores).execute(any());
        service = criarService(trabalhadores);
    }

    @Test
    void capturaDataEPlanejaAmbosOsGrausAntesDeDespachar() {
        var rodada = iniciar();
        assertEquals(LocalDate.of(2026, 11, 5), rodada.dataReferencia());
        assertEquals(clock.getZone(), rodada.zonaReferencia());
        assertEquals(dono, rodada.proprietario());
        assertEquals(executorId, rodada.executor());
        assertEquals(processo.numeroProcesso(), rodada.itens().getFirst().numeroProcesso());
        assertEquals(List.of(GrauJurisdicao.PRIMEIRO_GRAU, GrauJurisdicao.SEGUNDO_GRAU),
                rodada.itens().getFirst().consultas().stream().map(c -> c.origem().grau()).toList());
        var ordem = inOrder(prova, rodadas, transacoes, trabalhadores);
        ordem.verify(prova, times(2)).verificar(any(), eq(credenciais));
        ordem.verify(rodadas).criar(eq(rodada), argThat(m -> m.size() == 2 && m.values().stream().allMatch(u -> u.getHost().equals("pje.invalid"))));
        ordem.verify(transacoes).commit(any());
        ordem.verify(trabalhadores).execute(any());
        verifyNoInteractions(executor);
        trabalho.get().run();
        verify(executor).executar(dono, executorId, rodada.id(), credenciais);
        assertReservaLiberada();
    }

    @ParameterizedTest
    @EnumSource(value = StatusConsulta.class, names = {"ENCONTRADO", "NAO_ENCONTRADO", "PROCESSO_INVALIDO"})
    void aceitaRespostasQueConfirmamCredenciais(StatusConsulta status) {
        when(prova.verificar(any(), any())).thenReturn(status);
        iniciar();
        trabalho.get().run();
        assertReservaLiberada();
    }

    @ParameterizedTest
    @EnumSource(value = StatusConsulta.class, names = {"ACESSO_NEGADO", "RESPOSTA_INVALIDA", "INDISPONIVEL"})
    void falhaNoPreflightNaoCriaRodadaNemDespacha(StatusConsulta status) {
        when(prova.verificar(any(), any())).thenReturn(status);
        assertThrows(IllegalStateException.class, this::iniciar);
        verifyNoInteractions(rodadas, trabalhadores, executor, transacoes);
        verify(prova).verificar(any(), eq(credenciais));
        assertReservaLiberada();
    }

    @Test
    void falhaNoSegundoEndpointTambemImpedeAdmissao() {
        when(prova.verificar(any(), any())).thenReturn(StatusConsulta.ENCONTRADO, StatusConsulta.INDISPONIVEL);
        assertThrows(IllegalStateException.class, this::iniciar);
        verifyNoInteractions(rodadas, trabalhadores);
        assertReservaLiberada();
    }

    @Test
    void rejeitaSelecaoInvalidaAntesDoPreflight() {
        assertThrows(IllegalArgumentException.class, () -> iniciar(List.of()));
        assertThrows(IllegalArgumentException.class, () -> iniciar(List.of(processo.id(), processo.id())));
        assertThrows(IllegalArgumentException.class, () -> iniciar(List.of(UUID.randomUUID())));
        assertThrows(NullPointerException.class, () -> service.iniciar(dono, executorId, Rodada.Escopo.PROCESSO,
                null, null, List.of(processo.id()), null, credenciais));
        verifyNoInteractions(prova, rodadas, trabalhadores);
        assertReservaLiberada();
    }

    @Test
    void tribunalDesconhecidoNaoInventaOrigem() {
        when(processos.buscarPorId(dono, processo.id())).thenReturn(Optional.of(new ProcessoMonitorado(
                processo.id(), dono, processo.numeroProcesso(), null, Set.of(), "", false)));
        assertThrows(IllegalStateException.class, this::iniciar);
        verifyNoInteractions(prova, rodadas, trabalhadores);
        assertReservaLiberada();
    }

    @Test
    void falhaAoPersistirLiberaReservaSemDespachar() {
        doThrow(new DataIntegrityViolationException("conflito")).when(rodadas).criar(any(), anyMap());
        assertThrows(DataIntegrityViolationException.class, this::iniciar);
        verify(transacoes).rollback(any());
        verifyNoInteractions(trabalhadores, executor);
        assertReservaLiberada();
    }

    @Test
    void falhaNoCommitNaoDespacha() {
        doThrow(new IllegalStateException("falha no commit")).when(transacoes).commit(any());
        assertThrows(IllegalStateException.class, this::iniciar);
        verifyNoInteractions(trabalhadores, executor);
        assertReservaLiberada();
    }

    @Test
    void rejeicaoDoTrabalhadorInterrompeRodadaELiberaReserva() {
        doThrow(new RejectedExecutionException()).when(trabalhadores).execute(any());
        assertThrows(RejectedExecutionException.class, this::iniciar);
        verify(rodadas).interromper(eq(dono), eq(executorId), any(), eq(clock.instant()));
        verifyNoInteractions(executor);
        assertReservaLiberada();
    }

    @Test
    void falhaNoTrabalhadorLiberaCredenciaisMesmoSeBancoFalhar() {
        var rodada = iniciar();
        when(executor.executar(any(), any(), any(), any())).thenThrow(new IllegalStateException("senha-ficticia"));
        when(rodadas.interromper(any(), any(), any(), any())).thenThrow(new IllegalStateException("banco"));
        assertDoesNotThrow(() -> trabalho.get().run());
        verify(rodadas).interromper(dono, executorId, rodada.id(), clock.instant());
        assertReservaLiberada();
    }

    @Test
    void rejeitaSegundoInicioEnquantoPrimeiroAguardaExecucao() {
        iniciar();
        assertThrows(IllegalStateException.class, this::iniciar);
        verify(prova, times(2)).verificar(any(), any());
        verify(rodadas).criar(any(), anyMap());
        trabalho.get().run();
        iniciar();
        trabalho.get().run();
    }

    @Test
    void preservaOrdemDaSelecaoEReutilizaPreflightPorOrigem() {
        var outro = new ProcessoMonitorado(UUID.randomUUID(), dono, new NumeroProcesso("00000000120264010000"),
                Tribunal.TRF1, Set.of(), "", false);
        when(processos.buscarPorId(dono, outro.id())).thenReturn(Optional.of(outro));
        var rodada = service.iniciar(dono, executorId, Rodada.Escopo.LISTA, UUID.randomUUID(), " Lista ",
                List.of(outro.id(), processo.id()), new JanelaExibicao(2), credenciais);
        assertEquals(" Lista ", rodada.nomeLista());
        assertEquals(List.of(outro.id(), processo.id()), rodada.itens().stream().map(ItemRodada::processoMonitoradoId).toList());
        verify(prova, times(2)).verificar(any(), any());
        trabalho.get().run();
    }

    @Test
    void retornaAntesDoFimEPermiteOutroExecutorAoMesmoTempo() throws Exception {
        var entraram = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        when(executor.executar(any(), any(), any(), any())).thenAnswer(i -> {
            entraram.countDown();
            assertTrue(liberar.await(10, TimeUnit.SECONDS));
            return true;
        });
        var pool = Executors.newVirtualThreadPerTaskExecutor();
        service = criarService(pool);
        try {
            iniciar();
            service.iniciar(dono, dono, Rodada.Escopo.PROCESSO, null, null, List.of(processo.id()),
                    new JanelaExibicao(0), new CredenciaisPje("outra-senha", dono));
            assertTrue(entraram.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, this::iniciar);
        } finally {
            liberar.countDown();
            service.encerrar();
        }
        assertReservaLiberada();
    }

    private ExecutarRodadaService criarService(ExecutorService pool) {
        return new ExecutarRodadaService(rodadas, processos, store, prova, endpoints, executor, clock, transacoes, pool);
    }

    private Rodada iniciar() {
        return iniciar(List.of(processo.id()));
    }

    private Rodada iniciar(List<UUID> ids) {
        return service.iniciar(dono, executorId, Rodada.Escopo.PROCESSO, null, null, ids, new JanelaExibicao(0), credenciais);
    }

    private void assertReservaLiberada() {
        try (var reserva = store.reservar(UUID.randomUUID(), executorId, credenciais)) {
            assertEquals(credenciais, reserva.credenciais());
        }
    }
}
