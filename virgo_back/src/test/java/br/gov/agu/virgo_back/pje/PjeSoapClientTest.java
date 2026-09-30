package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMResult;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PjeSoapClientTest {
    private final WebServiceTemplate transporte = mock(WebServiceTemplate.class);
    private final PjeSoapClient cliente = new PjeSoapClient(transporte, new PjeRequestWriter(),
            new PjeResponseMapper(), "https://primeiro.invalid/soap", "https://segundo.invalid/soap");

    @ParameterizedTest
    @EnumSource(OrigemPje.class)
    void usaEndpointDaOrigemEMapeiaRespostaRealDoAdapter(OrigemPje origem) {
        String endpoint = origem == OrigemPje.TRF1PJE1
                ? "https://primeiro.invalid/soap" : "https://segundo.invalid/soap";
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
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar(OrigemPje.TRF1PJE1));
    }

    @Test
    void retornoSemDocumentoNaoViraSucessoVazio() {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenReturn(true);
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar(OrigemPje.TRF1PJE1));
    }

    @Test
    void falhaDeComunicacaoNaoRepeteChamadaNemExpoeDetalhes() {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenThrow(new WebServiceIOException("detalhe-interno-sensivel"));
        var resposta = consultar(OrigemPje.TRF1PJE1);
        assertFalha(StatusConsulta.INDISPONIVEL, resposta);
        assertFalse(resposta.erro().contains("detalhe-interno-sensivel"));
        verify(transporte).sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    private RespostaConsultaOrigem consultar(OrigemPje origem) {
        var credenciais = new CredenciaisPje();
        credenciais.setLogin("teste");
        credenciais.setSenha("senha-ficticia");
        return cliente.consultarProcesso(origem, credenciais, new NumeroProcesso("00000000020264010000"));
    }

    private void assertFalha(StatusConsulta status, RespostaConsultaOrigem resposta) {
        assertEquals(status, resposta.status());
        assertTrue(resposta.movimentacoes().isEmpty());
        assertNotNull(resposta.erro());
    }
}
