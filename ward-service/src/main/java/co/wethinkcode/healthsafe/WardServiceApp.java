package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Provides lists of wards and departments, sourced from the ingestion-service.
 */
public class WardServiceApp {

    private static final Logger log = LoggerFactory.getLogger(WardServiceApp.class);
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7031"));

    public static void main(String[] args) {
        WardRegistry registry = new WardRegistry(new IngestionClient());
        registry.startBackgroundLoader();

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));

        app.get("/wards", ctx -> {
            if (!ready(registry, ctx)) {
                return;
            }
            ctx.json(registry.sorted());
        });

        app.get("/departments", ctx -> {
            if (!ready(registry, ctx)) {
                return;
            }
            ctx.json(registry.departments());
        });

        app.get("/wards/{id}", ctx -> {
            if (!ready(registry, ctx)) {
                return;
            }
            Optional<Ward> ward = registry.find(ctx.pathParam("id"));
            if (ward.isEmpty()) {
                ctx.status(404).json(Map.of("error", "Unknown ward: " + ctx.pathParam("id")));
                return;
            }
            ctx.json(ward.get());
        });

        app.post("/wards/reload", ctx -> {
            try {
                int count = registry.reload();
                ctx.json(Map.of("loaded", count));
            } catch (IOException e) {
                ctx.status(502).json(Map.of("error", "ingestion-service unavailable: " + e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                ctx.status(502).json(Map.of("error", "ingestion-service call interrupted"));
            }
        });

        log.info("ward-service listening on {}", PORT);
    }

    private static boolean ready(WardRegistry registry, Context ctx) {
        if (!registry.isLoaded()) {
            ctx.status(503).json(Map.of(
                    "error", "ward data not loaded yet — is ingestion-service running on port 7030?"));
            return false;
        }
        return true;
    }
}
