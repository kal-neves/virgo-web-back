package br.gov.agu.virgo_back.processo.domain;

import java.util.List;
import java.util.Objects;

public record ResultadoConsultaProcesso(
        NumeroProcesso numeroProcesso,
        List<RespostaConsultaOrigem> respostas
) {
    public ResultadoConsultaProcesso {
        Objects.requireNonNull(numeroProcesso, "As movimentações são obrigatórias");
        Objects.requireNonNull(respostas, "O modo de consulta é obrigatório");
        respostas = List.copyOf(respostas);
    }
}

