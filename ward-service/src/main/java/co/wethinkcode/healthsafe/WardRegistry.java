package co.wethinkcode.healthsafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds ward data fetched from the ingestion-service. The service can start
 * before ingestion is up: a background loader retries until the first
 * successful fetch, and callers see {@link #isLoaded()} = false until then.
 */
public final class WardRegistry {

    private static final Logger log = LoggerFactory.getLogger(WardRegistry.class);
    private static final long RETRY_MILLIS = 5_000;

    private final WardSource client;
    private final Map<String, Ward> wards = new ConcurrentHashMap<>();
    private volatile boolean loaded;

    public WardRegistry(WardSource client) {
        this.client = client;
    }

    /** Starts a daemon thread that keeps retrying until the first fetch succeeds. */
    public void startBackgroundLoader() {
        Thread loader = new Thread(this::loadUntilFirstSuccess, "ward-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void loadUntilFirstSuccess() {
        while (!load()) {
            try {
                Thread.sleep(RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** One fetch attempt; returns whether it succeeded. */
    public boolean load() {
        try {
            replace(client.fetchWards());
            return true;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("could not load wards from ingestion-service: {}", e.getMessage());
            return false;
        }
    }

    /** Synchronous refetch for the admin reload endpoint; propagates failures. */
    public int reload() throws IOException, InterruptedException {
        List<Ward> fetched = client.fetchWards();
        replace(fetched);
        return fetched.size();
    }

    private void replace(List<Ward> fetched) {
        Map<String, Ward> updated = new ConcurrentHashMap<>();
        for (Ward ward : fetched) {
            updated.put(ward.wardId().toUpperCase(), ward);
        }
        wards.clear();
        wards.putAll(updated);
        loaded = true;
        log.info("loaded {} wards from ingestion-service", wards.size());
    }

    public boolean isLoaded() {
        return loaded;
    }

    public Optional<Ward> find(String wardId) {
        if (wardId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(wards.get(wardId.trim().toUpperCase()));
    }

    public Collection<Ward> all() {
        return wards.values();
    }

    public List<String> departments() {
        TreeSet<String> departments = new TreeSet<>();
        for (Ward ward : wards.values()) {
            if (ward.department() != null) {
                departments.add(ward.department());
            }
        }
        return List.copyOf(departments);
    }

    public List<Ward> sorted() {
        return wards.values().stream()
                .sorted(Comparator.comparing(Ward::wardId))
                .toList();
    }
}
