package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Rodada(
        UUID id,
        IdConsultante proprietario,
        IdConsultante executor,
        Escopo escopo,
        @Nullable UUID listaId,
        @Nullable String nomeLista,
        JanelaExibicao janela,
        LocalDate dataReferencia,
        ZoneId zonaReferencia,
        EstadoExecucao estado,
        @Nullable Instant inicio,
        @Nullable Instant fim,
        List<ItemRodada> itens
) {
    public enum Escopo { LISTA, PROCESSO }

    public Rodada {
        Objects.requireNonNull(id, "ID da rodada e obrigatorio");
        Objects.requireNonNull(proprietario, "Proprietario e obrigatorio");
        Objects.requireNonNull(executor, "Executor e obrigatorio");
        Objects.requireNonNull(escopo, "Escopo e obrigatorio");
        Objects.requireNonNull(janela, "Janela e obrigatoria");
        Objects.requireNonNull(dataReferencia, "Data capturada e obrigatoria");
        Objects.requireNonNull(zonaReferencia, "Fuso capturado e obrigatorio");
        Objects.requireNonNull(estado, "Estado e obrigatorio");
        estado.validarPeriodo(inicio, fim);
        if ((listaId == null) != (nomeLista == null) || (escopo == Escopo.LISTA && listaId == null)) {
            throw new IllegalArgumentException("Contexto de lista exige ID e nome capturado");
        }
        itens = List.copyOf(itens).stream().sorted(Comparator.comparingInt(ItemRodada::ordem)).toList();
        if (itens.isEmpty() || (escopo == Escopo.PROCESSO && itens.size() != 1)) {
            throw new IllegalArgumentException("Rodada exige itens; consulta pontual exige exatamente um");
        }
        var ids = new HashSet<UUID>();
        var processos = new HashSet<UUID>();
        var numeros = new HashSet<NumeroProcesso>();
        var ordens = new HashSet<Integer>();
        var consultas = new HashSet<UUID>();
        for (var item : itens) {
            if (!item.rodadaId().equals(id) || !ids.add(item.id())
                    || !processos.add(item.processoMonitoradoId()) || !numeros.add(item.numeroProcesso())
                    || !ordens.add(item.ordem())) {
                throw new IllegalArgumentException("Itens devem pertencer a rodada, sem processos, IDs ou ordens repetidos");
            }
            for (var consulta : item.consultas()) {
                if (!consultas.add(consulta.id())) throw new IllegalArgumentException("ID de consulta repetido");
                if (estado == EstadoExecucao.PENDENTE && consulta.estado() != EstadoExecucao.PENDENTE) {
                    throw new IllegalArgumentException("Rodada pendente exige consultas pendentes");
                }
                if (estado != EstadoExecucao.EM_ANDAMENTO && consulta.estado() == EstadoExecucao.EM_ANDAMENTO) {
                    throw new IllegalArgumentException("Consulta ativa exige rodada em andamento");
                }
            }
        }
        if (estado == EstadoExecucao.CONCLUIDA && itens.stream().anyMatch(i -> !i.concluido())) {
            throw new IllegalArgumentException("Rodada concluida exige todos os graus concluidos");
        }
        if (itens.stream().flatMap(i -> i.consultas().stream())
                .filter(c -> c.estado() == EstadoExecucao.EM_ANDAMENTO).count() > 1) {
            throw new IllegalArgumentException("Rodada permite somente uma chamada em andamento");
        }
    }

    public Optional<ResultadoExecucao> resultado() {
        if (estado != EstadoExecucao.CONCLUIDA) return Optional.empty();
        long sucessos = itens.stream().filter(i -> i.resultado().orElseThrow() == ResultadoExecucao.SUCESSO).count();
        if (sucessos == itens.size()) return Optional.of(ResultadoExecucao.SUCESSO);
        return Optional.of(sucessos == 0 ? ResultadoExecucao.FALHA : ResultadoExecucao.FALHA_PARCIAL);
    }

    public int processosTentados() {
        return (int) itens.stream().filter(ItemRodada::tentado).count();
    }

    public int processosConcluidos() {
        return (int) itens.stream().filter(ItemRodada::concluido).count();
    }

    public int detectadas() {
        return itens.stream().mapToInt(ItemRodada::detectadas).sum();
    }

    public int exibidas() {
        return itens.stream().mapToInt(ItemRodada::exibidas).sum();
    }
}
