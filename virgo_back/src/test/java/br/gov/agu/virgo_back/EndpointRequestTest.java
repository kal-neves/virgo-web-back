package br.gov.agu.virgo_back;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import br.gov.agu.virgo_back.pje.PjeSoapClient;
import br.gov.agu.virgo_back.pje.CredenciaisPje;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.client.WebServiceIOException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class EndpointRequestTest {

    @Autowired
    private PjeSoapClient client;

    @Value("${pje1}")
    private String PJE1;

    @Value("${pje2}")
    private String PJE2;

    @Test
    void ConsultarProcesso() {

        String login = System.getenv("TESTE_LOGIN");
        String senha = System.getenv("TESTE_SENHA");
        String numProcesso = System.getenv("TESTE_PROCESSO");

        CredenciaisPje user = new CredenciaisPje();
        user.setLogin(login);
        user.setSenha(senha);

        Document response;
        try {
            response = client.consultarProcesso(
                    PJE1,
                    user,
                    numProcesso
            );

            System.out.println(response.getDocumentElement().getTextContent());

            if (processoNaoEncontrado(response)) {
                response = client.consultarProcesso(
                        PJE2,
                        user,
                        numProcesso
                );
                System.out.println(response.getDocumentElement().getTextContent());
            }

        } catch (WebServiceIOException e) {
            response = client.consultarProcesso(
                    PJE2,
                    user,
                    numProcesso
            );

            System.out.println(response.getDocumentElement().getTextContent());
        }

        assertFalse(processoNaoEncontrado(response));
    }

    private boolean processoNaoEncontrado(Document response) {
        NodeList sucesso = response.getElementsByTagName("sucesso");
        return sucesso.getLength() > 0
                && "false".equals(sucesso.item(0).getTextContent());
    }
}
