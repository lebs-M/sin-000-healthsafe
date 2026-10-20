package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/** Minimal synchronous HTTP client for the ingestion-service {@code GET /wards}. */
public final class IngestionClient implements WardSource {

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private final ObjectMapper mapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public IngestionClient() {
        this(System.getenv().getOrDefault("INGESTION_URL", "http://localhost:7030"));
    }

    IngestionClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public List<Ward> fetchWards() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/wards"))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("ingestion-service returned HTTP " + response.statusCode());
        }
        return mapper.readValue(response.body(), new TypeReference<List<Ward>>() {
        });
    }
}
