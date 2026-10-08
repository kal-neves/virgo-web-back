package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import java.net.URI;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PjeSoapClientTest {
    private final WebServiceTemplate transporte = mock(WebServiceTemplate.class);
    private final PjeSoapClient cliente = new PjeSoapClient(transporte, new PjeRequestWriter(),
            new PjeResponseMapper(), new ResolverEndpointPje(new PjeProperties(new PjeProperties.Endpoints(
                    URI.create("https://primeiro.invalid/soap"), URI.create("https://segundo.invalid/soap"),
                    URI.create("https://trf6-primeiro.invalid/soap"), URI.create("https://trf6-segundo.invalid/soap")),
                    Duration.ofSeconds(5), Duration.ofSeconds(30), Duration.ofSeconds(30))));

    @ParameterizedTest
    @CsvSource({
            "TRF1, PRIMEIRO_GRAU, https://primeiro.invalid/soap",
            "TRF1, SEGUNDO_GRAU, https://segundo.invalid/soap",
            "TRF6, PRIMEIRO_GRAU, https://trf6-primeiro.invalid/soap",
            "TRF6, SEGUNDO_GRAU, https://trf6-segundo.invalid/soap"
    })
    void usaEndpointDaOrigemEMapeiaRespostaRealDoAdapter(Tribunal tribunal, GrauJurisdicao grau, String endpoint) {
        var origem = new OrigemPje(tribunal, grau);
        when(transporte.sendSourceAndReceiveToResult(eq(endpoint), any(Source.class), any(DOMResult.class)))
                .thenAnswer(invocacao -> {
                    DOMResult resultado = invocacao.getArgument(2);
                    resultado.setNode(XmlFixtures.parse(XmlFixtures.movimentos()));
                    return true;
                });
        var resposta = consultar(origem);
        assertEquals(StatusConsulta.ENCONTRADO, resposta.status());
        assertEquals(origem, resposta.origem());
        assertEquals(2, resposta.movimentacoes().size());
        assertEquals("-0008", resposta.movimentacoes().getFirst().id().valor());
        verify(transporte).sendSourceAndReceiveToResult(eq(endpoint), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    @Test
    void ausenciaDeRespostaNaoViraSucessoVazio() {
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar(new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU)));
    }

    @Test
    void retornoSemDocumentoNaoViraSucessoVazio() {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenReturn(true);
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar(new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU)));
    }

    @Test
    void falhaDeComunicacaoNaoRepeteChamadaNemExpoeDetalhes() {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenThrow(new WebServiceIOException("detalhe-interno-sensivel"));
        var resposta = consultar(new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU));
        assertFalha(StatusConsulta.INDISPONIVEL, resposta);
        assertFalse(resposta.erro().contains("detalhe-interno-sensivel"));
        verify(transporte).sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    private RespostaConsultaOrigem consultar(OrigemPje origem) {
        var credenciais = new CredenciaisPje("senha-ficticia", new IdConsultante("01234567890"));
        return cliente.consultarProcesso(origem, credenciais, new NumeroProcesso("00000000020264010000"));
    }

    private void assertFalha(StatusConsulta status, RespostaConsultaOrigem resposta) {
        assertEquals(status, resposta.status());
        assertTrue(resposta.movimentacoes().isEmpty());
        assertNotNull(resposta.erro());
    }
}
