package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.processo.domain.Movimentacao;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

public class PoliticaExibicao {

    public List<Movimentacao> filtrar(List<Movimentacao> novasMovimentacoes, LocalDate dataReferencia, JanelaExibicao janela) {
        Objects.requireNonNull(novasMovimentacoes);
        Objects.requireNonNull(dataReferencia);
        Objects.requireNonNull(janela);

        LocalDate dataLimite = dataReferencia.minusDays(janela.dias());

        return novasMovimentacoes.stream()
                .filter(deteccao ->
                        !deteccao.dataHora()
                        .toLocalDate()
                        .isBefore(dataLimite)
                )
                .toList();
        }
    }
