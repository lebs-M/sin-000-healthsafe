package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Synchronous REST client for ward-service. Distinguishes a genuine 404
 * (unknown ward) from the service being unreachable, so the caller can answer
 * 404 vs 503 appropriately.
 */
public final class WardServiceClient {

    public record WardSummary(String wardId, String wing, String department, Integer bedsAvailable) {
    }

    private static final String BASE_URL =
            System.getenv().getOrDefault("WARD_URL", "http://localhost:7031");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public WardSummary requireWard(String wardId) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/wards/" + wardId))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return switch (response.statusCode()) {
                case 200 -> mapper.readValue(response.body(), WardSummary.class);
                case 404 -> throw new UnknownWardException("Unknown ward: " + wardId);
                default -> throw new DownstreamUnavailableException(
                        "ward-service returned HTTP " + response.statusCode());
            };
        } catch (IOException e) {
            throw new DownstreamUnavailableException("ward-service unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("ward-service call interrupted");
        }
    }
}
