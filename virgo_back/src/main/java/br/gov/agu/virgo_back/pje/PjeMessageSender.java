package br.gov.agu.virgo_back.pje;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.async.methods.SimpleRequestProducer;
import org.apache.hc.client5.http.async.methods.SimpleResponseConsumer;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.transport.WebServiceConnection;
import org.springframework.ws.transport.http.AbstractHttpSenderConnection;
import org.springframework.ws.transport.http.AbstractHttpWebServiceMessageSender;

import java.io.*;
import java.net.URI;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.*;

@NullMarked
final class PjeMessageSender extends AbstractHttpWebServiceMessageSender {
    private final CloseableHttpAsyncClient client;
    private final Duration callTimeout;
    private final ExecutorService dnsExecutor;
    private final DnsResolver dnsResolver;

    PjeMessageSender(CloseableHttpAsyncClient client, Duration callTimeout,
                     ExecutorService dnsExecutor, DnsResolver dnsResolver) {
        this.client = client;
        this.callTimeout = callTimeout;
        this.dnsExecutor = dnsExecutor;
        this.dnsResolver = dnsResolver;
    }

    @Override
    public WebServiceConnection createConnection(URI uri) {
        return new Connection(uri);
    }

    private final class Connection extends AbstractHttpSenderConnection {
        private final URI uri;
        private final long started = System.nanoTime();
        private final SimpleHttpRequest request;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private @Nullable Future<SimpleHttpResponse> exchange;
        private @Nullable Future<InetAddress[]> lookup;
        private @Nullable SimpleHttpResponse response;

        private Connection(URI uri) {
            this.uri = uri;
            this.request = SimpleHttpRequest.create("POST", uri);
        }

        @Override public URI getUri() { return uri; }
        @Override
        public void addRequestHeader(String name, String value) {
            if (!name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Transfer-Encoding")) {
                request.addHeader(name, value);
            }
        }
        @Override protected OutputStream getRequestOutputStream() { return body; }

        @Override
        protected void onSendAfterWrite(WebServiceMessage message) throws IOException {
            request.setBody(body.toByteArray(), ContentType.parse(request.getFirstHeader("Content-Type").getValue()));
            try {
                lookup = dnsExecutor.submit(() -> dnsResolver.resolve(uri.getHost()));
                InetAddress[] addresses = lookup.get(remaining(), TimeUnit.NANOSECONDS);
                if (addresses.length == 0) throw new IOException("Endpoint PJe sem endereço");
                remaining();
                var target = new HttpHost(uri.getScheme(), addresses[0], uri.getHost(), uri.getPort());
                exchange = client.execute(target, SimpleRequestProducer.create(request),
                        SimpleResponseConsumer.create(), null, null, null);
                response = exchange.get(remaining(), TimeUnit.NANOSECONDS);
            } catch (TimeoutException e) {
                if (lookup != null) lookup.cancel(true);
                if (exchange != null) exchange.cancel(true);
                throw new SocketTimeoutException("Prazo da chamada PJe excedido");
            } catch (InterruptedException e) {
                if (lookup != null) lookup.cancel(true);
                if (exchange != null) exchange.cancel(true);
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Chamada PJe interrompida");
            } catch (ExecutionException | CancellationException | RejectedExecutionException e) {
                throw new IOException("Falha no transporte PJe", e);
            }
        }

        private long remaining() throws TimeoutException {
            long remaining = callTimeout.toNanos() - (System.nanoTime() - started);
            if (remaining <= 0) throw new TimeoutException();
            return remaining;
        }

        private SimpleHttpResponse response() {
            return Objects.requireNonNull(response, "Resposta HTTP ainda não recebida");
        }

        @Override protected int getResponseCode() { return response().getCode(); }
        @Override protected String getResponseMessage() { return "HTTP " + response().getCode(); }
        @Override protected long getResponseContentLength() {
            byte[] bytes = response().getBodyBytes();
            return bytes == null ? 0 : bytes.length;
        }
        @Override protected InputStream getRawResponseInputStream() {
            byte[] bytes = response().getBodyBytes();
            return new ByteArrayInputStream(bytes == null ? new byte[0] : bytes);
        }
        @Override public Iterator<String> getResponseHeaderNames() {
            return Arrays.stream(response().getHeaders()).map(Header::getName).distinct().iterator();
        }
        @Override public Iterator<String> getResponseHeaders(String name) {
            return Arrays.stream(response().getHeaders(name)).map(Header::getValue).iterator();
        }
        @Override public void onClose() {
            if (lookup != null && !lookup.isDone()) lookup.cancel(true);
            if (exchange != null && !exchange.isDone()) exchange.cancel(true);
        }
    }
}
