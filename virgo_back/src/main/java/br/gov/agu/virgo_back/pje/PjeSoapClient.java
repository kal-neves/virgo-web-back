package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.application.ConsultarProcessoGateway;
import br.gov.agu.virgo_back.consulta.application.VerificarConectividadeGateway;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.InvalidXmlException;
import org.springframework.ws.soap.SoapMessageCreationException;
import org.springframework.ws.soap.client.SoapFaultClientException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;

import javax.xml.transform.dom.DOMResult;
import java.util.List;
import java.net.URI;
import java.util.Objects;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;


@Component
public class PjeSoapClient implements ConsultarProcessoGateway, VerificarConectividadeGateway {

    private final WebServiceTemplate webServiceTemplate;
    private final PjeRequestWriter requestWriter;
    private final PjeResponseMapper responseMapper;
    private final ResolverEndpointPje resolverEndpoint;
    private final NumeroProcesso numeroProva;

    public PjeSoapClient(WebServiceTemplate webServiceTemplate,
                         PjeRequestWriter requestWriter,
                         PjeResponseMapper responseMapper,
                         ResolverEndpointPje resolverEndpoint, PjeProperties properties) {
        this.webServiceTemplate = webServiceTemplate;
        this.requestWriter = requestWriter;
        this.responseMapper = responseMapper;
        this.resolverEndpoint = resolverEndpoint;
        this.numeroProva = new NumeroProcesso(properties.numeroProva());
    }

    @Override
    public StatusConsulta verificar(OrigemPje origem, CredenciaisPje credenciais) {
        return enviar(origem, resolverEndpoint.resolver(origem), credenciais, numeroProva, true).status();
    }

    @Override
    public RespostaConsultaOrigem consultarProcesso(OrigemPje origem, CredenciaisPje credenciais, NumeroProcesso numProcesso) {

        return consultarProcesso(origem, resolverEndpoint.resolver(origem), credenciais, numProcesso);
    }

    @Override
    public RespostaConsultaOrigem consultarProcesso(OrigemPje origem, URI endpoint,
                                                   CredenciaisPje credenciais, NumeroProcesso numProcesso) {
        return enviar(origem, endpoint, credenciais, numProcesso, false);
    }

    private RespostaConsultaOrigem enviar(OrigemPje origem, URI endpoint, CredenciaisPje credenciais,
                                          NumeroProcesso numProcesso, boolean prova) {
        Objects.requireNonNull(origem);
        Objects.requireNonNull(endpoint);
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                || !("https".equalsIgnoreCase(endpoint.getScheme()) || "http".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("Endpoint HTTP(S) sem credenciais e obrigatorio");
        }
        String uri = endpoint.toString();

        StringSource request = new StringSource(
                requestWriter.gerarRequest(credenciais, numProcesso)
        );

        DOMResult response = new DOMResult();

        try {
            boolean recebeuResposta = webServiceTemplate.sendSourceAndReceiveToResult(
                    uri, request, response
            );

            if (!recebeuResposta || !(response.getNode() instanceof Document documento)) {
                return new RespostaConsultaOrigem(
                        origem,
                        StatusConsulta.RESPOSTA_INVALIDA,
                        List.of(),
                        "PJe não retornou documento de resposta");
            }

            return prova ? responseMapper.mapearConectividade(documento, origem)
                    : responseMapper.mapearResposta(documento, origem);

        } catch (SoapFaultClientException | SoapMessageCreationException | InvalidXmlException e) {
            return new RespostaConsultaOrigem(origem, StatusConsulta.RESPOSTA_INVALIDA,
                    List.of(), "PJe retornou uma resposta SOAP inválida ou uma falha SOAP");
        } catch (WebServiceIOException e) {
            return new RespostaConsultaOrigem(
                    origem,
                    StatusConsulta.INDISPONIVEL,
                    List.of(),
                    "Falha de comunicação com PJe");
        }
    }
}
