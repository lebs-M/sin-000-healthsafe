package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stage 2: tracks the hospital Emergency Status (0-8, 8 = full Code Blue) and
 * exposes it for {@code staffing-service} to size on-call schedules.
 */
public class AlertLevelServiceApp {

    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 8;

    private static final Logger log = LoggerFactory.getLogger(AlertLevelServiceApp.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7032"));

    private static final AtomicInteger LEVEL = new AtomicInteger(0);
    private static final AtomicReference<Instant> UPDATED_AT = new AtomicReference<>(Instant.now());

    public static void main(String[] args) {
        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alert-level", AlertLevelServiceApp::readLevel);
        app.put("/alert-level", AlertLevelServiceApp::updateLevel);
        app.post("/alert-level", AlertLevelServiceApp::updateLevel);

        log.info("alert-level-service listening on {}, current status {}", PORT, LEVEL.get());
    }

    private static void readLevel(Context ctx) {
        ctx.json(state());
    }

    private static void updateLevel(Context ctx) {
        int requested;
        try {
            requested = parseLevel(ctx.body());
        } catch (IllegalArgumentException e) {
            ctx.status(400).json(Map.of("error", e.getMessage()));
            return;
        }

        LEVEL.set(requested);
        UPDATED_AT.set(Instant.now());
        log.info("emergency status changed to {}", requested);
        ctx.json(state());
    }

    private static Map<String, Object> state() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", LEVEL.get());
        body.put("updatedAt", UPDATED_AT.get().toString());
        return body;
    }

    /**
     * Parses and validates a {@code {"level": n}} request body. Kept separate
     * from the HTTP handler so the range validation can be unit-tested directly.
     */
    static int parseLevel(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("request body must be JSON: {\"level\": 0-" + MAX_LEVEL + "}");
        }
        JsonNode levelNode;
        try {
            levelNode = MAPPER.readTree(body).get("level");
        } catch (Exception e) {
            throw new IllegalArgumentException("request body must be valid JSON: {\"level\": 0-" + MAX_LEVEL + "}");
        }
        if (levelNode == null || !levelNode.isIntegralNumber()) {
            throw new IllegalArgumentException("'level' must be an integer between "
                    + MIN_LEVEL + " and " + MAX_LEVEL);
        }
        int level = levelNode.asInt();
        if (level < MIN_LEVEL || level > MAX_LEVEL) {
            throw new IllegalArgumentException("'level' must be between " + MIN_LEVEL + " and " + MAX_LEVEL);
        }
        return level;
    }
}
