package br.gov.agu.virgo_back.consulta.execution;

import br.gov.agu.virgo_back.consulta.application.ConsultarProcessoService;
import br.gov.agu.virgo_back.consulta.domain.ConsultaGrau;
import br.gov.agu.virgo_back.consulta.domain.EstadoExecucao;
import br.gov.agu.virgo_back.consulta.domain.Rodada;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.consulta.persistence.ConsultaGrauRepository;
import br.gov.agu.virgo_back.consulta.persistence.RodadaRepository;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

@Service
public class ExecutorSequencialRodada {
    private final RodadaRepository rodadas;
    private final ConsultaGrauRepository consultas;
    private final ConsultarProcessoService consultaService;
    private final Clock clock;

    public ExecutorSequencialRodada(RodadaRepository rodadas, ConsultaGrauRepository consultas,
                                    ConsultarProcessoService consultaService, Clock clock) {
        this.rodadas = rodadas;
        this.consultas = consultas;
        this.consultaService = consultaService;
        this.clock = clock;
    }

    /**
     * executa uma rodada pendente já autorizada e validada pelo chamador
     * suspende a transação do chamador e confirma cada grau antes do próximo envio
     * retorna false se a rodada não puder iniciar e não retoma rodadas anteriores
     * o chamador escolhe o trabalhador que executará este método fora da requisição web
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public boolean executar(IdConsultante proprietario, IdConsultante executor, UUID rodadaId,
                            CredenciaisPje credenciais) {
        Objects.requireNonNull(proprietario);
        Objects.requireNonNull(executor);
        Objects.requireNonNull(rodadaId);
        Objects.requireNonNull(credenciais);
        if (!executor.equals(credenciais.login())) {
            throw new IllegalArgumentException("Credenciais nao pertencem ao executor");
        }
        var rodada = rodadas.buscarPorId(proprietario, rodadaId)
                .orElseThrow(() -> new IllegalArgumentException("Rodada inexistente para o proprietario"));
        if (!executor.equals(rodada.executor())) {
            throw new IllegalArgumentException("Executor nao pertence a rodada");
        }
        if (rodada.estado() != EstadoExecucao.PENDENTE) return false;
        if (!rodadas.iniciar(proprietario, executor, rodadaId, clock.instant())) {
            if (rodadas.cancelamentoSolicitado(proprietario, executor, rodadaId)) {
                rodadas.cancelar(proprietario, executor, rodadaId, clock.instant());
            }
            return false;
        }
        try {
            for (var item : rodada.itens()) {
                var graus = item.consultas().stream()
                        .sorted(Comparator.comparing(c -> c.origem().grau())).toList();
                for (ConsultaGrau consulta : graus) {
                    if (cancelarSeSolicitado(rodada)) return true;
                    var inicio = clock.instant();
                    if (!consultas.iniciar(proprietario, consulta.id(), inicio)) {
                        if (cancelarSeSolicitado(rodada)) return true;
                        throw new IllegalStateException("Grau planejado nao pode iniciar");
                    }
                    var resposta = consultaService.consultarGrauPlanejado(rodada, item, consulta, inicio, credenciais);
                    if (resposta.status() == StatusConsulta.ACESSO_NEGADO) {
                        if (!cancelarSeSolicitado(rodada)
                                && !rodadas.interromper(proprietario, executor, rodadaId, clock.instant())) {
                            throw new IllegalStateException("Rodada nao pode ser interrompida");
                        }
                        return true;
                    }
                }
            }
            if (!rodadas.concluir(proprietario, executor, rodadaId, clock.instant()) && !cancelarSeSolicitado(rodada)) {
                throw new IllegalStateException("Rodada nao pode ser concluida");
            }
            return true;
        } catch (RuntimeException falha) {
            try {
                rodadas.interromper(proprietario, executor, rodadaId, clock.instant());
            } catch (RuntimeException falhaAoInterromper) {
                falha.addSuppressed(falhaAoInterromper);
            }
            throw falha;
        }
    }

    private boolean cancelarSeSolicitado(Rodada rodada) {
        if (!rodadas.cancelamentoSolicitado(rodada.proprietario(), rodada.executor(), rodada.id())) return false;
        if (!rodadas.cancelar(rodada.proprietario(), rodada.executor(), rodada.id(), clock.instant())) {
            throw new IllegalStateException("Rodada nao pode ser cancelada");
        }
        return true;
    }
}
