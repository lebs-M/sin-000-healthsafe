package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

import static co.wethinkcode.healthsafe.FieldNormalizers.BedsIssue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldNormalizersTest {

    @Test
    void collapsesPaddingAndInternalWhitespaceAndTitleCases() {
        assertEquals("East Wing", FieldNormalizers.normalizeWing(" east wing "));
        assertEquals("South Wing", FieldNormalizers.normalizeWing("South  Wing"));
        assertEquals("North Wing", FieldNormalizers.normalizeWing("NORTH WING"));
    }

    @Test
    void blankOrPlaceholderWingBecomesNull() {
        assertNull(FieldNormalizers.normalizeWing(""));
        assertNull(FieldNormalizers.normalizeWing("  "));
        assertNull(FieldNormalizers.normalizeWing("N/A"));
    }

    @Test
    void normalisesIdCasingAndPadding() {
        assertEquals("W-05", FieldNormalizers.normalizeId("w-05"));
        assertEquals("W-03", FieldNormalizers.normalizeId(" W-03 "));
        assertNull(FieldNormalizers.normalizeId("TBD"));
    }

    @Test
    void canonicalisesDepartmentSpellingVariants() {
        assertEquals("Paediatrics", FieldNormalizers.normalizeDepartment("PAEDIATRICS"));
        assertEquals("Paediatrics", FieldNormalizers.normalizeDepartment("Pediatrics"));
        assertEquals("Paediatrics", FieldNormalizers.normalizeDepartment("paediatrics"));
        assertEquals("Cardiology", FieldNormalizers.normalizeDepartment("cardiology"));
        assertEquals("ICU", FieldNormalizers.normalizeDepartment("icu"));
    }

    @Test
    void flagsOnlySemanticDepartmentRemaps() {
        assertTrue(FieldNormalizers.departmentWasRemapped("Pediatrics"));
        assertFalse(FieldNormalizers.departmentWasRemapped("PAEDIATRICS"));
        assertFalse(FieldNormalizers.departmentWasRemapped("cardiology"));
    }

    @Test
    void parsesValidBedsAndClassifiesBadOnes() {
        assertEquals(3, FieldNormalizers.parseBeds(" 3 ").value());
        assertEquals(BedsIssue.NONE, FieldNormalizers.parseBeds("3").issue());

        assertEquals(BedsIssue.PLACEHOLDER, FieldNormalizers.parseBeds("N/A").issue());
        assertEquals(BedsIssue.PLACEHOLDER, FieldNormalizers.parseBeds("TBD").issue());
        assertEquals(BedsIssue.PLACEHOLDER, FieldNormalizers.parseBeds("unknown").issue());
        assertEquals(BedsIssue.PLACEHOLDER, FieldNormalizers.parseBeds("-").issue());
        assertEquals(BedsIssue.PLACEHOLDER, FieldNormalizers.parseBeds("NaN").issue());

        assertEquals(BedsIssue.NON_NUMERIC, FieldNormalizers.parseBeds("five").issue());
        assertEquals(BedsIssue.NON_NUMERIC, FieldNormalizers.parseBeds("full").issue());
        assertEquals(BedsIssue.NEGATIVE, FieldNormalizers.parseBeds("-1").issue());
        assertEquals(BedsIssue.UNREALISTIC, FieldNormalizers.parseBeds("2023").issue());

        assertNull(FieldNormalizers.parseBeds("five").value());
        assertNull(FieldNormalizers.parseBeds("-2").value());
        assertNull(FieldNormalizers.parseBeds("2023").value());
    }
}
