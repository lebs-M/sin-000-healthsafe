package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * The validated body of {@code POST /staffing/{wardId}/roster}, e.g.
 * {@code {"doctors":["Dr A","Dr B"]}}.
 *
 * Kept separate from the HTTP handler so the validation rules can be unit-tested
 * directly. Invalid input is reported as {@link IllegalArgumentException} (400).
 */
public final class RosterRequest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RosterRequest() {
    }

    public static List<String> parseDoctors(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("request body must be JSON: {\"doctors\": [\"...\"]}");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("request body must be valid JSON: {\"doctors\": [\"...\"]}");
        }
        JsonNode doctors = node.get("doctors");
        if (doctors == null || !doctors.isArray() || doctors.isEmpty()) {
            throw new IllegalArgumentException("'doctors' must be a non-empty array of names");
        }
        List<String> parsed = new ArrayList<>();
        for (JsonNode doctor : doctors) {
            if (!doctor.isTextual() || doctor.asText().isBlank()) {
                throw new IllegalArgumentException("every doctor name must be a non-blank string");
            }
            parsed.add(doctor.asText().trim());
        }
        return parsed;
    }
}
