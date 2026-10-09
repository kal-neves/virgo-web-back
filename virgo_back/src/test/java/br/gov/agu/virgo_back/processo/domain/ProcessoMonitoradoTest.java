package br.gov.agu.virgo_back.processo.domain;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProcessoMonitoradoTest {
    private final IdConsultante proprietario = new IdConsultante("01234567890");
    private final NumeroProcesso numero = new NumeroProcesso("00000000020264010000");

    @Test
    void permiteCadastroAntesDaDescobertaDoTribunal() {
        var processo = new ProcessoMonitorado(UUID.randomUUID(), proprietario, numero, null, Set.of(), "", false);
        assertNull(processo.tribunalConfirmado());
        assertTrue(processo.grausObservados().isEmpty());
    }

    @Test
    void protegeGrausObservadosEPreservaTextoDaAnotacao() {
        var graus = new HashSet<>(Set.of(GrauJurisdicao.PRIMEIRO_GRAU));
        var processo = new ProcessoMonitorado(UUID.randomUUID(), proprietario, numero, Tribunal.TRF1,
                graus, "  Revisar\nurgente  ", true);
        graus.add(GrauJurisdicao.SEGUNDO_GRAU);
        assertEquals(Set.of(GrauJurisdicao.PRIMEIRO_GRAU), processo.grausObservados());
        assertThrows(UnsupportedOperationException.class, () -> processo.grausObservados().clear());
        assertEquals("  Revisar\nurgente  ", processo.anotacao());
    }

    @Test
    void rejeitaGrauObservadoSemTribunalConfirmado() {
        assertThrows(IllegalArgumentException.class, () -> new ProcessoMonitorado(UUID.randomUUID(),
                proprietario, numero, null, Set.of(GrauJurisdicao.PRIMEIRO_GRAU), "", false));
    }
}
