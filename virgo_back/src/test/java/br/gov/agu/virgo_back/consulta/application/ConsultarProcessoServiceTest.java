package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.pje.CredenciaisPje;
import br.gov.agu.virgo_back.processo.domain.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConsultarProcessoServiceTest {
    @ParameterizedTest
    @EnumSource(value = StatusConsulta.class, names = {"ENCONTRADO", "NAO_ENCONTRADO", "INDISPONIVEL"})
    void consultaAmbosOsGrausEPreservaResultadosSeparados(StatusConsulta statusPrimeiro) {
        var numero = new NumeroProcesso("00000000020264010000");
        var credenciais = new CredenciaisPje();
        var movimento = new Movimentacao(new IdentificadorMovimento("-8"),
                LocalDateTime.of(2026, 9, 30, 10, 0), TipoMovimentacao.NACIONAL, 85, "teste");
        var primeiro = new RespostaConsultaOrigem(OrigemPje.TRF1PJE1, statusPrimeiro,
                statusPrimeiro == StatusConsulta.ENCONTRADO ? List.of(movimento) : List.of(),
                statusPrimeiro == StatusConsulta.ENCONTRADO ? null : "falha fictícia");
        var segundo = new RespostaConsultaOrigem(OrigemPje.TRF1PJE2, StatusConsulta.ENCONTRADO, List.of(movimento), null);
        var chamadas = new ArrayList<OrigemPje>();
        ConsultarProcessoGateway gateway = (origem, recebidas, recebido) -> {
            assertSame(credenciais, recebidas);
            assertEquals(numero, recebido);
            chamadas.add(origem);
            return origem == OrigemPje.TRF1PJE1 ? primeiro : segundo;
        };
        var resultado = new ConsultarProcessoService(gateway).consultarProcesso(credenciais, numero);
        assertEquals(List.of(OrigemPje.TRF1PJE1, OrigemPje.TRF1PJE2), chamadas);
        assertEquals(numero, resultado.numeroProcesso());
        assertEquals(List.of(primeiro, segundo), resultado.respostas());
    }
}
