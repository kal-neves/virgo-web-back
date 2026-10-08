package br.gov.agu.virgo_back.movimentacao.domain;

import br.gov.agu.virgo_back.processo.domain.IdentificadorMovimento;
import br.gov.agu.virgo_back.processo.domain.Movimentacao;
import br.gov.agu.virgo_back.processo.domain.TipoMovimentacao;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DetectorNovasMovimentacoesTest {
    private final DetectorNovasMovimentacoes detector = new DetectorNovasMovimentacoes();

    @Test
    void historicoVazioIncluiTodosOsIdsSemOrdenarOuFiltrarPorData() {
        var recebidas = List.of(movimento("100", "2026-10-08T12:00:00"),
                movimento("-8", "2020-01-01T10:00:00"),
                movimento("local-25", "2030-01-01T10:00:00"));

        assertEquals(recebidas, detector.detectar(recebidas, Set.of()));
    }

    @Test
    void excluiIdsConhecidosMesmoSeOutrosCamposMudarem() {
        var conhecido = movimento("100", "2026-10-08T12:00:00");
        var alterado = new Movimentacao(conhecido.id(), LocalDateTime.of(2026, 10, 9, 10, 0),
                TipoMovimentacao.LOCAL, 123, "Descrição atualizada");
        var novo = movimento("-8", "2020-01-01T10:00:00");

        assertEquals(List.of(novo), detector.detectar(List.of(alterado, novo), Set.of(conhecido.id())));
    }

    @Test
    void repeticoesMantemSomenteAPrimeiraOcorrenciaDoIdNovo() {
        var primeiro = movimento("-8", "2026-10-08T12:00:00");
        var repetido = movimento("-8", "2026-10-09T12:00:00");
        var outro = movimento("local-25", "2026-10-07T12:00:00");

        assertEquals(List.of(primeiro, outro),
                detector.detectar(List.of(primeiro, outro, repetido, primeiro), Set.of()));
    }

    @Test
    void respostaVaziaOuApenasIdsConhecidosNaoProduzNovidades() {
        var movimento = movimento("100", "2026-10-08T12:00:00");
        var conhecidos = Set.of(movimento.id());

        assertTrue(detector.detectar(List.of(), conhecidos).isEmpty());
        assertTrue(detector.detectar(List.of(movimento, movimento), conhecidos).isEmpty());
    }

    @Test
    void naoAlteraEntradasNemRetemIdsEntreConsultas() {
        var conhecido = movimento("100", "2026-10-08T12:00:00");
        var novo = movimento("-8", "2020-01-01T10:00:00");
        var recebidas = new ArrayList<>(List.of(conhecido, novo, novo));
        var conhecidos = new HashSet<>(Set.of(conhecido.id()));

        var resultado = detector.detectar(recebidas, conhecidos);

        assertEquals(List.of(conhecido, novo, novo), recebidas);
        assertEquals(Set.of(conhecido.id()), conhecidos);
        assertEquals(List.of(novo), resultado);
        assertThrows(UnsupportedOperationException.class, () -> resultado.add(conhecido));
        assertEquals(resultado, detector.detectar(recebidas, conhecidos));
        // Outro processo ou grau pode ter o mesmo ID, mas um histórico independente.
        assertEquals(List.of(conhecido), detector.detectar(List.of(conhecido), Set.of()));
    }

    @Test
    void exigeRecebidasEIdsConhecidos() {
        assertThrows(NullPointerException.class, () -> detector.detectar(null, Set.of()));
        assertThrows(NullPointerException.class, () -> detector.detectar(List.of(), null));
    }

    private Movimentacao movimento(String id, String dataHora) {
        return new Movimentacao(new IdentificadorMovimento(id), LocalDateTime.parse(dataHora),
                TipoMovimentacao.NACIONAL, 85, "Descrição fictícia");
    }
}
