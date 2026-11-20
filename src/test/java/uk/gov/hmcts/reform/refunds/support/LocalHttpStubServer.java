package uk.gov.hmcts.reform.refunds.support;

import com.sun.net.httpserver.HttpServer;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * Loopback HTTP stub on a random port for RestTemplateConfigurationTest and
 * OidcDependencyCompatibilityTest: serves canned JSON responses and records each request, so tests
 * can drive real HTTP clients and parsers instead of mocking them. Close it after each test.
 */
public final class LocalHttpStubServer implements AutoCloseable {

    private final HttpServer server;
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();

    public LocalHttpStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
    }

    public URI uri(String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }

    public void respond(String path, int status, String body) {
        server.createContext(path, exchange -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.putAll(exchange.getRequestHeaders());
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI(), headers,
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                byte[] response = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
                exchange.sendResponseHeaders(status, response.length);
                exchange.getResponseBody().write(response);
            } finally {
                exchange.close();
            }
        });
    }

    public Request nextRequest() throws InterruptedException {
        Request request = requests.poll(5, TimeUnit.SECONDS);
        assertThat(request).as("HTTP request received within five seconds").isNotNull();
        return request;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    // A plain class, not a record: the project's Checkstyle 7.8.2 cannot parse records.
    public static final class Request {
        private final String method;
        private final URI uri;
        private final HttpHeaders headers;
        private final String body;

        private Request(String method, URI uri, HttpHeaders headers, String body) {
            this.method = method;
            this.uri = uri;
            this.headers = headers;
            this.body = body;
        }

        public String method() {
            return method;
        }

        public URI uri() {
            return uri;
        }

        public HttpHeaders headers() {
            return headers;
        }

        public String body() {
            return body;
        }
    }
}
