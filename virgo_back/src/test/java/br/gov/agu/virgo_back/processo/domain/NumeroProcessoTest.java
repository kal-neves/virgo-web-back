package br.gov.agu.virgo_back.processo.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class NumeroProcessoTest {
    @ParameterizedTest
    @ValueSource(strings = {"00000000020264010000", "0000000-00.2026.4.01.0000", " 0000000-00.2026.4.01.0000 "})
    void normalizaFormatoSemPerderZeros(String entrada) {
        assertEquals(new NumeroProcesso("00000000020264010000"), new NumeroProcesso(entrada));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "123", "000000000202640100000", "0000000-AA.2026.4.01.0000", "0000000/00.2026.4.01.0000"})
    void rejeitaFormatoInvalido(String entrada) {
        assertThrows(IllegalArgumentException.class, () -> new NumeroProcesso(entrada));
    }
}
