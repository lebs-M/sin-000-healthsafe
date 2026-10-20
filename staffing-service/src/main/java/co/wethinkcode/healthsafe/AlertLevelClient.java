package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Synchronous REST client for the alert-level-service Emergency Status. */
public final class AlertLevelClient {

    private static final String BASE_URL =
            System.getenv().getOrDefault("ALERT_LEVEL_URL", "http://localhost:7032");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public int currentLevel() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + "/alert-level"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new DownstreamUnavailableException(
                        "alert-level-service returned HTTP " + response.statusCode());
            }
            JsonNode level = mapper.readTree(response.body()).get("level");
            if (level == null || !level.isIntegralNumber()) {
                throw new DownstreamUnavailableException("alert-level-service returned no usable level");
            }
            return level.asInt();
        } catch (IOException e) {
            throw new DownstreamUnavailableException("alert-level-service unavailable: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("alert-level-service call interrupted");
        }
    }
}
