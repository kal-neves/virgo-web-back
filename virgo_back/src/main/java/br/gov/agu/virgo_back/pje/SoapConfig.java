package br.gov.agu.virgo_back.pje;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
public class SoapConfig {

    @Bean
    public WebServiceTemplate webServiceTemplate() {
        return new WebServiceTemplate();
    }
}

