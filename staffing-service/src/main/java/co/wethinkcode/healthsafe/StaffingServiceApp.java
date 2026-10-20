package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Provides on-call schedules for doctors based on ward and status. Validates
 * the ward with ward-service and reads the current Emergency Status from
 * alert-level-service over synchronous HTTP.
 */
public class StaffingServiceApp {

    private static final Logger log = LoggerFactory.getLogger(StaffingServiceApp.class);
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7033"));

    private final WardServiceClient wardClient = new WardServiceClient();
    private final AlertLevelClient alertClient = new AlertLevelClient();
    private final ScheduleCalculator calculator = new ScheduleCalculator();

    public static void main(String[] args) {
        new StaffingServiceApp().start();
    }

    void start() {
        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/staffing/{wardId}", this::getSchedule);

        app.exception(UnknownWardException.class,
                (e, ctx) -> ctx.status(404).json(Map.of("error", e.getMessage())));
        app.exception(DownstreamUnavailableException.class,
                (e, ctx) -> ctx.status(503).json(Map.of("error", e.getMessage())));
        app.exception(IllegalArgumentException.class,
                (e, ctx) -> ctx.status(400).json(Map.of("error", e.getMessage())));

        log.info("staffing-service listening on {}", PORT);
    }

    /** Computes a schedule on demand, without recording it. */
    private void getSchedule(Context ctx) {
        String wardId = normalizeWardId(ctx.pathParam("wardId"));
        WardServiceClient.WardSummary ward = wardClient.requireWard(wardId);
        int alertLevel = alertClient.currentLevel();
        ctx.json(calculator.compute(ward, alertLevel, Instant.now().toString()));
    }

    private static String normalizeWardId(String wardId) {
        return wardId == null ? "" : wardId.trim().toUpperCase(Locale.ROOT);
    }
}
