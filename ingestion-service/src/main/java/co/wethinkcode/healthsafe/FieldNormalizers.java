package co.wethinkcode.healthsafe;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure normalisation helpers for the messy legacy CSV. Kept free of I/O so each
 * rule can be unit-tested in isolation.
 */
public final class FieldNormalizers {

    private static final Set<String> PLACEHOLDERS = Set.of(
            "", "-", "–", "—", "?", "n/a", "na", "tbd", "unknown", "nan", "null", "none");

    /**
     * Regional spelling / naming variants collapsed to one canonical label.
     * Unknown departments fall back to title case.
     */
    private static final Map<String, String> DEPARTMENT_SYNONYMS = Map.ofEntries(
            Map.entry("paediatrics", "Paediatrics"),
            Map.entry("pediatrics", "Paediatrics"),
            Map.entry("cardiology", "Cardiology"),
            Map.entry("oncology", "Oncology"),
            Map.entry("radiology", "Radiology"),
            Map.entry("icu", "ICU"),
            Map.entry("intensive care", "ICU"),
            Map.entry("intensive care unit", "ICU"),
            Map.entry("maternity", "Maternity"));

    private FieldNormalizers() {
    }

    public static String collapseWhitespace(String raw) {
        return raw == null ? "" : raw.trim().replaceAll("\\s+", " ");
    }

    public static boolean isPlaceholder(String raw) {
        return raw == null || PLACEHOLDERS.contains(collapseWhitespace(raw).toLowerCase(Locale.ROOT));
    }

    public static String titleCase(String text) {
        String collapsed = collapseWhitespace(text);
        if (collapsed.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(collapsed.length());
        for (String word : collapsed.split(" ")) {
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                out.append(word.substring(1).toLowerCase(Locale.ROOT));
            }
        }
        return out.toString();
    }

    /** {@code "w-05"} / {@code " w-05 "} → {@code "W-05"}; blank/placeholder → {@code null}. */
    public static String normalizeId(String raw) {
        if (isPlaceholder(raw)) {
            return null;
        }
        return collapseWhitespace(raw).toUpperCase(Locale.ROOT);
    }

    /** {@code "east wing "} → {@code "East Wing"}; missing → {@code null}. */
    public static String normalizeWing(String raw) {
        if (isPlaceholder(raw)) {
            return null;
        }
        return titleCase(raw);
    }

    /** Canonicalises spelling variants ({@code Pediatrics}/{@code PAEDIATRICS} → {@code Paediatrics}). */
    public static String normalizeDepartment(String raw) {
        if (isPlaceholder(raw)) {
            return null;
        }
        String canonical = DEPARTMENT_SYNONYMS.get(collapseWhitespace(raw).toLowerCase(Locale.ROOT));
        return canonical != null ? canonical : titleCase(raw);
    }

    /** True when the raw department needed synonym mapping (so the cleaner can flag it). */
    public static boolean departmentWasRemapped(String raw) {
        if (isPlaceholder(raw)) {
            return false;
        }
        String collapsed = collapseWhitespace(raw);
        String canonical = DEPARTMENT_SYNONYMS.get(collapsed.toLowerCase(Locale.ROOT));
        return canonical != null && !canonical.equals(titleCase(collapsed));
    }
}
