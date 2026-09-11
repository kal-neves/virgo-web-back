package br.gov.agu.virgo_back.pje;

import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;
import org.w3c.dom.Document;

import javax.xml.transform.dom.DOMResult;

@Component
public class PjeSoapClient {

    private final WebServiceTemplate webServiceTemplate;

    public PjeSoapClient(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public Document enviarRequest(String uri, CredenciaisPje user, String numProcesso) {

        StringSource request = new StringSource(
                gerarRequest(user, numProcesso)
        );

        DOMResult response = new DOMResult();

        webServiceTemplate.sendSourceAndReceiveToResult(
                uri,
                request,
                response
        );

        return (Document) response.getNode();
    }

    private String gerarRequest(CredenciaisPje user, String numProcesso) {
        return """
                <ser:consultarProcesso
                    xmlns:ser="http://www.cnj.jus.br/servico-intercomunicacao-2.2.2/"
                    xmlns:tip="http://www.cnj.jus.br/tipos-servico-intercomunicacao-2.2.2">

                    <tip:idConsultante>%s</tip:idConsultante>
                    <tip:senhaConsultante>%s</tip:senhaConsultante>
                    <tip:numeroProcesso>%s</tip:numeroProcesso>
                    <!--Optional:-->
                    <tip:movimentos>true</tip:movimentos>

                </ser:consultarProcesso>
                """.formatted(
                user.getLogin(),
                user.getSenha(),
                numProcesso
        );
    }
}
