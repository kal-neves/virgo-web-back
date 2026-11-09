package br.gov.agu.virgo_back.identidade.session;

import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CredenciaisJobStore {
    private final ConcurrentHashMap<IdConsultante, Reserva> reservas = new ConcurrentHashMap<>();

    public Reserva reservar(UUID rodadaId, IdConsultante executor, CredenciaisPje credenciais) {
        Objects.requireNonNull(rodadaId);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(credenciais);
        if (!executor.equals(credenciais.login())) {
            throw new IllegalArgumentException("Credenciais nao pertencem ao executor");
        }
        var reserva = new Reserva(rodadaId, executor, credenciais);
        if (reservas.putIfAbsent(executor, reserva) != null) {
            throw new IllegalStateException("Executor ja possui uma rodada reservada");
        }
        return reserva;
    }

    public final class Reserva implements AutoCloseable {
        private final UUID rodadaId;
        private final IdConsultante executor;
        private CredenciaisPje credenciais;

        private Reserva(UUID rodadaId, IdConsultante executor, CredenciaisPje credenciais) {
            this.rodadaId = rodadaId;
            this.executor = executor;
            this.credenciais = credenciais;
        }

        public UUID rodadaId() {
            return rodadaId;
        }

        public synchronized CredenciaisPje credenciais() {
            if (credenciais == null) throw new IllegalStateException("Reserva de credenciais encerrada");
            return credenciais;
        }

        @Override
        public synchronized void close() {
            credenciais = null;
            reservas.remove(executor, this);
        }
    }
}
