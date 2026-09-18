package br.gov.agu.virgo_back.processo.domain;

import java.time.LocalDateTime;

public record Movimentacao (LocalDateTime dataHora, String descricao) {
}
