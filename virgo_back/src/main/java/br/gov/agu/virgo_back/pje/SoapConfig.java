package br.gov.agu.virgo_back.pje;

import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import java.util.concurrent.*;

@Configuration
@EnableConfigurationProperties(PjeProperties.class)
public class SoapConfig {

    @Bean(destroyMethod = "shutdownNow")
    public ExecutorService pjeDnsExecutor() {
        return new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(16),
                Thread.ofPlatform().daemon().name("pje-dns-", 0).factory());
    }

    @Bean
    public CloseableHttpAsyncClient pjeHttpClient(PjeProperties properties) {
        var connections = PoolingAsyncClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(properties.connectTimeout().toMillis()))
                        .setSocketTimeout(Timeout.ofMilliseconds(properties.readTimeout().toMillis()))
                        .build())
                .build();
        return HttpAsyncClients.custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.callTimeout().toMillis()))
                        .setResponseTimeout(Timeout.ofMilliseconds(properties.readTimeout().toMillis()))
                        .setHardCancellationEnabled(true)
                        .build())
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .build();
    }

    @Bean
    PjeSoapMessageFactory pjeSoapMessageFactory() {
        return new PjeSoapMessageFactory();
    }

    @Bean
    WebServiceTemplate webServiceTemplate(WebServiceTemplateBuilder builder, PjeProperties properties,
                                                CloseableHttpAsyncClient pjeHttpClient,
                                                PjeSoapMessageFactory messageFactory,
                                                ExecutorService pjeDnsExecutor) {
        pjeHttpClient.start();

        return builder
                .messageSenders(new PjeMessageSender(pjeHttpClient, properties.callTimeout(),
                        pjeDnsExecutor, SystemDefaultDnsResolver.INSTANCE))
                .setWebServiceMessageFactory(messageFactory)
                .build();
    }
}
