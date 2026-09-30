package br.gov.agu.virgo_back.consulta.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class JanelaExibicaoTest {
    @ParameterizedTest
    @ValueSource(ints = {0, 1, 30})
    void aceitaDiasNaoNegativos(int dias) {
        assertEquals(dias, new JanelaExibicao(dias).dias());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -30, Integer.MIN_VALUE})
    void rejeitaDiasNegativos(int dias) {
        assertThrows(IllegalArgumentException.class, () -> new JanelaExibicao(dias));
    }
}
