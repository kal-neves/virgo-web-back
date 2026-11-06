package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.consulta.execution.ExecutorSequencialRodada;
import br.gov.agu.virgo_back.consulta.persistence.RodadaRepository;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.identidade.session.CredenciaisJobStore;
import br.gov.agu.virgo_back.pje.ResolverEndpointPje;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.persistence.ProcessoMonitoradoRepository;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ExecutarRodadaService {
    private static final Logger log = LoggerFactory.getLogger(ExecutarRodadaService.class);
    private final RodadaRepository rodadas;
    private final ProcessoMonitoradoRepository processos;
    private final CredenciaisJobStore credenciaisJobs;
    private final VerificarConectividadeGateway conectividade;
    private final ResolverEndpointPje endpoints;
    private final ExecutorSequencialRodada executor;
    private final Clock clock;
    private final TransactionTemplate transacao;
    private final ExecutorService trabalhadores;

    @Autowired
    public ExecutarRodadaService(RodadaRepository rodadas, ProcessoMonitoradoRepository processos,
                                 CredenciaisJobStore credenciaisJobs, VerificarConectividadeGateway conectividade,
                                 ResolverEndpointPje endpoints, ExecutorSequencialRodada executor,
                                 Clock clock, PlatformTransactionManager transacoes) {
        this(rodadas, processos, credenciaisJobs, conectividade, endpoints, executor, clock,
                transacoes, Executors.newVirtualThreadPerTaskExecutor());
    }

    ExecutarRodadaService(RodadaRepository rodadas, ProcessoMonitoradoRepository processos,
                          CredenciaisJobStore credenciaisJobs, VerificarConectividadeGateway conectividade,
                          ResolverEndpointPje endpoints, ExecutorSequencialRodada executor,
                          Clock clock, PlatformTransactionManager transacoes, ExecutorService trabalhadores) {
        this.rodadas = rodadas;
        this.processos = processos;
        this.credenciaisJobs = credenciaisJobs;
        this.conectividade = conectividade;
        this.endpoints = endpoints;
        this.executor = executor;
        this.clock = clock;
        this.transacao = new TransactionTemplate(transacoes);
        this.trabalhadores = trabalhadores;
    }

    /**
     * recebe uma seleção cuja sessão e permissão de acesso já foram verificadas pelo chamador
     * suspende a transação do chamador para não manter o banco ocupado durante a verificação do PJe
     * confirma a criação da rodada antes de entregar o trabalho ao executor independente
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Rodada iniciar(IdConsultante proprietario, IdConsultante executorId, Rodada.Escopo escopo,
                         @Nullable UUID listaId, @Nullable String nomeLista, List<UUID> processoIds,
                         JanelaExibicao janela, CredenciaisPje credenciais) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(escopo);
        Objects.requireNonNull(janela);
        var ids = List.copyOf(processoIds);
        if (ids.isEmpty() || new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("Selecao exige processos sem repeticoes");
        }
        UUID rodadaId = UUID.randomUUID();
        var reserva = credenciaisJobs.reservar(rodadaId, executorId, credenciais);
        boolean entregue = false;
        try {
            var instante = clock.instant();
            var itens = new ArrayList<ItemRodada>();
            Map<OrigemPje, URI> destinos = new LinkedHashMap<>();
            for (UUID processoId : ids) {
                var processo = processos.buscarPorId(proprietario, processoId)
                        .orElseThrow(() -> new IllegalArgumentException("Processo inexistente para o proprietario"));
                if (processo.tribunalConfirmado() == null) {
                    throw new IllegalStateException("Processo ainda nao possui tribunal confirmado");
                }
                UUID itemId = UUID.randomUUID();
                var graus = new ArrayList<ConsultaGrau>();
                for (var grau : GrauJurisdicao.values()) {
                    var origem = new OrigemPje(processo.tribunalConfirmado(), grau);
                    destinos.computeIfAbsent(origem, endpoints::resolver);
                    graus.add(ConsultaGrau.planejar(UUID.randomUUID(), itemId, origem));
                }
                itens.add(new ItemRodada(itemId, rodadaId, processo.id(), processo.numeroProcesso(), itens.size(), graus));
            }
            var rodada = new Rodada(rodadaId, proprietario, executorId, escopo, listaId, nomeLista, janela,
                    LocalDate.ofInstant(instante, clock.getZone()), clock.getZone(), EstadoExecucao.PENDENTE,
                    null, null, itens);
            for (var origem : destinos.keySet()) {
                var status = conectividade.verificar(origem, reserva.credenciais());
                if (status != StatusConsulta.ENCONTRADO && status != StatusConsulta.NAO_ENCONTRADO
                        && status != StatusConsulta.PROCESSO_INVALIDO) {
                    throw new IllegalStateException("PJe nao confirmou acesso a " + origem + ": " + status);
                }
            }
            transacao.executeWithoutResult(ignorada -> rodadas.criar(rodada, destinos));
            try {
                trabalhadores.execute(() -> executar(rodada, reserva));
                entregue = true;
            } catch (RuntimeException | Error falha) {
                interromper(rodada);
                throw falha;
            }
            return rodada;
        } finally {
            if (!entregue) reserva.close();
        }
    }

    private void executar(Rodada rodada, CredenciaisJobStore.Reserva reserva) {
        try (reserva) {
            try {
                if (!executor.executar(rodada.proprietario(), rodada.executor(), rodada.id(), reserva.credenciais())) {
                    interromper(rodada);
                }
            } catch (RuntimeException | Error falha) {
                interromper(rodada);
                log.error("Falha na execucao da rodada {}", rodada.id());
            }
        }
    }

    private void interromper(Rodada rodada) {
        try {
            rodadas.interromper(rodada.proprietario(), rodada.executor(), rodada.id(), clock.instant());
        } catch (RuntimeException falha) {
            log.error("Nao foi possivel registrar a interrupcao da rodada {}", rodada.id());
        }
    }

    @PreDestroy
    public void encerrar() {
        trabalhadores.close();
    }
}
