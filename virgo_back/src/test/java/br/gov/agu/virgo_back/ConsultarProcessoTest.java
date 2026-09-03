package br.gov.agu.virgo_back;

import br.gov.agu.virgo_back.client.ClientConsultarProcesso;
import br.gov.agu.virgo_back.entities.User;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.client.WebServiceIOException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class ConsultarProcessoTest {

    @Autowired
    private ClientConsultarProcesso client;

    @Value("${pje1}")
    private String PJE1;

    @Value("${pje2}")
    private String PJE2;

    @Test
    void ConsultarProcesso() {

        String login = System.getenv("TESTE_LOGIN");
        String senha = System.getenv("TESTE_SENHA");
        String numProcesso = System.getenv("TESTE_PROCESSO");

        User user = new User();
        user.setLogin(login);
        user.setSenha(senha);

        String response;
        try {
            response = client.enviarRequest(
                    PJE1,
                    user,
                    numProcesso
            );

            System.out.println(response);

            if (response.contains("<sucesso>false</sucesso>")) {
                response = client.enviarRequest(
                        PJE2,
                        user,
                        numProcesso
                );
                System.out.println(response);
            }

        } catch (WebServiceIOException e) {
            response = client.enviarRequest(
                    PJE2,
                    user,
                    numProcesso
            );

            System.out.println(response);
            assertFalse(response.contains("<sucesso>false</sucesso>"));
        }
    }
}
