package br.gov.agu.virgo_back.pje;

import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.transport.http.HttpUrlConnectionMessageSender;

@Configuration
@EnableConfigurationProperties(PjeProperties.class)
public class SoapConfig {

    @Bean
    public WebServiceTemplate webServiceTemplate(WebServiceTemplateBuilder builder, PjeProperties properties) {
        var sender = new HttpUrlConnectionMessageSender();
        sender.setConnectionTimeout(properties.connectTimeout());
        sender.setReadTimeout(properties.readTimeout());

        return builder
                .messageSenders(sender)
                .build();
    }
}

