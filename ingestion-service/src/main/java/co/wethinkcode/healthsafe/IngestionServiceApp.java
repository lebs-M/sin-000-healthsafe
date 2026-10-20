package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Stage 1 of HealthSafe: reads and cleans the legacy {@code wards-outdated.csv}
 * export once at startup, then serves the cleaned records to the other services.
 */
public class IngestionServiceApp {

    private static final Logger log = LoggerFactory.getLogger(IngestionServiceApp.class);
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7030"));

    public static void main(String[] args) throws IOException {
        WardCleaner.IngestionResult result = loadWards();
        List<CleanedWard> wards = result.wards();
        IngestionReport report = result.report();

        log.info("cleaned {} data rows -> {} wards ({} duplicates merged, {} rejected)",
                report.getTotalDataRows(), wards.size(), report.getDuplicatesMerged(),
                report.getRejectedRows().size());
        report.getRejectedRows()
                .forEach(rejected -> log.warn("rejected row {}: {} [{}]",
                        rejected.row(), rejected.reason(), rejected.raw()));

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(wards));
        app.get("/wards/report", ctx -> ctx.json(report));
        app.get("/wards/{id}", ctx -> {
            String id = FieldNormalizers.normalizeId(ctx.pathParam("id"));
            wards.stream()
                    .filter(ward -> ward.getWardId().equals(id))
                    .findFirst()
                    .ifPresentOrElse(ctx::json,
                            () -> ctx.status(404).result("Unknown ward: " + ctx.pathParam("id")));
        });
    }

    static WardCleaner.IngestionResult loadWards() throws IOException {
        try (Reader reader = new InputStreamReader(
                IngestionServiceApp.class.getResourceAsStream("/wards-outdated.csv"),
                StandardCharsets.UTF_8)) {
            return new WardCleaner().clean(reader);
        }
    }
}
