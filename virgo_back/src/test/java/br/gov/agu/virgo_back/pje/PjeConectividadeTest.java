package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.GrauJurisdicao;
import br.gov.agu.virgo_back.processo.domain.Tribunal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PjeConectividadeTest {
    private final WebServiceTemplate transporte = mock(WebServiceTemplate.class);
    private final OrigemPje origem = new OrigemPje(Tribunal.TRF6, GrauJurisdicao.SEGUNDO_GRAU);
    private final CredenciaisPje credenciais = new CredenciaisPje("senha-ficticia", new IdConsultante("01234567890"));
    private final URI endpoint = URI.create("https://prova.invalid/soap");
    private final PjeProperties properties = new PjeProperties(
            new PjeProperties.Endpoints(endpoint, endpoint, endpoint, endpoint),
            Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(3), "00000000000000000000");
    private final PjeSoapClient cliente = new PjeSoapClient(transporte, new PjeRequestWriter(),
            new PjeResponseMapper(), new ResolverEndpointPje(properties), properties);

    @ParameterizedTest
    @CsvSource(delimiter = '|', quoteCharacter = '"', value = {
            "true|Processo consultado com sucesso|ENCONTRADO",
            "false|Número do processo inválido: 00000000000000000000|PROCESSO_INVALIDO",
            "false|Processo de número 00000000000000000000 não encontrado!|NAO_ENCONTRADO",
            "false|Erro ao realizar login via MNI. exception invoking: loginFailed|ACESSO_NEGADO",
            "false|Erro ao realizar login via MNI. O usuário '01234567890' não está corretamente cadastrado no sistema.|ACESSO_NEGADO",
            "false|O usuário '01234567890' não está corretamente cadastrado no sistema.|ACESSO_NEGADO",
            "false|Mensagem desconhecida|RESPOSTA_INVALIDA",
            "true|Mensagem desconhecida|RESPOSTA_INVALIDA",
            "true|Número do processo inválido: 00000000000000000000|RESPOSTA_INVALIDA",
            "false|Processo consultado com sucesso|RESPOSTA_INVALIDA",
            "sim|Processo consultado com sucesso|RESPOSTA_INVALIDA",
            "false|Aviso: Processo de número 00000000000000000000 não encontrado!|RESPOSTA_INVALIDA"
    })
    void verificaSomenteMensagensReconhecidasSemMapearMovimentacoes(String sucesso, String mensagem, StatusConsulta esperado) {
        responder(resposta(sucesso, mensagem));
        assertEquals(esperado, cliente.verificar(origem, credenciais));
        verify(transporte).sendSourceAndReceiveToResult(eq(endpoint.toString()), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<resposta/>",
            "<s:consultarProcessoResposta xmlns:s='http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/'/>",
            "<s:consultarProcessoResposta xmlns:s='http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/' xmlns:t='errado'><t:sucesso>true</t:sucesso><t:mensagem>Processo consultado com sucesso</t:mensagem></s:consultarProcessoResposta>"
    })
    void estruturaInvalidaNaoConfirmaCredenciais(String xml) {
        responder(xml);
        assertEquals(StatusConsulta.RESPOSTA_INVALIDA, cliente.verificar(origem, credenciais));
    }

    @Test
    void enviaNumeroConfiguradoETrataTimeoutSemRepetir() {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenAnswer(invocacao -> {
                    var xml = new StringWriter();
                    TransformerFactory.newInstance().newTransformer().transform(invocacao.getArgument(1), new StreamResult(xml));
                    assertTrue(xml.toString().contains(properties.numeroProva()));
                    throw new WebServiceIOException("senha-ficticia");
                });
        assertEquals(StatusConsulta.INDISPONIVEL, cliente.verificar(origem, credenciais));
        verify(transporte).sendSourceAndReceiveToResult(eq(endpoint.toString()), any(Source.class), any(DOMResult.class));
        verifyNoMoreInteractions(transporte);
    }

    @Test
    void camposDuplicadosOuRespostaAusenteNaoConfirmamCredenciais() {
        assertEquals(StatusConsulta.RESPOSTA_INVALIDA, cliente.verificar(origem, credenciais));
        responder(resposta("true", "Processo consultado com sucesso")
                .replace("</s:consultarProcessoResposta>", "<t:sucesso>true</t:sucesso></s:consultarProcessoResposta>"));
        assertEquals(StatusConsulta.RESPOSTA_INVALIDA, cliente.verificar(origem, credenciais));
    }

    private void responder(String xml) {
        when(transporte.sendSourceAndReceiveToResult(anyString(), any(Source.class), any(DOMResult.class)))
                .thenAnswer(invocacao -> {
                    DOMResult resultado = invocacao.getArgument(2);
                    resultado.setNode(XmlFixtures.parse(xml));
                    return true;
                });
    }

    private String resposta(String sucesso, String mensagem) {
        return """
                <s:consultarProcessoResposta xmlns:s="http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/"
                    xmlns:t="http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2">
                    <t:sucesso>%s</t:sucesso><t:mensagem>%s</t:mensagem>
                </s:consultarProcessoResposta>
                """.formatted(sucesso, mensagem);
    }
}
