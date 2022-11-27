package dev.mccue.rosie.microhttp;

import dev.mccue.rosie.Body;
import dev.mccue.rosie.IntoResponse;
import dev.mccue.rosie.Request;
import org.microhttp.*;

import java.io.*;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;

public final class MicrohttpAdapter {
    private MicrohttpAdapter() {}

    private static final Function<Integer, String> STATUS_TO_REASON = (status) -> Map.ofEntries(
            Map.entry(100, "Continue"),
            Map.entry(101, "Switching Protocols"),
            Map.entry(200, "OK"),
            Map.entry(201, "Created"),
            Map.entry(202, "Accepted"),
            Map.entry(203, "Non-Authoritative Information"),
            Map.entry(204, "No Content"),
            Map.entry(205, "Reset Content"),
            Map.entry(206, "Partial Content"),
            Map.entry(300, "Multiple Choices"),
            Map.entry(301, "Moved Permanently"),
            Map.entry(302, "Found"),
            Map.entry(303, "See Other"),
            Map.entry(304, "Not Modified"),
            Map.entry(305, "Use Proxy"),
            Map.entry(307, "Temporary Redirect"),
            Map.entry(400, "Bad Request"),
            Map.entry(401, "Unauthorized"),
            Map.entry(402, "Payment Required"),
            Map.entry(403, "Forbidden"),
            Map.entry(404, "Not Found"),
            Map.entry(405, "Method Not Allowed"),
            Map.entry(406, "Not Acceptable"),
            Map.entry(407, "Proxy Authentication Required"),
            Map.entry(408, "Request Time-out"),
            Map.entry(409, "Conflict"),
            Map.entry(410, "Gone"),
            Map.entry(411, "Length Required"),
            Map.entry(412, "Precondition Failed"),
            Map.entry(413, "Request Entity Too Large"),
            Map.entry(414, "Request-URI Too Large"),
            Map.entry(415, "Unsupported Media Type"),
            Map.entry(416, "Requested range not satisfiable"),
            Map.entry(417, "Expectation Failed"),
            Map.entry(500, "Internal Server Error"),
            Map.entry(501, "Not Implemented"),
            Map.entry(502, "Bad Gateway"),
            Map.entry(503, "Service Unavailable"),
            Map.entry(504, "Gateway Time-out"),
            Map.entry(505, "HTTP Version not supported")
    ).getOrDefault(status, "Unknown");
    private static final class MicrohttpHandler implements Handler {

        private final Options options;
        private final Function<Request, dev.mccue.rosie.Response> handler;
        private final ExecutorService executorService;

        MicrohttpHandler(
                Function<Request, dev.mccue.rosie.Response> handler,
                Options options,
                ExecutorService executorService
        ) {
            this.handler = handler;
            this.options = options;
            this.executorService = executorService;
        }

        @Override
        public void handle(org.microhttp.Request request, Consumer<Response> consumer) {
            var errorResponse = new Response(
                    500,
                    STATUS_TO_REASON.apply(500),
                    List.of(),
                    new byte[] {}
            );

            executorService.submit(() -> {
                Response response = errorResponse;
                try {
                    var rosieRequest = MicrohttpAdapter.fromMicrohttpRequest(
                            options.host(),
                            options.port(),
                            request
                    );
                    response = toMicrohttpResponse(handler.apply(rosieRequest).intoResponse());
                } finally {
                    consumer.accept(response);
                }
            });
        }
    }

    public static void runServer(
            Function<Request, dev.mccue.rosie.Response> handler,
            Options options,
            ExecutorService executorService
    ) {
        try {
            var eventLoop = new EventLoop(options,new MicrohttpHandler(handler, options, executorService));
            eventLoop.start();
            eventLoop.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static Response toMicrohttpResponse(dev.mccue.rosie.Response response) {
        var baos = new ByteArrayOutputStream();
        response.body().writeToStream(baos);
        return new Response(
                response.status(),
                STATUS_TO_REASON.apply(response.status()),
                response.headers()
                        .entrySet()
                        .stream()
                        .map(entry -> new Header(entry.getKey(), entry.getValue()))
                        .toList(),
                baos.toByteArray()
        );
    }

    public static Request fromMicrohttpRequest(String host, int port, org.microhttp.Request request) {
        return new MicrohttpRequest(host, port, request);
    }

    private static final class MicrohttpRequest implements Request {
        private final String host;
        private final int port;
        private final String uri;
        private final String queryString;
        private final Map<String, String> headers;
        private final ByteArrayInputStream body;
        private final org.microhttp.Request request;

        private MicrohttpRequest(String host, int port, org.microhttp.Request request) {
            this.host = host;
            this.port = port;
            this.request = request;

            var split = request.uri().split("\\?", 2);

            if (split.length == 1) {
                this.uri = split[0];
                this.queryString = null;
            }
            else {
                this.uri = split[0];
                this.queryString = split[1];
            }

            var headers = new HashMap<String, String>();
            for (var header : request.headers()) {
                headers.put(header.name().toLowerCase(), header.value());
            }
            this.headers = Collections.unmodifiableMap(headers);
            this.body = new ByteArrayInputStream(request.body() == null ? new byte[]{} : request.body());
        }

        @Override
        public int serverPort() {
            return port;
        }

        @Override
        public String serverName() {
            return host;
        }

        @Override
        public String remoteAddr() {
            return "";
        }

        @Override
        public String uri() {
            return uri;
        }

        @Override
        public Optional<String> queryString() {
            return Optional.ofNullable(queryString);
        }

        @Override
        public String scheme() {
            return "http";
        }

        @Override
        public String requestMethod() {
            return this.request.method().toLowerCase();
        }

        @Override
        public String protocol() {
            return "HTTP/1.1";
        }

        @Override
        public Map<String, String> headers() {
            return this.headers;
        }

        @Override
        public Optional<X509Certificate> sslClientCert() {
            return Optional.empty();
        }

        @Override
        public InputStream body() {
            return body;
        }
    }
}
