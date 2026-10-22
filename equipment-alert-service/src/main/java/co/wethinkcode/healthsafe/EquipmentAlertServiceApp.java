package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 4: uses the {@code equipment-failure-queue} to guarantee delivery of
 * critical medical equipment failure alerts. Exposes the alerts it has consumed
 * for observability.
 */
public class EquipmentAlertServiceApp {

    private static final Logger log = LoggerFactory.getLogger(EquipmentAlertServiceApp.class);
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7034"));

    public static void main(String[] args) {
        EquipmentAlertConsumer consumer = new EquipmentAlertConsumer();
        consumer.start();

        Javalin app = Javalin.create().start(PORT);

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/alerts", ctx -> ctx.json(consumer.processed()));

        log.info("equipment-alert-service listening on {}", PORT);
    }
}
