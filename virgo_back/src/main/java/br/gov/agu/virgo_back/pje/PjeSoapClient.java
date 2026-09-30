package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.application.ConsultarProcessoGateway;
import br.gov.agu.virgo_back.processo.domain.NumeroProcesso;
import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import br.gov.agu.virgo_back.consulta.domain.StatusConsulta;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;

import javax.xml.transform.dom.DOMResult;
import java.util.List;

import br.gov.agu.virgo_back.consulta.domain.RespostaConsultaOrigem;


@Component
public class PjeSoapClient implements ConsultarProcessoGateway {

    private final WebServiceTemplate webServiceTemplate;
    private final PjeRequestWriter requestWriter;
    private final PjeResponseMapper responseMapper;
    private final String pje1;
    private final String pje2;

    public PjeSoapClient(WebServiceTemplate webServiceTemplate,
                         PjeRequestWriter requestWriter,
                         PjeResponseMapper responseMapper,
                         PjeProperties properties) {
        this.webServiceTemplate = webServiceTemplate;
        this.requestWriter = requestWriter;
        this.responseMapper = responseMapper;
        this.pje1 = properties.endpoints().trf1PrimeiroGrau().toString();
        this.pje2 = properties.endpoints().trf1SegundoGrau().toString();
    }

    @Override
    public RespostaConsultaOrigem consultarProcesso(OrigemPje origem, CredenciaisPje credenciais, NumeroProcesso numProcesso) {

        String uri = switch (origem) {
            case TRF1PJE1 -> pje1;
            case TRF1PJE2 -> pje2;
        };

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

            return responseMapper.mapearResposta(documento, origem);

        } catch (WebServiceIOException e) {
            return new RespostaConsultaOrigem(
                    origem,
                    StatusConsulta.INDISPONIVEL,
                    List.of(),
                    "Falha de comunicação com PJe");
        }
    }
}
