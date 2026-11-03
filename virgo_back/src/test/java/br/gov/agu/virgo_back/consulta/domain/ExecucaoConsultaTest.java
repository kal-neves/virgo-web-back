package br.gov.agu.virgo_back.consulta.domain;

import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ExecucaoConsultaTest {
    private static final Instant INICIO = Instant.parse("2026-10-14T23:59:00Z");
    private static final Instant FIM = INICIO.plusSeconds(120);
    private static final UUID RODADA = UUID.randomUUID();
    private static final IdConsultante DONO = new IdConsultante("01234567890");
    private static final IdConsultante EXECUTOR = new IdConsultante("09876543210");
    private static final ZoneId ZONA = ZoneId.of("UTC");

    @Test
    void consultaPlanejadaNaoRepresentaFalhaNemTentativa() {
        var consulta = ConsultaGrau.planejar(UUID.randomUUID(), UUID.randomUUID(), origem(GrauJurisdicao.PRIMEIRO_GRAU));
        assertEquals(EstadoExecucao.PENDENTE, consulta.estado());
        assertNull(consulta.status());
        assertFalse(consulta.tentada());
        assertTrue(consulta.necessitaConsulta());
    }

    @Test
    void sucessoSemMovimentosEPreservadoNaContinuacao() {
        var consulta = concluida(UUID.randomUUID(), GrauJurisdicao.PRIMEIRO_GRAU, StatusConsulta.ENCONTRADO, 0, 0);
        assertTrue(consulta.sucesso());
        assertTrue(consulta.tentada());
        assertFalse(consulta.necessitaConsulta());
        var falha = concluida(consulta.itemRodadaId(), GrauJurisdicao.SEGUNDO_GRAU, StatusConsulta.INDISPONIVEL, 0, 0);
        assertTrue(falha.necessitaConsulta());
    }

    @Test
    void cancelamentoAntesDaChamadaNaoContaComoTentativaEChamadaAtivaNaoPodeSerReenviada() {
        UUID item = UUID.randomUUID();
        var cancelada = new ConsultaGrau(UUID.randomUUID(), item, origem(GrauJurisdicao.PRIMEIRO_GRAU),
                EstadoExecucao.CANCELADA, null, FIM, null, null, 0, 0);
        assertFalse(cancelada.tentada());
        assertTrue(cancelada.necessitaConsulta());
        var ativa = new ConsultaGrau(UUID.randomUUID(), item, origem(GrauJurisdicao.SEGUNDO_GRAU),
                EstadoExecucao.EM_ANDAMENTO, INICIO, null, null, null, 0, 0);
        assertTrue(ativa.tentada());
        assertFalse(ativa.necessitaConsulta());
        var outraAtiva = new ConsultaGrau(UUID.randomUUID(), item, origem(GrauJurisdicao.PRIMEIRO_GRAU),
                EstadoExecucao.EM_ANDAMENTO, INICIO, null, null, null, 0, 0);
        assertThrows(IllegalArgumentException.class, () -> item(item, 0, List.of(ativa, outraAtiva)));
        var itemAtivo = item(item, 0, List.of(cancelada, ativa));
        assertThrows(IllegalArgumentException.class, () -> rodada(EstadoExecucao.CANCELADA, List.of(itemAtivo)));
    }

    @Test
    void validaContagensResultadosEPeriodos() {
        UUID item = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> concluida(item, GrauJurisdicao.PRIMEIRO_GRAU, StatusConsulta.ENCONTRADO, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> concluida(item, GrauJurisdicao.PRIMEIRO_GRAU, StatusConsulta.ENCONTRADO, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> concluida(item, GrauJurisdicao.PRIMEIRO_GRAU, StatusConsulta.INDISPONIVEL, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConsultaGrau(UUID.randomUUID(), item,
                origem(GrauJurisdicao.PRIMEIRO_GRAU), EstadoExecucao.CONCLUIDA, FIM, INICIO,
                StatusConsulta.ENCONTRADO, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConsultaGrau(UUID.randomUUID(), item,
                origem(GrauJurisdicao.PRIMEIRO_GRAU), EstadoExecucao.EM_ANDAMENTO, null, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new ConsultaGrau(UUID.randomUUID(), item,
                origem(GrauJurisdicao.PRIMEIRO_GRAU), EstadoExecucao.CONCLUIDA, INICIO, FIM,
                StatusConsulta.INDISPONIVEL, null, 0, 0));
    }

    @ParameterizedTest
    @CsvSource({"ENCONTRADO, ENCONTRADO, SUCESSO", "ENCONTRADO, NAO_ENCONTRADO, SUCESSO",
            "INDISPONIVEL, ENCONTRADO, SUCESSO", "NAO_ENCONTRADO, INDISPONIVEL, FALHA"})
    void resultadoDoProcessoPreservaOsResultadosDosGraus(StatusConsulta primeiro, StatusConsulta segundo,
                                                        ResultadoExecucao esperado) {
        var item = itemConcluido(0, primeiro, segundo);
        assertEquals(esperado, item.resultado().orElseThrow());
        assertEquals(segundo, item.consultas().getLast().status());
        assertTrue(item.concluido());
    }

    @Test
    void itemExigeOsDoisGrausDaMesmaOrigemEVinculoCorreto() {
        UUID id = UUID.randomUUID();
        var primeiro = ConsultaGrau.planejar(UUID.randomUUID(), id, origem(GrauJurisdicao.PRIMEIRO_GRAU));
        var duplicado = ConsultaGrau.planejar(UUID.randomUUID(), id, origem(GrauJurisdicao.PRIMEIRO_GRAU));
        var outroTribunal = ConsultaGrau.planejar(UUID.randomUUID(), id, new OrigemPje(Tribunal.TRF6, GrauJurisdicao.SEGUNDO_GRAU));
        var outroItem = ConsultaGrau.planejar(UUID.randomUUID(), UUID.randomUUID(), origem(GrauJurisdicao.SEGUNDO_GRAU));
        assertThrows(IllegalArgumentException.class, () -> item(id, 0, List.of(primeiro)));
        assertThrows(IllegalArgumentException.class, () -> item(id, 0, List.of(primeiro, duplicado)));
        assertThrows(IllegalArgumentException.class, () -> item(id, 0, List.of(primeiro, outroTribunal)));
        assertThrows(IllegalArgumentException.class, () -> item(id, 0, List.of(primeiro, outroItem)));
    }

    @Test
    void interrupcaoNaoTransformaTrabalhoPendenteEmFalhaNemSucessoDaRodada() {
        UUID id = UUID.randomUUID();
        var primeiro = concluida(id, GrauJurisdicao.PRIMEIRO_GRAU, StatusConsulta.ENCONTRADO, 8, 2);
        var segundo = ConsultaGrau.planejar(UUID.randomUUID(), id, origem(GrauJurisdicao.SEGUNDO_GRAU));
        var item = item(id, 0, List.of(primeiro, segundo));
        var rodada = rodada(EstadoExecucao.INTERROMPIDA, List.of(item));
        assertEquals(ResultadoExecucao.SUCESSO, item.resultado().orElseThrow());
        assertFalse(item.concluido());
        assertTrue(rodada.resultado().isEmpty());
        assertEquals(1, rodada.processosTentados());
        assertEquals(0, rodada.processosConcluidos());
        assertEquals(8, rodada.detectadas());
        assertEquals(2, rodada.exibidas());
        assertThrows(IllegalArgumentException.class, () -> rodada(EstadoExecucao.CONCLUIDA, List.of(item)));
    }

    @Test
    void agregaResultadoDaRodadaPorProcesso() {
        var sucesso = itemConcluido(0, StatusConsulta.ENCONTRADO, StatusConsulta.INDISPONIVEL);
        var falha = itemConcluido(1, StatusConsulta.NAO_ENCONTRADO, StatusConsulta.INDISPONIVEL);
        assertEquals(ResultadoExecucao.SUCESSO, rodada(EstadoExecucao.CONCLUIDA, List.of(sucesso)).resultado().orElseThrow());
        assertEquals(ResultadoExecucao.FALHA, rodada(EstadoExecucao.CONCLUIDA, List.of(falha)).resultado().orElseThrow());
        var parcial = rodada(EstadoExecucao.CONCLUIDA, List.of(sucesso, falha));
        assertEquals(ResultadoExecucao.FALHA_PARCIAL, parcial.resultado().orElseThrow());
        assertEquals(2, parcial.processosConcluidos());
        assertEquals(2, parcial.processosTentados());
    }

    @Test
    void preservaSnapshotOrdemDonoExecutorEDataCapturada() {
        var primeiro = itemConcluido(0, StatusConsulta.ENCONTRADO, StatusConsulta.ENCONTRADO);
        var segundo = itemConcluido(1, StatusConsulta.ENCONTRADO, StatusConsulta.ENCONTRADO);
        var mutavel = new ArrayList<>(List.of(segundo, primeiro));
        var rodada = rodada(EstadoExecucao.CONCLUIDA, mutavel);
        mutavel.clear();
        assertEquals(List.of(primeiro, segundo), rodada.itens());
        assertThrows(UnsupportedOperationException.class, () -> rodada.itens().clear());
        assertThrows(UnsupportedOperationException.class, () -> primeiro.consultas().clear());
        assertEquals(DONO, rodada.proprietario());
        assertEquals(EXECUTOR, rodada.executor());
        assertEquals(LocalDate.of(2026, 10, 14), rodada.dataReferencia());
        assertEquals(" Lista ", rodada.nomeLista());
        assertEquals(0, rodada.janela().dias());
    }

    @Test
    void rejeitaRodadaVaziaItensDuplicadosEConsultaPontualComDoisProcessos() {
        var primeiro = itemConcluido(0, StatusConsulta.ENCONTRADO, StatusConsulta.ENCONTRADO);
        var segundo = itemConcluido(1, StatusConsulta.ENCONTRADO, StatusConsulta.ENCONTRADO);
        assertThrows(IllegalArgumentException.class, () -> rodada(EstadoExecucao.CONCLUIDA, List.of()));
        assertThrows(IllegalArgumentException.class, () -> rodada(EstadoExecucao.CONCLUIDA, List.of(primeiro, primeiro)));
        assertThrows(IllegalArgumentException.class, () -> new Rodada(RODADA, DONO, EXECUTOR,
                Rodada.Escopo.PROCESSO, null, null, new JanelaExibicao(0), LocalDate.of(2026, 10, 14), ZONA,
                EstadoExecucao.CONCLUIDA, INICIO, FIM, List.of(primeiro, segundo)));
    }

    private static ItemRodada itemConcluido(int ordem, StatusConsulta primeiro, StatusConsulta segundo) {
        UUID id = UUID.randomUUID();
        return item(id, ordem, List.of(concluida(id, GrauJurisdicao.PRIMEIRO_GRAU, primeiro, 0, 0),
                concluida(id, GrauJurisdicao.SEGUNDO_GRAU, segundo, 0, 0)));
    }

    private static ItemRodada item(UUID id, int ordem, List<ConsultaGrau> consultas) {
        return new ItemRodada(id, RODADA, UUID.randomUUID(),
                new NumeroProcesso(String.format("%020d", ordem + 1)), ordem, consultas);
    }

    private static ConsultaGrau concluida(UUID item, GrauJurisdicao grau, StatusConsulta status, int detectadas, int exibidas) {
        return new ConsultaGrau(UUID.randomUUID(), item, origem(grau), EstadoExecucao.CONCLUIDA,
                INICIO, FIM, status, status == StatusConsulta.ENCONTRADO ? null : "Erro do PJe", detectadas, exibidas);
    }

    private static OrigemPje origem(GrauJurisdicao grau) {
        return new OrigemPje(Tribunal.TRF1, grau);
    }

    private static Rodada rodada(EstadoExecucao estado, List<ItemRodada> itens) {
        return new Rodada(RODADA, DONO, EXECUTOR, Rodada.Escopo.LISTA, UUID.randomUUID(), " Lista ",
                new JanelaExibicao(0), LocalDate.of(2026, 10, 14), ZONA, estado, INICIO, FIM, itens);
    }
}
