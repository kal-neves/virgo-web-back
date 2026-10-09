package br.gov.agu.virgo_back.pje;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.webservices.autoconfigure.client.WebServiceTemplateAutoConfiguration;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@ImportAutoConfiguration(WebServiceTemplateAutoConfiguration.class)
@SpringBootTest(classes = SoapConfig.class, properties = {
        "pje.connect-timeout=1250ms",
        "pje.read-timeout=2500ms",
        "pje.call-timeout=3s",
        "pje.endpoints.trf1-primeiro-grau=https://trf1-primeiro.invalid/soap",
        "pje.endpoints.trf1-segundo-grau=https://trf1-segundo.invalid/soap",
        "pje.endpoints.trf6-primeiro-grau=https://trf6-primeiro.invalid/soap",
        "pje.endpoints.trf6-segundo-grau=https://trf6-segundo.invalid/soap"
})
class SoapConfigTest {
    @Autowired
    private PjeProperties properties;

    @Test
    void vinculaEndpointsDosDoisTribunais() {
        assertEquals(URI.create("https://trf1-primeiro.invalid/soap"), properties.endpoints().trf1PrimeiroGrau());
        assertEquals(URI.create("https://trf1-segundo.invalid/soap"), properties.endpoints().trf1SegundoGrau());
        assertEquals(URI.create("https://trf6-primeiro.invalid/soap"), properties.endpoints().trf6PrimeiroGrau());
        assertEquals(URI.create("https://trf6-segundo.invalid/soap"), properties.endpoints().trf6SegundoGrau());
    }

    @Test
    void vinculaTimeoutsIndependentes() {
        assertEquals(Duration.ofMillis(1250), properties.connectTimeout());
        assertEquals(Duration.ofMillis(2500), properties.readTimeout());
        assertEquals(Duration.ofSeconds(3), properties.callTimeout());
    }

    @Test
    void rejeitaTimeoutsAusentesOuQueDesabilitariamOLimite() {
        var endpoints = properties.endpoints();
        var valido = Duration.ofSeconds(1);
        for (Duration invalido : java.util.Arrays.asList(null, Duration.ZERO, Duration.ofMillis(-1), Duration.ofNanos(1))) {
            assertThrows(IllegalArgumentException.class, () -> new PjeProperties(endpoints, invalido, valido, valido));
            assertThrows(IllegalArgumentException.class, () -> new PjeProperties(endpoints, valido, invalido, valido));
            assertThrows(IllegalArgumentException.class, () -> new PjeProperties(endpoints, valido, valido, invalido));
        }
    }
}
