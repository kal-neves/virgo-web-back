package br.gov.agu.virgo_back.pje;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "pje")
public record PjeProperties (Endpoints endpoints,
                             Duration connectTimeout,
                             Duration readTimeout){

    public record Endpoints(
            URI trf1PrimeiroGrau,
            URI trf1SegundoGrau,
            URI trf6PrimeiroGrau,
            URI trf6SegundoGrau
    ) {}
}