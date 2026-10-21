package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provides on-call schedules for doctors based on ward and status. Validates
 * the ward with ward-service and reads the current Emergency Status from
 * alert-level-service over synchronous HTTP, then broadcasts roster changes on
 * the {@code staffing-events-topic} (stage 3) instead of being polled directly.
 */
public class StaffingServiceApp {

    private static final Logger log = LoggerFactory.getLogger(StaffingServiceApp.class);
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7033"));

    private final WardServiceClient wardClient = new WardServiceClient();
    private final AlertLevelClient alertClient = new AlertLevelClient();
    private final ScheduleCalculator calculator = new ScheduleCalculator();
    private final StaffingPublisher publisher = new StaffingPublisher();
    private final Map<String, StaffingEvent> rosters = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        new StaffingServiceApp().start();
    }

    void start() {
        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/staffing/{wardId}", this::getSchedule);
        app.get("/staffing", ctx -> ctx.json(rosters.values()));
        app.post("/staffing/{wardId}/roster", this::updateRoster);

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

    /**
     * State change: records an on-call roster for the ward and broadcasts it to
     * the staffing-events-topic so subscribers (ward-service) react without polling.
     */
    private void updateRoster(Context ctx) {
        String wardId = normalizeWardId(ctx.pathParam("wardId"));
        List<String> doctors = RosterRequest.parseDoctors(ctx.body());

        WardServiceClient.WardSummary ward = wardClient.requireWard(wardId);
        int alertLevel = alertClient.currentLevel();
        OnCallSchedule schedule = calculator.compute(ward, alertLevel, Instant.now().toString());

        StaffingEvent event = new StaffingEvent(
                StaffingEvent.ROSTER_UPDATED,
                schedule.wardId(),
                schedule.wing(),
                schedule.department(),
                schedule.alertLevel(),
                schedule.requiredDoctors(),
                schedule.onCall(),
                doctors,
                Instant.now().toString());

        rosters.put(wardId, event);
        publisher.publish(event);
        ctx.status(201).json(event);
    }

    private static String normalizeWardId(String wardId) {
        return wardId == null ? "" : wardId.trim().toUpperCase(Locale.ROOT);
    }
}
