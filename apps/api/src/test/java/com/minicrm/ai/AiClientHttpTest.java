package com.minicrm.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AiClientHttpTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void malformedResponseIsRejected() throws Exception {
        startServer(200, "[]");
        assertThrows(AiClient.AiProviderException.class, () -> client().generate(AiSuggestionType.INTENT_SCORE, context()));
    }

    @Test
    void retries429OnceThenFails() throws Exception {
        startServer(429, "{}");
        assertThrows(AiClient.AiProviderException.class, () -> client().generate(AiSuggestionType.INTENT_SCORE, context()));
    }

    @Test
    void retries502503And504ClassOfFailures() throws Exception {
        startServer(503, "{}");
        assertThrows(AiClient.AiProviderException.class, () -> client().generate(AiSuggestionType.INTENT_SCORE, context()));
    }

    private void startServer(int status, String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/intent-score", exchange -> {
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        });
        server.start();
    }

    private AiClient client() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(8));
        RestClient restClient = RestClient.builder().baseUrl("http://localhost:" + server.getAddress().getPort())
                .requestFactory(factory).build();
        AiProperties properties = new AiProperties();
        ReflectionTestUtils.setField(properties, "serviceToken", "test");
        ReflectionTestUtils.setField(properties, "model", "mock-model");
        return new AiClient(restClient, properties);
    }

    private AiDtos.LeadContext context() {
        return new AiDtos.LeadContext(UUID.randomUUID(), "hello", "NEW", UUID.randomUUID(), "");
    }
}
