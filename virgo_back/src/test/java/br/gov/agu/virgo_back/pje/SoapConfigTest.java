package br.gov.agu.virgo_back.pje;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnection;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "pje.connect-timeout=1250ms",
        "pje.read-timeout=2500ms",
        "pje.endpoints.trf1-primeiro-grau=https://trf1-primeiro.invalid/soap",
        "pje.endpoints.trf1-segundo-grau=https://trf1-segundo.invalid/soap",
        "pje.endpoints.trf6-primeiro-grau=https://trf6-primeiro.invalid/soap",
        "pje.endpoints.trf6-segundo-grau=https://trf6-segundo.invalid/soap"
})
class SoapConfigTest {
    @Autowired
    private PjeProperties properties;

    @Autowired
    private WebServiceTemplate template;

    @Test
    void vinculaEndpointsDosDoisTribunais() {
        assertEquals(URI.create("https://trf1-primeiro.invalid/soap"), properties.endpoints().trf1PrimeiroGrau());
        assertEquals(URI.create("https://trf1-segundo.invalid/soap"), properties.endpoints().trf1SegundoGrau());
        assertEquals(URI.create("https://trf6-primeiro.invalid/soap"), properties.endpoints().trf6PrimeiroGrau());
        assertEquals(URI.create("https://trf6-segundo.invalid/soap"), properties.endpoints().trf6SegundoGrau());
    }

    @Test
    void aplicaTimeoutsConfiguradosNaConexao() throws Exception {
        var senders = template.getMessageSenders();
        assertEquals(1, senders.length);
        // Creating the connection does not send a request or contact the endpoint.
        try (var connection = senders[0].createConnection(properties.endpoints().trf1PrimeiroGrau())) {
            var http = assertInstanceOf(HttpUrlConnection.class, connection).getConnection();
            assertEquals(1250, http.getConnectTimeout());
            assertEquals(2500, http.getReadTimeout());
        }
    }
}
