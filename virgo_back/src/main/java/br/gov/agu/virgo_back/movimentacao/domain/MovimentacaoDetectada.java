package br.gov.agu.virgo_back.movimentacao.domain;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;
import br.gov.agu.virgo_back.processo.domain.Movimentacao;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record MovimentacaoDetectada(
        UUID processoMonitoradoId,
        OrigemPje origem,
        Movimentacao movimentacao,
        Instant primeiraDeteccao,
        UUID consultaGrauId
) {
    public MovimentacaoDetectada {
        Objects.requireNonNull(processoMonitoradoId, "O processo monitorado é obrigatório");
        Objects.requireNonNull(origem, "A origem é obrigatória");
        Objects.requireNonNull(movimentacao, "A movimentação é obrigatória");
        Objects.requireNonNull(primeiraDeteccao, "O instante da primeira detecção é obrigatório");
        Objects.requireNonNull(consultaGrauId, "A consulta de grau que detectou o movimento é obrigatória");
    }

    public Chave chave() {
        return new Chave(processoMonitoradoId, origem.grau(), movimentacao.id());
    }

    public record Chave(UUID processoMonitoradoId, GrauJurisdicao grau, IdentificadorMovimento movimentoId) {
        public Chave {
            Objects.requireNonNull(processoMonitoradoId, "O processo monitorado é obrigatório");
            Objects.requireNonNull(grau, "O grau é obrigatório");
            Objects.requireNonNull(movimentoId, "O ID da movimentação é obrigatório");
        }
    }
}
