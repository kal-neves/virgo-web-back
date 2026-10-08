package br.gov.agu.virgo_back.identidade.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class IdConsultanteTest {
    @ParameterizedTest
    @ValueSource(strings = {"01234567890", "012.345.678-90", " 012.345.678-90 \t"})
    void normalizaCpfPreservandoZeroInicial(String valor) {
        assertEquals("01234567890", new IdConsultante(valor).valor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "0123456789", "012345678901", "abc01234567890", "01234567890<>&", "0123456789a"})
    void rejeitaFormatoInvalido(String valor) {
        assertThrows(IllegalArgumentException.class, () -> new IdConsultante(valor));
    }

    @ParameterizedTest
    @NullSource
    void exigeCpf(String valor) {
        assertThrows(NullPointerException.class, () -> new IdConsultante(valor));
    }
}
