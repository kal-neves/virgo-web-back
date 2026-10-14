package br.gov.agu.virgo_back.movimentacao.application;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.consulta.persistence.ConsultaGrauRepository;
import br.gov.agu.virgo_back.movimentacao.domain.DetectorNovasMovimentacoes;
import br.gov.agu.virgo_back.movimentacao.domain.MovimentacaoDetectada;
import br.gov.agu.virgo_back.movimentacao.persistence.HistoricoMovimentacaoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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
    private final DetectorNovasMovimentacoes detector = new DetectorNovasMovimentacoes();

    public RegistrarDeteccoesService(HistoricoMovimentacaoRepository historico,
                                    ConsultaGrauRepository consultas, Clock clock) {
        this.historico = historico;
        this.consultas = consultas;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<MovimentacaoDetectada> registrar(UUID processoId, UUID consultaGrauId,
                                                RespostaConsultaOrigem resposta) {
        Objects.requireNonNull(processoId, "O processo monitorado e obrigatorio");
        Objects.requireNonNull(consultaGrauId, "A consulta de grau e obrigatoria");
        Objects.requireNonNull(resposta, "A resposta e obrigatoria");
        boolean sucesso = resposta.status() == StatusConsulta.ENCONTRADO;
        if (sucesso && resposta.erro() != null) {
            throw new IllegalArgumentException("Uma resposta bem-sucedida nao pode conter erro");
        }
        if (!sucesso && (resposta.erro() == null || resposta.erro().isBlank()
                || !resposta.movimentacoes().isEmpty())) {
            throw new IllegalArgumentException("Uma resposta de falha exige erro e nenhuma movimentacao");
        }

        var instante = clock.instant();
        consultas.registrar(consultaGrauId, processoId, resposta, instante);
        if (!sucesso) return List.of();

        var conhecidos = historico.buscarIdsConhecidos(processoId, resposta.origem().grau());
        var novas = detector.detectar(resposta.movimentacoes(), conhecidos);
        var inseridas = new ArrayList<MovimentacaoDetectada>();
        // Ordem estavel de insercao evita deadlocks entre respostas com IDs em ordens distintas.
        var ordenadas = novas.stream().sorted(Comparator.comparing(m -> m.id().valor())).toList();
        for (var movimento : ordenadas) {
            var deteccao = new MovimentacaoDetectada(processoId, resposta.origem(), movimento,
                    instante, consultaGrauId);
            if (historico.inserirSeAusente(deteccao)) inseridas.add(deteccao);
        }
        consultas.registrarQuantidadeDetectada(consultaGrauId, inseridas.size());
        return List.copyOf(inseridas);
    }
}
