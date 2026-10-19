package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WardCleanerTest {

    private static WardCleaner.IngestionResult clean(String csv) throws IOException {
        return new WardCleaner().clean(new StringReader(csv));
    }

    private static Map<String, CleanedWard> byId(List<CleanedWard> wards) {
        return wards.stream().collect(Collectors.toMap(CleanedWard::getWardId, Function.identity()));
    }

    @Test
    void cleansTheRealLegacyExportWithoutCrashing() throws IOException {
        WardCleaner.IngestionResult result = IngestionServiceApp.loadWards();
        List<CleanedWard> wards = result.wards();

        // 18 data rows, one duplicate pair (W-05 / w-05) folded together
        assertEquals(18, result.report().getTotalDataRows());
        assertEquals(17, wards.size());
        assertEquals(1, result.report().getDuplicatesMerged());
        assertTrue(result.report().getRejectedRows().isEmpty());

        Map<String, CleanedWard> map = byId(wards);

        CleanedWard w05 = map.get("W-05");
        assertEquals("East Wing", w05.getWing());
        assertEquals("Paediatrics", w05.getDepartment());
        // first-seen valid value wins over the duplicate's invalid "five"
        assertEquals(5, w05.getBedsAvailable());
        assertTrue(w05.getNotes().stream().anyMatch(n -> n.contains("merged duplicate row 6")));

        // casing / padding all normalised, no duplicate keys survive
        assertEquals("Cardiology", map.get("W-03").getDepartment());
        assertEquals("South Wing", map.get("W-10").getWing());
        assertEquals("West Wing", map.get("W-12").getWing());
        assertEquals("Paediatrics", map.get("W-11").getDepartment());

        // bad numeric values flagged, not silently kept
        assertNull(map.get("W-04").getBedsAvailable());                       // -1
        assertEquals(0, map.get("W-03").getBedsAvailable());                   // 0 is legitimate
        assertNull(map.get("W-13").getBedsAvailable());                       // 2023 unrealistic
        assertNull(map.get("W-07").getBedsAvailable());                       // TBD
    }

    @Test
    void mergesDuplicateIdsKeepingFirstValidValuePerField() throws IOException {
        WardCleaner.IngestionResult result = clean("""
                ward_id,wing,department,beds_available
                w-05,east wing,PAEDIATRICS,five
                W-05,East Wing,Paediatrics,5
                """);

        assertEquals(1, result.wards().size());
        CleanedWard ward = result.wards().get(0);
        assertEquals("W-05", ward.getWardId());
        // the first row's beds were unusable, so the duplicate's valid value fills the gap
        assertEquals(5, ward.getBedsAvailable());
        assertEquals(List.of(1, 2), ward.getSourceRows());
        assertTrue(ward.getNotes().stream().anyMatch(n -> n.contains("non-numeric ('five')")));
    }

    @Test
    void recordsConflictsWhenDuplicatesDisagreeOnValidValues() throws IOException {
        CleanedWard ward = clean("""
                ward_id,wing,department,beds_available
                W-01,North Wing,ICU,4
                W-01,South Wing,ICU,6
                """).wards().get(0);

        assertEquals("North Wing", ward.getWing());
        assertEquals(4, ward.getBedsAvailable());
        assertTrue(ward.getNotes().stream().anyMatch(n -> n.contains("conflicting wing 'South Wing'")));
        assertTrue(ward.getNotes().stream().anyMatch(n -> n.contains("conflicting bedsAvailable 6")));
    }

    @Test
    void rejectsMalformedRowsWithoutAbortingTheParse() throws IOException {
        WardCleaner.IngestionResult result = clean("""
                ward_id,wing,department,beds_available
                W-01,North Wing,ICU,4
                W-02,only-three,columns-here
                W-03,South Wing,Maternity,2
                """);

        assertEquals(2, result.wards().size());
        assertEquals(1, result.report().getRejectedRows().size());
        IngestionReport.RejectedRow rejected = result.report().getRejectedRows().get(0);
        assertEquals(2, rejected.row());
        assertTrue(rejected.reason().contains("expected 4 columns"));
    }

    @Test
    void rejectsRowsWithNoUsableIdButKeepsGoing() throws IOException {
        WardCleaner.IngestionResult result = clean("""
                ward_id,wing,department,beds_available
                ,Nowhere,ICU,1
                W-02,West Wing,Cardiology,2
                N/A,East Wing,ICU,3
                """);

        assertEquals(1, result.wards().size());
        assertEquals("W-02", result.wards().get(0).getWardId());
        assertEquals(2, result.report().getRejectedRows().size());
    }

    @Test
    void flagsMissingFieldsAndRemappedDepartmentsInNotes() throws IOException {
        Map<String, CleanedWard> map = byId(clean("""
                ward_id,wing,department,beds_available
                W-08,,Oncology,4
                W-11,East Wing,Pediatrics,3
                """).wards());

        assertNull(map.get("W-08").getWing());
        assertTrue(map.get("W-08").getNotes().contains("wing was missing — set to null"));

        assertEquals("Paediatrics", map.get("W-11").getDepartment());
        assertTrue(map.get("W-11").getNotes().stream()
                .anyMatch(n -> n.contains("canonicalised to 'Paediatrics'")));
    }

    @Test
    void normalisesExtraDateAndBooleanColumnsWhenPresent() throws IOException {
        WardCleaner.IngestionResult result = clean("""
                ward_id,wing,department,beds_available,last_updated,is_active
                W-01,North Wing,ICU,4,01-02-2023,Y
                W-02,South Wing,Maternity,2,13/45/2023,maybe
                """);

        Map<String, CleanedWard> map = byId(result.wards());
        assertEquals("2023-02-01", map.get("W-01").getAttributes().get("last_updated"));
        assertEquals(Boolean.TRUE, map.get("W-01").getAttributes().get("is_active"));

        assertNull(map.get("W-02").getAttributes().get("last_updated"));
        assertNull(map.get("W-02").getAttributes().get("is_active"));
        assertTrue(map.get("W-02").getNotes().stream()
                .anyMatch(n -> n.contains("invalid date ('13/45/2023')")));
        assertTrue(map.get("W-02").getNotes().stream()
                .anyMatch(n -> n.contains("unrecognized flag ('maybe')")));
    }

    @Test
    void skipsBlankLines() throws IOException {
        WardCleaner.IngestionResult result = clean("""
                ward_id,wing,department,beds_available

                W-01,North Wing,ICU,4
                """);

        assertEquals(1, result.wards().size());
        assertEquals(2, result.report().getTotalDataRows());
        assertTrue(result.report().getRejectedRows().isEmpty());
    }
}
