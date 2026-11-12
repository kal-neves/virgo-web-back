package br.gov.agu.virgo_back.identidade.session;

import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.identidade.domain.SituacaoUsuario;
import br.gov.agu.virgo_back.identidade.domain.Usuario;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
public class CredenciaisSessaoStore {
    private final ConcurrentHashMap<UUID, Entrada> sessoes = new ConcurrentHashMap<>();
    private final PoliticaInatividade politica = new PoliticaInatividade();
    private final Clock clock;
    private boolean fechado;
    private final ScheduledExecutorService limpeza = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("virgo-sessoes").factory());

    public CredenciaisSessaoStore(Clock clock) {
        this.clock = clock;
    }

    public synchronized SessaoVirgo criar(Usuario usuario, CredenciaisPje credenciais) {
        if (fechado) throw new IllegalStateException("Armazenamento de sessoes encerrado");
        Objects.requireNonNull(usuario);
        Objects.requireNonNull(credenciais);
        if (!usuario.id().equals(credenciais.login()) || usuario.situacao() != SituacaoUsuario.ATIVO) {
            throw new SecurityException("Usuario nao pode iniciar sessao");
        }
        var entrada = new Entrada(UUID.randomUUID(), usuario.id(), clock.instant(), credenciais);
        sessoes.put(entrada.id, entrada);
        return entrada.snapshot();
    }

    public Optional<SessaoVirgo> buscar(UUID id) {
        var entrada = sessoes.get(Objects.requireNonNull(id));
        if (entrada == null) return Optional.empty();
        synchronized (entrada.monitor) {
            return valida(entrada) ? Optional.of(entrada.snapshot()) : Optional.empty();
        }
    }

    public boolean registrarAtividade(UUID id) {
        var entrada = sessoes.get(Objects.requireNonNull(id));
        if (entrada == null) return false;
        synchronized (entrada.monitor) {
            if (!valida(entrada)) return false;
            entrada.atualizarAtividade(clock.instant());
            return true;
        }
    }

    public CredenciaisJobStore.Reserva reservarJob(UUID id, UUID rodadaId, CredenciaisJobStore jobs) {
        var entrada = sessoes.get(Objects.requireNonNull(id));
        if (entrada == null) throw new SecurityException("Sessao ausente ou expirada");
        synchronized (entrada.monitor) {
            if (!valida(entrada)) throw new SecurityException("Sessao ausente ou expirada");
            var reserva = jobs.reservar(rodadaId, entrada.usuario, entrada.credenciais,
                    () -> finalizarJob(entrada, rodadaId), () -> encerrar(id));
            entrada.jobs.add(rodadaId);
            entrada.atualizarAtividade(clock.instant());
            return reserva;
        }
    }

    public void encerrar(UUID id) {
        var entrada = sessoes.get(Objects.requireNonNull(id));
        if (entrada == null) return;
        synchronized (entrada.monitor) {
            remover(entrada);
        }
    }

    private boolean valida(Entrada entrada) {
        if (sessoes.get(entrada.id) != entrada) return false;
        if (politica.expirou(entrada.snapshot(), clock.instant())) {
            remover(entrada);
            return false;
        }
        return true;
    }

    private void remover(Entrada entrada) {
        sessoes.remove(entrada.id, entrada);
        entrada.credenciais = null;
    }

    private void finalizarJob(Entrada entrada, UUID rodadaId) {
        synchronized (entrada.monitor) {
            if (entrada.jobs.remove(rodadaId) && sessoes.get(entrada.id) == entrada) {
                entrada.atualizarAtividade(clock.instant());
            }
        }
    }

    public void limparExpiradas() {
        sessoes.keySet().forEach(this::buscar);
    }

    @PostConstruct
    void iniciarLimpeza() {
        limpeza.scheduleWithFixedDelay(this::limparExpiradas, 1, 1, TimeUnit.MINUTES);
    }

    @PreDestroy
    public synchronized void fechar() {
        fechado = true;
        limpeza.shutdownNow();
        sessoes.keySet().forEach(this::encerrar);
    }

    private static final class Entrada {
        private final Object monitor = new Object();
        private final UUID id;
        private final IdConsultante usuario;
        private Instant ultimaAtividade;
        private CredenciaisPje credenciais;
        private final Set<UUID> jobs = new HashSet<>();

        private Entrada(UUID id, IdConsultante usuario, Instant ultimaAtividade, CredenciaisPje credenciais) {
            this.id = id;
            this.usuario = usuario;
            this.ultimaAtividade = ultimaAtividade;
            this.credenciais = credenciais;
        }

        private SessaoVirgo snapshot() {
            return new SessaoVirgo(id, usuario, ultimaAtividade, jobs.size());
        }

        private void atualizarAtividade(Instant agora) {
            if (agora.isAfter(ultimaAtividade)) ultimaAtividade = agora;
        }
    }
}
