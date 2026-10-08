package br.gov.agu.virgo_back.pje;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "pje")
public record PjeProperties (Endpoints endpoints,
                             Duration connectTimeout,
                             Duration readTimeout,
                             Duration callTimeout){

    public PjeProperties {
        validarTimeout(connectTimeout, "connect-timeout");
        validarTimeout(readTimeout, "read-timeout");
        validarTimeout(callTimeout, "call-timeout");
    }

    private static void validarTimeout(Duration valor, String nome) {
        if (valor == null || valor.compareTo(Duration.ofMillis(1)) < 0) {
            throw new IllegalArgumentException("pje." + nome + " deve ser de pelo menos 1ms");
        }
    }

    public record Endpoints(
            URI trf1PrimeiroGrau,
            URI trf1SegundoGrau,
            URI trf6PrimeiroGrau,
            URI trf6SegundoGrau
    ) {}
}
