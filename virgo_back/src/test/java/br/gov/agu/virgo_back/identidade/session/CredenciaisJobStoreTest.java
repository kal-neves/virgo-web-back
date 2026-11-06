package br.gov.agu.virgo_back.identidade.session;

import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class CredenciaisJobStoreTest {
    private final CredenciaisJobStore store = new CredenciaisJobStore();
    private final IdConsultante executor = new IdConsultante("01234567890");
    private final CredenciaisPje credenciais = new CredenciaisPje("senha-ficticia", executor);

    @Test
    void encerraSomenteAReservaDoJobSemAlterarCredenciaisDaSessao() {
        var reserva = store.reservar(UUID.randomUUID(), executor, credenciais);
        assertSame(credenciais, reserva.credenciais());
        reserva.close();
        assertThrows(IllegalStateException.class, reserva::credenciais);
        try (var seguinte = store.reservar(UUID.randomUUID(), executor, credenciais)) {
            reserva.close();
            assertSame(credenciais, seguinte.credenciais());
            assertThrows(IllegalStateException.class, () -> store.reservar(UUID.randomUUID(), executor, credenciais));
        }
    }

    @Test
    void separaExecutoresERejeitaCredenciaisDeOutraPessoa() {
        var outro = new IdConsultante("98765432100");
        assertThrows(IllegalArgumentException.class, () -> store.reservar(UUID.randomUUID(), outro, credenciais));
        try (var primeira = store.reservar(UUID.randomUUID(), executor, credenciais);
             var segunda = store.reservar(UUID.randomUUID(), outro, new CredenciaisPje("outra-senha", outro))) {
            assertEquals(executor, primeira.credenciais().login());
            assertEquals(outro, segunda.credenciais().login());
            assertFalse(primeira.toString().contains(credenciais.senha()));
        }
    }

    @Test
    void duasAdmissoesConcorrentesNaoReservamOMesmoExecutor() throws Exception {
        var inicio = new CountDownLatch(1);
        try (var threads = Executors.newVirtualThreadPerTaskExecutor()) {
            var primeira = threads.submit(() -> reservarAoMesmoTempo(inicio));
            var segunda = threads.submit(() -> reservarAoMesmoTempo(inicio));
            inicio.countDown();
            var a = primeira.get(5, TimeUnit.SECONDS);
            var b = segunda.get(5, TimeUnit.SECONDS);
            assertNotEquals(a == null, b == null);
            if (a != null) a.close();
            if (b != null) b.close();
        }
    }

    private CredenciaisJobStore.Reserva reservarAoMesmoTempo(CountDownLatch inicio) throws InterruptedException {
        assertTrue(inicio.await(5, TimeUnit.SECONDS));
        try {
            return store.reservar(UUID.randomUUID(), executor, credenciais);
        } catch (IllegalStateException ocupada) {
            return null;
        }
    }
}
