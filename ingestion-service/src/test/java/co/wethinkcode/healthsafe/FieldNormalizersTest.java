package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.Test;

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
}
