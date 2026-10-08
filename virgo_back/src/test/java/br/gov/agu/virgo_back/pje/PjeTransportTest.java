package br.gov.agu.virgo_back.pje;

import br.gov.agu.virgo_back.consulta.domain.*;
import br.gov.agu.virgo_back.identidade.application.CredenciaisPje;
import br.gov.agu.virgo_back.identidade.domain.IdConsultante;
import br.gov.agu.virgo_back.processo.domain.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.webservices.client.WebServiceTemplateBuilder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Real HTTP and SOAP stack; all endpoints belong to an ephemeral loopback server. */
class PjeTransportTest {
    private HttpServer server;
    private ExecutorService handlers;
    private ExecutorService dnsExecutor;
    private CloseableHttpAsyncClient httpClient;
    private PjeSoapClient client;
    private WebServiceTemplate template;
    private final AtomicInteger requests = new AtomicInteger();
    private static final OrigemPje ORIGEM = new OrigemPje(Tribunal.TRF1, GrauJurisdicao.PRIMEIRO_GRAU);

    @BeforeEach
    void configurar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        handlers = Executors.newVirtualThreadPerTaskExecutor();
        server.setExecutor(handlers);
        server.start();
        dnsExecutor = new SoapConfig().pjeDnsExecutor();
        configurarCliente(Duration.ofSeconds(2), Duration.ofSeconds(5));
    }

    private void configurarCliente(Duration callTimeout, Duration readTimeout) throws Exception {
        if (httpClient != null) httpClient.close();
        var endpoint = URI.create(url("/soap"));
        var properties = new PjeProperties(new PjeProperties.Endpoints(endpoint, endpoint, endpoint, endpoint),
                Duration.ofSeconds(1), readTimeout, callTimeout);
        var config = new SoapConfig();
        httpClient = config.pjeHttpClient(properties);
        var factory = config.pjeSoapMessageFactory();
        factory.afterPropertiesSet();
        template = config.webServiceTemplate(new WebServiceTemplateBuilder(), properties, httpClient, factory, dnsExecutor);
        client = new PjeSoapClient(template, new PjeRequestWriter(), new PjeResponseMapper(), new ResolverEndpointPje(properties));
    }

    @AfterEach
    void fechar() throws Exception {
        server.stop(0);
        handlers.shutdownNow();
        dnsExecutor.shutdownNow();
        if (httpClient != null) httpClient.close();
    }

    @Test
    void respostaSoapRealChegaAoMapper() throws Exception {
        String payload = XmlFixtures.movimentos().replaceFirst("<\\?xml[^?]*\\?>", "");
        server.createContext("/soap", exchange -> {
            requests.incrementAndGet();
            responder(exchange, 200, envelope(payload));
        });
        var resposta = consultar();
        assertEquals(StatusConsulta.ENCONTRADO, resposta.status());
        assertEquals(ORIGEM, resposta.origem());
        assertEquals(2, resposta.movimentacoes().size());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void prazoTotalCancelaServidorSilenciosoOuRespostaGotejada(boolean gotejar) throws Exception {
        configurarCliente(Duration.ofMillis(600), Duration.ofSeconds(5));
        var closed = new CountDownLatch(1);
        server.createContext("/soap", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                if (gotejar) {
                    exchange.getResponseHeaders().set("Content-Type", "text/xml");
                    exchange.sendResponseHeaders(200, 0);
                    while (!Thread.currentThread().isInterrupted()) {
                        exchange.getResponseBody().write(' ');
                        exchange.getResponseBody().flush();
                        // Deliberately pace response bytes to exercise the total deadline, not polling.
                        //noinspection BusyWait
                        Thread.sleep(40);
                    }
                } else {
                    Thread.sleep(5000);
                }
            } catch (IOException e) {
                closed.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        long start = System.nanoTime();
        var resposta = assertTimeoutPreemptively(Duration.ofSeconds(3), this::consultar);
        assertFalha(StatusConsulta.INDISPONIVEL, resposta);
        assertTrue(Duration.ofNanos(System.nanoTime() - start).compareTo(Duration.ofMillis(300)) >= 0);
        assertEquals(1, requests.get());
        if (gotejar) assertTrue(closed.await(2, TimeUnit.SECONDS), "Cancelamento deve fechar a conexão HTTP");
    }

    @Test
    void timeoutDeLeituraPodeEncerrarAntesDoPrazoTotal() throws Exception {
        configurarCliente(Duration.ofSeconds(5), Duration.ofMillis(200));
        server.createContext("/soap", exchange -> {
            requests.incrementAndGet();
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        assertFalha(StatusConsulta.INDISPONIVEL,
                assertTimeoutPreemptively(Duration.ofSeconds(3), this::consultar));
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 503, 429})
    void naoRepeteFalhasDeConexaoOuHttp(int status) {
        server.createContext("/soap", exchange -> {
            requests.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            if (status == 0) exchange.close();
            else {
                exchange.getResponseHeaders().set("Retry-After", "0");
                responder(exchange, status, "falha-interna-sensivel");
            }
        });
        assertFalha(StatusConsulta.INDISPONIVEL, consultar());
        assertEquals(1, requests.get());
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 307, 308})
    void naoSegueRedirecionamento(int status) {
        var redirected = new AtomicInteger();
        server.createContext("/destino", exchange -> {
            redirected.incrementAndGet();
            responder(exchange, 200, "credenciais não deveriam chegar aqui");
        });
        server.createContext("/soap", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Location", url("/destino"));
            responder(exchange, status, "");
        });
        assertFalha(StatusConsulta.INDISPONIVEL, consultar());
        assertEquals(1, requests.get());
        assertEquals(0, redirected.get());
    }

    @Test
    void soapFaultNaoExpoeDetalhesRemotos() {
        server.createContext("/soap", exchange -> responder(exchange, 500, envelope(
                "<s:Fault><faultcode>s:Server</faultcode><faultstring>falha-interna-sensivel</faultstring></s:Fault>")));
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar());
    }

    @ParameterizedTest
    @ValueSource(strings = {"<xml>falha-interna-sensivel", "<html>falha-interna-sensivel</html>",
            "<Envelope xmlns='urn:invalido'><Body/></Envelope>"})
    void xmlMalformadoOuEnvelopeInvalidoViraFalhaTipada(String xml) {
        server.createContext("/soap", exchange -> responder(exchange, 200, xml));
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar());
    }

    @Test
    void dnsLentoNaoUltrapassaPrazoNemEnviaRequisicaoTardia() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var resolver = new SystemDefaultDnsResolver() {
            @Override public InetAddress[] resolve(String host) {
                entered.countDown();
                // Model a native lookup that does not respond to interruption.
                boolean interrupted = false;
                while (true) {
                    try { release.await(); break; }
                    catch (InterruptedException e) { interrupted = true; }
                }
                if (interrupted) Thread.currentThread().interrupt();
                finished.countDown();
                return new InetAddress[]{InetAddress.getLoopbackAddress()};
            }
        };
        template.setMessageSender(new PjeMessageSender(httpClient, Duration.ofMillis(600), dnsExecutor, resolver));
        server.createContext("/soap", exchange -> {
            requests.incrementAndGet();
            responder(exchange, 200, envelope(""));
        });
        try {
            assertFalha(StatusConsulta.INDISPONIVEL,
                    assertTimeoutPreemptively(Duration.ofSeconds(3), this::consultar));
            assertEquals(0, entered.getCount());
        } finally {
            release.countDown();
        }
        assertTrue(finished.await(2, TimeUnit.SECONDS));
        assertEquals(0, requests.get());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void rejeitaDtdEEntidadesSemAcessarRecursoExterno(boolean entidade) {
        var fetched = new AtomicInteger();
        server.createContext("/externo", exchange -> {
            fetched.incrementAndGet();
            responder(exchange, 200, "falha-interna-sensivel");
        });
        String doctype = entidade
                ? "<!DOCTYPE s:Envelope [<!ENTITY externo SYSTEM '" + url("/externo") + "'>]>"
                : "<!DOCTYPE s:Envelope SYSTEM '" + url("/externo") + "'>";
        server.createContext("/soap", exchange -> responder(exchange, 200,
                doctype + envelope(entidade ? "&externo;" : "")));
        assertFalha(StatusConsulta.RESPOSTA_INVALIDA, consultar());
        assertEquals(0, fetched.get());
    }

    private RespostaConsultaOrigem consultar() {
        return client.consultarProcesso(ORIGEM,
                new CredenciaisPje("senha-ficticia", new IdConsultante("01234567890")),
                new NumeroProcesso("00000000020264010000"));
    }

    private void assertFalha(StatusConsulta status, RespostaConsultaOrigem resposta) {
        assertEquals(status, resposta.status());
        assertEquals(ORIGEM, resposta.origem());
        assertTrue(resposta.movimentacoes().isEmpty());
        assertNotNull(resposta.erro());
        assertFalse(resposta.erro().contains("falha-interna-sensivel"));
    }

    private String url(String path) { return "http://127.0.0.1:" + server.getAddress().getPort() + path; }
    private String envelope(String payload) {
        return "<s:Envelope xmlns:s='http://schemas.xmlsoap.org/soap/envelope/'><s:Body>" + payload + "</s:Body></s:Envelope>";
    }
    private void responder(HttpExchange exchange, int status, String xml) throws IOException {
        try (exchange) {
            exchange.getRequestBody().readAllBytes();
            byte[] body = xml.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
        }
    }
}
