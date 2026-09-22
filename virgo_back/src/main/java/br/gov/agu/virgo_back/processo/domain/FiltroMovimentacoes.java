package br.gov.agu.virgo_back.processo.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class FiltroMovimentacoes {

    public List<Movimentacao> filtrar(List<Movimentacao> movimentacoes, ModoConsulta modo, PeriodoConsulta periodo) {
        Objects.requireNonNull(movimentacoes, "Movimentações são obrigatórias");
        Objects.requireNonNull(modo, "Modo de consulta é obrigatório");

        return switch (modo) {
            case INTERVALO -> {
                Objects.requireNonNull(periodo, "Período é obrigatório para consulta por intervalo");
                yield movimentacoes.stream()
                        .filter(movimentacao -> {
                            LocalDate data = movimentacao.dataHora().toLocalDate();
                            return !data.isBefore(periodo.inicio()) && !data.isAfter(periodo.fim());
                        })
                        .toList();
            }
            case ULTIMA_DATA -> {
                LocalDate ultimaData = movimentacoes.stream()
                        .map(movimentacao -> movimentacao.dataHora().toLocalDate())
                        .max(LocalDate::compareTo)
                        .orElse(null);

                yield movimentacoes.stream()
                        .filter(movimentacao -> movimentacao.dataHora().toLocalDate().equals(ultimaData))
                        .toList();
            }
            case COMPLETO -> List.copyOf(movimentacoes);
        };
    }
}
