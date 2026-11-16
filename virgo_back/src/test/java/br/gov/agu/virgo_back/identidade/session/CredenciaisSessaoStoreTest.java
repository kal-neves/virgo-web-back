package br.gov.agu.virgo_back.identidade.session;

import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredenciaisSessaoStoreTest {
    private final Instant inicio = Instant.parse("2026-11-12T12:00:00Z");
    private final AtomicReference<Instant> agora = new AtomicReference<>(inicio);
    private final Clock clock = mock(Clock.class);
    private final CredenciaisSessaoStore sessoes;
    private final CredenciaisJobStore jobs = new CredenciaisJobStore();
    private final Usuario usuario = new Usuario(new IdConsultante("01234567890"), null, null, null,
            Perfil.COMUM, SituacaoUsuario.ATIVO, false, inicio);
    private final CredenciaisPje credenciais = new CredenciaisPje("senha-ficticia", usuario.id());

    CredenciaisSessaoStoreTest() {
        when(clock.instant()).thenAnswer(_ -> agora.get());
        sessoes = new CredenciaisSessaoStore(clock);
    }

    @AfterEach
    void fechar() {
        sessoes.fechar();
    }

    @Test
    void pollingNaoRenovaEExpiraExatamenteAosSessentaMinutos() {
        var sessao = sessoes.criar(usuario, credenciais);
        avancar(Duration.ofMinutes(59));
        assertEquals(inicio, sessoes.buscar(sessao.id()).orElseThrow().ultimaAtividade());
        avancar(Duration.ofMinutes(1));
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
        assertFalse(sessoes.registrarAtividade(sessao.id()));
        assertThrows(SecurityException.class, () -> sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs));
    }

    @Test
    void atividadeRealRenovaSemLimiteAbsoluto() {
        var sessao = sessoes.criar(usuario, credenciais);
        for (int i = 0; i < 30; i++) {
            avancar(Duration.ofMinutes(59));
            assertTrue(sessoes.registrarAtividade(sessao.id()));
        }
        assertEquals(agora.get(), sessoes.buscar(sessao.id()).orElseThrow().ultimaAtividade());
        avancar(Duration.ofMinutes(60));
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
    }

    @Test
    void atividadeTardiaNaoRecuperaSessaoExpirada() {
        var sessao = sessoes.criar(usuario, credenciais);
        avancar(Duration.ofHours(2));
        assertFalse(sessoes.registrarAtividade(sessao.id()));
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
    }

    @Test
    void jobLongoImpedeExpiracaoETerminoIniciaNovaJanela() {
        var sessao = sessoes.criar(usuario, credenciais);
        var reserva = sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs);
        avancar(Duration.ofHours(20));
        sessoes.limparExpiradas();
        assertEquals(1, sessoes.buscar(sessao.id()).orElseThrow().consultasAtivas());
        assertSame(credenciais, reserva.credenciais());
        reserva.close();
        assertEquals(agora.get(), sessoes.buscar(sessao.id()).orElseThrow().ultimaAtividade());
        avancar(Duration.ofMinutes(59));
        reserva.close();
        assertEquals(0, sessoes.buscar(sessao.id()).orElseThrow().consultasAtivas());
        avancar(Duration.ofMinutes(1));
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
    }

    @Test
    void terminoDoJobPreservaCredenciaisParaOutraRodada() {
        var sessao = sessoes.criar(usuario, credenciais);
        var primeira = sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs);
        primeira.close();
        try (var segunda = sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs)) {
            primeira.close();
            assertSame(credenciais, segunda.credenciais());
            assertEquals(1, sessoes.buscar(sessao.id()).orElseThrow().consultasAtivas());
        }
    }

    @Test
    void logoutNaoCancelaJobNemPermiteQueEleRecrieASessao() {
        var sessao = sessoes.criar(usuario, credenciais);
        var reserva = sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs);
        sessoes.encerrar(sessao.id());
        sessoes.encerrar(sessao.id());
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
        assertSame(credenciais, reserva.credenciais());
        reserva.close();
        assertThrows(IllegalStateException.class, reserva::credenciais);
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
    }

    @Test
    void sessoesSeparadasCompartilhamOLimiteDeUmJobPorExecutor() {
        var primeira = sessoes.criar(usuario, credenciais);
        var segunda = sessoes.criar(usuario, credenciais);
        assertNotEquals(primeira.id(), segunda.id());
        try (var reserva = sessoes.reservarJob(primeira.id(), UUID.randomUUID(), jobs)) {
            assertThrows(IllegalStateException.class, () -> sessoes.reservarJob(segunda.id(), UUID.randomUUID(), jobs));
            assertEquals(0, sessoes.buscar(segunda.id()).orElseThrow().consultasAtivas());
            sessoes.encerrar(segunda.id());
            assertSame(credenciais, reserva.credenciais());
            assertTrue(sessoes.buscar(primeira.id()).isPresent());
        }
    }

    @Test
    void rejeicaoDoPjeInvalidaOrigemMasNaoUmaNovaSessao() {
        var antiga = sessoes.criar(usuario, credenciais);
        var reserva = sessoes.reservarJob(antiga.id(), UUID.randomUUID(), jobs);
        var nova = sessoes.criar(usuario, new CredenciaisPje("nova-senha", usuario.id()));
        reserva.rejeitarCredenciais();
        assertTrue(sessoes.buscar(antiga.id()).isEmpty());
        assertTrue(sessoes.buscar(nova.id()).isPresent());
        reserva.close();
        try (var proxima = sessoes.reservarJob(nova.id(), UUID.randomUUID(), jobs)) {
            assertEquals("nova-senha", proxima.credenciais().senha());
        }
    }

    @Test
    void limpezaRemoveSessaoAbandonadaSemNovoAcesso() {
        sessoes.criar(usuario, credenciais);
        avancar(Duration.ofMinutes(60));
        sessoes.limparExpiradas();
        clearInvocations(clock);
        sessoes.limparExpiradas();
        verifyNoInteractions(clock);
    }

    @Test
    void encerramentoDescartaSessoesSemApagarCredenciaisDeJob() {
        var sessao = sessoes.criar(usuario, credenciais);
        var reserva = sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs);
        sessoes.fechar();
        assertTrue(sessoes.buscar(sessao.id()).isEmpty());
        assertSame(credenciais, reserva.credenciais());
        reserva.close();
        assertThrows(IllegalStateException.class, () -> sessoes.criar(usuario, credenciais));
        var reiniciado = new CredenciaisSessaoStore(clock);
        try { assertTrue(reiniciado.buscar(sessao.id()).isEmpty()); }
        finally { reiniciado.fechar(); }
    }

    @Test
    void protegeIdentidadeESaidaTextual() {
        var outro = new CredenciaisPje("outra-senha", new IdConsultante("98765432100"));
        assertThrows(SecurityException.class, () -> sessoes.criar(usuario, outro));
        var inativo = new Usuario(usuario.id(), null, null, null, Perfil.COMUM, SituacaoUsuario.INATIVO, false, inicio);
        assertThrows(SecurityException.class, () -> sessoes.criar(inativo, credenciais));
        var sessao = sessoes.criar(usuario, credenciais);
        assertFalse(sessao.toString().contains(credenciais.senha()));
        assertFalse(sessao.toString().contains(sessao.id().toString()));
    }

    @Test
    void logoutConcorrenteComAdmissaoNaoDeixaReservaSemDono() throws Exception {
        var sessao = sessoes.criar(usuario, credenciais);
        var inicioSimultaneo = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var admissao = threads.submit(() -> {
                assertTrue(inicioSimultaneo.await(5, TimeUnit.SECONDS));
                try { return sessoes.reservarJob(sessao.id(), UUID.randomUUID(), jobs); }
                catch (SecurityException encerrada) { return null; }
            });
            var logout = threads.submit(() -> {
                assertTrue(inicioSimultaneo.await(5, TimeUnit.SECONDS));
                sessoes.encerrar(sessao.id());
                return true;
            });
            inicioSimultaneo.countDown();
            assertTrue(logout.get(5, TimeUnit.SECONDS));
            var reserva = admissao.get(5, TimeUnit.SECONDS);
            assertTrue(sessoes.buscar(sessao.id()).isEmpty());
            if (reserva != null) { assertSame(credenciais, reserva.credenciais()); reserva.close(); }
            try (var proxima = jobs.reservar(UUID.randomUUID(), usuario.id(), credenciais)) {
                assertSame(credenciais, proxima.credenciais());
            }
        }
    }

    private void avancar(Duration duracao) {
        agora.updateAndGet(i -> i.plus(duracao));
    }
}
