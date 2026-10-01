package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.OrigemPje;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class ResolverEndpointPje {
    private final PjeProperties.Endpoints endpoints;

    public ResolverEndpointPje(PjeProperties properties) {
        this.endpoints = properties.endpoints();
    }

    public URI resolver(OrigemPje origem) {
        return switch (origem.tribunal()) {
            case TRF1 -> switch (origem.grau()) {
                case PRIMEIRO_GRAU -> endpoints.trf1PrimeiroGrau();
                case SEGUNDO_GRAU -> endpoints.trf1SegundoGrau();
            };
            case TRF6 -> switch (origem.grau()) {
                case PRIMEIRO_GRAU -> endpoints.trf6PrimeiroGrau();
                case SEGUNDO_GRAU -> endpoints.trf6SegundoGrau();
            };
        };
    }
}
