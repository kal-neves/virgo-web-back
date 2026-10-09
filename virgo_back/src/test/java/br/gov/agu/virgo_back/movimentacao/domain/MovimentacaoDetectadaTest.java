package br.gov.agu.virgo_back.movimentacao.domain;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.processo.domain.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MovimentacaoDetectadaTest {
    private final UUID processoId = UUID.randomUUID();
    private final OrigemPje origem = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU);
    private final Movimentacao movimento = new Movimentacao(new IdentificadorMovimento("-8"),
            LocalDateTime.of(2020, 1, 1, 10, 0), TipoMovimentacao.NACIONAL, 85, "Descrição original");
    private final Instant agora = Instant.parse("2026-10-08T15:00:00Z");

    @Test
    void chaveSeparaProcessosEGrausMesmoComIdDeMovimentoIgual() {
        var deteccao = detectar(processoId, origem, movimento, agora);
        var outroProcesso = detectar(UUID.randomUUID(), origem, movimento, agora);
        var outroGrau = detectar(processoId, new OrigemPje(Tribunal.TRF1, GrauJurisdicao.SEGUNDO_GRAU), movimento, agora);
        assertNotEquals(deteccao.chave(), outroProcesso.chave());
        assertNotEquals(deteccao.chave(), outroGrau.chave());
    }

    @Test
    void metadadosNaoAlteramChaveNemConfundemDataJudicialComDeteccao() {
        var deteccao = detectar(processoId, origem, movimento, agora);
        var alterado = new Movimentacao(movimento.id(), movimento.dataHora().plusDays(1),
                TipoMovimentacao.LOCAL, 123, "Descrição atualizada");
        var outraConsulta = detectar(processoId, new OrigemPje(Tribunal.TRF6, origem.grau()), alterado, agora.plusSeconds(60));
        assertEquals(deteccao.chave(), outraConsulta.chave());
        assertEquals(movimento.dataHora(), deteccao.movimentacao().dataHora());
        assertEquals(agora, deteccao.primeiraDeteccao());
    }

    private MovimentacaoDetectada detectar(UUID processo, OrigemPje fonte, Movimentacao evento, Instant instante) {
        return new MovimentacaoDetectada(processo, fonte, evento, instante, UUID.randomUUID());
    }
}
