package co.wethinkcode.healthsafe;

import java.util.List;

/** A ward as served by the ingestion-service, cleaned of legacy CSV noise. */
public record Ward(
        String wardId,
        String wing,
        String department,
        Integer bedsAvailable,
        List<String> notes) {
}
