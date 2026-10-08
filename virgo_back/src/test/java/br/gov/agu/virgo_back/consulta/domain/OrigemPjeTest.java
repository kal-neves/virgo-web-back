package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OrigemPjeTest {
    @Test
    void identidadeDistingueTribunalEGrau() {
        var origem = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU);
        var origens = Set.of(origem,
                new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU),
                new OrigemPje(Tribunal.TRF6, GrauJurisdicao.PRIMEIRO_GRAU),
                new OrigemPje(Tribunal.TRF6, GrauJurisdicao.SEGUNDO_GRAU));

        assertEquals(4, origens.size());
        assertTrue(origens.contains(new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU)));
    }

    @Test
    void rejeitaOrigemIncompleta() {
        assertThrows(NullPointerException.class, () -> new OrigemPje(null, GrauJurisdicao.PRIMEIRO_GRAU));
        assertThrows(NullPointerException.class, () -> new OrigemPje(Tribunal.TRF1, null));
    }
}
