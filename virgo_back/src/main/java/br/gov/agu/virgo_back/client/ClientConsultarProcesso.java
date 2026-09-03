package br.gov.agu.virgo_back.client;

import br.gov.agu.virgo_back.entities.User;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.xml.transform.StringSource;

import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;

@Component
public class ClientConsultarProcesso {

    private final WebServiceTemplate webServiceTemplate;

    public ClientConsultarProcesso(WebServiceTemplate webServiceTemplate) {
        this.webServiceTemplate = webServiceTemplate;
    }

    public String enviarRequest(String uri, User user, String numProcesso) {

        StringSource request = new StringSource(gerarRequest(user, numProcesso));
        StringWriter response = new StringWriter();

        webServiceTemplate.sendSourceAndReceiveToResult(
                uri,
                request,
                new StreamResult(response)
        );

        return response.toString();
    }

    private String gerarRequest(User user, String numProcesso) {
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
