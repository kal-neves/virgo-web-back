package br.gov.agu.virgo_back.consulta.domain;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConsultaGrau(
        UUID id,
        UUID itemRodadaId,
        OrigemPje origem,
        EstadoExecucao estado,
        @Nullable Instant inicio,
        @Nullable Instant fim,
        @Nullable StatusConsulta status,
        @Nullable String erro,
        int detectadas,
        int exibidas
) {
    public ConsultaGrau {
        Objects.requireNonNull(id, "ID da consulta e obrigatorio");
        Objects.requireNonNull(itemRodadaId, "Item da rodada e obrigatorio");
        Objects.requireNonNull(origem, "Origem e obrigatoria");
        Objects.requireNonNull(estado, "Estado e obrigatorio");
        estado.validarPeriodo(inicio, fim);
        if (exibidas < 0 || exibidas > detectadas) {
            throw new IllegalArgumentException("Contagens invalidas de deteccao/exibicao");
        }
        if (estado == EstadoExecucao.CONCLUIDA) {
            Objects.requireNonNull(status, "Consulta concluida exige resultado");
            if (status == StatusConsulta.ENCONTRADO) {
                if (erro != null) throw new IllegalArgumentException("Sucesso nao possui erro");
            } else if (erro == null || erro.isBlank() || detectadas != 0) {
                throw new IllegalArgumentException("Falha exige erro e nenhuma deteccao");
            }
        } else if (status != null || erro != null || detectadas != 0) {
            throw new IllegalArgumentException("Somente consultas concluidas possuem resultado e deteccoes");
        }
    }

    public static ConsultaGrau planejar(UUID id, UUID itemId, OrigemPje origem) {
        return new ConsultaGrau(id, itemId, origem, EstadoExecucao.PENDENTE, null, null, null, null, 0, 0);
    }

    public boolean sucesso() {
        return status == StatusConsulta.ENCONTRADO;
    }

    public boolean tentada() {
        return inicio != null;
    }

    public boolean necessitaConsulta() {
        return !sucesso() && estado != EstadoExecucao.EM_ANDAMENTO;
    }
}
