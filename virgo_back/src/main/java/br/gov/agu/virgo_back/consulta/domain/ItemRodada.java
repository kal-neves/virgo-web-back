package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Snapshot de um processo; a ordem e sua posicao (a partir de zero) na rodada. */
public record ItemRodada(
        UUID id,
        UUID rodadaId,
        UUID processoMonitoradoId,
        NumeroProcesso numeroProcesso,
        int ordem,
        List<ConsultaGrau> consultas
) {
    public ItemRodada {
        Objects.requireNonNull(id, "ID do item e obrigatorio");
        Objects.requireNonNull(rodadaId, "Rodada e obrigatoria");
        Objects.requireNonNull(processoMonitoradoId, "Processo monitorado e obrigatorio");
        Objects.requireNonNull(numeroProcesso, "CNJ e obrigatorio");
        consultas = List.copyOf(consultas);
        if (ordem < 0) throw new IllegalArgumentException("Ordem nao pode ser negativa");
        if (consultas.size() != 2) throw new IllegalArgumentException("Cada item exige os dois graus");
        var primeira = consultas.getFirst();
        var segunda = consultas.getLast();
        if (consultas.stream().anyMatch(c -> !c.itemRodadaId().equals(id))
                || primeira.id().equals(segunda.id())
                || primeira.origem().grau() == segunda.origem().grau()
                || primeira.origem().tribunal() != segunda.origem().tribunal()) {
            throw new IllegalArgumentException("Consultas devem pertencer ao item e a graus distintos do mesmo tribunal");
        }
        if (consultas.stream().filter(c -> c.estado() == EstadoExecucao.EM_ANDAMENTO).count() > 1) {
            throw new IllegalArgumentException("Graus devem executar sequencialmente");
        }
    }

    public boolean concluido() {
        return consultas.stream().allMatch(c -> c.estado() == EstadoExecucao.CONCLUIDA);
    }

    public boolean tentado() {
        return consultas.stream().anyMatch(ConsultaGrau::tentada);
    }

    public Optional<ResultadoExecucao> resultado() {
        if (consultas.stream().anyMatch(ConsultaGrau::sucesso)) return Optional.of(ResultadoExecucao.SUCESSO);
        return concluido() ? Optional.of(ResultadoExecucao.FALHA) : Optional.empty();
    }

    public int detectadas() {
        return consultas.stream().mapToInt(ConsultaGrau::detectadas).sum();
    }

    public int exibidas() {
        return consultas.stream().mapToInt(ConsultaGrau::exibidas).sum();
    }
}
