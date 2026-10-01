package br.gov.agu.virgo_back.consulta.application;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.pje.CredenciaisPje;
import br.gov.agu.virgo_back.processo.domain.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConsultarProcessoServiceTest {
    @ParameterizedTest
    @CsvSource({
            "TRF1, ENCONTRADO", "TRF1, NAO_ENCONTRADO", "TRF1, INDISPONIVEL",
            "TRF6, ENCONTRADO", "TRF6, NAO_ENCONTRADO", "TRF6, INDISPONIVEL"
    })
    void consultaAmbosOsGrausEPreservaResultadosSeparados(Tribunal tribunal, StatusConsulta statusPrimeiro) {
        var primeiroGrau = new OrigemPje(tribunal, GrauJurisdicao.PRIMEIRO_GRAU);
        var segundoGrau = new OrigemPje(tribunal, GrauJurisdicao.SEGUNDO_GRAU);
        var numero = new NumeroProcesso("00000000020264010000");
        var credenciais = new CredenciaisPje();
        var movimento = new Movimentacao(new IdentificadorMovimento("-8"),
                LocalDateTime.of(2026, 9, 30, 10, 0), TipoMovimentacao.NACIONAL, 85, "teste");
        var primeiro = new RespostaConsultaOrigem(primeiroGrau, statusPrimeiro,
                statusPrimeiro == StatusConsulta.ENCONTRADO ? List.of(movimento) : List.of(),
                statusPrimeiro == StatusConsulta.ENCONTRADO ? null : "falha fictícia");
        var segundo = new RespostaConsultaOrigem(segundoGrau, StatusConsulta.ENCONTRADO, List.of(movimento), null);
        var chamadas = new ArrayList<OrigemPje>();
        ConsultarProcessoGateway gateway = (origem, recebidas, recebido) -> {
            assertSame(credenciais, recebidas);
            assertEquals(numero, recebido);
            chamadas.add(origem);
            return origem.equals(primeiroGrau) ? primeiro : segundo;
        };
        var resultado = new ConsultarProcessoService(gateway).consultarProcesso(credenciais, numero, tribunal);
        assertEquals(List.of(primeiroGrau, segundoGrau), chamadas);
        assertEquals(numero, resultado.numeroProcesso());
        assertEquals(List.of(primeiro, segundo), resultado.respostas());
    }
}
