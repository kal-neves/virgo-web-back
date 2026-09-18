package br.gov.agu.virgo_back.processo.domain;

import java.util.List;

public record ResultadoConsultaProcesso(
        NumeroProcesso numeroProcesso,
        List<RespostaConsultaOrigem> respostas
) {
    public ResultadoConsultaProcesso {
        respostas = List.copyOf(respostas);
    }
}

