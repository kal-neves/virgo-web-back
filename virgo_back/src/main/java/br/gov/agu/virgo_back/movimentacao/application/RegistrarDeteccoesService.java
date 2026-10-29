package br.gov.agu.virgo_back.movimentacao.application;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.consulta.domain.PoliticaExibicao;
import br.gov.agu.virgo_back.consulta.persistence.ConsultaGrauRepository;
import br.gov.agu.virgo_back.consulta.persistence.RodadaRepository;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.movimentacao.domain.DetectorNovasMovimentacoes;
import br.gov.agu.virgo_back.movimentacao.domain.MovimentacaoDetectada;
import br.gov.agu.virgo_back.movimentacao.persistence.HistoricoMovimentacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class RegistrarDeteccoesService {
    private final HistoricoMovimentacaoRepository historico;
    private final ConsultaGrauRepository consultas;
    private final Clock clock;
    private final RodadaRepository rodadas;
    private final DetectorNovasMovimentacoes detector = new DetectorNovasMovimentacoes();
    private final PoliticaExibicao exibicao = new PoliticaExibicao();

    public RegistrarDeteccoesService(HistoricoMovimentacaoRepository historico,
                                    ConsultaGrauRepository consultas, Clock clock, RodadaRepository rodadas) {
        this.historico = historico;
        this.consultas = consultas;
        this.clock = clock;
        this.rodadas = rodadas;
    }

    /**
     * registra uma resposta avulsa e suas novas movimentações em uma transação própria
     * confirma o resultado e as detecções juntos antes de retornar
     * se o registro falhar, nenhuma alteração desta tentativa é confirmada
     * retorna todas as detecções inseridas sem aplicar a janela de exibição
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<MovimentacaoDetectada> registrar(UUID processoId, UUID consultaGrauId,
                                                RespostaConsultaOrigem resposta) {
        Objects.requireNonNull(processoId, "O processo monitorado e obrigatorio");
        Objects.requireNonNull(consultaGrauId, "A consulta de grau e obrigatoria");
        validarResposta(resposta);

        var instante = clock.instant();
        consultas.registrar(consultaGrauId, processoId, resposta, instante);
        var inseridas = inserirDeteccoes(processoId, consultaGrauId, resposta, instante);
        consultas.registrarQuantidadeDetectada(consultaGrauId, inseridas.size());
        return inseridas;
    }

    /**
     * registra o resultado de um grau ja iniciado
     * o chamador deve autorizar o executor
     * tentativa, deteccoes e conclusao sao confirmadas juntas antes de retornar
     * retorna todas as deteccoes inseridas, inclusive as que ficam fora da janela de exibicao
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<MovimentacaoDetectada> registrarPlanejada(
            IdConsultante proprietario, UUID rodadaId, UUID consultaId, UUID tentativaId,
            Instant inicioEsperado, RespostaConsultaOrigem resposta) {
        Objects.requireNonNull(proprietario, "Proprietario e obrigatorio");
        Objects.requireNonNull(rodadaId, "Rodada e obrigatoria");
        Objects.requireNonNull(consultaId, "Consulta planejada e obrigatoria");
        Objects.requireNonNull(tentativaId, "Tentativa e obrigatoria");
        Objects.requireNonNull(inicioEsperado, "Inicio da execucao e obrigatorio");
        validarResposta(resposta);
        var rodada = rodadas.buscarPorId(proprietario, rodadaId)
                .orElseThrow(() -> new IllegalArgumentException("Rodada inexistente para o proprietario"));
        var item = rodada.itens().stream()
                .filter(i -> i.consultas().stream().anyMatch(c -> c.id().equals(consultaId)))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Consulta nao pertence a rodada"));
        var instante = clock.instant();
        // O repository valida origem, inicio e estado sob lock antes de gravar qualquer deteccao.
        consultas.registrarTentativa(proprietario, consultaId, tentativaId, inicioEsperado, instante, resposta);
        var inseridas = inserirDeteccoes(item.processoMonitoradoId(), tentativaId, resposta, instante);
        int exibidas = exibicao.filtrar(inseridas.stream().map(MovimentacaoDetectada::movimentacao).toList(),
                rodada.dataReferencia(), rodada.janela()).size();
        consultas.concluir(proprietario, consultaId, tentativaId, inseridas.size(), exibidas);
        return inseridas;
    }

    private static void validarResposta(RespostaConsultaOrigem resposta) {
        Objects.requireNonNull(resposta, "A resposta e obrigatoria");
        boolean sucesso = resposta.status() == StatusConsulta.ENCONTRADO;
        if (sucesso && resposta.erro() != null) {
            throw new IllegalArgumentException("Uma resposta bem-sucedida nao pode conter erro");
        }
        if (!sucesso && (resposta.erro() == null || resposta.erro().isBlank()
                || !resposta.movimentacoes().isEmpty())) {
            throw new IllegalArgumentException("Uma resposta de falha exige erro e nenhuma movimentacao");
        }

    }

    private List<MovimentacaoDetectada> inserirDeteccoes(UUID processoId, UUID tentativaId,
                                                       RespostaConsultaOrigem resposta, Instant instante) {
        if (resposta.status() != StatusConsulta.ENCONTRADO) return List.of();

        var conhecidos = historico.buscarIdsConhecidos(processoId, resposta.origem().grau());
        var novas = detector.detectar(resposta.movimentacoes(), conhecidos);
        var inseridas = new ArrayList<MovimentacaoDetectada>();
        // Ordem estavel de insercao evita deadlocks entre respostas com IDs em ordens distintas.
        var ordenadas = novas.stream().sorted(Comparator.comparing(m -> m.id().valor())).toList();
        for (var movimento : ordenadas) {
            var deteccao = new MovimentacaoDetectada(processoId, resposta.origem(), movimento,
                    instante, tentativaId);
            if (historico.inserirSeAusente(deteccao)) inseridas.add(deteccao);
        }
        return List.copyOf(inseridas);
    }
}
