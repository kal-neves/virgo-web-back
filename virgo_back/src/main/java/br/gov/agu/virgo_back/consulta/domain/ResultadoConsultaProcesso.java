package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;

import java.util.List;
import java.util.Objects;

public record ResultadoConsultaProcesso(
        NumeroProcesso numeroProcesso,
        List<RespostaConsultaOrigem> respostas
) {
    public ResultadoConsultaProcesso {
        Objects.requireNonNull(numeroProcesso, "Número de processo é obrigatório");
        Objects.requireNonNull(respostas, "As respostas são obrigatórias");
        respostas = List.copyOf(respostas);
    }
}

