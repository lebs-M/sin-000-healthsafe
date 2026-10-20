package co.wethinkcode.healthsafe;

import java.io.IOException;
import java.util.List;

/**
 * Supplies cleaned ward records to {@link WardRegistry}.
 *
 * The production implementation is {@link IngestionClient} (an HTTP call to the
 * ingestion-service); tests substitute a trivial in-memory source so the
 * registry's merge/lookup logic can be exercised without a running service.
 */
public interface WardSource {

    List<Ward> fetchWards() throws IOException, InterruptedException;
}
