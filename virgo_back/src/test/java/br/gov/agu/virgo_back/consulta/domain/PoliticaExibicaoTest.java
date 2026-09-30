package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.processo.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PoliticaExibicaoTest {
    private final PoliticaExibicao politica = new PoliticaExibicao();

    @ParameterizedTest
    @CsvSource({"2026-09-30, 0", "2026-09-30, 1", "2026-03-01, 1", "2024-03-01, 1", "2026-01-01, 1"})
    void incluiLimiteEFuturoMasExcluiInstanteAnterior(LocalDate referencia, int dias) {
        var limite = referencia.minusDays(dias).atStartOfDay();
        var antes = movimento("1", limite.minusNanos(1));
        var noLimite = movimento("2", limite);
        var futuro = movimento("3", referencia.plusDays(10).atStartOfDay());
        assertEquals(List.of(noLimite, futuro), politica.filtrar(
                List.of(antes, noLimite, futuro), referencia, new JanelaExibicao(dias)));
    }

    @Test
    void preservaEntradaEIdentidadeSemDeduplicarPorId() {
        var referencia = LocalDate.of(2026, 9, 30);
        var primeiro = movimento("mesmo-id", referencia.atTime(10, 0));
        var segundo = movimento("mesmo-id", referencia.atTime(11, 0));
        var entrada = new ArrayList<>(List.of(primeiro, segundo));
        var resultado = politica.filtrar(entrada, referencia, new JanelaExibicao(0));
        assertEquals(List.of(primeiro, segundo), entrada);
        assertEquals(entrada, resultado);
        entrada.clear();
        assertEquals(2, resultado.size());
        assertThrows(UnsupportedOperationException.class, () -> resultado.add(primeiro));
    }

    @Test
    void aceitaListaVaziaERejeitaArgumentosAusentes() {
        var data = LocalDate.of(2026, 9, 30);
        var janela = new JanelaExibicao(0);
        assertTrue(politica.filtrar(List.of(), data, janela).isEmpty());
        assertThrows(NullPointerException.class, () -> politica.filtrar(null, data, janela));
        assertThrows(NullPointerException.class, () -> politica.filtrar(List.of(), null, janela));
        assertThrows(NullPointerException.class, () -> politica.filtrar(List.of(), data, null));
    }

    private Movimentacao movimento(String id, LocalDateTime data) {
        return new Movimentacao(new IdentificadorMovimento(id), data, TipoMovimentacao.NACIONAL, 85, "teste");
    }
}
