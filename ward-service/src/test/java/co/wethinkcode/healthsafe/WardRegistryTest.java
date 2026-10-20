package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardRegistryTest {

    private static Ward ward(String id, String wing, String department) {
        return new Ward(id, wing, department, 3, List.of());
    }

    /** A WardSource whose behaviour each test controls; no HTTP involved. */
    private static final class FakeSource implements WardSource {
        private List<Ward> wards = List.of();
        private boolean fail;

        @Override
        public List<Ward> fetchWards() throws IOException {
            if (fail) {
                throw new IOException("ingestion-service down");
            }
            return wards;
        }
    }

    @Test
    void startsUnloadedAndBecomesLoadedAfterASuccessfulLoad() {
        FakeSource source = new FakeSource();
        source.wards = List.of(ward("W-01", "East Wing", "Cardiology"));
        WardRegistry registry = new WardRegistry(source);

        assertFalse(registry.isLoaded());
        assertTrue(registry.load());
        assertTrue(registry.isLoaded());
        assertEquals(1, registry.all().size());
    }

    @Test
    void failedLoadLeavesTheRegistryUnloaded() {
        FakeSource source = new FakeSource();
        source.fail = true;
        WardRegistry registry = new WardRegistry(source);

        assertFalse(registry.load());
        assertFalse(registry.isLoaded());
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void findIsCaseInsensitiveAndTrimsWhitespace() {
        FakeSource source = new FakeSource();
        source.wards = List.of(ward("W-05", "East Wing", "Paediatrics"));
        WardRegistry registry = new WardRegistry(source);
        registry.load();

        assertTrue(registry.find("w-05").isPresent());
        assertTrue(registry.find("  W-05 ").isPresent());
        assertTrue(registry.find("W-99").isEmpty());
        assertTrue(registry.find(null).isEmpty());
    }

    @Test
    void departmentsAreSortedDistinctAndIgnoreNull() {
        FakeSource source = new FakeSource();
        source.wards = List.of(
                ward("W-01", "East Wing", "Cardiology"),
                ward("W-02", "West Wing", "Cardiology"),
                ward("W-03", "East Wing", "ICU"),
                ward("W-04", "East Wing", null));
        WardRegistry registry = new WardRegistry(source);
        registry.load();

        assertEquals(List.of("Cardiology", "ICU"), registry.departments());
    }

    @Test
    void sortedOrdersByWardId() {
        FakeSource source = new FakeSource();
        source.wards = List.of(
                ward("W-03", "East Wing", "ICU"),
                ward("W-01", "East Wing", "Cardiology"),
                ward("W-02", "West Wing", "Paediatrics"));
        WardRegistry registry = new WardRegistry(source);
        registry.load();

        assertEquals(List.of("W-01", "W-02", "W-03"),
                registry.sorted().stream().map(Ward::wardId).toList());
    }

    @Test
    void reloadReplacesTheContentsAndReturnsTheCount() throws Exception {
        FakeSource source = new FakeSource();
        source.wards = List.of(ward("W-01", "East Wing", "Cardiology"));
        WardRegistry registry = new WardRegistry(source);
        registry.load();

        source.wards = List.of(ward("W-02", "West Wing", "ICU"), ward("W-03", "West Wing", "ICU"));

        assertEquals(2, registry.reload());
        assertTrue(registry.find("W-01").isEmpty());
        assertTrue(registry.find("W-02").isPresent());
    }
}
