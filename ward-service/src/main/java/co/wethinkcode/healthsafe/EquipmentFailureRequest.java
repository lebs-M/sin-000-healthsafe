package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The validated body of {@code POST /wards/{id}/equipment-failure}, e.g.
 * {@code {"equipment":"Ventilator-3","detail":"not powering on"}}.
 *
 * Kept separate from the HTTP handler so the validation rules can be unit-tested
 * directly. Invalid input is reported as {@link IllegalArgumentException} (400).
 */
public record EquipmentFailureRequest(String equipment, String detail) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static EquipmentFailureRequest parse(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("request body must be JSON: {\"equipment\": \"...\"}");
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("request body must be valid JSON: {\"equipment\": \"...\"}");
        }
        JsonNode equipment = node.get("equipment");
        if (equipment == null || !equipment.isTextual() || equipment.asText().isBlank()) {
            throw new IllegalArgumentException("'equipment' is required and must be a non-blank name");
        }
        return new EquipmentFailureRequest(equipment.asText().trim(), node.path("detail").asText(""));
    }
}
